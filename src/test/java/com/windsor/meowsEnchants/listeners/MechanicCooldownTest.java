package com.windsor.meowsEnchants.listeners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.windsor.meowsEnchants.config.EnchantConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * 专用机制（箭雨、跟踪）的冷却入口：和通用附魔共用「玩家 + 附魔」的计时，从 start（机制真正触发）开始算，
 * 同一 tick 的多重射击算一次触发。
 */
class MechanicCooldownTest {
    static final NamespacedKey RAIN = new NamespacedKey("meowsenchants", "arrow_rain");
    static final NamespacedKey TRACKING = new NamespacedKey("meowsenchants", "tracking");

    final AtomicLong now = new AtomicLong(10_000);
    final AtomicLong tick = new AtomicLong(100);
    final Map<NamespacedKey, Long> configured = new HashMap<>(Map.of(RAIN, 1500L, TRACKING, 800L));
    final MechanicCooldown cooldown = new MechanicCooldown(key -> configured.getOrDefault(key, 0L), now::get);
    final World world = mock(World.class);
    final Player player = player();

    MechanicCooldownTest() {
        when(world.getGameTime()).thenAnswer(invocation -> tick.get());
    }

    Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        return player;
    }

    /** 时间前进 ms 毫秒，tick 按 50ms 一个同步前进。 */
    void advance(long ms) {
        now.addAndGet(ms);
        tick.addAndGet(Math.max(1, ms / 50));
    }

    @Test void theCooldownStartsWhenTheMechanicTriggersAndEndsAfterTheConfiguredTime() {
        // 只问不触发：问多少次都不进冷却
        for (int i = 0; i < 3; i++) assertTrue(cooldown.ready(player, RAIN));
        advance(1000);
        cooldown.start(player, RAIN);
        advance(1);
        assertFalse(cooldown.ready(player, RAIN));
        advance(1498);
        assertFalse(cooldown.ready(player, RAIN), "1499ms after the trigger");
        advance(1);
        assertTrue(cooldown.ready(player, RAIN), "1500ms after the trigger");
    }

    @Test void aZeroCooldownIsAlwaysReady() {
        configured.put(RAIN, 0L);
        for (int i = 0; i < 5; i++) {
            assertTrue(cooldown.ready(player, RAIN));
            cooldown.start(player, RAIN);
            advance(1);
        }
        assertFalse(EnchantTriggerHelper.isOnCooldown(player, RAIN, now.get()));
    }

    @Test void eachPlayerAndEnchantmentHasItsOwnCooldown() {
        Player other = player();
        cooldown.start(player, RAIN);
        advance(50);
        assertFalse(cooldown.ready(player, RAIN));
        assertTrue(cooldown.ready(player, TRACKING), "arrow rain's cooldown never blocks tracking");
        assertTrue(cooldown.ready(other, RAIN));
        cooldown.start(player, TRACKING);
        advance(800);
        assertTrue(cooldown.ready(player, TRACKING));
        assertFalse(cooldown.ready(player, RAIN), "tracking's shorter cooldown leaves arrow rain's alone");
    }

    @Test void oneMultishotVolleyIsOneTriggerAndDoesNotRefreshTheCooldown() {
        cooldown.start(player, TRACKING); // First arrow of the volley, at 10000ms.
        for (int i = 0; i < 2; i++) {
            now.addAndGet(5); // Same region tick: the volley's other arrows.
            assertTrue(cooldown.ready(player, TRACKING));
            cooldown.start(player, TRACKING);
        }
        advance(50); // Next tick, still on cooldown.
        assertFalse(cooldown.ready(player, TRACKING));
        now.set(10_800);
        tick.addAndGet(20);
        assertTrue(cooldown.ready(player, TRACKING), "the cooldown still counts from the volley's first arrow");
    }

    @Test void sharesTheGenericEnchantCooldown() {
        EnchantTriggerHelper.setCooldown(player, RAIN, 300, now.get());
        assertFalse(cooldown.ready(player, RAIN));
        advance(300);
        assertTrue(cooldown.ready(player, RAIN));
        cooldown.start(player, RAIN);
        assertTrue(EnchantTriggerHelper.isOnCooldown(player, RAIN, now.get()));
    }

    @Test void theGenericTriggerLeavesMechanicEnchantmentsToTheirMechanic() {
        // 通用触发器替带 mechanic 的附魔「执行」空动作时会顺手设冷却，机制就会被它挡住
        EnchantConfig rain = config("arrow_rain", Map.of("mechanic", "landing_arrow_rain_v1"));
        assertEquals("landing_arrow_rain_v1", rain.getMechanic());
        assertFalse(EnchantTriggerHelper.handles(rain, "BOW_SHOOT"));
        EnchantConfig plain = config("plain_probe", Map.of());
        assertNull(plain.getMechanic());
        assertTrue(EnchantTriggerHelper.handles(plain, "BOW_SHOOT"));
        assertTrue(EnchantTriggerHelper.handles(plain, "bow_shoot"));
        assertFalse(EnchantTriggerHelper.handles(plain, "PROJECTILE_HIT"));
        assertFalse(EnchantTriggerHelper.handles(config("tracking", Map.of("mechanic", "tracking_v1")), "BOW_SHOOT"));
    }

    @Test void aStrayMechanicFlagDoesNotHideAnEnchantmentFromTheGenericTrigger() {
        // 只有 arrow_rain + landing_arrow_rain_v1、tracking + tracking_v1 由机制接管；其余照常走通用触发器
        for (EnchantConfig stray : List.of(
                config("night_vision", Map.of("mechanic", "tracking_v1")),
                config("arrow_rain", Map.of("mechanic", "landing_arrow_rain_v2")),
                config("arrow_rain", Map.of("mechanic", "tracking_v1")),
                config("tracking", Map.of("mechanic", "something_else")))) {
            assertNotNull(stray.getMechanic());
            assertFalse(stray.isMechanicEnchant(), stray.getId());
            assertTrue(EnchantTriggerHelper.handles(stray, "BOW_SHOOT"), stray.getId() + " " + stray.getMechanic());
        }
    }

    static EnchantConfig config(String id, Map<String, Object> raw) {
        return new EnchantConfig(id, id, null, 1, 1, "BOW", "BOW_SHOOT", "SELF", 60_000, List.of(), null, null,
                null, null, null, null, raw);
    }
}
