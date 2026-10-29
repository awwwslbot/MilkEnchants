package com.windsor.meowsEnchants.arrowrain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.windsor.meowsEnchants.listeners.MechanicCooldown;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/**
 * 箭雨读 cooldown：冷却从箭雨真正触发（原箭发射、雨箭已预留）开始算；冷却中射出的是普通箭，
 * 不预留雨箭、不扣额外耐久。雨箭不进入这个入口。
 */
class ArrowRainCooldownTest {
    static final NamespacedKey TRACKING = new NamespacedKey("meowsenchants", "tracking");

    final Plugin plugin = mock(Plugin.class);
    final World world = mock(World.class);
    final Player player = mock(Player.class);
    final ItemStack bow = mock(ItemStack.class);
    final AtomicLong now = new AtomicLong(10_000);
    final AtomicLong tick = new AtomicLong(100);
    long rainCooldown = 1500;
    final MechanicCooldown cooldown = new MechanicCooldown(
            key -> ArrowRainListener.ENCHANT.equals(key) ? rainCooldown : TRACKING.equals(key) ? 60_000 : 0, now::get);
    final ArrowRainListener listener = BundledRain.listener(plugin, cooldown);

    ArrowRainCooldownTest() {
        when(plugin.isEnabled()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE); // Rain arrows are free, no durability charge.
        when(world.getGameTime()).thenAnswer(invocation -> tick.get());
        Keyed rain = mock(Keyed.class);
        when(rain.getKey()).thenReturn(ArrowRainListener.ENCHANT);
        when(bow.getType()).thenReturn(Material.BOW);
        doReturn(Map.of(rain, 1)).when(bow).getEnchantments();
        when(bow.clone()).thenReturn(bow);
    }

    void advance(long ms) {
        now.addAndGet(ms);
        tick.addAndGet(Math.max(1, ms / 50));
    }

    /** A source arrow; its entity scheduler only sees calls when arrow rain took the shot. */
    static AbstractArrow arrow(boolean rain) {
        AbstractArrow arrow = mock(AbstractArrow.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getPickupStatus()).thenReturn(AbstractArrow.PickupStatus.ALLOWED);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(ArrowRainListener.GENERATED, PersistentDataType.BYTE)).thenReturn(rain);
        when(arrow.getPersistentDataContainer()).thenReturn(pdc);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(scheduler.runDelayed(any(), any(), any(), anyLong())).thenReturn(mock(ScheduledTask.class));
        when(arrow.getScheduler()).thenReturn(scheduler);
        return arrow;
    }

    AbstractArrow fire() { return fire(arrow(false)); }

    AbstractArrow fire(AbstractArrow arrow) {
        EntityShootBowEvent shoot = mock(EntityShootBowEvent.class);
        when(shoot.getEntity()).thenReturn(player);
        when(shoot.getProjectile()).thenReturn(arrow);
        when(shoot.getBow()).thenReturn(bow);
        listener.onShoot(shoot);
        ProjectileLaunchEvent launch = mock(ProjectileLaunchEvent.class);
        when(launch.getEntity()).thenReturn(arrow);
        listener.onLaunch(launch);
        return arrow;
    }

    /** Arrow rain took the shot: it scheduled the source's expiry. */
    static boolean took(AbstractArrow arrow) {
        return !mockingDetails(arrow.getScheduler()).getInvocations().isEmpty();
    }

    @Test void shotsDuringTheCooldownStayPlainArrowsUntilItEnds() {
        assertTrue(took(fire()));
        advance(500);
        AbstractArrow plain = fire();
        assertFalse(took(plain));
        verify(plain, never()).getPickupStatus(); // Never even priced as a rain shot.
        advance(999);
        assertFalse(took(fire()));
        advance(1); // 1500ms after the first rain triggered.
        assertTrue(took(fire()));
    }

    @Test void aZeroCooldownTriggersEveryShot() {
        rainCooldown = 0;
        for (int i = 0; i < 3; i++) {
            assertTrue(took(fire()));
            advance(1);
        }
    }

    @Test void aShotThatReservesNoRainArrowDoesNotStartTheCooldown() {
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        AbstractArrow noAmmunition = fire(); // No consumable captured: nothing to reserve.
        assertTrue(took(noAmmunition));
        assertTrue(cooldown.ready(player, ArrowRainListener.ENCHANT));
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        advance(50);
        assertTrue(took(fire()));
        advance(50);
        assertFalse(cooldown.ready(player, ArrowRainListener.ENCHANT));
    }

    @Test void rainArrowsNeitherStartAnotherRainNorRefreshTheCooldown() {
        fire();
        advance(1000);
        AbstractArrow rain = fire(arrow(true));
        assertFalse(took(rain));
        ProjectileHitEvent landed = mock(ProjectileHitEvent.class);
        when(landed.getEntity()).thenReturn(rain);
        listener.onHit(landed);
        advance(500);
        assertTrue(cooldown.ready(player, ArrowRainListener.ENCHANT), "still 1500ms after the original shot");
    }

    @Test void trackingsCooldownNeverBlocksArrowRain() {
        cooldown.start(player, TRACKING);
        advance(50);
        assertTrue(took(fire()));
    }
}
