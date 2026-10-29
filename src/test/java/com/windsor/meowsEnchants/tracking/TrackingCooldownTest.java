package com.windsor.meowsEnchants.tracking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.windsor.meowsEnchants.arrowrain.ArrowRainListener;
import com.windsor.meowsEnchants.listeners.MechanicCooldown;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * 跟踪读 cooldown：冷却从一次射击开始跟踪那一刻算，同一次多重射击的几支算一次；冷却中射出的弹射物不打标记、
 * 不跟踪。雨箭跟随它所属的那一箭，既不查也不刷新冷却。
 */
class TrackingCooldownTest {
    final Plugin plugin = mock(Plugin.class);
    final World world = TargetRulesTest.world("minecraft:overworld", "world");
    final CombatTracker combat = new CombatTracker(TimeUnit.SECONDS.toNanos(15));
    final Player player = TargetRulesTest.player(world);
    final AtomicLong now = new AtomicLong(10_000);
    final AtomicLong tick = new AtomicLong(100);
    long trackingCooldown = 1500;
    final MechanicCooldown cooldown = new MechanicCooldown(
            key -> TrackingListener.ENCHANT.equals(key) ? trackingCooldown
                    : ArrowRainListener.ENCHANT.equals(key) ? 60_000 : 0, now::get);
    final TrackingListener listener = new TrackingListener(plugin, TrackingSettings.DEFAULTS, combat,
            new TargetRules(TrackingSettings.DEFAULTS, combat, TargetRules.Protection.NONE, w -> true), null, null, cooldown);

    TrackingCooldownTest() {
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.isEnabled()).thenReturn(true);
        when(player.getLocation()).thenAnswer(invocation -> new Location(world, 0, 64, 0));
        when(world.getGameTime()).thenAnswer(invocation -> tick.get());
    }

    void advance(long ms) {
        now.addAndGet(ms);
        tick.addAndGet(Math.max(1, ms / 50));
    }

    Projectile projectile() {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(arrow.isValid()).thenReturn(true);
        when(arrow.getWorld()).thenReturn(world);
        when(arrow.hasGravity()).thenReturn(true);
        when(arrow.getLocation()).thenAnswer(invocation -> new Location(world, 0, 65, 0));
        when(arrow.getVelocity()).thenAnswer(invocation -> new Vector(3, 0, 0));
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(scheduler.runAtFixedRate(any(), any(), any(), anyLong(), anyLong())).thenReturn(mock(ScheduledTask.class));
        when(arrow.getScheduler()).thenReturn(scheduler);
        return arrow;
    }

    static ItemStack weapon(Material type) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(type);
        Keyed tracking = mock(Keyed.class);
        when(tracking.getKey()).thenReturn(TrackingListener.ENCHANT);
        doReturn(Map.of(tracking, 1)).when(item).getEnchantments();
        return item;
    }

    Projectile fire(Material weapon) { return fire(weapon, false); }

    Projectile fire(Material weapon, boolean launchCancelled) {
        Projectile projectile = projectile();
        ItemStack bow = weapon(weapon);
        EntityShootBowEvent shoot = mock(EntityShootBowEvent.class);
        when(shoot.getEntity()).thenReturn(player);
        when(shoot.getProjectile()).thenReturn(projectile);
        when(shoot.getBow()).thenReturn(bow);
        listener.onShoot(shoot);
        ProjectileLaunchEvent launch = mock(ProjectileLaunchEvent.class);
        when(launch.getEntity()).thenReturn(projectile);
        when(launch.isCancelled()).thenReturn(launchCancelled);
        listener.onLaunch(launch);
        return projectile;
    }

    boolean tracked(Projectile projectile) { return listener.tracker(projectile.getUniqueId()) != null; }

    boolean marked(Projectile projectile) {
        return mockingDetails(projectile.getPersistentDataContainer()).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("set")
                        && TrackingListener.MARK.equals(invocation.getArgument(0)));
    }

    @Test void shotsDuringTheCooldownStayPlainAndUnmarkedUntilItEnds() {
        assertTrue(tracked(fire(Material.BOW)));
        advance(500);
        Projectile plain = fire(Material.BOW);
        assertFalse(tracked(plain));
        assertFalse(marked(plain), "a plain arrow: named mobs and bystanders are not special-cased for it");
        advance(999);
        assertFalse(tracked(fire(Material.BOW)));
        advance(1);
        assertTrue(tracked(fire(Material.BOW)));
    }

    @Test void aZeroCooldownTracksEveryShot() {
        trackingCooldown = 0;
        for (int i = 0; i < 3; i++) {
            assertTrue(tracked(fire(Material.BOW)));
            advance(1);
        }
    }

    @Test void aMultishotVolleyTracksEveryArrowAndCountsOnce() {
        for (int i = 0; i < 3; i++) {
            assertTrue(tracked(fire(Material.CROSSBOW)), "arrow " + i + " of the volley");
            now.addAndGet(5); // Same region tick.
        }
        advance(50);
        assertFalse(tracked(fire(Material.CROSSBOW)), "the next volley is on cooldown");
        now.set(11_500);
        tick.addAndGet(30);
        assertTrue(tracked(fire(Material.CROSSBOW)), "counted from the first arrow, not refreshed by the others");
    }

    @Test void aCancelledLaunchDoesNotStartTheCooldown() {
        assertFalse(tracked(fire(Material.BOW, true)));
        advance(50);
        assertTrue(tracked(fire(Material.BOW)));
    }

    @Test void rainArrowsFollowTheirShotAndNeverTouchTheCooldown() {
        Projectile source = fire(Material.BOW);
        when(source.getPersistentDataContainer().has(TrackingListener.MARK, PersistentDataType.BYTE)).thenReturn(true);
        assertTrue(listener.followsRain((AbstractArrow) source));
        advance(1000);
        Projectile rain = projectile();
        listener.rainSpawned((Arrow) rain, player, weapon(Material.BOW), new Location(world, 0, 64, 0), mock(Runnable.class));
        assertTrue(tracked(rain), "rain of a tracked shot tracks even while the cooldown runs");
        advance(500);
        assertTrue(tracked(fire(Material.BOW)), "the rain arrow did not refresh the cooldown");

        advance(100);
        Projectile plain = fire(Material.BOW); // On cooldown: unmarked.
        assertFalse(listener.followsRain((AbstractArrow) plain), "its rain stays plain too");
    }

    @Test void arrowRainsCooldownNeverBlocksTracking() {
        cooldown.start(player, ArrowRainListener.ENCHANT);
        advance(50);
        assertTrue(tracked(fire(Material.BOW)));
    }
}
