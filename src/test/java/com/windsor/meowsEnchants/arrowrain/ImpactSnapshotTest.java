package com.windsor.meowsEnchants.arrowrain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
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
        Block spawnBlock = mock(Block.class);
        when(spawnBlock.isPassable()).thenReturn(true);
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(f.world.getBlockAt(any(Location.class))).thenReturn(spawnBlock);
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
        Block spawnBlock = mock(Block.class);
        when(spawnBlock.isPassable()).thenReturn(true);
        when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(f.world.getBlockAt(any(Location.class))).thenReturn(spawnBlock);
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
            long minimumCompletionTick = BundledRain.RAIN.lastSpawnTick(level)
                    + (long) Math.ceil(20.0 / BundledRain.RAIN.speed()) + 1;
            assertTrue(f.completionTick.get() >= minimumCompletionTick,
                    "Shot completion must not cancel/remove the final drops released at tick "
                            + BundledRain.RAIN.lastSpawnTick(level));
        }
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

        Fixture() { this(1); }

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
            when(regions.runDelayed(eq(plugin), any(Location.class), any(), anyLong())).thenAnswer(invocation -> {
                Location location = invocation.getArgument(1);
                long delay = invocation.getArgument(3);
                if (location.getY() == 64.0) {
                    completionCentre.set(location.clone());
                    completionTick.set(delay);
                } else firstDrop.compareAndSet(null, invocation.getArgument(2));
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
