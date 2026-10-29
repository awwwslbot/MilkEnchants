package com.windsor.meowsEnchants.arrowrain;

import static org.junit.jupiter.api.Assertions.*;
import com.windsor.meowsEnchants.config.EnchantConfigLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

class ArrowRainConfigTest {
    @TempDir Path directory;

    @Test void existingArrowRainAndOtherConfigsAreNeverOverwritten() throws Exception {
        Path file = directory.resolve("arrow_rain.yml");
        Files.writeString(file, "server-custom: keep-exactly\n");
        Path other = directory.resolve("rapid_fire.yml");
        Files.writeString(other, "server-other: keep-exactly\n");
        ArrowRainConfig.installMissing(directory);
        ArrowRainConfig.installMissing(directory);
        assertEquals("server-custom: keep-exactly\n", Files.readString(file));
        assertEquals("server-other: keep-exactly\n", Files.readString(other));
    }

    @Test void freshConfigUsesBowFiveLevelsAndExistingAcquisitionPolicy() throws Exception {
        ArrowRainConfig.installMissing(directory);
        try (var input = Files.newInputStream(directory.resolve("arrow_rain.yml"))) {
            Map<String, Object> config = new Yaml().load(input);
            assertEquals(5, config.get("max_level"));
            assertEquals("BOW", config.get("applicable_items"));
            assertEquals("landing_arrow_rain_v1", config.get("mechanic"));
            assertEquals(Map.of(), config.get("actions"));
            Map<?, ?> table = (Map<?, ?>) config.get("enchanting_table");
            assertEquals(true, table.get("enabled"));
            assertEquals(0.01, table.get("chance"));
            assertEquals(30, table.get("min_cost"));
            assertEquals(30, table.get("max_cost"));
            assertEquals(false, ((Map<?, ?>) config.get("villager_trades")).get("enabled"));
        }
    }

    @Test void originalConfigLoaderAcceptsLandingConfigWithoutLegacyVolleyActions() throws Exception {
        ArrowRainConfig.installMissing(directory);
        var loaded = EnchantConfigLoader.loadAll(directory, LoggerFactory.getLogger(getClass()));
        assertEquals(1, loaded.size());
        var config = loaded.get(Key.key("meowsenchants", "arrow_rain"));
        assertNotNull(config);
        assertEquals(5, config.getMaxLevel());
        assertEquals("BOW", config.getApplicableItem());
        assertTrue(config.getActions().isEmpty());
        assertEquals(0, config.getCooldown());
        // Mutually exclusive with tracking, both ways (tracking.yml lists arrow_rain); the lore says so too.
        assertEquals(List.of("meowsenchants:tracking"), config.getConflictIds());
        assertTrue(config.getDescription().contains("&c与跟踪互斥"));
    }
}
