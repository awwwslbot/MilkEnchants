package com.windsor.meowsEnchants.tracking;

import com.windsor.meowsEnchants.arrowrain.ArrowRainListener;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

/**
 * The tracking enchantment: marks every projectile a player fires from a tracking bow or crossbow,
 * steers it on its own entity scheduler, and protects named mobs and untrackable bystanders.
 * Level and shooter location are captured at the shot; the hand is never read again.
 */
public final class TrackingListener implements Listener, AutoCloseable, ArrowRainListener.RainObserver {
    public static final NamespacedKey ENCHANT = new NamespacedKey("meowsenchants", "tracking");
    /** BYTE tracking level on every projectile from a tracking weapon, rain arrows included. */
    public static final NamespacedKey MARK = new NamespacedKey("meowsenchants", "tracking_level");
    private static final long PENDING_NANOS = TimeUnit.SECONDS.toNanos(5);

    private final Plugin plugin;
    private final TrackingSettings settings;
    private final CombatTracker combat;
    private final TargetRules rules;
    private final Particle particle;
    private final Sound chime;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    /** Shot but not yet launched: ProjectileLaunchEvent may still be cancelled by protection plugins. */
    private final Map<UUID, Tracker> pending = new ConcurrentHashMap<>();
    /** Projectiles still tracking; a tracker leaves this map as soon as it stops. */
    private final Map<UUID, Tracker> trackers = new ConcurrentHashMap<>();
    /**
     * Every tracking projectile's own shot (shooter session and shot-time view), kept until the
     * projectile is removed whether or not it still tracks: a firework stops tracking when it hits,
     * and its blast is filtered afterwards.
     */
    private final Map<UUID, ShotRecord> shots = new ConcurrentHashMap<>();
    private final Map<UUID, Shooter> shooters = new ConcurrentHashMap<>();

    /** particle and chime are resolved from settings by {@link TrackingConfig}; a null chime is silent. */
    TrackingListener(Plugin plugin, TrackingSettings settings, CombatTracker combat, TargetRules rules,
                     Particle particle, Sound chime) {
        this.plugin = plugin;
        this.settings = settings;
        this.combat = combat;
        this.rules = rules;
        this.particle = particle;
        this.chime = chime;
    }

    public CombatTracker combat() { return combat; }
    TrackingSettings settings() { return settings; }
    TargetRules rules() { return rules; }
    Logger logger() { return plugin.getLogger(); }
    boolean active() { return enabled.get(); }

    public static boolean isMarked(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(MARK, PersistentDataType.BYTE);
    }

    /** Tracking level on a bow or crossbow; 0 for anything else. */
    public static int enchantLevel(ItemStack weapon) {
        if (weapon == null || weapon.getType() != Material.BOW && weapon.getType() != Material.CROSSBOW) return 0;
        return levelOf(weapon.getEnchantments());
    }

    private static int levelOf(Map<? extends Keyed, Integer> enchantments) {
        for (Map.Entry<? extends Keyed, Integer> entry : enchantments.entrySet()) {
            if (ENCHANT.equals(entry.getKey().getKey())) return Math.max(0, entry.getValue());
        }
        return 0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!enabled.get() || !(event.getEntity() instanceof Player player)) return;
        Entity entity = event.getProjectile();
        Tracker.Kind kind = entity instanceof Firework ? Tracker.Kind.FIREWORK
                : entity instanceof AbstractArrow && !(entity instanceof Trident) ? Tracker.Kind.ARROW : null;
        if (kind == null || ArrowRainListener.isRain(entity)) return;
        int level = enchantLevel(event.getBow());
        if (level == 0) return;
        // This event runs on the shooter's region thread: the only place its position is read.
        Location shotLocation = player.getLocation();
        Shooter shooter = shooters.compute(player.getUniqueId(),
                (id, old) -> old != null && old.player == player && old.online ? old : new Shooter(player));
        entity.getPersistentDataContainer().set(MARK, PersistentDataType.BYTE, (byte) Math.min(level, Byte.MAX_VALUE));
        long now = System.nanoTime();
        pending.values().removeIf(stale -> now - stale.createdNanos > PENDING_NANOS);
        pending.put(entity.getUniqueId(), new Tracker(this, (Projectile) entity, kind, level, shooter, shotLocation, null));
    }

    /** Each multishot projectile has its own shoot and launch event. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLaunch(ProjectileLaunchEvent event) {
        Tracker tracker = pending.remove(event.getEntity().getUniqueId());
        if (tracker != null && !event.isCancelled()) start(tracker);
    }

    /** Rain arrows fire no shoot event; the arrow rain listener hands them over after spawning. */
    @Override
    public void rainSpawned(Arrow arrow, Player player, ItemStack weapon, Location shotLocation, Runnable stopTrail) {
        if (!enabled.get()) return;
        int level = enchantLevel(weapon);
        if (level == 0) return;
        arrow.getPersistentDataContainer().set(MARK, PersistentDataType.BYTE, (byte) Math.min(level, Byte.MAX_VALUE));
        Shooter shooter = shooters.get(player.getUniqueId());
        if (shooter == null || shooter.player != player || !shooter.online) return;
        start(new Tracker(this, arrow, Tracker.Kind.RAIN, level, shooter, shotLocation, stopTrail));
    }

    private void start(Tracker tracker) {
        if (!enabled.get()) return;
        trackers.put(tracker.id, tracker);
        shots.put(tracker.id, new ShotRecord(tracker.shooter, tracker.view));
        try {
            ScheduledTask task = tracker.projectile.getScheduler().runAtFixedRate(plugin, tracker::tick,
                    () -> forgetProjectile(tracker.id), 1L, 1L);
            if (task == null) {
                forgetProjectile(tracker.id);
                return;
            }
            tracker.task = task;
            if (!enabled.get()) tracker.stop(); // close() raced the registration.
        } catch (IllegalPluginAccessException ignored) {
            forgetProjectile(tracker.id); // Disabled concurrently.
        }
    }

    /** Called by a tracker that stops; its shot record stays until the projectile is removed. */
    void stopped(Tracker tracker) { trackers.remove(tracker.id, tracker); }

    private void forgetProjectile(UUID id) {
        pending.remove(id);
        shots.remove(id);
        Tracker tracker = trackers.remove(id);
        if (tracker != null) tracker.stop();
    }

    /**
     * Named mobs are never hurt by a tracking weapon's projectiles: the hit is cancelled so arrows
     * pass through and fireworks do not explode on them. LOWEST lets other plugins see it cancelled.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHitNamed(ProjectileHitEvent event) {
        if (TargetRules.isNamed(event.getHitEntity()) && isMarked(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        Tracker tracker = trackers.get(event.getEntity().getUniqueId());
        if (tracker != null) tracker.hit(event.getHitEntity());
    }

    /**
     * A tracking firework's blast only hurts targets the shooter could track; any damage from a
     * tracking projectile to a named mob is cancelled as a backstop for the hit cancellation.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!isMarked(damager)) return;
        Entity victim = event.getEntity();
        if (TargetRules.isNamed(victim)) {
            event.setCancelled(true);
            return;
        }
        if (!(damager instanceof Firework firework)) return;
        // The rocket's own shot record, not the shooter's live state or latest shot. Without a record
        // (fired before a reload, or its chunk was unloaded) or once the shooter has left, nothing is
        // trackable for that shot any more, so the blast hurts nobody.
        ShotRecord shot = shots.get(firework.getUniqueId());
        if (shot == null || !shot.shooter().online || !(victim instanceof LivingEntity living)
                || !rules.trackable(living, shot.view())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) { forgetProjectile(event.getEntity().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Shooter shooter = shooters.remove(event.getPlayer().getUniqueId());
        if (shooter != null) shooter.online = false; // Its projectiles stop on their next tick.
    }

    /** One chime per shooter per region tick, played on the shooter's own scheduler. */
    void chime(Shooter shooter, long gameTick) {
        if (chime == null || !shooter.online || !shooter.claimChime(gameTick)) return;
        try {
            shooter.player.getScheduler().run(plugin, task -> {
                // Only the shooter hears it, following the shooter's position.
                if (enabled.get() && shooter.online) shooter.player.playSound(chime, Sound.Emitter.self());
            }, null);
        } catch (IllegalPluginAccessException ignored) { /* Disabled concurrently. */ }
    }

    void emit(Location at) {
        if (particle == null) return;
        int min = settings.particleCountMin(), max = settings.particleCountMax();
        int count = max > min ? ThreadLocalRandom.current().nextInt(min, max + 1) : min;
        if (count <= 0) return;
        double offset = settings.particleOffset();
        at.getWorld().spawnParticle(particle, at, count, offset, offset, offset, settings.particleSpeed());
    }

    Tracker tracker(UUID projectile) { return trackers.get(projectile); }

    boolean hasShotRecord(UUID projectile) { return shots.containsKey(projectile); }

    /** A projectile's shooter session and the view taken when it was fired. */
    private record ShotRecord(Shooter shooter, Shooter.View view) {}

    /** Logs the first failure of a tracking task; later ones only stop their own projectile. */
    void failed(Throwable error) {
        if (failureLogged.compareAndSet(false, true)) {
            plugin.getLogger().log(Level.WARNING, "Tracking task failed; that projectile stops tracking", error);
        }
    }

    @Override
    public void close() {
        enabled.set(false);
        trackers.values().forEach(Tracker::stop);
        trackers.clear();
        shots.clear();
        pending.clear();
        shooters.values().forEach(shooter -> shooter.online = false);
        shooters.clear();
        combat.clear();
    }
}
