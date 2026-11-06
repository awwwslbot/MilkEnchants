package com.windsor.meowsEnchants.arrowrain;

import com.windsor.meowsEnchants.listeners.MechanicCooldown;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Captures the actual fired bow once; impact never consults the shooter's current equipment. */
public final class ArrowRainListener implements Listener, AutoCloseable {
    public static final NamespacedKey ENCHANT = new NamespacedKey("meowsenchants", "arrow_rain");
    public static final NamespacedKey GENERATED = new NamespacedKey("meowsenchants", "arrow_rain_generated");
    private final Plugin plugin;
    /** Current tunables, read once per shot; null while arrow_rain.yml has no landing_arrow_rain_v1 mechanic. */
    private final Supplier<ArrowRainSettings> settings;
    /** The enchantment's cooldown, shared with tracking: checked at the shot, started when a rain is reserved. */
    private final MechanicCooldown cooldown;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final Map<UUID, Shot> sources = new ConcurrentHashMap<>();
    private final Set<Shot> shots = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AbstractArrow> rainArrows = new ConcurrentHashMap<>();
    private final Map<UUID, Trail> trails = new ConcurrentHashMap<>();
    private volatile RainObserver rainObserver;

    public ArrowRainListener(Plugin plugin, Supplier<ArrowRainSettings> settings, MechanicCooldown cooldown) {
        this.plugin = plugin;
        this.settings = settings;
        this.cooldown = cooldown;
    }

    /** Rain arrows fire no shoot event; another mechanic (tracking) can take them over here. */
    public interface RainObserver {
        /**
         * Called on the rain arrow's region thread right after it spawns. weapon and shotLocation are
         * the bow and shooter position captured at the shot that started this rain (the shooter may
         * have moved or fired again since); stopTrail ends this arrow's CRIT trail.
         */
        void rainSpawned(Arrow arrow, Player shooter, ItemStack weapon, Location shotLocation, Runnable stopTrail);

        /**
         * Asked once on the original arrow's region thread when it hits and its rain starts; false keeps this
         * observer away from that rain's arrows (tracking passes on a shot it did not take, e.g. on cooldown).
         */
        default boolean followsRain(AbstractArrow source) { return true; }
    }

    public void setRainObserver(RainObserver observer) { this.rainObserver = observer; }

    public static boolean isRain(Entity entity) {
        return entity.getPersistentDataContainer().has(GENERATED, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.isCancelled() || !enabled.get() || settings.get() == null || !(event.getEntity() instanceof Player player)
                || !(event.getProjectile() instanceof AbstractArrow source) || isRain(source)) return;
        ItemStack bow = event.getBow();
        int level = enchantLevel(bow);
        if (level == 0) return;
        // On cooldown this stays a plain arrow: no rain, no extra ammunition or durability, no message.
        if (!cooldown.ready(player, ENCHANT)) return;

        trackSource(source, player, bow, level, event.getConsumable());
    }

    void trackSource(AbstractArrow source, Player player, ItemStack bow, int level, ItemStack ammunition) {
        ArrowRainSettings current = settings.get();
        if (current == null) return;
        Shot shot = new Shot(source.getUniqueId(), player, level, new RainPattern(current), bow, bow.clone(),
                ammunition == null ? null : ammunition.clone(), AmmunitionBudget.isFreeShot(source, bow, ammunition));
        Shot previous = sources.putIfAbsent(shot.sourceId, shot);
        if (previous != null) return;
        shots.add(shot);
        ScheduledTask expiry = source.getScheduler().runDelayed(plugin, task -> finish(shot),
                () -> { if (shot.state.flying()) finish(shot); }, current.sourceMaxTicks());
        shot.add(expiry);
        if (expiry == null) finish(shot);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onLaunch(ProjectileLaunchEvent event) {
        Shot shot = sources.get(event.getEntity().getUniqueId());
        if (shot == null) return;
        if (event.isCancelled() || !active(shot)) { finish(shot); return; }
        if (!shot.launched.compareAndSet(false, true)) return;
        // A successful native bow launch occurs synchronously in the shooter's region, after
        // EntityShootBowEvent and before vanilla's normal one-point durability charge. Waiting
        // until this event also exempts arrows rejected by ProjectileLaunchEvent protection.
        // Vanilla already paid for the original arrow before the shoot event. Reserve only the
        // extra rain arrows now, and never inspect a remote shooter's inventory on impact.
        shot.rainCount = AmmunitionBudget.reserve(shot.player, shot.ammunition, shot.freeAmmunition, shot.pattern.count(shot.level));
        chargeDurability(shot.firedBow, shot.player, shot.pattern.extraDurability(shot.level));
        if (shot.rainCount == 0) {
            finish(shot);
            return;
        }
        // The rain is committed here (extra arrows reserved, extra durability paid): its cooldown starts now.
        // Rain arrows never reach this point, so they can neither start another rain nor refresh the cooldown.
        cooldown.start(shot.player, ENCHANT);
    }

    static int enchantLevel(ItemStack bow) {
        if (bow == null || bow.getType() != Material.BOW) return 0;
        return levelOf(bow.getEnchantments());
    }

    /** Keyed rather than Enchantment, which cannot be loaded without a server (same as tracking). */
    private static int levelOf(Map<? extends Keyed, Integer> enchantments) {
        for (Map.Entry<? extends Keyed, Integer> entry : enchantments.entrySet()) {
            if (ENCHANT.equals(entry.getKey().getKey())) {
                return Math.max(0, entry.getValue());
            }
        }
        return 0;
    }

    static void chargeDurability(ItemStack bow, Player player, int extraDurability) {
        if (extraDurability > 0 && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            bow.damage(extraDurability, player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (event.isCancelled() || !enabled.get()) return;
        if (isRain(event.getEntity())) {
            stopTrail(event.getEntity().getUniqueId());
            // Ground arrows retain their spawn+30-tick deadline. Entity collisions still resolve
            // natively; terminate any surviving deflection before another physics tick can hurt.
            if (event.getHitEntity() != null) removeLater(event.getEntity());
            return;
        }
        Shot shot = sources.get(event.getEntity().getUniqueId());
        if (shot == null || !shot.launched.get() || !shot.state.impact()) return;
        sources.remove(shot.sourceId, shot);
        shot.cancelTasks(); // Source expiry/removal must not cancel an already triggered rain.
        // In the supported Folia 26.1.2 server, AbstractArrow.stepMoveAndHit sets its position
        // to HitResult.location before firing this event. Keep that exact first impact point;
        // a later target ray trace can select another surface after the target has moved.
        Location centre = event.getEntity().getLocation().clone();
        // PROJECTILE_SPAWNED (including Flame) runs after shoot/launch event observers. Read
        // the original arrow here on its owner thread, and pass immutable values to all drops.
        ArrowState state = ArrowState.capture((AbstractArrow) event.getEntity());
        // The impact point's own headroom, read once here on the source's thread and in its own column; every
        // drop column whose impact-height block is solid (slopes, walls) is capped by it.
        double centreCeiling = centreCeiling((AbstractArrow) event.getEntity(), centre, shot.pattern);
        RainObserver observer = rainObserver;
        shot.observed = observer == null || observer.followsRain((AbstractArrow) event.getEntity());
        startRain(shot, centre, centreCeiling, state);
    }

    /**
     * The ceiling above the impact point (see {@link RainHeight#centreCeiling}). The point is the one the arrow
     * hit from: its position a hair back along its flight, so a wall or ceiling hit reads the air in front of
     * it, and a hit on a bottom slab's top stays on the slab (ground, by its collision boxes). That probe can
     * cross into a chunk another region owns; the arrow's own position is used then.
     */
    private static double centreCeiling(AbstractArrow source, Location centre, RainPattern pattern) {
        World world = centre.getWorld();
        Location probe = centre;
        Vector velocity = source.getVelocity();
        if (velocity.lengthSquared() > 0) {
            Location back = centre.clone().subtract(velocity.clone().normalize().multiply(RainHeight.PROBE_BACKOFF));
            if (Bukkit.isOwnedByCurrentRegion(back) && world.isChunkLoaded(back.getBlockX() >> 4, back.getBlockZ() >> 4)) {
                probe = back;
            }
        }
        return RainHeight.centreCeiling(centre.getY(), probe.getY(), pattern.height(), pattern.minHeight(),
                world.getMinHeight(), world.getMaxHeight(), new WorldColumn(world, probe.getX(), probe.getZ()));
    }

    /**
     * The world's column through the point (x, z); reads only blocks of that column, i.e. of its chunk. A block
     * stops arrows when it has a collision box (same test as an arrow's block collision), as a full block; the
     * block holding the impact height is looked at by its collision boxes at the point.
     */
    record WorldColumn(World world, double x, double z) implements RainHeight.Column {
        @Override public boolean blocks(int y) { return !block(y).isPassable(); }

        @Override public boolean blocksPoint(int y, double fy) {
            List<RainHeight.Box> boxes = new ArrayList<>();
            for (BoundingBox box : block(y).getCollisionShape().getBoundingBoxes()) {
                boxes.add(new RainHeight.Box(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ()));
            }
            return RainHeight.stops(boxes, x - Math.floor(x), fy, z - Math.floor(z));
        }

        private Block block(int y) { return world.getBlockAt((int) Math.floor(x), y, (int) Math.floor(z)); }
    }

    private void startRain(Shot shot, Location centre, double centreCeiling, ArrowState state) {
        if (!active(shot) || !shot.rainStarted.compareAndSet(false, true)) return;
        RainPattern pattern = shot.pattern;
        for (RainPattern.Drop drop : pattern.drops(shot.level, shot.rainCount, ThreadLocalRandom.current())) {
            // Regions follow chunks, not y: schedule on the drop's column at impact height. Its spawn height is
            // chosen from that column's blocks when the arrow spawns (out-of-world impacts are skipped there).
            Location column = centre.clone().add(drop.x(), 0, drop.z());
            shot.add(Bukkit.getRegionScheduler().runDelayed(plugin, column,
                    task -> spawnRain(shot, column, centreCeiling, state), drop.tick()));
        }
        // Keep the final drop alive for its full cleanup window, including level III at tick 60.
        long completionTick = pattern.lastSpawnTick(shot.level) + (long) pattern.settings().lifetimeTicks();
        shot.add(Bukkit.getRegionScheduler().runDelayed(plugin, centre, task -> finish(shot), completionTick));
    }

    private void spawnRain(Shot shot, Location column, double centreCeiling, ArrowState state) {
        if (!active(shot)) return;
        World world = column.getWorld();
        int x = column.getBlockX();
        int z = column.getBlockZ();
        // Do not load/generate an unvisited chunk.
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
        // Only this column is read (this chunk, on its own region thread), at the moment this arrow spawns.
        OptionalDouble height = RainHeight.spawnY(column.getY(), shot.pattern.height(), shot.pattern.minHeight(),
                centreCeiling, world.getMinHeight(), world.getMaxHeight(), new WorldColumn(world, column.getX(), column.getZ()));
        if (height.isEmpty()) return; // Not enough headroom here: this arrow is skipped, nothing is registered.
        Location location = column.clone();
        location.setY(height.getAsDouble());
        Arrow arrow = world.spawn(location, Arrow.class, rain -> {
            rain.getPersistentDataContainer().set(GENERATED, PersistentDataType.BYTE, (byte) 1);
            // resetPickupStatus=false only copies owner identity; it never reads live shooter state.
            rain.setShooter(shot.player, false);
            rain.setWeapon(shot.weapon.clone());
            rain.setDamage(state.damage());
            rain.setCritical(state.critical());
            rain.setFireTicks(state.fireTicks());
            rain.setPierceLevel(0);
            rain.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            rain.setPersistent(false);
            // Do not pre-age arrows: they must remain visible on the ground until spawn+30 ticks.
            rain.setLifetimeTicks(0);
            rain.setVelocity(new Vector(0, -shot.pattern.speed(), 0));
            // Projectile pitch uses atan2(dy, horizontalSpeed), so downward is -90 degrees.
            // setVelocity does not update rotation. Set both current/old rotation before insertion
            // so the first spawn packet already shows a vertical arrow, without interpolating from 0.
            rain.setRotation(0f, -90f);
        });
        if (!arrow.isValid()) return; // A protection plugin may cancel ProjectileLaunchEvent.
        rainArrows.put(arrow.getUniqueId(), arrow);
        shot.arrows.add(arrow);
        startTrail(arrow, shot);
        // This uses the entity scheduler so cleanup follows arrows across region boundaries.
        arrow.getScheduler().runDelayed(plugin, task -> removeOwned(arrow),
                () -> rainArrows.remove(arrow.getUniqueId()), shot.pattern.settings().lifetimeTicks());
        RainObserver observer = rainObserver;
        if (observer != null && shot.observed) observer.rainSpawned(arrow, shot.player, shot.weapon, shot.shotLocation, () -> stopTrail(arrow.getUniqueId()));
        if (!active(shot)) removeOwned(arrow); // Covers a quit/disable racing the spawn callback.
    }

    private boolean active(Shot shot) { return enabled.get() && shot.state.active(); }

    private void startTrail(Arrow arrow, Shot shot) {
        UUID id = arrow.getUniqueId();
        ArrowRainSettings tunables = shot.pattern.settings();
        Trail trail = new Trail();
        trails.put(id, trail);
        try {
            trail.task = arrow.getScheduler().runAtFixedRate(plugin, scheduled -> {
                if (!enabled.get() || !trail.active.get() || !arrow.isValid() || arrow.isInBlock()
                        || trail.ticks++ >= tunables.lifetimeTicks()) {
                    trails.remove(id, trail);
                    trail.active.set(false);
                    scheduled.cancel();
                    return;
                }
                // Independent server packets keep the trail visible for partial draws and
                // clients that skip an off-screen arrow's local physics/CRIT-particle tick.
                // The real critical flag and damage are never changed to create this effect.
                double offset = tunables.trailOffset();
                arrow.getWorld().spawnParticle(shot.trailParticle, arrow.getLocation(), tunables.trailCount(),
                        offset, offset, offset, tunables.trailSpeed());
            }, () -> stopTrail(id), 1L, 1L);
            if (trail.task == null || !enabled.get() || !trail.active.get()) {
                trails.remove(id, trail);
                trail.cancel();
            }
        } catch (IllegalPluginAccessException ignored) {
            stopTrail(id); // Disable may race between the spawn callback and task registration.
        }
    }

    private void stopTrail(UUID id) {
        Trail trail = trails.remove(id);
        if (trail != null) trail.cancel();
    }

    private void removeLater(Entity entity) {
        entity.getScheduler().runDelayed(plugin, task -> removeOwned(entity),
                () -> rainArrows.remove(entity.getUniqueId()), 1L);
    }

    private void removeOwned(Entity entity) {
        stopTrail(entity.getUniqueId());
        rainArrows.remove(entity.getUniqueId());
        entity.remove();
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        UUID id = event.getEntity().getUniqueId();
        stopTrail(id);
        rainArrows.remove(id);
        Shot shot = sources.get(id);
        if (shot != null && shot.state.flying()) finish(shot);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        for (Shot shot : shots) if (shot.playerId.equals(id)) finish(shot);
    }

    private void finish(Shot shot) {
        shot.state.stop();
        sources.remove(shot.sourceId, shot);
        shots.remove(shot);
        shot.cancelTasks();
        shot.arrows.forEach(arrow -> stopTrail(arrow.getUniqueId()));
        if (plugin.isEnabled()) {
            for (AbstractArrow arrow : shot.arrows) {
                try {
                    arrow.getScheduler().run(plugin, task -> removeOwned(arrow),
                            () -> rainArrows.remove(arrow.getUniqueId()));
                } catch (IllegalPluginAccessException ignored) { /* Concurrent disable. */ }
            }
        }
        shot.arrows.clear();
    }

    @Override
    public void close() {
        enabled.set(false);
        shots.forEach(this::finish);
        sources.clear();
        shots.clear();
        trails.values().forEach(Trail::cancel);
        trails.clear();
        // onDisable may run after Plugin.isEnabled becomes false. Never fall back to an unsafe
        // global-thread removal; nonpersistent/nonpickup arrows retain their native expiry.
        rainArrows.values().forEach(arrow -> {
            if (Bukkit.isOwnedByCurrentRegion(arrow)) arrow.remove();
            else if (plugin.isEnabled()) {
                try { arrow.getScheduler().run(plugin, task -> arrow.remove(), null); }
                catch (IllegalPluginAccessException ignored) { /* Concurrent disable. */ }
            }
        });
        rainArrows.clear();
    }

    private static final class Shot {
        final UUID sourceId;
        final UUID playerId;
        final Player player;
        final int level;
        /** Settings captured at the shot; a reload never changes a rain already on its way. */
        final RainPattern pattern;
        final Particle trailParticle;
        final ItemStack firedBow;
        final ItemStack weapon;
        final ItemStack ammunition;
        final boolean freeAmmunition;
        /** Read in the shoot event, on the shooter's own region thread. */
        final Location shotLocation;
        volatile int rainCount;
        /** Whether the rain observer takes this shot's rain arrows; decided at impact. */
        volatile boolean observed = true;
        final ShotState state = new ShotState();
        final AtomicBoolean rainStarted = new AtomicBoolean();
        final AtomicBoolean launched = new AtomicBoolean();
        final Set<ScheduledTask> tasks = ConcurrentHashMap.newKeySet();
        final Set<AbstractArrow> arrows = ConcurrentHashMap.newKeySet();

        Shot(UUID sourceId, Player player, int level, RainPattern pattern, ItemStack firedBow, ItemStack weapon, ItemStack ammunition, boolean freeAmmunition) {
            this.sourceId = sourceId;
            this.player = player;
            this.playerId = player.getUniqueId();
            this.level = level;
            this.pattern = pattern;
            this.trailParticle = trailParticle(pattern.settings().trailParticle());
            this.firedBow = firedBow;
            this.weapon = weapon;
            this.ammunition = ammunition;
            this.freeAmmunition = freeAmmunition;
            this.shotLocation = player.getLocation();
        }

        void add(ScheduledTask task) {
            if (task == null) return;
            tasks.add(task);
            if (!state.active()) { task.cancel(); tasks.remove(task); }
        }

        void cancelTasks() { tasks.forEach(ScheduledTask::cancel); tasks.clear(); }
    }

    /** A data-free particle by Bukkit name; anything else falls back to CRIT. */
    static Particle trailParticle(String name) {
        try {
            Particle particle = Particle.valueOf(name.toUpperCase(java.util.Locale.ROOT));
            if (particle.getDataType() == Void.class) return particle;
        } catch (IllegalArgumentException ignored) { /* Unknown name. */ }
        return Particle.CRIT;
    }

    private record ArrowState(double damage, boolean critical, int fireTicks) {
        static ArrowState capture(AbstractArrow source) {
            return new ArrowState(source.getDamage(), source.isCritical(), source.getFireTicks());
        }
    }

    private static final class Trail {
        final AtomicBoolean active = new AtomicBoolean(true);
        volatile ScheduledTask task;
        int ticks; // Accessed only by the arrow's entity scheduler.

        void cancel() {
            active.set(false);
            ScheduledTask current = task;
            if (current != null) current.cancel();
        }
    }
}
