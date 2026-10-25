package com.windsor.meowsEnchants.arrowrain;

import com.windsor.meowsEnchants.config.EnchantConfigLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Installs only missing defaults; existing volley/custom configurations remain an explicit opt-in. */
public final class ArrowRainConfig {
    private ArrowRainConfig() {}

    public static void installMissing(Path directory) throws IOException {
        try (InputStream stream = ArrowRainConfig.class.getResourceAsStream("/enchants/arrow_rain.yml")) {
            if (stream == null) throw new IOException("Missing arrow_rain.yml resource");
            copyMissing(directory.resolve("arrow_rain.yml"), stream);
        }
    }

    static void copyMissing(Path path, InputStream stream) throws IOException {
        try { Files.copy(stream, path); }
        catch (FileAlreadyExistsException ignored) { /* Never overwrite server configuration. */ }
    }

    public static ArrowRainListener createListener(JavaPlugin plugin) {
        Path file = EnchantConfigLoader.enchantDir().resolve("arrow_rain.yml");
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        if (!"landing_arrow_rain_v1".equals(yaml.getString("mechanic"))) {
            plugin.getLogger().warning("Landing arrow rain disabled: existing arrow_rain.yml is a legacy/custom config. "
                    + "Migrate it to the bundled landing_arrow_rain_v1 config and restart to enable landing arrow rain.");
            return null;
        }
        return new ArrowRainListener(plugin);
    }
}
