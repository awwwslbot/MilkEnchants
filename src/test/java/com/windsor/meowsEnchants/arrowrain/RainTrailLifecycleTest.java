package com.windsor.meowsEnchants.arrowrain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class RainTrailLifecycleTest {
    @Test void shortAndFullDrawReceiveServerParticlesWithoutChangingCriticalDamage() {
        for (boolean critical : new boolean[]{false, true}) {
            Fixture f = new Fixture(critical);
            f.spawn();
            f.emit();
            Location position = f.rain.getLocation();
            verify(f.world).spawnParticle(eq(Particle.CRIT), eq(position), eq(2), eq(0.02), eq(0.02), eq(0.02), eq(0.0));
            verify(f.rain).setCritical(critical);
            verify(f.rain, never()).setCritical(!critical);
            verify(f.rain).setDamage(2.0);
        }
    }

    @Test void groundLifetimeIsThirtyTicksAndDoesNotExpireNativelyOnLanding() {
        Fixture f = new Fixture(false);
        f.spawn();
        assertEquals(30L, f.cleanupDelay.get());
        verify(f.rain).setLifetimeTicks(0);
    }

    @Test void groundHitStopsParticlesAndRetainsTheOriginalSpawnDeadline() {
        Fixture f = new Fixture(false);
        f.spawn();
        f.requireTrail();
        ProjectileHitEvent hit = f.rainHit();
        when(hit.getHitBlock()).thenReturn(mock(Block.class));
        f.listener.onHit(hit);
        verify(f.trailTask).cancel();
        assertEquals(30L, f.cleanupDelay.get());
        verify(f.rainScheduler, never()).runDelayed(eq(f.plugin), any(), any(), eq(1L));
    }

    @Test void entityHitStopsParticlesAndTerminatesBeforeASecondPhysicsTick() {
        Fixture f = new Fixture(false);
        f.spawn();
        f.requireTrail();
        ProjectileHitEvent hit = f.rainHit();
        when(hit.getHitEntity()).thenReturn(mock(Entity.class));
        f.listener.onHit(hit);
        verify(f.trailTask).cancel();
        verify(f.rainScheduler).runDelayed(eq(f.plugin), any(), any(), eq(1L));
    }

    @Test void removalAndQuitCancelTheirTrailTasks() {
        for (boolean quit : new boolean[]{false, true}) {
            Fixture f = new Fixture(false);
            f.spawn();
            f.requireTrail();
            if (quit) {
                PlayerQuitEvent event = mock(PlayerQuitEvent.class);
                when(event.getPlayer()).thenReturn(f.player);
                f.listener.onQuit(event);
            } else {
                EntityRemoveEvent event = mock(EntityRemoveEvent.class);
                when(event.getEntity()).thenReturn(f.rain);
                f.listener.onRemove(event);
            }
            verify(f.trailTask).cancel();
        }
    }

    @Test void disableCancelsTrailsAndSuppressesAnAlreadyQueuedCallback() {
        Fixture f = new Fixture(false);
        f.spawn();
        f.requireTrail();
        try (var bukkit = mockStatic(Bukkit.class)) { f.listener.close(); }
        verify(f.trailTask).cancel();
        f.emit();
        verify(f.world, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test void invalidAndEmbeddedArrowsCannotContinueEmitting() {
        for (boolean embedded : new boolean[]{false, true}) {
            Fixture f = new Fixture(false);
            f.spawn();
            when(f.rain.isValid()).thenReturn(embedded);
            when(f.rain.isInBlock()).thenReturn(embedded);
            f.emit();
            verify(f.trailTask).cancel();
            verify(f.world, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        }
    }

    @Test void trailHasAHardThirtyTickBoundEvenWithoutTheRemovalCallback() {
        Fixture f = new Fixture(false);
        f.spawn();
        for (int tick = 0; tick <= 30; tick++) f.emit();
        verify(f.trailTask).cancel();
        verify(f.world, times(30)).spawnParticle(any(Particle.class), any(Location.class), eq(2), anyDouble(), anyDouble(), anyDouble(), eq(0.0));
    }

    private static final class Fixture {
        final Plugin plugin = mock(Plugin.class);
        final Player player = mock(Player.class);
        final AbstractArrow source = mock(AbstractArrow.class);
        final Arrow rain = mock(Arrow.class);
        final EntityScheduler rainScheduler = mock(EntityScheduler.class);
        final ScheduledTask trailTask = mock(ScheduledTask.class);
        final World world = mock(World.class);
        final RegionScheduler regions = mock(RegionScheduler.class);
        final ArrowRainListener listener = BundledRain.listener(plugin);
        final AtomicReference<Consumer<ScheduledTask>> drop = new AtomicReference<>();
        final AtomicReference<Consumer<ScheduledTask>> trail = new AtomicReference<>();
        final AtomicLong cleanupDelay = new AtomicLong(-1);

        Fixture(boolean critical) {
            when(plugin.isEnabled()).thenReturn(true);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(source.getUniqueId()).thenReturn(UUID.randomUUID());
            when(source.getPickupStatus()).thenReturn(AbstractArrow.PickupStatus.CREATIVE_ONLY);
            when(source.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(source.getScheduler()).thenReturn(mock(EntityScheduler.class));
            when(source.getScheduler().runDelayed(eq(plugin), any(), any(), anyLong())).thenReturn(mock(ScheduledTask.class));
            when(source.getLocation()).thenReturn(new Location(world, 10, 64, 10));
            when(source.getDamage()).thenReturn(2.0);
            when(source.isCritical()).thenReturn(critical);
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            RainWorld.openSky(world);
            RainWorld.fallingOnto(source);
            when(regions.runDelayed(eq(plugin), any(Location.class), any(), anyLong())).thenAnswer(invocation -> {
                if (BundledRain.isDrop(invocation.getArgument(3), 1)) drop.compareAndSet(null, invocation.getArgument(2));
                return mock(ScheduledTask.class);
            });
            when(rain.getUniqueId()).thenReturn(UUID.randomUUID());
            when(rain.isValid()).thenReturn(true);
            when(rain.getScheduler()).thenReturn(rainScheduler);
            when(rain.getWorld()).thenReturn(world);
            when(rain.getLocation()).thenReturn(new Location(world, 10, 82, 10));
            PersistentDataContainer generated = mock(PersistentDataContainer.class);
            when(generated.has(ArrowRainListener.GENERATED, PersistentDataType.BYTE)).thenReturn(true);
            when(rain.getPersistentDataContainer()).thenReturn(generated);
            when(rainScheduler.runDelayed(eq(plugin), any(), any(), anyLong())).thenAnswer(invocation -> {
                cleanupDelay.set(invocation.getArgument(3));
                return mock(ScheduledTask.class);
            });
            when(rainScheduler.runAtFixedRate(eq(plugin), any(), any(), eq(1L), eq(1L))).thenAnswer(invocation -> {
                trail.set(invocation.getArgument(1));
                return trailTask;
            });
            when(world.spawn(any(Location.class), eq(Arrow.class), org.mockito.ArgumentMatchers.<Consumer<Arrow>>any())).thenAnswer(invocation -> {
                Consumer<Arrow> initialize = invocation.getArgument(2);
                initialize.accept(rain);
                return rain;
            });
            ItemStack bow = mock(ItemStack.class);
            when(bow.clone()).thenReturn(bow);
            listener.trackSource(source, player, bow, 1, null);
            ProjectileLaunchEvent launch = mock(ProjectileLaunchEvent.class);
            when(launch.getEntity()).thenReturn(source);
            listener.onLaunch(launch);
        }

        void spawn() {
            ProjectileHitEvent hit = mock(ProjectileHitEvent.class);
            when(hit.getEntity()).thenReturn(source);
            try (var bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getRegionScheduler).thenReturn(regions);
                listener.onHit(hit);
                assertNotNull(drop.get());
                drop.get().accept(mock(ScheduledTask.class));
            }
        }

        ProjectileHitEvent rainHit() {
            ProjectileHitEvent hit = mock(ProjectileHitEvent.class);
            when(hit.getEntity()).thenReturn(rain);
            return hit;
        }

        void requireTrail() { assertNotNull(trail.get(), "Rain needs an independent server particle task"); }
        void emit() { requireTrail(); trail.get().accept(trailTask); }
    }
}
