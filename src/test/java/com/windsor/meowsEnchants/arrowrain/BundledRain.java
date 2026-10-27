package com.windsor.meowsEnchants.arrowrain;

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

    static ArrowRainListener listener(org.bukkit.plugin.Plugin plugin) {
        return new ArrowRainListener(plugin, () -> SETTINGS);
    }
}
