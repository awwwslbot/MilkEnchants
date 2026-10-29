package com.windsor.meowsEnchants.config;

import static org.junit.jupiter.api.Assertions.*;
import com.windsor.meowsEnchants.schema.Param;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** 契约 §10：加载期校验只报告；类型错是 error，其余是 warning。 */
class ConfigValidatorTest {
    static List<ConfigValidator.Issue> check(String yaml) {
        return check("probe.yml", yaml, ConfigValidator.Registries.NONE);
    }

    @SuppressWarnings("unchecked")
    static List<ConfigValidator.Issue> check(String file, String yaml, ConfigValidator.Registries registries) {
        Map<String, Object> data = new Yaml().load(yaml);
        return ConfigValidator.validateEnchant(file.replace(".yml", ""), file, data, registries);
    }

    static ConfigValidator.Issue only(List<ConfigValidator.Issue> issues) {
        assertEquals(1, issues.size(), issues.toString());
        return issues.getFirst();
    }

    static final String BASE = """
            name: x
            max_level: 3
            anvil_cost: 1
            applicable_items: BOW
            trigger: RIGHT_CLICK
            target: SELF
            """;

    @Test void allBundledEnchantsAndConfigYmlAreClean() throws Exception {
        List<ConfigValidator.Issue> issues = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/enchants"))) {
            for (Path file : files.sorted().toList()) {
                try (InputStream input = Files.newInputStream(file)) {
                    Map<String, Object> data = new Yaml().load(input);
                    issues.addAll(ConfigValidator.validateEnchant(file.getFileName().toString().replace(".yml", ""),
                            file.getFileName().toString(), data, ConfigValidator.Registries.NONE));
                }
            }
        }
        // auto_mending 的 ENCHANTABLE 不是合法值（落到 #enchantable/durability），这是已知的旧问题
        assertEquals(List.of("auto_mending:applicable_items"),
                issues.stream().map(i -> i.enchant() + ":" + i.path()).toList(), issues.toString());
        assertFalse(issues.getFirst().error());
        try (InputStream input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            Map<String, Object> config = new Yaml().load(input);
            assertEquals(List.of(), ConfigValidator.validateGlobal(config, ConfigValidator.Registries.NONE));
        }
    }

    @Test void unknownKeysAreWarnings() {
        ConfigValidator.Issue issue = only(check(BASE + "colour: red\n"));
        assertFalse(issue.error());
        assertEquals("colour", issue.path());
        issue = only(check(BASE + "sound: {type: a, volum: 1}\n"));
        assertEquals("sound.volum", issue.path());
    }

    @Test void typeMismatchesAreErrors() {
        ConfigValidator.Issue issue = only(check(BASE.replace("anvil_cost: 1", "anvil_cost: \"1\"")));
        assertTrue(issue.error());
        assertEquals("anvil_cost", issue.path());
        issue = only(check(BASE + "actions: {p: {type: potion_effect, effect: speed, duration: abc, amplifier: 0}}\n"));
        assertTrue(issue.error());
        assertEquals("actions.p.duration", issue.path());
        issue = only(check(BASE + "actions: {p: {type: potion_effect, effect: speed, duration: 1, amplifier: 0, particles: \"false\"}}\n"));
        assertTrue(issue.error());
        assertEquals("actions.p.particles", issue.path());
        issue = only(check(BASE + "actions: [a, b]\n"));
        assertTrue(issue.error(), "a list of actions is silently treated as none");
    }

    @Test void emptyPerLevelListsAreErrors() {
        ConfigValidator.Issue issue = only(check(BASE + "actions: {p: {type: feast, amount: []}}\n"));
        assertTrue(issue.error());
        assertEquals("actions.p.amount", issue.path());
    }

    @Test void shortPerLevelListsWarnButTrackingRadiusOverlaysDefaults() {
        ConfigValidator.Issue issue = only(check(BASE + "actions: {p: {type: feast, amount: [0.1, 0.2]}}\n"));
        assertFalse(issue.error());
        assertTrue(issue.message().contains("只有 2 项"), issue.message());
        assertEquals(List.of(), check("tracking.yml", BASE + "tracking: {radius: [2.0]}\n", ConfigValidator.Registries.NONE));
    }

    @Test void aMechanicFlagThatEnablesNothingWarns() {
        assertEquals(List.of(), check("arrow_rain.yml", BASE + "mechanic: landing_arrow_rain_v1\n", ConfigValidator.Registries.NONE));
        assertEquals(List.of(), check("tracking.yml", BASE + "mechanic: tracking_v1\n", ConfigValidator.Registries.NONE));
        for (String[] stray : new String[][] {
                {"probe.yml", "tracking_v1"}, {"arrow_rain.yml", "tracking_v1"}, {"tracking.yml", "landing_arrow_rain_v2"}}) {
            ConfigValidator.Issue issue = only(check(stray[0], BASE + "mechanic: " + stray[1] + "\n", ConfigValidator.Registries.NONE));
            assertFalse(issue.error(), issue.toString());
            assertEquals("mechanic", issue.path());
            assertTrue(issue.message().contains("照常走通用触发器"), issue.message());
        }
    }

    @Test void mechanicSectionsBelongToTheirOwnFile() {
        ConfigValidator.Issue issue = only(check(BASE + "tracking: {radius: [2.0]}\n"));
        assertEquals("tracking", issue.path());
        assertEquals(List.of(), check("arrow_rain.yml", BASE + "arrow_rain: {arrows: 4}\n", ConfigValidator.Registries.NONE));
    }

    @Test void unknownEnumValuesActionsAndMissingParameters() {
        assertFalse(only(check(BASE.replace("RIGHT_CLICK", "LEFT_CLICK"))).error());
        assertFalse(only(check(BASE.replace("target: SELF", "target: everyone"))).error());
        assertFalse(only(check(BASE + "actions: {g: {type: gravity}}\n")).error(), "documented but non-existent action");
        ConfigValidator.Issue missing = only(check(BASE + "actions: {t: {type: teleport}}\n"));
        assertTrue(missing.error());
        assertEquals("actions.t.range", missing.path());
        assertEquals(List.of(), check(BASE.replace("RIGHT_CLICK", "right_click")), "triggers are case-insensitive");
    }

    @Test void registryEntriesAreCheckedThroughTheServer() {
        ConfigValidator.Registries registries = (registry, value) ->
                registry == Param.Registry.SOUND ? value.equals("entity.player.attack.crit") : null;
        assertEquals(List.of(), check("p.yml", BASE + "sound: {type: entity.player.attack.crit}\n", registries));
        ConfigValidator.Issue issue = only(check("p.yml", BASE + "sound: {type: entity.spider.attack}\n", registries));
        assertFalse(issue.error());
        assertEquals("sound.type", issue.path());
    }

    @Test void rangesAndListApplicableItemsWarn() {
        assertFalse(only(check(BASE + "actions: {p: {type: feast, amount: 1.5}}\n")).error());
        assertFalse(only(check(BASE + "actions: {p: {type: feast, amount: 0.5, chance: {base: 0.5, per_level: 0.5}}}\n")).error(),
                "chance 0.5 + 0.5 × 2 = 1.5 at max level is out of range");
        assertFalse(only(check(BASE.replace("applicable_items: BOW", "applicable_items: [BOW, CROSSBOW]"))).error());
    }

    @Test void unresolvedPlaceholdersAreWarningsPerLine() {
        ConfigValidator.Issue issue = only(check(BASE + "description: [\"ok {max_level}\", \"bad {cooldwn}\"]\n"));
        assertFalse(issue.error());
        assertEquals("description[1]", issue.path());
        assertTrue(issue.message().contains("{cooldwn}"));
    }

    @Test void bothSpellingsOfARenamedKeyWarn() {
        assertEquals(List.of(), check(BASE + "actions: {v: {type: velocity, direction: UP, power: 1, onGround: false}}\n"));
        ConfigValidator.Issue issue = only(check(BASE + "actions: {v: {type: velocity, direction: UP, power: 1, onGround: false, on_ground: true}}\n"));
        assertFalse(issue.error());
        assertEquals("actions.v.on_ground", issue.path());
    }

    @Test void globalConfigIsChecked() {
        Map<String, Object> data = new Yaml().load("skyllia_compatibility: maybe\ntriggers: {passive_min_period_tick: 5}\n");
        List<ConfigValidator.Issue> issues = ConfigValidator.validateGlobal(data, ConfigValidator.Registries.NONE);
        assertEquals(2, issues.size(), issues.toString());
        assertTrue(issues.get(0).error());
        assertEquals("config.yml", issues.get(0).enchant());
        assertEquals("triggers.passive_min_period_tick", issues.get(1).path());
    }
}
