package com.windsor.meowsEnchants.config;

import static org.junit.jupiter.api.Assertions.*;
import com.windsor.meowsEnchants.actions.ActionRegistry;
import com.windsor.meowsEnchants.actions.FixedScaling;
import com.windsor.meowsEnchants.actions.ListScaling;
import com.windsor.meowsEnchants.actions.impl.*;
import com.windsor.meowsEnchants.arrowrain.ArrowRainSettings;
import com.windsor.meowsEnchants.schema.EnchantSchema;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.tracking.TrackingSettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 每个从代码挪进配置的常量，缺省值都等于 milk.12（f96add8）里写死的值；注释是原来的位置。
 * 行为层面的等价另见各动作的行为测试。
 */
class MovedConstantsTest {
    private static void same(Object milk12, Param<?> param) {
        assertTrue(param.hasDefault(), param.key() + " has a default");
        assertEquals(milk12, param.defaultValue(), param.key());
    }

    @Test void actionInternals() {
        same(2, MultiBreakAction.DURABILITY_PER_BLOCK);            // MultiBreakAction.java:156 tool.damage(2)
        same(1, TillAction.DURABILITY_PER_BLOCK);                  // TillAction.java:245 tool.damage(1)
        same(1, RepairAction.REPAIR_AMOUNT);                       // RepairAction.java:75 setDamage(current - 1)
        same(1, RepairAction.EXP_COST);                            // RepairAction.java:69,81 < 1 / takePlayerExp(1)
        same(true, DropHeadAction.PLAYER_HEADS);                   // DropHeadAction.java:41 player heads always dropped
        same(false, DropHeadAction.PLAYER_HEAD_SUCCESS);           // DropHeadAction.java:49 return false
        same(1.0, TeleportAction.STEP);                            // TeleportAction.java:55 dist -= 1.0
        same(0.5, TeleportAction.MIN_DISTANCE);                    // TeleportAction.java:55 dist >= 0.5
        same(4, TeleportAction.GROUND_SEARCH_DEPTH);               // TeleportAction.java:79 yOffset <= 4
        same(new FixedScaling(1.0), DeflectAction.AMOUNT);         // DeflectAction.java:42 always deflects
        same(360.0, DeflectAction.YAW_SPREAD);                     // DeflectAction.java:54 random * 2π
        same(30.0, DeflectAction.PITCH_SPREAD);                    // DeflectAction.java:55 ±π/6
        same(1, FeastAction.FOOD);                                 // FeastAction.java:42 foodLevel + 1
        same(1.0, FeastAction.SATURATION);                         // FeastAction.java:47 saturation + 1
        same(1.0, HealthstealAction.MAX_FRACTION);                 // HealthstealAction.java:53 cap 1.0
        same(0.25, VelocityAction.VERTICAL_SCALE);                 // VelocityAction.java:74 y * 0.25
        same(1, VelocityAction.GROUND_DISTANCE);                   // VelocityAction.java:28 MAX_GROUND_DISTANCE
        same(100, VelocityAction.NO_FALL_DAMAGE_TICKS);            // VelocityAction.java:82,85 5000 ms / 100 ticks
        same(64, ShockwaveAction.MAX_ENTITIES);                    // ShockwaveAction.java:27
        same(16.0, ShockwaveAction.MAX_POWER);                     // ShockwaveAction.java:29
        same(0.5, ShockwaveAction.KNOCKBACK_RESISTANCE_REDUCTION); // ShockwaveAction.java:31
        same(1.0, ShockwaveAction.FALLOFF_START);                  // ShockwaveAction.java:82,85 distance - 1
        same(2.0, ShockwaveAction.FALLOFF_EXPONENT);               // ShockwaveAction.java:85 pow(.., 2)
        same(45.0, ShockwaveAction.LAUNCH_ANGLE);                  // ShockwaveAction.java:98-100 1/√2, 1/√2
        same(1.0, ReflectDamageAction.DAMAGE_RATIO);               // ReflectDamageAction.java:64-66 full damage
        same(5, ReplantAction.BONE_MEAL);                          // ReplantAction.java:86
        same(3, ReplantAction.DELAY_TICKS);                        // ReplantAction.java:62
        same(1, BlockBreakAction.DURABILITY);                      // BlockBreakAction.java:150
        same(20, AutoBowAction.FULL_DRAW_TICKS);                   // AutoBowAction.java:25
        same(13000, BonusDamageAction.NIGHT_START);                // BonusDamageAction.java:87
        same(23000, BonusDamageAction.NIGHT_END);                  // BonusDamageAction.java:87
        same(1.0, HookAction.MIN_DISTANCE);                        // HookAction.java:39
        same(5.0, HookAction.FULL_POWER_DISTANCE);                 // HookAction.java:46-49
        same(45.0, HookAction.LAUNCH_ANGLE);                       // HookAction.java:60-63
        same(3.0, MultiArrowAction.DEFAULT_SPEED);                 // MultiArrowAction.java:29
        same(60.0, MultiArrowAction.MAX_SPREAD);                   // MultiArrowAction.java:30
        same(1, MultiArrowAction.INTERVAL_TICKS);                  // MultiArrowAction.java:97 i + 1
    }

    @Test void dropHeadTableIsTheOldSwitch() {
        Map<String, Object> heads = new LinkedHashMap<>();         // DropHeadAction.java:62-69
        heads.put("CREEPER", "CREEPER_HEAD");
        heads.put("ZOMBIE", "ZOMBIE_HEAD");
        heads.put("SKELETON", "SKELETON_SKULL");
        heads.put("WITHER_SKELETON", "WITHER_SKELETON_SKULL");
        heads.put("PIGLIN", "PIGLIN_HEAD");
        heads.put("ENDER_DRAGON", "DRAGON_HEAD");
        assertEquals(heads, DropHeadAction.HEADS.defaultValue());
    }

    @Test void replantCropTableIsTheOldEnum() {
        Map<String, Object> crops = ReplantAction.CROPS.defaultValue(); // ReplantAction.java:124-136
        assertEquals(List.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART"), List.copyOf(crops.keySet()));
        assertEquals(Map.of("seed", "WHEAT_SEEDS", "soil", List.of("FARMLAND")), crops.get("WHEAT"));
        assertEquals(Map.of("seed", "CARROT", "soil", List.of("FARMLAND")), crops.get("CARROTS"));
        assertEquals(Map.of("seed", "POTATO", "soil", List.of("FARMLAND")), crops.get("POTATOES"));
        assertEquals(Map.of("seed", "BEETROOT_SEEDS", "soil", List.of("FARMLAND")), crops.get("BEETROOTS"));
        assertEquals(Map.of("seed", "NETHER_WART", "soil", List.of("SOUL_SAND", "SOUL_SOIL")), crops.get("NETHER_WART"));
    }

    @Test void registrationAttributes() {                         // MeowsEnchantsBootstrap.java:97-104
        same(1, EnchantSchema.REGISTRY_WEIGHT);
        same(30, EnchantSchema.REGISTRY_MIN_COST);
        same(0, EnchantSchema.REGISTRY_MIN_COST_PER_LEVEL);
        same(30, EnchantSchema.REGISTRY_MAX_COST);
        same(0, EnchantSchema.REGISTRY_MAX_COST_PER_LEVEL);
        same("ANY", EnchantSchema.REGISTRY_ACTIVE_SLOTS);
        EnchantConfig.RegistrySettings defaults = EnchantConfig.RegistrySettings.from(Map.of());
        assertEquals(new EnchantConfig.RegistrySettings(1, 30, 0, 30, 0, "ANY"), defaults);
    }

    @Test void globalFormulasAndFramework() {
        GlobalSettings g = GlobalSettings.DEFAULTS;
        assertEquals(512, g.skylliaRegionSize());                  // SkylliaCompatibility.java:9
        assertEquals(Set.of("explosion", "hook", "multi_arrow", "multi_break", "potion_effect", "shockwave", "till",
                "vacuum_drops"), g.skylliaProtectedActions());     // EnchantTriggerHelper.java:40-49 (class names)
        assertEquals(2.0, g.tableLevelChanceDivisor());            // EnchantTableListener.java:73 Math.pow(2, level)
        assertEquals(4.0, g.tableBookDivisor());                   // EnchantTableListener.java:78 /= 4.0
        assertEquals(12, g.tradeDefaultMaxUses());                 // VillagerTradeListener.java:187
        assertEquals("&f[&b附魔&f] &c该村民交易已被污染！", g.tradePollutedMessage()); // VillagerTradeListener.java:37-38
        assertEquals(20, g.passiveMinPeriodTicks());               // PassiveTaskManager.java:25
        assertEquals(20, g.holdMinPeriodTicks());                  // PlayerStateListener.java:64
        assertEquals(500.0, g.tablePlayerLevelFactor());           // MeowsEnchants.java:170-173
    }

    @Test void villagerPriceFormulaIsUnchanged() {               // VillagerTradeListener.java:176-182
        GlobalSettings g = GlobalSettings.DEFAULTS;
        for (int level = 1; level <= 10; level++) {
            for (boolean treasure : new boolean[]{false, true}) {
                int baseMin = 2 + level * 3, baseMax = 6 + level * 13;
                for (int roll : new int[]{0, (baseMax - baseMin) / 2, baseMax - baseMin}) {
                    int price = baseMin + roll;
                    if (treasure) price *= 2;
                    int expected = Math.clamp(price, 1, 64);
                    int bound = baseMax - baseMin + 1;
                    assertEquals(expected, g.tradePrice(level, treasure, b -> {
                        assertEquals(bound, b, "same random bound");
                        return roll;
                    }));
                }
            }
        }
    }

    @Test void enchantingTableChanceFormulaIsUnchanged() {       // EnchantTableListener.java:73
        for (int level = 1; level <= 5; level++) {
            assertEquals(0.01 / Math.pow(2, level), GlobalSettings.DEFAULTS.tableLevelChance(0.01, level), 0.0);
        }
    }

    @Test void arrowRainPatternIsTheOldRainPattern() {           // RainPattern.java:9-18, ArrowRainListener.java:37,84,212
        ArrowRainSettings s = ArrowRainSettings.DEFAULTS;
        assertEquals(new ListScaling(List.of(8.0, 16.0, 32.0, 64.0, 128.0)), s.arrows());
        assertEquals(new ListScaling(List.of(1.5, 2.5, 3.5, 4.5, 5.5)), s.radius());
        assertEquals(new ListScaling(List.of(1.0, 3.0, 7.0, 15.0, 31.0)), s.extraDurability());
        assertEquals(new ListScaling(List.of(20.0, 40.0, 60.0, 80.0, 100.0)), s.releaseTicks());
        assertEquals(20.0, s.height());
        assertEquals(2.5, s.speed());
        assertEquals(30, s.lifetimeTicks());
        assertEquals(1200, s.sourceMaxTicks());
        assertEquals("CRIT", s.trailParticle());
        assertEquals(2, s.trailCount());
        assertEquals(0.02, s.trailOffset());
        assertEquals(0.0, s.trailSpeed());
        var pattern = new com.windsor.meowsEnchants.arrowrain.RainPattern(s);
        for (int level = 1; level <= 9; level++) {
            int old = Math.clamp(level, 1, 5);                     // RainPattern.java:13-18
            assertEquals(8 << (old - 1), pattern.count(level));
            assertEquals(old + 0.5, pattern.radius(level));
            assertEquals((2 << (old - 1)) - 1, pattern.extraDurability(level));
            assertEquals(20 * old, pattern.lastSpawnTick(level));
        }
    }

    @Test void trackingTargetRules() {                           // TargetRules.java:54-57, 83-84
        TrackingSettings t = TrackingSettings.DEFAULTS;
        assertEquals(Set.of("VILLAGER", "WANDERING_TRADER", "IRON_GOLEM", "SNOW_GOLEM", "COPPER_GOLEM", "ALLAY"), t.neverTracked());
        assertEquals(Set.of("ENDERMAN", "ZOMBIFIED_PIGLIN", "PIGLIN"), t.neutralUntilAttacking());
    }

    @Test void newBehaviourDefaultsAreNeutral() {
        same(new FixedScaling(1.0), ActionRegistry.CHANCE);        // 契约 §5：默认 100%
    }
}
