package com.windsor.meowsEnchants.arrowrain;

import com.windsor.meowsEnchants.listeners.MechanicCooldown;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** 箭雨测试读 jar 里内置的 arrow_rain.yml（默认配置），不再用写死的常量。 */
final class BundledRain {
    static final ArrowRainSettings SETTINGS;
    static final int MAX_LEVEL;

    static {
        try {
            Map<String, Object> yaml = ArrowRainConfig.bundledYaml();
            SETTINGS = ArrowRainConfig.settings(yaml);
            MAX_LEVEL = ((Number) yaml.get("max_level")).intValue();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static final RainPattern RAIN = new RainPattern(SETTINGS);

    private BundledRain() {}

    /**
     * Whether a region task scheduled with this delay is a rain drop rather than the shot's completion. Both run
     * at impact height (drops are scheduled on their column and choose their spawn height when they run).
     */
    static boolean isDrop(long delay, int level) {
        return delay <= RAIN.lastSpawnTick(level);
    }

    /** 箭雨的 cooldown 为 0：每次射击都触发。 */
    static ArrowRainListener listener(org.bukkit.plugin.Plugin plugin) {
        return listener(plugin, new MechanicCooldown(key -> 0, System::currentTimeMillis));
    }

    static ArrowRainListener listener(org.bukkit.plugin.Plugin plugin, MechanicCooldown cooldown) {
        return new ArrowRainListener(plugin, () -> SETTINGS, cooldown);
    }
}
