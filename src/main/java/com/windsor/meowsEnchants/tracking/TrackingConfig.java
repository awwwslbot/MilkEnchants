package com.windsor.meowsEnchants.tracking;

import com.windsor.meowsEnchants.schema.EnchantSchema;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
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
    public static final String FILE = "tracking.yml";
    public static final String SECTION = "tracking";
    public static final String MECHANIC = "tracking_v1";
    private TrackingConfig() {}

    public static void installMissing(Path directory) throws IOException {
        try (InputStream stream = TrackingConfig.class.getResourceAsStream("/enchants/" + FILE)) {
            if (stream == null) throw new IOException("Missing tracking.yml resource");
            try { Files.copy(stream, directory.resolve(FILE)); }
            catch (FileAlreadyExistsException ignored) { /* Never overwrite server configuration. */ }
        }
    }

    static TrackingSettings load(Path file) {
        return TrackingSettings.from(YamlConfiguration.loadConfiguration(file.toFile()).getConfigurationSection(SECTION));
    }

    /**
     * 由 tracking.yml 的原始内容得到跟踪参数；文件不存在或没有 {@code mechanic: tracking_v1} 时返回 null（跟踪停用）。
     */
    public static TrackingSettings settings(Map<String, Object> yaml) {
        if (yaml == null || !MECHANIC.equals(EnchantSchema.MECHANIC.read(yaml))) return null;
        return TrackingSettings.fromYaml(section(yaml.get(SECTION)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /** The listener starts disabled; {@link TrackingListener#apply} feeds it the loaded settings. */
    public static TrackingListener createListener(JavaPlugin plugin) {
        CombatTracker combat = new CombatTracker(TrackingSettings.DEFAULTS.pvpCombatNanos());
        ResidenceProtection residence = ResidenceProtection.bind(
                () -> Bukkit.getPluginManager().getPlugin("Residence"), plugin.getLogger());
        return new TrackingListener(plugin, combat, residence, TargetRules.VANILLA_PVP);
    }

    static Sound chime(TrackingSettings settings, Logger logger) {
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

    static Particle particle(String name, Logger logger) {
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
