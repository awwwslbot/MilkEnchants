package com.windsor.meowsEnchants.arrowrain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/** Tracking takes rain arrows over through the observer; arrow rain itself must stay unchanged. */
class RainObserverTest {
    @Test void observerGetsTheShotTimeBowSnapshotAndCanStopTheCritTrail() {
        Fixture f = new Fixture();
        AtomicReference<Arrow> arrow = new AtomicReference<>();
        AtomicReference<Player> shooter = new AtomicReference<>();
        AtomicReference<ItemStack> weapon = new AtomicReference<>();
        AtomicReference<Runnable> stopTrail = new AtomicReference<>();
        AtomicReference<Location> shotAt = new AtomicReference<>();
        f.listener.setRainObserver((rain, player, bow, location, stop) -> {
            arrow.set(rain);
            shooter.set(player);
            weapon.set(bow);
            shotAt.set(location);
            stopTrail.set(stop);
        });
        when(f.player.getLocation()).thenReturn(new Location(f.world, 500, 64, 500)); // moved after the shot
        f.spawn();
        assertSame(f.rain, arrow.get());
        assertSame(f.player, shooter.get());
        assertSame(f.snapshot, weapon.get(), "the clone taken in the shoot event, not the live hand");
        assertNotSame(f.liveBow, weapon.get());
        assertEquals(f.shotLocation, shotAt.get(), "the shooter's position in the shoot event, not later");
        verify(f.rainScheduler).runDelayed(eq(f.plugin), any(), any(), eq(30L));
        stopTrail.get().run();
        verify(f.trailTask).cancel();
    }

    @Test void eachRainCarriesTheLocationOfTheShotThatStartedIt() {
        Fixture f = new Fixture(); // First shot at f.shotLocation.
        AtomicReference<Location> shotAt = new AtomicReference<>();
        f.listener.setRainObserver((rain, player, bow, location, stop) -> shotAt.compareAndSet(null, location));
        // The shooter walks into a claim and fires a second rain arrow before the first one lands.
        Location second = new Location(f.world, 300, 64, 300);
        when(f.player.getLocation()).thenReturn(second);
        AbstractArrow secondSource = mock(AbstractArrow.class);
        when(secondSource.getUniqueId()).thenReturn(UUID.randomUUID());
        when(secondSource.getPickupStatus()).thenReturn(AbstractArrow.PickupStatus.CREATIVE_ONLY);
        when(secondSource.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        EntityScheduler secondScheduler = mock(EntityScheduler.class);
        when(secondSource.getScheduler()).thenReturn(secondScheduler);
        when(secondScheduler.runDelayed(eq(f.plugin), any(), any(), anyLong())).thenReturn(mock(ScheduledTask.class));
        f.listener.trackSource(secondSource, f.player, f.liveBow, 1, null);
        f.spawn(); // The first shot lands and its rain spawns.
        assertEquals(f.shotLocation, shotAt.get());
        assertNotEquals(second, shotAt.get());
    }

    @Test void anObserverThatPassesOnTheShotNeverSeesItsRain() {
        Fixture f = new Fixture();
        AtomicReference<Arrow> arrow = new AtomicReference<>();
        f.listener.setRainObserver(new ArrowRainListener.RainObserver() {
            @Override public void rainSpawned(Arrow rain, Player player, ItemStack bow, Location location, Runnable stop) {
                arrow.set(rain);
            }

            // Tracking passes on a shot it did not take (fired on cooldown): that rain stays plain.
            @Override public boolean followsRain(AbstractArrow source) { return source != f.source; }
        });
        f.spawn();
        assertNull(arrow.get());
        verify(f.rain).setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED); // The rain itself still falls.
    }

    @Test void withoutAnObserverRainSpawnsExactlyAsBefore() {
        Fixture f = new Fixture();
        f.spawn();
        verify(f.rain).setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        verify(f.rainScheduler).runAtFixedRate(eq(f.plugin), any(), any(), eq(1L), eq(1L));
        verify(f.trailTask, never()).cancel();
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
        final ItemStack liveBow = mock(ItemStack.class);
        final ItemStack snapshot = mock(ItemStack.class);
        final ArrowRainListener listener = BundledRain.listener(plugin);
        final AtomicReference<Consumer<ScheduledTask>> drop = new AtomicReference<>();
        final Location shotLocation = new Location(world, 0, 64, 0);

        Fixture() {
            when(plugin.isEnabled()).thenReturn(true);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getLocation()).thenReturn(shotLocation);
            when(source.getUniqueId()).thenReturn(UUID.randomUUID());
            when(source.getPickupStatus()).thenReturn(AbstractArrow.PickupStatus.CREATIVE_ONLY);
            when(source.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(source.getScheduler()).thenReturn(mock(EntityScheduler.class));
            when(source.getScheduler().runDelayed(eq(plugin), any(), any(), anyLong())).thenReturn(mock(ScheduledTask.class));
            when(source.getLocation()).thenReturn(new Location(world, 10, 64, 10));
            when(source.getDamage()).thenReturn(2.0);
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            Block block = mock(Block.class);
            when(block.isPassable()).thenReturn(true);
            when(world.getBlockAt(any(Location.class))).thenReturn(block);
            when(regions.runDelayed(eq(plugin), any(Location.class), any(), anyLong())).thenAnswer(invocation -> {
                if (((Location) invocation.getArgument(1)).getY() == 84.0) drop.compareAndSet(null, invocation.getArgument(2));
                return mock(ScheduledTask.class);
            });
            when(rain.getUniqueId()).thenReturn(UUID.randomUUID());
            when(rain.isValid()).thenReturn(true);
            when(rain.getScheduler()).thenReturn(rainScheduler);
            when(rain.getWorld()).thenReturn(world);
            when(rain.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(rainScheduler.runDelayed(eq(plugin), any(), any(), anyLong())).thenReturn(mock(ScheduledTask.class));
            when(rainScheduler.runAtFixedRate(eq(plugin), any(), any(), eq(1L), eq(1L))).thenReturn(trailTask);
            when(world.spawn(any(Location.class), eq(Arrow.class), org.mockito.ArgumentMatchers.<Consumer<Arrow>>any())).thenAnswer(invocation -> {
                Consumer<Arrow> initialize = invocation.getArgument(2);
                initialize.accept(rain);
                return rain;
            });
            when(liveBow.clone()).thenReturn(snapshot);
            when(snapshot.clone()).thenReturn(snapshot);
            listener.trackSource(source, player, liveBow, 1, null);
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
    }
}
