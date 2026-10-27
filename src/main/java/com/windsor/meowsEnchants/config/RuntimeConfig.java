package com.windsor.meowsEnchants.config;

import com.windsor.meowsEnchants.arrowrain.ArrowRainSettings;
import com.windsor.meowsEnchants.tracking.TrackingSettings;
import net.kyori.adventure.key.Key;

import java.util.Map;

/**
 * 运行时配置的一个不可变快照。插件只持有一个 volatile 引用，reload 时整体替换，
 * Folia 各区域线程读到的要么全是旧值、要么全是新值。
 *
 * @param arrowRain 箭雨参数；arrow_rain.yml 没有 landing_arrow_rain_v1 标记时为 null（停用）
 * @param tracking  跟踪参数；tracking.yml 没有 tracking_v1 标记时为 null（停用）
 */
public record RuntimeConfig(Map<Key, EnchantConfig> enchants, GlobalSettings global,
                            ArrowRainSettings arrowRain, TrackingSettings tracking) {
    public static final RuntimeConfig EMPTY = new RuntimeConfig(Map.of(), GlobalSettings.DEFAULTS, null, null);

    public RuntimeConfig {
        enchants = Map.copyOf(enchants);
    }

    public RuntimeConfig withEnchants(Map<Key, EnchantConfig> enchants) {
        return new RuntimeConfig(enchants, global, arrowRain, tracking);
    }
}
