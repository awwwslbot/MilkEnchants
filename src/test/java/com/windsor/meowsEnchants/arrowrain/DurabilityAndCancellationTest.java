package com.windsor.meowsEnchants.arrowrain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class DurabilityAndCancellationTest {
    @Test void chargesOnlyExtraNativeDurabilityOnExactFiredBow() {
        for (int level = 1; level <= BundledRain.MAX_LEVEL; level++) {
            Player player = mock(Player.class);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            ItemStack actualFiredBow = mock(ItemStack.class);
            ArrowRainListener.chargeDurability(actualFiredBow, player, BundledRain.RAIN.extraDurability(level));
            verify(actualFiredBow).damage(BundledRain.RAIN.extraDurability(level), player);
            verifyNoMoreInteractions(actualFiredBow);
            verify(player, never()).getInventory();
        }
    }

    @Test void creativeAndSpectatorNeverPayAdditionalDurability() {
        for (GameMode mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
            Player player = mock(Player.class);
            when(player.getGameMode()).thenReturn(mode);
            ItemStack bow = mock(ItemStack.class);
            ArrowRainListener.chargeDurability(bow, player, BundledRain.RAIN.extraDurability(3));
            verifyNoInteractions(bow);
        }
    }

    @Test void cancelledShootNeverReadsBowOrCapturesProjectile() {
        var listener = BundledRain.listener(mock(Plugin.class));
        var event = mock(EntityShootBowEvent.class);
        when(event.isCancelled()).thenReturn(true);
        listener.onShoot(event);
        verify(event, never()).getBow();
        verify(event, never()).getEntity();
        verify(event, never()).getProjectile();
    }

    @Test void cancelledImpactNeverStartsRain() {
        var listener = BundledRain.listener(mock(Plugin.class));
        var event = mock(ProjectileHitEvent.class);
        when(event.isCancelled()).thenReturn(true);
        listener.onHit(event);
        verify(event, never()).getEntity();
        verify(event, never()).getHitBlock();
        verify(event, never()).getHitEntity();
    }

    @Test void crossbowAndUnenchantedBowNeverTrigger() {
        ItemStack crossbow = mock(ItemStack.class);
        when(crossbow.getType()).thenReturn(Material.CROSSBOW);
        assertEquals(0, ArrowRainListener.enchantLevel(crossbow));
        verify(crossbow, never()).getEnchantments();
        ItemStack bow = mock(ItemStack.class);
        when(bow.getType()).thenReturn(Material.BOW);
        when(bow.getEnchantments()).thenReturn(java.util.Map.of());
        assertEquals(0, ArrowRainListener.enchantLevel(bow));
        assertEquals(0, ArrowRainListener.enchantLevel(null));
    }
}
