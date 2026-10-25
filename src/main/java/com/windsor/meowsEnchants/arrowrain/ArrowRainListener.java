package com.windsor.meowsEnchants.arrowrain;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.util.Vector;

/** Captures the actual fired bow once; impact never consults the shooter's current equipment. */
public final class ArrowRainListener implements Listener, AutoCloseable {
    private static final long RAIN_LIFETIME_TICKS = 40L;
    public static final NamespacedKey ENCHANT = new NamespacedKey("meowsenchants", "arrow_rain");
    public static final NamespacedKey GENERATED = new NamespacedKey("meowsenchants", "arrow_rain_generated");
    private final Plugin plugin;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final Map<UUID, Shot> sources = new ConcurrentHashMap<>();
    private final Set<Shot> shots = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AbstractArrow> rainArrows = new ConcurrentHashMap<>();

    ArrowRainListener(Plugin plugin) { this.plugin = plugin; }

    public static boolean isRain(Entity entity) {
        return entity.getPersistentDataContainer().has(GENERATED, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.isCancelled() || !enabled.get() || !(event.getEntity() instanceof Player player)
                || !(event.getProjectile() instanceof AbstractArrow source) || isRain(source)) return;
        ItemStack bow = event.getBow();
        int level = enchantLevel(bow);
        if (level == 0) return;

        trackSource(source, player, bow, level);
    }

    void trackSource(AbstractArrow source, Player player, ItemStack bow, int level) {
        Shot shot = new Shot(source.getUniqueId(), player, level, bow, bow.clone());
        Shot previous = sources.putIfAbsent(shot.sourceId, shot);
        if (previous != null) return;
        shots.add(shot);
        ScheduledTask expiry = source.getScheduler().runDelayed(plugin, task -> finish(shot),
                () -> { if (shot.state.flying()) finish(shot); }, 1200L);
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
        chargeDurability(shot.firedBow, shot.player, shot.level);
    }

    static int enchantLevel(ItemStack bow) {
        if (bow == null || bow.getType() != Material.BOW) return 0;
        for (Map.Entry<Enchantment, Integer> entry : bow.getEnchantments().entrySet()) {
            if (ENCHANT.equals(entry.getKey().getKey())) {
                return entry.getValue() > 0 ? RainPattern.level(entry.getValue()) : 0;
            }
        }
        return 0;
    }

    static void chargeDurability(ItemStack bow, Player player, int level) {
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            bow.damage(RainPattern.extraDurability(level), player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (event.isCancelled() || !enabled.get()) return;
        if (isRain(event.getEntity())) {
            removeLater(event.getEntity()); // After vanilla has applied its normal damage/collision.
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
        startRain(shot, centre, state);
    }

    private void startRain(Shot shot, Location centre, ArrowState state) {
        if (!active(shot) || !shot.rainStarted.compareAndSet(false, true)) return;
        for (RainPattern.Drop drop : RainPattern.drops(shot.level, ThreadLocalRandom.current())) {
            Location spawn = centre.clone().add(drop.x(), RainPattern.HEIGHT, drop.z());
            if (spawn.getY() >= spawn.getWorld().getMaxHeight()
                    || spawn.getY() < spawn.getWorld().getMinHeight()) continue;
            shot.add(Bukkit.getRegionScheduler().runDelayed(plugin, spawn, task -> spawnRain(shot, spawn, state), drop.tick()));
        }
        // Keep the final drop alive for its full cleanup window, including level III at tick 60.
        long completionTick = RainPattern.lastSpawnTick(shot.level) + RAIN_LIFETIME_TICKS;
        shot.add(Bukkit.getRegionScheduler().runDelayed(plugin, centre, task -> finish(shot), completionTick));
    }

    private void spawnRain(Shot shot, Location location, ArrowState state) {
        if (!active(shot)) return;
        // Do not load/generate an unvisited chunk, or create arrows inside solid ceilings.
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                || !location.getBlock().isPassable()) return;
        Arrow arrow = location.getWorld().spawn(location, Arrow.class, rain -> {
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
            // Native expiry still applies after disable, when plugin scheduler tasks cannot run.
            rain.setLifetimeTicks(1199);
            rain.setVelocity(new Vector(0, -RainPattern.SPEED, 0));
            // Projectile pitch uses atan2(dy, horizontalSpeed), so downward is -90 degrees.
            // setVelocity does not update rotation. Set both current/old rotation before insertion
            // so the first spawn packet already shows a vertical arrow, without interpolating from 0.
            rain.setRotation(0f, -90f);
        });
        if (!arrow.isValid()) return; // A protection plugin may cancel ProjectileLaunchEvent.
        rainArrows.put(arrow.getUniqueId(), arrow);
        shot.arrows.add(arrow);
        // This uses the entity scheduler so cleanup follows arrows across region boundaries.
        arrow.getScheduler().runDelayed(plugin, task -> removeOwned(arrow),
                () -> rainArrows.remove(arrow.getUniqueId()), RAIN_LIFETIME_TICKS);
        if (!active(shot)) removeOwned(arrow); // Covers a quit/disable racing the spawn callback.
    }

    private boolean active(Shot shot) { return enabled.get() && shot.state.active(); }

    private void removeLater(Entity entity) {
        entity.getScheduler().runDelayed(plugin, task -> removeOwned(entity),
                () -> rainArrows.remove(entity.getUniqueId()), 1L);
    }

    private void removeOwned(Entity entity) {
        rainArrows.remove(entity.getUniqueId());
        entity.remove();
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        UUID id = event.getEntity().getUniqueId();
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
        final ItemStack firedBow;
        final ItemStack weapon;
        final ShotState state = new ShotState();
        final AtomicBoolean rainStarted = new AtomicBoolean();
        final AtomicBoolean launched = new AtomicBoolean();
        final Set<ScheduledTask> tasks = ConcurrentHashMap.newKeySet();
        final Set<AbstractArrow> arrows = ConcurrentHashMap.newKeySet();

        Shot(UUID sourceId, Player player, int level, ItemStack firedBow, ItemStack weapon) {
            this.sourceId = sourceId;
            this.player = player;
            this.playerId = player.getUniqueId();
            this.level = level;
            this.firedBow = firedBow;
            this.weapon = weapon;
        }

        void add(ScheduledTask task) {
            if (task == null) return;
            tasks.add(task);
            if (!state.active()) { task.cancel(); tasks.remove(task); }
        }

        void cancelTasks() { tasks.forEach(ScheduledTask::cancel); tasks.clear(); }
    }

    private record ArrowState(double damage, boolean critical, int fireTicks) {
        static ArrowState capture(AbstractArrow source) {
            return new ArrowState(source.getDamage(), source.isCritical(), source.getFireTicks());
        }
    }
}
