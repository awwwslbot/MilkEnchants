package com.windsor.meowsEnchants.tracking;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Players who recently hurt, or were hurt by, another player. Damage events arrive on the victim's
 * region thread while projectiles read from theirs, so the table is a concurrent map of timestamps.
 */
public final class CombatTracker implements Listener, TargetRules.Combat {
    private final Map<UUID, Long> lastPvp = new ConcurrentHashMap<>();
    private volatile long windowNanos;
    private final LongSupplier clock;

    public CombatTracker(long windowNanos) { this(windowNanos, System::nanoTime); }

    CombatTracker(long windowNanos, LongSupplier clock) {
        this.windowNanos = windowNanos;
        this.clock = clock;
    }

    /** Only damage that really landed counts: the final damage after armour, blocking and absorption. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0.0) return;
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (victim.hasMetadata("NPC") || attacker.hasMetadata("NPC")) return;
        long now = clock.getAsLong();
        lastPvp.put(victim.getUniqueId(), now);
        lastPvp.put(attacker.getUniqueId(), now);
    }

    /** Melee attackers and the shooters of arrows, fireworks, tridents and other projectiles. */
    static Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    /** pvp_combat_seconds changed by a reload; applies to every later check. */
    public void setWindowNanos(long windowNanos) { this.windowNanos = windowNanos; }

    @Override
    public boolean inCombat(UUID player) {
        Long at = lastPvp.get(player);
        return at != null && clock.getAsLong() - at <= windowNanos;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { lastPvp.remove(event.getPlayer().getUniqueId()); }

    public void clear() { lastPvp.clear(); }
}
