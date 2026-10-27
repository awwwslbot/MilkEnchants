package com.windsor.meowsEnchants.schema;

import static org.junit.jupiter.api.Assertions.*;
import com.windsor.meowsEnchants.actions.ActionRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** editor/enchant-schema.json 的形状符合契约 §3。 */
class SchemaShapeTest {
    static final Map<String, Object> SCHEMA = EnchantSchema.toJson("test");
    static final Set<String> ALWAYS = Set.of("key", "label", "help", "type", "scalable", "required", "unit", "min", "max",
            "warnAbove", "apply", "danger");

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> params(Object list) { return (List<Map<String, Object>>) list; }

    /** 全部 Param（含嵌套），路径 → 对象。 */
    @SuppressWarnings("unchecked")
    static void walk(String prefix, List<Map<String, Object>> params, Map<String, Map<String, Object>> out) {
        for (Map<String, Object> param : params) {
            String path = prefix + param.get("key");
            out.put(path, param);
            if (param.containsKey("children")) walk(path + ".", (List<Map<String, Object>>) param.get("children"), out);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> all() {
        Map<String, Map<String, Object>> out = new java.util.LinkedHashMap<>();
        walk("enchant:", params(((Map<String, Object>) SCHEMA.get("enchant")).get("params")), out);
        walk("common:", params(SCHEMA.get("actionCommon")), out);
        ((Map<String, Map<String, Object>>) SCHEMA.get("actions")).forEach((type, action) ->
                walk("action:" + type + ":", params(action.get("params")), out));
        ((Map<String, Map<String, Object>>) SCHEMA.get("mechanics")).forEach((section, mechanic) ->
                walk("mechanic:" + section + ":", params(mechanic.get("params")), out));
        walk("global:", params(((Map<String, Object>) SCHEMA.get("global")).get("params")), out);
        return out;
    }

    @Test void topLevelListsMatchTheContract() {
        assertEquals(List.of("schemaVersion", "plugin", "pluginVersion", "triggers", "targets", "applicableItems",
                "enchant", "actionCommon", "actions", "mechanics", "global"), List.copyOf(SCHEMA.keySet()));
        assertEquals(21, params(SCHEMA.get("triggers")).size());
        assertEquals(Set.of("PASSIVE", "SNEAK_HOLD", "SPRINT_HOLD"), Set.copyOf(params(SCHEMA.get("triggers")).stream()
                .filter(t -> Boolean.TRUE.equals(t.get("periodic"))).map(t -> t.get("id")).toList()));
        assertEquals(List.of("SELF", "PLAYERS", "MOBS", "ALL_ENTITIES", "NONE"),
                params(SCHEMA.get("targets")).stream().map(t -> t.get("id")).toList());
        assertEquals(21, params(SCHEMA.get("applicableItems")).size());
        @SuppressWarnings("unchecked") Map<String, Object> actions = (Map<String, Object>) SCHEMA.get("actions");
        assertEquals(23, actions.size());
        assertFalse(actions.containsKey("command"), "command is never exposed to the editor");
        @SuppressWarnings("unchecked") Map<String, Object> mechanics = (Map<String, Object>) SCHEMA.get("mechanics");
        assertEquals(Set.of("arrow_rain", "tracking"), mechanics.keySet());
    }

    @Test void everyParamHasTheMandatoryFieldsAndSnakeCaseKeys() {
        List<String> problems = new ArrayList<>();
        all().forEach((path, param) -> {
            if (!param.keySet().containsAll(ALWAYS)) problems.add(path + " missing " + ALWAYS);
            String key = (String) param.get("key");
            if (!key.equals("*") && !key.matches("[a-z][a-z0-9_]*")) problems.add(path + " key not snake_case");
            if (Boolean.TRUE.equals(param.get("required")) && param.containsKey("default")) problems.add(path + " required with default");
            if (Boolean.TRUE.equals(param.get("scalable")) && !param.containsKey("default") && !Boolean.TRUE.equals(param.get("required")))
                problems.add(path + " scalable without default");
            String type = (String) param.get("type");
            if (type.equals("enum") != param.containsKey("enum")) problems.add(path + " enum list");
            if ((type.equals("registry") || type.equals("registry_list")) != param.containsKey("registry")) problems.add(path + " registry");
            if ((type.equals("section") || type.equals("map")) != param.containsKey("children")) problems.add(path + " children");
        });
        assertEquals(List.of(), problems);
    }

    @Test void restartFieldsAreExactlyTheRegistrationOnes() {
        List<String> restart = all().entrySet().stream()
                .filter(e -> "restart".equals(e.getValue().get("apply"))).map(Map.Entry::getKey).toList();
        assertEquals(List.of("enchant:name", "enchant:max_level", "enchant:anvil_cost", "enchant:applicable_items",
                "enchant:conflicts", "enchant:registry", "enchant:registry.weight", "enchant:registry.min_cost",
                "enchant:registry.min_cost_per_level", "enchant:registry.max_cost", "enchant:registry.max_cost_per_level",
                "enchant:registry.active_slots"), restart);
    }

    @Test void dangerousFieldsAreMarked() {
        Map<String, Map<String, Object>> all = all();
        for (String path : List.of("enchant:villager_trades.enabled", "action:attribute_modifier:key",
                "mechanic:tracking:never_tracked", "mechanic:tracking:neutral_until_attacking", "enchant:cooldown")) {
            assertNotNull(all.get(path).get("danger"), path);
        }
        assertEquals(Map.of("trigger", List.of("PASSIVE")), all.get("enchant:cooldown").get("dangerWhen"));
        assertEquals("overlay", all.get("mechanic:tracking:radius").get("listSemantics"));
        assertEquals(List.of("onGround"), all.get("action:velocity:on_ground").get("aliases"));
    }

    @Test void chanceIsCommonNotPerAction() {
        assertEquals(List.of("chance"), params(SCHEMA.get("actionCommon")).stream().map(p -> p.get("key")).toList());
        all().keySet().forEach(path -> assertFalse(path.startsWith("action:") && path.endsWith(":chance"), path));
        for (ActionRegistry.ActionType type : ActionRegistry.types()) {
            for (Param<?> param : type.params()) assertNotEquals("chance", param.key(), type.id());
        }
    }
}
