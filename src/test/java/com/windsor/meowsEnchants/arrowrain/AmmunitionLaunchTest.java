package com.windsor.meowsEnchants.arrowrain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class AmmunitionLaunchTest {
    @Test void successfulLaunchPaysExtraRainArrowsOnceForEveryLevel() {
        int initial = BundledRain.RAIN.count(BundledRain.MAX_LEVEL) + 1;
        for (int level = 1; level <= BundledRain.MAX_LEVEL; level++) {
            Fixture f = new Fixture(level, initial, false);
            f.listener.onLaunch(f.launch);
            f.listener.onLaunch(f.launch);
            assertEquals(initial - BundledRain.RAIN.count(level), f.amount(9));
            assertEquals(BundledRain.RAIN.count(level), f.captureRainCount());
        }
    }

    @Test void insufficientAmmunitionLimitsRainAndIsNotRecountedAfterSwitching() {
        Fixture f = new Fixture(3, 3, false);
        f.listener.onLaunch(f.launch);
        assertEquals(0, f.amount(9));
        verify(f.bow).damage(7, f.player);
        f.slots.put(9, f.stack(Material.ARROW, 64, "normal"));
        clearInvocations(f.inventory);
        assertEquals(3, f.captureRainCount());
        verifyNoInteractions(f.inventory);
        assertEquals(64, f.amount(9));
    }

    @Test void noRemainingAmmunitionProducesNoRain() {
        Fixture f = new Fixture(1, 0, false);
        f.listener.onLaunch(f.launch);
        assertEquals(0, f.captureRainCount());
        verify(f.bow).damage(1, f.player);
    }

    @Test void nativeFreeShotAndCreativeDoNotConsumeExtraAmmunition() {
        int initial = BundledRain.RAIN.count(BundledRain.MAX_LEVEL) + 1;
        for (boolean creative : new boolean[]{false, true}) {
            for (int level = 1; level <= BundledRain.MAX_LEVEL; level++) {
                Fixture f = new Fixture(level, initial, !creative);
                if (creative) when(f.player.getGameMode()).thenReturn(GameMode.CREATIVE);
                f.listener.onLaunch(f.launch);
                assertEquals(initial, f.amount(9));
                assertEquals(BundledRain.RAIN.count(level), f.captureRainCount());
            }
        }
    }

    @Test void cancelledLaunchConsumesNoExtraAmmunitionOrDurability() {
        Fixture f = new Fixture(3, 63, false);
        when(f.launch.isCancelled()).thenReturn(true);
        f.listener.onLaunch(f.launch);
        assertEquals(63, f.amount(9));
        assertEquals(0, f.captureRainCount());
        verify(f.bow, never()).damage(anyInt(), any());
    }

    @Test void offhandThenHeldSlotThenStoragePayOnlyMatchingOriginalArrowMetadata() {
        Fixture f = new Fixture(1, 0, false);
        f.slots.put(40, f.stack(Material.ARROW, 2, "normal"));
        f.slots.put(0, f.stack(Material.ARROW, 3, "normal"));
        f.slots.put(1, f.stack(Material.ARROW, 4, "normal"));
        f.slots.put(2, f.stack(Material.TIPPED_ARROW, 9, "poison"));
        f.slots.put(3, f.stack(Material.ARROW, 9, "custom-name"));
        f.listener.onLaunch(f.launch);
        assertEquals(0, f.amount(40));
        assertEquals(0, f.amount(0));
        assertEquals(1, f.amount(1));
        assertEquals(9, f.amount(2));
        assertEquals(9, f.amount(3));
    }

    @Test void differentArrowTypesAndMetadataCannotPayForTheOriginalAmmo() {
        Fixture f = new Fixture(1, 0, false);
        f.slots.put(0, f.stack(Material.ARROW, 20, "custom-name"));
        f.slots.put(1, f.stack(Material.TIPPED_ARROW, 20, "poison"));
        f.slots.put(2, f.stack(Material.SPECTRAL_ARROW, 20, "spectral"));
        f.listener.onLaunch(f.launch);
        assertEquals(0, f.captureRainCount());
        assertEquals(20, f.amount(0));
        assertEquals(20, f.amount(1));
        assertEquals(20, f.amount(2));
    }

    @Test void nativePaidTippedAndSpectralShotsConsumeOnlyTheirOwnAmmo() {
        for (Material type : new Material[]{Material.TIPPED_ARROW, Material.SPECTRAL_ARROW}) {
            Fixture f = new Fixture(1, 10, false, type, "original");
            f.slots.put(0, f.stack(Material.ARROW, 64, "normal"));
            f.listener.onLaunch(f.launch);
            assertEquals(2, f.amount(9));
            assertEquals(64, f.amount(0));
            assertEquals(8, f.captureRainCount());
            // Infinity's normal-arrow fallback must not even inspect the bow for special arrows.
            verify(f.bow, never()).getEnchantments();
        }
    }

    private static final class Fixture {
        final Plugin plugin = mock(Plugin.class);
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final ItemStack bow = mock(ItemStack.class);
        final AbstractArrow source = mock(AbstractArrow.class);
        final World world = mock(World.class);
        final RegionScheduler regions = mock(RegionScheduler.class);
        final ArrowRainListener listener = BundledRain.listener(plugin);
        final ProjectileLaunchEvent launch = mock(ProjectileLaunchEvent.class);
        final Map<Integer, ItemStack> slots = new HashMap<>();
        final Map<ItemStack, String> variants = new HashMap<>();
        final int level;

        Fixture(int level, int remaining, boolean nativeFree) {
            this(level, remaining, nativeFree, Material.ARROW, "normal");
        }

        Fixture(int level, int remaining, boolean nativeFree, Material type, String variant) {
            this.level = level;
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getHeldItemSlot()).thenReturn(0);
            when(inventory.getItem(anyInt())).thenAnswer(invocation -> slots.get(invocation.getArgument(0)));
            doAnswer(invocation -> {
                slots.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(inventory).setItem(anyInt(), any());
            slots.put(9, stack(type, remaining, variant));
            when(bow.clone()).thenReturn(bow);
            when(bow.getEnchantments()).thenReturn(Map.of());
            when(source.getUniqueId()).thenReturn(UUID.randomUUID());
            when(source.getPickupStatus()).thenReturn(nativeFree ? AbstractArrow.PickupStatus.CREATIVE_ONLY : AbstractArrow.PickupStatus.ALLOWED);
            when(source.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(source.getScheduler()).thenReturn(mock(EntityScheduler.class));
            when(source.getScheduler().runDelayed(eq(plugin), any(), any(), anyLong())).thenReturn(mock(ScheduledTask.class));
            when(source.getLocation()).thenReturn(new Location(world, 10, 64, 10));
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            RainWorld.openSky(world);
            RainWorld.fallingOnto(source);
            when(launch.getEntity()).thenReturn(source);
            listener.trackSource(source, player, bow, level, stack(type, 1, variant));
        }

        ItemStack stack(Material type, int count, String variant) {
            ItemStack item = mock(ItemStack.class);
            AtomicInteger amount = new AtomicInteger(count);
            variants.put(item, variant);
            when(item.getType()).thenReturn(type);
            when(item.getAmount()).thenAnswer(invocation -> amount.get());
            doAnswer(invocation -> { amount.set(invocation.getArgument(0)); return null; }).when(item).setAmount(anyInt());
            when(item.clone()).thenAnswer(invocation -> stack(type, amount.get(), variant));
            when(item.isSimilar(any())).thenAnswer(invocation -> {
                ItemStack other = invocation.getArgument(0);
                return other != null && other.getType() == type && variant.equals(variants.get(other));
            });
            return item;
        }

        int amount(int slot) {
            ItemStack item = slots.get(slot);
            return item == null ? 0 : item.getAmount();
        }

        int captureRainCount() {
            AtomicInteger drops = new AtomicInteger();
            when(regions.runDelayed(eq(plugin), any(Location.class), any(), anyLong())).thenAnswer(invocation -> {
                if (BundledRain.isDrop(invocation.getArgument(3), level)) drops.incrementAndGet();
                return mock(ScheduledTask.class);
            });
            ProjectileHitEvent hit = mock(ProjectileHitEvent.class);
            when(hit.getEntity()).thenReturn(source);
            try (var bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getRegionScheduler).thenReturn(regions);
                listener.onHit(hit);
            }
            return drops.get();
        }
    }
}
