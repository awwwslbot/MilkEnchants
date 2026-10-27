package com.windsor.meowsEnchants.config;

import static org.junit.jupiter.api.Assertions.*;
import com.windsor.meowsEnchants.arrowrain.ArrowRainConfig;
import com.windsor.meowsEnchants.arrowrain.ArrowRainSettings;
import com.windsor.meowsEnchants.schema.Json;
import com.windsor.meowsEnchants.tracking.TrackingConfig;
import com.windsor.meowsEnchants.tracking.TrackingSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/** /enchants reload 的计算部分：原子替换的新快照、需重启检测、保留旧配置、JSON 结果（契约 §9）。 */
class ConfigReloadTest {
    @TempDir Path root;
    Path enchants;
    Path configYml;

    static final String NIGHT_VISION = """
            name: "<gradient:#0B0B2B:#4FC3F7>夜视"
            description:
              - "&b冷却：{cooldown|s}秒，最高 {max_level} 级"
            max_level: 1
            anvil_cost: 2
            applicable_items: HELMETS
            trigger: PASSIVE
            target: SELF
            cooldown: 1000
            actions:
              vision:
                type: potion_effect
                effect: night_vision
                duration: 300
                amplifier: 0
            """;

    static final String BEHEADING = """
            name: "斩首"
            max_level: 3
            anvil_cost: 2
            applicable_items: SHARP_WEAPON
            trigger: KILL_ENTITY
            target: ALL_ENTITIES
            actions:
              head:
                type: drop_head
                amount: {base: 0.1, per_level: 0.1}
            """;

    @BeforeEach void files() throws Exception {
        enchants = Files.createDirectories(root.resolve("enchants"));
        configYml = root.resolve("config.yml");
        write("night_vision", NIGHT_VISION);
        write("beheading", BEHEADING);
    }

    void write(String id, String yaml) throws Exception { Files.writeString(enchants.resolve(id + ".yml"), yaml); }

    ConfigReload.Outcome start() {
        return ConfigReload.load(enchants, configYml, RuntimeConfig.EMPTY, Map.of(), true, ConfigValidator.Registries.NONE, null);
    }

    ConfigReload.Outcome reload(ConfigReload.Outcome previous) {
        return ConfigReload.load(enchants, configYml, previous.runtime(), previous.baseline(), false,
                ConfigValidator.Registries.NONE, null);
    }

    static EnchantConfig config(ConfigReload.Outcome outcome, String id) {
        return outcome.runtime().enchants().get(Key.key("meowsenchants", id));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> json(ReloadReport report) {
        String line = report.jsonLine("0.2-milk.13");
        assertTrue(line.startsWith("MEOWSENCHANTS_RELOAD {"));
        assertFalse(line.contains("\n"), "single line");
        return (Map<String, Object>) new Yaml().load(line.substring("MEOWSENCHANTS_RELOAD ".length()));
    }

    @Test void startupLoadsEveryParsedEnchantAndRecordsTheBaseline() {
        ConfigReload.Outcome start = start();
        assertEquals(2, start.runtime().enchants().size());
        assertEquals(Map.of("night_vision", RegisteredEnchant.of(config(start, "night_vision")),
                "beheading", RegisteredEnchant.of(config(start, "beheading"))), start.baseline());
        assertEquals(GlobalSettings.DEFAULTS, start.runtime().global(), "missing config.yml = defaults");
        assertTrue(start.report().ok());
    }

    @Test void startupPrefersWhatBootstrapRegistered() throws Exception {
        RegisteredEnchant registered = new RegisteredEnchant("斩首", 3, 2, "SHARP_WEAPON", List.of(),
                EnchantConfig.RegistrySettings.from(Map.of()));
        ConfigReload.Outcome start = ConfigReload.load(enchants, configYml, RuntimeConfig.EMPTY,
                Map.of("beheading", registered), true, ConfigValidator.Registries.NONE, null);
        assertSame(registered, start.baseline().get("beheading"));
    }

    @Test void unchangedFilesApplyNothing() {
        ConfigReload.Outcome next = reload(start());
        Map<String, Object> json = json(next.report());
        assertEquals(true, json.get("ok"));
        assertEquals("0.2-milk.13", json.get("pluginVersion"));
        assertEquals(List.of(), json.get("applied"));
        assertEquals(List.of(), json.get("restartRequired"));
        assertEquals(List.of("ok", "pluginVersion", "applied", "restartRequired", "warnings", "errors"), List.copyOf(json.keySet()));
    }

    @Test void cooldownAndDescriptionApplyWithoutRestart() throws Exception {
        ConfigReload.Outcome start = start();
        write("night_vision", NIGHT_VISION.replace("cooldown: 1000", "cooldown: 3000"));
        ConfigReload.Outcome next = reload(start);
        assertEquals(List.of("night_vision"), next.report().applied());
        assertEquals(List.of(), next.report().restartRequired());
        assertEquals(3000, config(next, "night_vision").getCooldown());
        assertEquals(List.of("&b冷却：3秒，最高 1 级"), config(next, "night_vision").getDisplayDescription());
        assertNotSame(start.runtime(), next.runtime(), "a new immutable snapshot replaces the old one");
        assertEquals(1000, config(start, "night_vision").getCooldown(), "the old snapshot is never mutated");
    }

    @Test void registrationFieldsAreReportedAndKeptAtTheirRegisteredValues() throws Exception {
        ConfigReload.Outcome start = start();
        write("night_vision", NIGHT_VISION.replace("max_level: 1", "max_level: 2")
                .replace("anvil_cost: 2", "anvil_cost: 5").replace("HELMETS", "ARMOR")
                .replace("cooldown: 1000", "cooldown: 3000\nconflicts: [minecraft:protection]\nregistry: {weight: 5}"));
        ConfigReload.Outcome next = reload(start);
        assertEquals(List.of(new ReloadReport.RestartItem("night_vision",
                List.of("max_level", "anvil_cost", "applicable_items", "conflicts", "registry.weight"), "changed")),
                next.report().restartRequired());
        EnchantConfig config = config(next, "night_vision");
        assertEquals(1, config.getMaxLevel(), "registry is frozen: keep the registered max level");
        assertEquals(2, config.getAnvilCost());
        assertEquals("HELMETS", config.getApplicableItem());
        assertEquals(List.of(), config.getConflictIds());
        assertEquals(3000, config.getCooldown(), "the rest of the file still applies");
        assertEquals(List.of("&b冷却：3秒，最高 1 级"), config.getDisplayDescription(), "placeholders use the registered max level");
        assertTrue(next.report().ok());
        ConfigReload.Outcome again = reload(next);
        assertEquals(List.of(), again.report().applied(), "nothing new to apply while the restart is pending");
        assertEquals(1, again.report().restartRequired().size(), "still reported until the server restarts");
    }

    @Test void aRenameIsRestartRequiredButTheGuiNameChangesNow() throws Exception {
        ConfigReload.Outcome start = start();
        write("beheading", BEHEADING.replace("\"斩首\"", "\"斩首（新）\""));
        ConfigReload.Outcome next = reload(start);
        assertEquals(List.of(new ReloadReport.RestartItem("beheading", List.of("name"), "changed")), next.report().restartRequired());
        assertEquals("斩首（新）", config(next, "beheading").getDisplayName());
        assertEquals(List.of(), next.report().applied(), "name is a restart field (contract 1.3 §9)");
        Map<String, Object> json = json(next.report());
        assertEquals(List.of(), json.get("applied"));
        assertEquals(List.of(Map.of("enchant", "beheading", "fields", List.of("name"), "reason", "changed")),
                json.get("restartRequired"));
    }

    // ---- applied 只列可热生效的部分真的变了的附魔（契约 1.3 §9）

    @Test void onlyMaxLevelIsRestartRequiredAndNotApplied() throws Exception {
        ConfigReload.Outcome start = start();
        write("night_vision", NIGHT_VISION.replace("max_level: 1", "max_level: 2"));
        ConfigReload.Outcome next = reload(start);
        assertEquals(List.of(), next.report().applied());
        assertEquals(List.of(), json(next.report()).get("applied"));
        assertEquals(List.of(new ReloadReport.RestartItem("night_vision", List.of("max_level"), "changed")),
                next.report().restartRequired());
        assertEquals(1, config(next, "night_vision").getMaxLevel());
        assertEquals(config(start, "night_vision").getDisplayDescription(), config(next, "night_vision").getDisplayDescription(),
                "{max_level} still renders the registered value");
    }

    @Test void onlyCooldownIsAppliedAndNotRestartRequired() throws Exception {
        ConfigReload.Outcome start = start();
        write("night_vision", NIGHT_VISION.replace("cooldown: 1000", "cooldown: 3000"));
        ConfigReload.Outcome next = reload(start);
        assertEquals(List.of("night_vision"), next.report().applied());
        assertEquals(List.of(), next.report().restartRequired());
        assertEquals(3000, config(next, "night_vision").getCooldown());
    }

    @Test void maxLevelAndCooldownTogetherAreInBothLists() throws Exception {
        ConfigReload.Outcome start = start();
        write("night_vision", NIGHT_VISION.replace("max_level: 1", "max_level: 2").replace("cooldown: 1000", "cooldown: 3000"));
        ConfigReload.Outcome next = reload(start);
        assertEquals(List.of("night_vision"), next.report().applied());
        assertEquals(List.of(new ReloadReport.RestartItem("night_vision", List.of("max_level"), "changed")),
                next.report().restartRequired());
        assertEquals(1, config(next, "night_vision").getMaxLevel());
        assertEquals(3000, config(next, "night_vision").getCooldown());
        // 之后只把 max_level 改回去：热字段没再变
        write("night_vision", NIGHT_VISION.replace("cooldown: 1000", "cooldown: 3000"));
        ConfigReload.Outcome back = reload(next);
        assertEquals(List.of(), back.report().applied());
        assertEquals(List.of(), back.report().restartRequired());
    }

    @Test void otherRestartOnlyChangesAreNotApplied() throws Exception {
        ConfigReload.Outcome start = start();
        write("beheading", BEHEADING.replace("anvil_cost: 2", "anvil_cost: 4")
                + "conflicts: [minecraft:looting]\nregistry: {weight: 3, active_slots: MAINHAND}\n");
        ConfigReload.Outcome next = reload(start);
        assertEquals(List.of(), next.report().applied());
        assertEquals(List.of("anvil_cost", "conflicts", "registry.weight", "registry.active_slots"),
                next.report().restartRequired().getFirst().fields());
    }

    @Test void appliedComparesEffectiveValuesNotFileText() throws Exception {
        ConfigReload.Outcome start = start();
        // 显式写出等于默认值的键、未知键：生效的值不变
        write("beheading", BEHEADING + "cooldown: 0\nunknown_key: 1\n");
        assertEquals(List.of(), reload(start).report().applied());
        // 动作按顺序执行：只换顺序也算变了
        write("beheading", BEHEADING.replace("actions:\n", "actions:\n  first:\n    type: feast\n")
                .replace("  head:", "  second_head:"));
        ConfigReload.Outcome withTwo = reload(start);
        assertEquals(List.of("beheading"), withTwo.report().applied());
        write("beheading", BEHEADING.replace("actions:\n", "actions:\n  second_head:\n    type: drop_head\n    amount: {base: 0.1, per_level: 0.1}\n")
                .replace("  head:\n    type: drop_head\n    amount: {base: 0.1, per_level: 0.1}\n", "  first:\n    type: feast\n"));
        ConfigReload.Outcome swapped = reload(withTwo);
        assertEquals(List.of("second_head", "first"), config(swapped, "beheading").getActionKeys());
        assertEquals(List.of("beheading"), swapped.report().applied());
    }

    @Test void mechanicSectionChangesAreAppliedButTheirRestartFieldsAreNot() throws Exception {
        Files.write(enchants.resolve(ArrowRainConfig.FILE), Files.readAllBytes(Path.of("src/main/resources/enchants/arrow_rain.yml")));
        Files.write(enchants.resolve(TrackingConfig.FILE), Files.readAllBytes(Path.of("src/main/resources/enchants/tracking.yml")));
        ConfigReload.Outcome start = start();
        String rain = Files.readString(enchants.resolve(ArrowRainConfig.FILE));
        String tracking = Files.readString(enchants.resolve(TrackingConfig.FILE));
        Files.writeString(enchants.resolve(ArrowRainConfig.FILE), rain.replace("arrows: [8, 16, 32, 64, 128]", "arrows: 4"));
        Files.writeString(enchants.resolve(TrackingConfig.FILE), tracking.replace("max_level: 3", "max_level: 4"));
        ConfigReload.Outcome next = reload(start);
        assertEquals(List.of("arrow_rain"), next.report().applied());
        assertEquals(List.of(new ReloadReport.RestartItem("tracking", List.of("max_level"), "changed")),
                next.report().restartRequired());
    }

    @Test void newFilesAreNotRegisteredAndDeletedFilesKeepTheirOldConfig() throws Exception {
        ConfigReload.Outcome start = start();
        write("speed", BEHEADING.replace("斩首", "速度"));
        Files.delete(enchants.resolve("beheading.yml"));
        ConfigReload.Outcome next = reload(start);
        assertEquals(List.of(
                new ReloadReport.RestartItem("beheading", List.of("file"), "removed"),
                new ReloadReport.RestartItem("speed", List.of("file"), "added")), sortedRestart(next));
        assertNull(config(next, "speed"), "a new file is not registered");
        assertSame(config(start, "beheading"), config(next, "beheading"), "villager trades must still find its config");
        Map<String, Object> json = json(next.report());
        assertEquals(Map.of("enchant", "speed", "fields", List.of("file"), "reason", "added"),
                ((List<?>) json.get("restartRequired")).get(1));
    }

    @Test void aBrokenFileKeepsItsOldConfigAndOthersStillApply() throws Exception {
        ConfigReload.Outcome start = start();
        write("beheading", "name: [unclosed\n");
        write("night_vision", NIGHT_VISION.replace("cooldown: 1000", "cooldown: 2000"));
        ConfigReload.Outcome next = reload(start);
        assertFalse(next.report().ok());
        assertEquals("beheading", next.report().errors().getFirst().enchant());
        assertSame(config(start, "beheading"), config(next, "beheading"));
        assertEquals(2000, config(next, "night_vision").getCooldown());
        assertEquals(false, json(next.report()).get("ok"));
    }

    @Test void missingRequiredFieldsAreAnErrorAndKeepTheOldConfig() throws Exception {
        ConfigReload.Outcome start = start();
        write("beheading", BEHEADING.replace("max_level: 3\n", ""));
        ConfigReload.Outcome next = reload(start);
        assertFalse(next.report().ok());
        assertSame(config(start, "beheading"), config(next, "beheading"));
    }

    @Test void validationWarningsAndPlaceholderIssuesAreReportedWithPaths() throws Exception {
        ConfigReload.Outcome start = start();
        write("night_vision", NIGHT_VISION.replace("cooldown|s", "cooldwn|s") + "unknown_key: 1\n");
        ConfigReload.Outcome next = reload(start);
        assertTrue(next.report().ok(), "warnings never block");
        List<String> paths = next.report().warnings().stream().map(ConfigValidator.Issue::path).toList();
        assertTrue(paths.contains("description[0]"), paths.toString());
        assertTrue(paths.contains("unknown_key"), paths.toString());
    }

    @Test void configYmlChangesApply() throws Exception {
        ConfigReload.Outcome start = start();
        Files.writeString(configYml, "triggers:\n  passive_min_period_ticks: 40\n");
        ConfigReload.Outcome next = reload(start);
        assertEquals(40, next.runtime().global().passiveMinPeriodTicks());
        assertEquals(List.of("config.yml"), next.report().applied());
        Files.writeString(configYml, "triggers: [oops\n");
        ConfigReload.Outcome broken = reload(next);
        assertFalse(broken.report().ok());
        assertEquals(40, broken.runtime().global().passiveMinPeriodTicks(), "a broken config.yml keeps the old settings");
    }

    @Test void mechanicsSwitchOnAndOffWithTheirFlag() throws Exception {
        ConfigReload.Outcome start = start();
        assertNull(start.runtime().arrowRain());
        assertNull(start.runtime().tracking());
        Files.write(enchants.resolve(ArrowRainConfig.FILE), Files.readAllBytes(Path.of("src/main/resources/enchants/arrow_rain.yml")));
        Files.write(enchants.resolve(TrackingConfig.FILE), Files.readAllBytes(Path.of("src/main/resources/enchants/tracking.yml")));
        ConfigReload.Outcome on = ConfigReload.load(enchants, configYml, RuntimeConfig.EMPTY, Map.of(), true,
                ConfigValidator.Registries.NONE, null);
        assertEquals(ArrowRainSettings.DEFAULTS, on.runtime().arrowRain());
        assertEquals(TrackingSettings.DEFAULTS, on.runtime().tracking());
        String rain = Files.readString(enchants.resolve(ArrowRainConfig.FILE));
        Files.writeString(enchants.resolve(ArrowRainConfig.FILE), rain.replace("arrows: [8, 16, 32, 64, 128]", "arrows: 4"));
        Files.writeString(enchants.resolve(TrackingConfig.FILE),
                Files.readString(enchants.resolve(TrackingConfig.FILE)).replace("mechanic: tracking_v1", "mechanic: off"));
        ConfigReload.Outcome next = reload(on);
        assertEquals(4, (int) next.runtime().arrowRain().arrows().getValue(5));
        assertNull(next.runtime().tracking(), "tracking switches off without a restart");
        assertTrue(next.report().notes().stream().anyMatch(n -> n.contains("跟踪未启用")));
    }

    @Test void chineseLinesEndWithTheJsonLine() throws Exception {
        ConfigReload.Outcome start = start();
        write("night_vision", NIGHT_VISION.replace("max_level: 1", "max_level: 2"));
        List<String> lines = reload(start).report().lines("0.2-milk.13", false);
        assertTrue(lines.getFirst().startsWith("[MeowsEnchants] 重载完成"));
        assertTrue(lines.stream().anyMatch(l -> l.equals("需重启：night_vision（max_level 改了，重启后生效）")), lines.toString());
        assertTrue(lines.getLast().startsWith(ReloadReport.JSON_PREFIX));
    }

    @Test void jsonEscapesQuotesAndNewlines() {
        ReloadReport report = new ReloadReport();
        report.error("x", "description[0]", "引号 \" 和\n换行");
        String line = report.jsonLine("v");
        assertFalse(line.contains("\n"));
        assertEquals(Json.compact(report.toJson("v")), line.substring(ReloadReport.JSON_PREFIX.length()));
    }

    private static List<ReloadReport.RestartItem> sortedRestart(ConfigReload.Outcome outcome) {
        return outcome.report().restartRequired().stream()
                .sorted(java.util.Comparator.comparing(ReloadReport.RestartItem::enchant)).toList();
    }
}
