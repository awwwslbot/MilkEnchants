package com.windsor.meowsEnchants.tracking;

import static org.junit.jupiter.api.Assertions.*;
import com.windsor.meowsEnchants.actions.ListScaling;
import com.windsor.meowsEnchants.config.EnchantConfigLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

class TrackingConfigTest {
    @TempDir Path directory;

    @Test void existingTrackingAndOtherConfigsAreNeverOverwritten() throws Exception {
        Path file = directory.resolve("tracking.yml");
        Files.writeString(file, "server-custom: keep-exactly\n");
        Path other = directory.resolve("arrow_rain.yml");
        Files.writeString(other, "server-other: keep-exactly\n");
        TrackingConfig.installMissing(directory);
        TrackingConfig.installMissing(directory);
        assertEquals("server-custom: keep-exactly\n", Files.readString(file));
        assertEquals("server-other: keep-exactly\n", Files.readString(other));
    }

    @Test void freshConfigIsCommandOnlyRangedThreeLevelsWithDedicatedMechanic() throws Exception {
        TrackingConfig.installMissing(directory);
        try (var input = Files.newInputStream(directory.resolve("tracking.yml"))) {
            Map<String, Object> config = new Yaml().load(input);
            assertEquals(3, config.get("max_level"));
            assertEquals("RANGED", config.get("applicable_items"));
            assertEquals(2, config.get("anvil_cost"));
            assertEquals(TrackingConfig.MECHANIC, config.get("mechanic"));
            assertEquals(Map.of(), config.get("actions"));
            assertEquals(false, ((Map<?, ?>) config.get("enchanting_table")).get("enabled"));
            assertEquals(false, ((Map<?, ?>) config.get("villager_trades")).get("enabled"));
            // Mutually exclusive with arrow rain, both ways (arrow_rain.yml lists tracking).
            assertEquals(List.of("meowsenchants:arrow_rain"), config.get("conflicts"));
            // Mechanic enchantments bypass the generic trigger, so top-level sound/particle would do nothing.
            assertNull(config.get("sound"));
            assertNull(config.get("particle"));
        }
    }

    @Test void originalConfigLoaderRegistersTrackingWithoutActions() throws Exception {
        TrackingConfig.installMissing(directory);
        var loaded = EnchantConfigLoader.loadAll(directory, LoggerFactory.getLogger(getClass()));
        var config = loaded.get(Key.key("meowsenchants", "tracking"));
        assertNotNull(config);
        assertEquals(3, config.getMaxLevel());
        assertEquals(2, config.getAnvilCost());
        assertEquals("RANGED", config.getApplicableItem());
        assertTrue(config.getActions().isEmpty());
        assertEquals(List.of("meowsenchants:arrow_rain"), config.getConflictIds());
        assertTrue(config.getDescription().contains("&c与箭雨互斥"));
        assertFalse(config.getEnchantingTableConfig().isEnabled());
        assertFalse(config.getVillagerTradeConfig().isEnabled());
    }

    @Test void bundledTunablesAreTheDefaults() throws Exception {
        TrackingConfig.installMissing(directory);
        TrackingSettings settings = TrackingConfig.load(directory.resolve("tracking.yml"));
        assertEquals(TrackingSettings.DEFAULTS, settings);
        assertEquals(new ListScaling(List.of(1.5, 2.5, 3.5)), settings.radii());
        assertEquals(5, settings.scanIntervalTicks());
        assertEquals(10, settings.lookaheadTicks());
        assertEquals(20.0, settings.maxTurnDegrees());
        assertEquals(6.0, settings.directAimDistance());
        assertEquals(90.0, settings.giveUpAngleDegrees());
        assertEquals(200, settings.maxTrackingTicks());
        assertEquals(15, settings.pvpCombatSeconds());
        assertEquals(Set.of("milk:pvp"), settings.alwaysPvpWorlds());
        assertEquals("ENCHANT", settings.particle());
        assertEquals("block.note_block.chime", settings.sound());
        assertEquals(1.0f, settings.soundVolume());
    }

    @Test void olderFilesWithoutTunablesFallBackKeyByKey() throws Exception {
        assertEquals(TrackingSettings.DEFAULTS, TrackingSettings.from((org.bukkit.configuration.ConfigurationSection) null));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                tracking:
                  radius: [2.0]
                  scan_interval_ticks: 0
                  max_turn_degrees: 30
                  always_pvp_worlds: ["Milk:Arena"]
                  particle:
                    count_min: 3
                    count_max: 1
                """);
        TrackingSettings settings = TrackingSettings.from(yaml.getConfigurationSection("tracking"));
        assertEquals(new ListScaling(List.of(2.0, 2.5, 3.5)), settings.radii(), "a short list keeps milk.12 per-index defaults");
        assertEquals(1, settings.scanIntervalTicks(), "clamped to at least one tick");
        assertEquals(30.0, settings.maxTurnDegrees());
        assertEquals(10, settings.lookaheadTicks());
        assertEquals(Set.of("milk:arena"), settings.alwaysPvpWorlds());
        assertEquals(3, settings.particleCountMin());
        assertEquals(3, settings.particleCountMax(), "max never below min");
        assertEquals("block.note_block.chime", settings.sound());
    }

    @Test void radiusByLevelClampsToTheConfiguredLevels() {
        TrackingSettings settings = TrackingSettings.DEFAULTS;
        assertEquals(1.5, settings.radius(0));
        assertEquals(1.5, settings.radius(1));
        assertEquals(2.5, settings.radius(2));
        assertEquals(3.5, settings.radius(3));
        assertEquals(3.5, settings.radius(7));
    }

    @Test void alwaysPvpWorldsMatchKeyOrBukkitName() {
        TrackingSettings settings = TrackingSettings.DEFAULTS;
        assertTrue(settings.isAlwaysPvp(TargetRulesTest.world("milk:pvp", "milk_pvp")));
        assertTrue(settings.isAlwaysPvp(TargetRulesTest.world("minecraft:other", "milk:pvp")));
        assertFalse(settings.isAlwaysPvp(TargetRulesTest.world("minecraft:overworld", "world")));
        assertFalse(settings.isAlwaysPvp(null));
    }
}
