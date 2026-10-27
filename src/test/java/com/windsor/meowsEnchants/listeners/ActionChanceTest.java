package com.windsor.meowsEnchants.listeners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ActionRegistry;
import com.windsor.meowsEnchants.actions.FixedScaling;
import com.windsor.meowsEnchants.actions.ListScaling;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 契约 §5：每个动作可写 chance；没中 = 该动作失败，中止后续动作（checkAndTrigger 据此不播效果、不进冷却）。
 * 走的是 checkAndTrigger 里同一段加载 + 执行代码。
 */
class ActionChanceTest {
    static final List<String> RUNS = new ArrayList<>();

    @BeforeAll static void probe() {
        ActionRegistry.register("chance_probe", params -> context -> {
            RUNS.add(String.valueOf(params.get("tag")));
            return !Boolean.FALSE.equals(params.get("succeed"));
        });
    }

    @AfterEach void reset() { RUNS.clear(); }

    static Map<String, Object> action(String tag, Object chance) {
        return chance == null ? Map.of("type", "chance_probe", "tag", tag)
                : Map.of("type", "chance_probe", "tag", tag, "chance", chance);
    }

    static boolean run(int level, double roll, List<Map<String, Object>> actions) {
        ActionContext context = new ActionContext(null, null, Key.key("meowsenchants", "probe"), level, "TEST",
                List.of(), null, null, "SELF");
        return EnchantTriggerHelper.runActions(EnchantTriggerHelper.loadActions(actions), context, () -> roll);
    }

    @Test void aMissedChanceStopsTheFollowingActions() {
        assertFalse(run(1, 0.5, List.of(action("first", 0.3), action("second", null))));
        assertEquals(List.of(), RUNS);
    }

    @Test void aLaterMissKeepsEarlierActionsButTheWholeTriggerFails() {
        assertFalse(run(1, 0.5, List.of(action("first", null), action("second", 0.0), action("third", null))));
        assertEquals(List.of("first"), RUNS);
    }

    @Test void aHitRunsTheAction() {
        assertTrue(run(1, 0.29, List.of(action("first", 0.3), action("second", 1.0))));
        assertEquals(List.of("first", "second"), RUNS);
    }

    @Test void withoutChanceNothingIsRolled() {
        ActionContext context = new ActionContext(null, null, Key.key("meowsenchants", "probe"), 1, "TEST",
                List.of(), null, null, "SELF");
        assertTrue(EnchantTriggerHelper.runActions(EnchantTriggerHelper.loadActions(List.of(action("only", null))),
                context, () -> { throw new AssertionError("rolled"); }));
        assertEquals(List.of("only"), RUNS);
    }

    @Test void chanceScalesWithTheEnchantLevel() {
        assertFalse(run(1, 0.5, List.of(action("lv1", List.of(0.0, 1.0)))));
        assertTrue(run(2, 0.5, List.of(action("lv2", List.of(0.0, 1.0)))));
        assertTrue(run(3, 0.5, List.of(action("lv3", Map.of("base", 0.1, "per_level", 0.3)))));
        assertFalse(run(1, 0.5, List.of(action("lv1b", Map.of("base", 0.1, "per_level", 0.3)))));
        assertEquals(List.of("lv2", "lv3"), RUNS);
    }

    @Test void anInvalidChanceSkipsOnlyThatAction() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("ActionChanceTest"));
            assertTrue(run(1, 0.5, List.of(action("bad", List.of()), action("good", null))));
        }
        assertEquals(List.of("good"), RUNS);
    }

    @Test void aFailingActionAfterAHitStillStops() {
        Map<String, Object> failing = Map.of("type", "chance_probe", "tag", "fails", "succeed", false, "chance", 1.0);
        assertFalse(run(1, 0.1, List.of(failing, action("after", null))));
        assertEquals(List.of("fails"), RUNS);
    }

    @Test void theRollIsAStrictComparison() {
        assertTrue(EnchantTriggerHelper.chancePasses(new FixedScaling(0.3), 1, 0.2999));
        assertFalse(EnchantTriggerHelper.chancePasses(new FixedScaling(0.3), 1, 0.3));
        assertFalse(EnchantTriggerHelper.chancePasses(new FixedScaling(0.0), 1, 0.0));
        assertTrue(EnchantTriggerHelper.chancePasses(new FixedScaling(1.0), 1, 0.9999999));
        assertTrue(EnchantTriggerHelper.chancePasses(new ListScaling(List.of(0.0, 0.5)), 2, 0.4));
    }

    @Test void aboutTheConfiguredShareOfTriggersPass() {
        Random random = new Random(5);
        int passed = 0;
        for (int i = 0; i < 4000; i++) if (run(1, random.nextDouble(), List.of(action("x", 0.25)))) passed++;
        assertEquals(0.25, passed / 4000.0, 0.03);
    }
}
