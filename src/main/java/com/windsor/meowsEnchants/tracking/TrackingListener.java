package com.windsor.meowsEnchants.tracking;

import com.windsor.meowsEnchants.arrowrain.ArrowRainListener;
import com.windsor.meowsEnchants.listeners.MechanicCooldown;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
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
    private final CombatTracker combat;
    private final TargetRules.Protection protection;
    private final Predicate<World> pvpRule;
    /**
     * The enchantment's cooldown, shared with arrow rain: checked at each shot, started when a shot starts
     * tracking. All projectiles of one multishot volley count as one shot; rain arrows never touch it.
     */
    private final MechanicCooldown cooldown;
    /** Current tunables and the rules built from them; null while tracking.yml has no tracking_v1 mechanic. */
    private volatile Runtime runtime;
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

    /**
     * One settings snapshot: a tracker keeps the snapshot it started with, so a reload never changes a
     * projectile already in flight. particle and chime are resolved from the settings; a null chime is silent.
     */
    record Runtime(TrackingSettings settings, TargetRules rules, Particle particle, Sound chime) {}

    /** Fixed settings, as built by tests. */
    TrackingListener(Plugin plugin, TrackingSettings settings, CombatTracker combat, TargetRules rules,
                     Particle particle, Sound chime, MechanicCooldown cooldown) {
        this.plugin = plugin;
        this.combat = combat;
        this.protection = TargetRules.Protection.NONE;
        this.pvpRule = TargetRules.VANILLA_PVP;
        this.cooldown = cooldown;
        this.runtime = new Runtime(settings, rules, particle, chime);
    }

    /** Starts disabled; {@link #apply} switches it on. */
    TrackingListener(Plugin plugin, CombatTracker combat, TargetRules.Protection protection, Predicate<World> pvpRule,
                     MechanicCooldown cooldown) {
        this.plugin = plugin;
        this.combat = combat;
        this.protection = protection;
        this.pvpRule = pvpRule;
        this.cooldown = cooldown;
    }

    /**
     * Switches to new tunables atomically, or disables tracking when settings is null. Projectiles already
     * tracking keep their own snapshot; when disabled they stop on their next tick.
     */
    public void apply(TrackingSettings settings) {
        if (settings == null) {
            runtime = null;
            return;
        }
        combat.setWindowNanos(settings.pvpCombatNanos());
        runtime = new Runtime(settings, new TargetRules(settings, combat, protection, pvpRule),
                TrackingConfig.particle(settings.particle(), plugin.getLogger()),
                TrackingConfig.chime(settings, plugin.getLogger()));
    }

    public CombatTracker combat() { return combat; }
    Runtime runtime() { return runtime; }
    Logger logger() { return plugin.getLogger(); }
    boolean active() { return enabled.get() && runtime != null; }

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
        Runtime current = runtime;
        if (!enabled.get() || current == null || !(event.getEntity() instanceof Player player)) return;
        Entity entity = event.getProjectile();
        Tracker.Kind kind = entity instanceof Firework ? Tracker.Kind.FIREWORK
                : entity instanceof AbstractArrow && !(entity instanceof Trident) ? Tracker.Kind.ARROW : null;
        if (kind == null || ArrowRainListener.isRain(entity)) return;
        int level = enchantLevel(event.getBow());
        if (level == 0) return;
        // On cooldown this stays a plain, unmarked projectile; the player is not told.
        if (!cooldown.ready(player, ENCHANT)) return;
        // This event runs on the shooter's region thread: the only place its position is read.
        Location shotLocation = player.getLocation();
        Shooter shooter = shooters.compute(player.getUniqueId(),
                (id, old) -> old != null && old.player == player && old.online ? old : new Shooter(player));
        entity.getPersistentDataContainer().set(MARK, PersistentDataType.BYTE, (byte) Math.min(level, Byte.MAX_VALUE));
        long now = System.nanoTime();
        pending.values().removeIf(stale -> now - stale.createdNanos > PENDING_NANOS);
        pending.put(entity.getUniqueId(), new Tracker(this, current, (Projectile) entity, kind, level, shooter, shotLocation, null));
    }

    /** Each multishot projectile has its own shoot and launch event. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLaunch(ProjectileLaunchEvent event) {
        Tracker tracker = pending.remove(event.getEntity().getUniqueId());
        // Tracking is triggered when a launched shot starts tracking: the cooldown starts here, on the shooter's thread.
        if (tracker != null && !event.isCancelled() && start(tracker)) cooldown.start(tracker.shooter.player, ENCHANT);
    }

    /** Rain arrows track only when their original arrow did: a shot fired on cooldown keeps its rain plain too. */
    @Override
    public boolean followsRain(AbstractArrow source) { return isMarked(source); }

    /**
     * Rain arrows fire no shoot event; the arrow rain listener hands them over after spawning. They belong to the
     * shot that started the rain, so they neither check nor start the cooldown.
     */
    @Override
    public void rainSpawned(Arrow arrow, Player player, ItemStack weapon, Location shotLocation, Runnable stopTrail) {
        Runtime current = runtime;
        if (!enabled.get() || current == null) return;
        int level = enchantLevel(weapon);
        if (level == 0) return;
        arrow.getPersistentDataContainer().set(MARK, PersistentDataType.BYTE, (byte) Math.min(level, Byte.MAX_VALUE));
        Shooter shooter = shooters.get(player.getUniqueId());
        if (shooter == null || shooter.player != player || !shooter.online) return;
        start(new Tracker(this, current, arrow, Tracker.Kind.RAIN, level, shooter, shotLocation, stopTrail));
    }

    /** @return whether the tracker is running */
    private boolean start(Tracker tracker) {
        if (!enabled.get()) return false;
        trackers.put(tracker.id, tracker);
        shots.put(tracker.id, new ShotRecord(tracker.shooter, tracker.view, tracker.runtime.rules()));
        try {
            ScheduledTask task = tracker.projectile.getScheduler().runAtFixedRate(plugin, tracker::tick,
                    () -> forgetProjectile(tracker.id), 1L, 1L);
            if (task == null) {
                forgetProjectile(tracker.id);
                return false;
            }
            tracker.task = task;
            if (!enabled.get()) {
                tracker.stop(); // close() raced the registration.
                return false;
            }
            return true;
        } catch (IllegalPluginAccessException ignored) {
            forgetProjectile(tracker.id); // Disabled concurrently.
            return false;
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
                || !shot.rules().trackable(living, shot.view())) {
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
    void chime(Runtime snapshot, Shooter shooter, long gameTick) {
        Sound chime = snapshot.chime();
        if (chime == null || !shooter.online || !shooter.claimChime(gameTick)) return;
        try {
            shooter.player.getScheduler().run(plugin, task -> {
                // Only the shooter hears it, following the shooter's position.
                if (enabled.get() && shooter.online) shooter.player.playSound(chime, Sound.Emitter.self());
            }, null);
        } catch (IllegalPluginAccessException ignored) { /* Disabled concurrently. */ }
    }

    void emit(Runtime snapshot, Location at) {
        Particle particle = snapshot.particle();
        TrackingSettings settings = snapshot.settings();
        if (particle == null) return;
        int min = settings.particleCountMin(), max = settings.particleCountMax();
        int count = max > min ? ThreadLocalRandom.current().nextInt(min, max + 1) : min;
        if (count <= 0) return;
        double offset = settings.particleOffset();
        at.getWorld().spawnParticle(particle, at, count, offset, offset, offset, settings.particleSpeed());
    }

    Tracker tracker(UUID projectile) { return trackers.get(projectile); }

    boolean hasShotRecord(UUID projectile) { return shots.containsKey(projectile); }

    /** A projectile's shooter session, the view taken when it was fired and the rules then in force. */
    private record ShotRecord(Shooter shooter, Shooter.View view, TargetRules rules) {}

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
