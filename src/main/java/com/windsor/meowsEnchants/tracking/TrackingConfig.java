package com.windsor.meowsEnchants.tracking;

import com.windsor.meowsEnchants.config.EnchantConfigLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Installs only a missing tracking.yml and builds the dedicated listener from it. */
public final class TrackingConfig {
    public static final String MECHANIC = "tracking_v1";
    private TrackingConfig() {}

    public static void installMissing(Path directory) throws IOException {
        try (InputStream stream = TrackingConfig.class.getResourceAsStream("/enchants/tracking.yml")) {
            if (stream == null) throw new IOException("Missing tracking.yml resource");
            try { Files.copy(stream, directory.resolve("tracking.yml")); }
            catch (FileAlreadyExistsException ignored) { /* Never overwrite server configuration. */ }
        }
    }

    static TrackingSettings load(Path file) {
        return TrackingSettings.from(YamlConfiguration.loadConfiguration(file.toFile()).getConfigurationSection("tracking"));
    }

    public static TrackingListener createListener(JavaPlugin plugin) {
        Path file = EnchantConfigLoader.enchantDir().resolve("tracking.yml");
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        if (!MECHANIC.equals(yaml.getString("mechanic"))) {
            plugin.getLogger().warning("Tracking disabled: tracking.yml is missing or has no 'mechanic: " + MECHANIC + "'.");
            return null;
        }
        TrackingSettings settings = TrackingSettings.from(yaml.getConfigurationSection("tracking"));
        CombatTracker combat = new CombatTracker(settings.pvpCombatNanos());
        ResidenceProtection residence = ResidenceProtection.bind(
                () -> Bukkit.getPluginManager().getPlugin("Residence"), plugin.getLogger());
        TargetRules rules = new TargetRules(settings, combat, residence, TargetRules.VANILLA_PVP);
        return new TrackingListener(plugin, settings, combat, rules, particle(settings.particle(), plugin.getLogger()),
                chime(settings, plugin.getLogger()));
    }

    private static Sound chime(TrackingSettings settings, Logger logger) {
        Key key;
        try {
            key = Key.key(settings.sound());
        } catch (RuntimeException e) {
            logger.warning("Tracking: invalid sound " + settings.sound() + "; locks are silent.");
            return null;
        }
        if (Registry.SOUNDS.get(key) == null) logger.warning("Tracking: sound " + key.asString() + " is not a vanilla sound.");
        return Sound.sound(key, category(settings.soundCategory()), settings.soundVolume(), settings.soundPitch());
    }

    private static Particle particle(String name, Logger logger) {
        try {
            Particle particle = Particle.valueOf(name.toUpperCase(Locale.ROOT));
            if (particle.getDataType() == Void.class) return particle;
            logger.warning("Tracking: particle " + name + " needs extra data; using ENCHANT.");
        } catch (IllegalArgumentException e) {
            logger.warning("Tracking: unknown particle " + name + "; using ENCHANT.");
        }
        return Particle.ENCHANT;
    }

    private static SoundCategory category(String name) {
        try {
            return SoundCategory.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SoundCategory.PLAYERS;
        }
    }
}
