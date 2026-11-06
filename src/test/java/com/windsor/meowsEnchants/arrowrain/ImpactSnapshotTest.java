package com.windsor.meowsEnchants.arrowrain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class ImpactSnapshotTest {
    @Test void movingTargetCannotMoveTheOriginalImpactCentre() {
        Fixture f = new Fixture();
        // Folia 26.1.2 has already placed the arrow at HitResult.location before this event.
        // The target may move by the next tick; reading this future box would change the centre.
        Entity target = mock(Entity.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(target.getScheduler()).thenReturn(scheduler);
        when(target.getBoundingBox()).thenReturn(new BoundingBox(11.5, 64, 9.5, 12.5, 66, 10.5));
        doAnswer(invocation -> {
            Consumer<ScheduledTask> callback = invocation.getArgument(1);
            callback.accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        }).when(scheduler).run(eq(f.plugin), any(), any());
        when(f.hit.getHitEntity()).thenReturn(target);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(f.regions);
            f.listener.onHit(f.hit);
        }
        assertNotNull(f.completionCentre.get());
        assertEquals(10.0, f.completionCentre.get().getX());
        assertEquals(64.0, f.completionCentre.get().getY());
        assertEquals(10.0, f.completionCentre.get().getZ());
        verify(target, never()).getBoundingBox();
        verify(target, never()).getScheduler();
    }

    @Test void rainUsesSourceStateAppliedAfterShootAndLaunchEvents() {
        Fixture f = new Fixture();
        // PROJECTILE_SPAWNED applies Flame after the shoot/launch event observers have run.
        when(f.source.getFireTicks()).thenReturn(240);
        when(f.source.getDamage()).thenReturn(5.0);
        when(f.source.isCritical()).thenReturn(true);
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Arrow rain = mock(Arrow.class);
        when(rain.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(rain.getScheduler()).thenReturn(mock(EntityScheduler.class));
        when(rain.getUniqueId()).thenReturn(UUID.randomUUID());
        when(rain.isValid()).thenReturn(true);
        when(f.world.spawn(any(Location.class), eq(Arrow.class), org.mockito.ArgumentMatchers.<Consumer<Arrow>>any())).thenAnswer(invocation -> {
            Consumer<Arrow> initializer = invocation.getArgument(2);
            initializer.accept(rain);
            return rain;
        });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(f.regions);
            f.listener.onHit(f.hit);
            assertNotNull(f.firstDrop.get());
            // Further source mutations/removal cannot change the values held by queued drops.
            when(f.source.getFireTicks()).thenReturn(0);
            when(f.source.getDamage()).thenReturn(2.0);
            when(f.source.isCritical()).thenReturn(false);
            f.firstDrop.get().accept(mock(ScheduledTask.class));
        }
        verify(rain).setFireTicks(240);
        verify(rain).setDamage(5.0);
        verify(rain).setCritical(true);
    }

    @Test void downwardVelocityAndModelAreInitializedBeforeWorldInsertion() {
        Fixture f = new Fixture();
        when(f.source.getLocation()).thenReturn(new Location(f.world, 10, 64, 10, 123f, 45f));
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Arrow rain = mock(Arrow.class);
        when(rain.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(rain.getScheduler()).thenReturn(mock(EntityScheduler.class));
        when(rain.getUniqueId()).thenReturn(UUID.randomUUID());
        when(rain.isValid()).thenReturn(true);
        when(f.world.spawn(any(Location.class), eq(Arrow.class), org.mockito.ArgumentMatchers.<Consumer<Arrow>>any())).thenAnswer(invocation -> {
            Location spawn = invocation.getArgument(0);
            Consumer<Arrow> initializer = invocation.getArgument(2);
            initializer.accept(rain);
            // These values must be ready when World.spawn inserts the entity and sends its first packet.
            verify(rain).setRotation(0f, -90f);
            verify(rain).setVelocity(argThat(v -> v.getX() == 0 && v.getY() == -2.5 && v.getZ() == 0));
            // Open ground: exactly impact + 20, as before headroom mattered.
            assertEquals(84.0, spawn.getY());
            return rain;
        });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(f.regions);
            f.listener.onHit(f.hit);
            f.firstDrop.get().accept(mock(ScheduledTask.class));
        }
    }

    @Test void finalDropOfEveryLevelHasTimeToFallBeforeShotCompletion() {
        for (int level : new int[]{3, BundledRain.MAX_LEVEL}) {
            Fixture f = new Fixture(level);
            try (var bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getRegionScheduler).thenReturn(f.regions);
                f.listener.onHit(f.hit);
            }
            // The highest spawn height (open sky) takes the longest to fall.
            long minimumCompletionTick = BundledRain.RAIN.lastSpawnTick(level)
                    + (long) Math.ceil(BundledRain.RAIN.height() / BundledRain.RAIN.speed()) + 1;
            assertTrue(f.completionTick.get() >= minimumCompletionTick,
                    "Shot completion must not cancel/remove the final drops released at tick "
                            + BundledRain.RAIN.lastSpawnTick(level));
        }
    }

    /**
     * The source flies +x into a wall at x = 10. A hair back along its flight is the air block x = 9, whose
     * ceiling (y 72) caps every wall column; without that cap the wall columns would reach the cave up to y 91.
     */
    private static boolean wallAndCave(int x, int y, int z) {
        return x <= 9 ? y <= 63 || y >= 72 : y <= 65 || y >= 91;
    }

    @Test void theImpactsOwnCeilingReachesEveryDropAndEachDropReadsOnlyItsOwnColumn() {
        Fixture f = new Fixture(BundledRain.MAX_LEVEL);
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        AtomicReference<int[]> reading = new AtomicReference<>(new int[]{9, 10}); // The probe block's column.
        RainWorld.blocks(f.world, (x, y, z) -> {
            assertArrayEquals(reading.get(), new int[]{x, z}, "read a block outside the column being decided");
            return wallAndCave(x, y, z);
        });
        List<Location> spawns = f.captureSpawns();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(f.regions);
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
            f.listener.onHit(f.hit);
            assertEquals(BundledRain.RAIN.count(BundledRain.MAX_LEVEL), f.drops.size());
            for (Location column : f.dropColumns) assertEquals(64.0, column.getY(), "scheduled by column, at impact height");
            for (int i = 0; i < f.drops.size(); i++) {
                Location column = f.dropColumns.get(i);
                reading.set(new int[]{column.getBlockX(), column.getBlockZ()});
                f.drops.get(i).accept(mock(ScheduledTask.class));
            }
        }
        assertTrue(f.dropColumns.stream().anyMatch(column -> column.getBlockX() >= 10), "some drops land on the wall");
        assertEquals(f.drops.size(), spawns.size());
        for (Location spawn : spawns) assertEquals(71.5, spawn.getY());
    }

    @Test void aProbeInAnotherRegionFallsBackToTheArrowsOwnBlock() {
        Fixture f = new Fixture(BundledRain.MAX_LEVEL);
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        RainWorld.blocks(f.world, ImpactSnapshotTest::wallAndCave);
        List<Location> spawns = f.captureSpawns();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(f.regions);
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(any(Location.class))).thenReturn(false);
            f.listener.onHit(f.hit);
            bukkit.verify(() -> Bukkit.isOwnedByCurrentRegion(argThat((Location probe) ->
                    Math.abs(probe.getX() - (10 - RainHeight.PROBE_BACKOFF)) < 1e-12 && probe.getY() == 64.0)));
            f.drops.forEach(drop -> drop.accept(mock(ScheduledTask.class)));
        }
        // The arrow's own block is the wall: no headroom, so wall columns get nothing; the air side keeps its own.
        long airColumns = f.dropColumns.stream().filter(column -> column.getBlockX() <= 9).count();
        assertTrue(airColumns < f.drops.size());
        assertEquals(airColumns, spawns.size());
        for (Location spawn : spawns) {
            assertTrue(spawn.getBlockX() <= 9);
            assertEquals(71.5, spawn.getY());
        }
    }

    @Test void aDropWithoutHeadroomSpawnsAndRegistersNothing() {
        Fixture f = new Fixture(BundledRain.MAX_LEVEL);
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        RainWorld.blocks(f.world, (x, y, z) -> y <= 63 || y >= 67); // A 3-block-high tunnel everywhere.
        AtomicReference<Arrow> observed = new AtomicReference<>();
        f.listener.setRainObserver((rain, player, bow, location, stop) -> observed.set(rain));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(f.regions);
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
            f.listener.onHit(f.hit);
            f.drops.forEach(drop -> drop.accept(mock(ScheduledTask.class)));
        }
        assertEquals(BundledRain.RAIN.count(BundledRain.MAX_LEVEL), f.drops.size());
        verify(f.world, never()).spawn(any(Location.class), eq(Arrow.class), org.mockito.ArgumentMatchers.<Consumer<Arrow>>any());
        assertNull(observed.get());
        assertNotNull(f.completionCentre.get(), "the shot still completes on schedule");
    }

    /** Runs one level-V rain from the source at {@code at} flying with {@code velocity}; returns the spawn points. */
    private static List<Location> rainOver(Fixture f, RainWorld.Shapes layout, Location at, Vector velocity) {
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(f.source.getLocation()).thenReturn(at);
        when(f.source.getVelocity()).thenReturn(velocity);
        RainWorld.shapes(f.world, layout);
        List<Location> spawns = f.captureSpawns();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(f.regions);
            bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
            f.listener.onHit(f.hit);
            f.drops.forEach(drop -> drop.accept(mock(ScheduledTask.class)));
        }
        assertEquals(BundledRain.RAIN.count(BundledRain.MAX_LEVEL), f.drops.size());
        return spawns;
    }

    @Test void aSlabFloorUnderOpenSkyRainsFromTheFullHeight() {
        // Bottom slabs everywhere at y 64 (top 64.5): the impact's own block and every column's impact-height block
        // is a slab under the point, ground by its collision box, so every arrow spawns at hY + 20.
        Fixture f = new Fixture(BundledRain.MAX_LEVEL);
        List<Location> spawns = rainOver(f, (x, y, z) -> y <= 63 ? RainWorld.FULL : y == 64 ? RainWorld.BOTTOM_SLAB : RainWorld.AIR,
                new Location(f.world, 10.3, 64.5, 10.7), new Vector(0.4, -3, 0.2));
        assertEquals(f.drops.size(), spawns.size());
        for (Location spawn : spawns) assertEquals(84.5, spawn.getY());
    }

    @Test void aTopSlabRoofHitFromBelowHasNoRain() {
        // A 6-high cave whose roof blocks at y 70 are top slabs (bottom face 70.5); the arrow flew up into one.
        Fixture f = new Fixture(BundledRain.MAX_LEVEL);
        List<Location> spawns = rainOver(f, (x, y, z) -> y <= 63 || y >= 71 ? RainWorld.FULL
                        : y == 70 ? RainWorld.TOP_SLAB : RainWorld.AIR,
                new Location(f.world, 10.3, 70.5, 10.7), new Vector(0.4, 3, 0.2));
        assertTrue(spawns.isEmpty());
        verify(f.world, never()).spawn(any(Location.class), eq(Arrow.class), org.mockito.ArgumentMatchers.<Consumer<Arrow>>any());
    }

    private static final class Fixture {
        final Plugin plugin = mock(Plugin.class);
        final Player player = mock(Player.class);
        final ItemStack bow = mock(ItemStack.class);
        final AbstractArrow source = mock(AbstractArrow.class);
        final World world = mock(World.class);
        final RegionScheduler regions = mock(RegionScheduler.class);
        final ArrowRainListener listener = BundledRain.listener(plugin);
        final ProjectileHitEvent hit = mock(ProjectileHitEvent.class);
        final AtomicReference<Location> completionCentre = new AtomicReference<>();
        final AtomicLong completionTick = new AtomicLong();
        final AtomicReference<Consumer<ScheduledTask>> firstDrop = new AtomicReference<>();
        final List<Consumer<ScheduledTask>> drops = new ArrayList<>();
        final List<Location> dropColumns = new ArrayList<>();

        Fixture() { this(1); }

        /** Records where each rain arrow is spawned; every spawn gets its own arrow mock. */
        List<Location> captureSpawns() {
            List<Location> spawns = new ArrayList<>();
            when(world.spawn(any(Location.class), eq(Arrow.class), org.mockito.ArgumentMatchers.<Consumer<Arrow>>any())).thenAnswer(invocation -> {
                Arrow rain = mock(Arrow.class);
                when(rain.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
                when(rain.getScheduler()).thenReturn(mock(EntityScheduler.class));
                when(rain.getUniqueId()).thenReturn(UUID.randomUUID());
                when(rain.isValid()).thenReturn(true);
                Consumer<Arrow> initializer = invocation.getArgument(2);
                initializer.accept(rain);
                spawns.add(((Location) invocation.getArgument(0)).clone());
                return rain;
            });
            return spawns;
        }

        Fixture(int level) {
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(bow.clone()).thenReturn(bow);
            when(source.getUniqueId()).thenReturn(UUID.randomUUID());
            when(source.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(source.getScheduler()).thenReturn(mock(EntityScheduler.class));
            when(source.getScheduler().runDelayed(eq(plugin), any(), any(), anyLong())).thenReturn(mock(ScheduledTask.class));
            when(source.getLocation()).thenReturn(new Location(world, 10, 64, 10));
            when(source.getVelocity()).thenReturn(new Vector(3, 0, 0));
            when(source.getDamage()).thenReturn(2.0);
            when(hit.getEntity()).thenReturn(source);
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            RainWorld.openSky(world);
            when(regions.runDelayed(eq(plugin), any(Location.class), any(), anyLong())).thenAnswer(invocation -> {
                Location location = invocation.getArgument(1);
                long delay = invocation.getArgument(3);
                if (BundledRain.isDrop(delay, level)) {
                    firstDrop.compareAndSet(null, invocation.getArgument(2));
                    drops.add(invocation.getArgument(2));
                    dropColumns.add(location.clone());
                } else {
                    completionCentre.set(location.clone());
                    completionTick.set(delay);
                }
                return mock(ScheduledTask.class);
            });
            when(source.getPickupStatus()).thenReturn(AbstractArrow.PickupStatus.CREATIVE_ONLY);
            listener.trackSource(source, player, bow, level, null);
            ProjectileLaunchEvent launch = mock(ProjectileLaunchEvent.class);
            when(launch.getEntity()).thenReturn(source);
            listener.onLaunch(launch);
        }
    }
}
