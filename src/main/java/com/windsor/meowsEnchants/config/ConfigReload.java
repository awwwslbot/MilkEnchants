package com.windsor.meowsEnchants.config;

import com.windsor.meowsEnchants.arrowrain.ArrowRainConfig;
import com.windsor.meowsEnchants.arrowrain.ArrowRainSettings;
import com.windsor.meowsEnchants.schema.EnchantSchema;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.tracking.TrackingConfig;
import com.windsor.meowsEnchants.tracking.TrackingSettings;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * 读 config.yml 和全部附魔 yml，构建下一份 {@link RuntimeConfig}（不改任何运行时状态）。
 * <p>
 * 和启动时注册进注册表的值（baseline）比较：restart 字段变了 → 不生效，记入 restartRequired；新文件 → 不加入；
 * 文件消失或解析失败 → 保留旧的运行时配置（避免村民交易因 config == null 被杀）。
 * applied 只列可热生效的部分真的变了的附魔（{@link #hotChanged}）。
 */
public final class ConfigReload {
    private ConfigReload() {}

    /**
     * @param baseline 下一次 reload 用的比较基准（启动时由本次结果确定，之后不变）
     */
    public record Outcome(RuntimeConfig runtime, ReloadReport report, Map<String, RegisteredEnchant> baseline) {}

    /**
     * @param previous   当前运行时配置（启动时为 {@link RuntimeConfig#EMPTY}）
     * @param registered 启动时注册进注册表的值；启动加载时是 bootstrap 的记录，reload 时是启动加载返回的 baseline
     * @param initial    true = 启动加载：所有解析成功的附魔都加入，不做 restart 比较
     */
    public static Outcome load(@NotNull Path enchantDir, @NotNull Path configFile, @NotNull RuntimeConfig previous,
                               @NotNull Map<String, RegisteredEnchant> registered, boolean initial,
                               @NotNull ConfigValidator.Registries registries, Logger logger) {
        ReloadReport report = new ReloadReport();

        // ---- config.yml
        GlobalSettings global = previous.global();
        Map<String, Object> globalYaml = null;
        try {
            Map<String, Object> loaded = Files.exists(configFile) ? readYaml(configFile) : null;
            globalYaml = loaded == null ? Map.of() : loaded;
        } catch (IOException | RuntimeException e) {
            report.error("config.yml", "", "config.yml 解析失败，保留原配置：" + e.getMessage());
        }
        if (globalYaml != null) {
            global = GlobalSettings.from(globalYaml);
            ConfigValidator.validateGlobal(globalYaml, registries).forEach(report::issue);
            if (!initial && !global.equals(previous.global())) report.applied("config.yml");
        }

        // ---- 附魔文件
        Map<String, Path> files = new TreeMap<>();
        try (Stream<Path> list = Files.isDirectory(enchantDir) ? Files.list(enchantDir) : Stream.empty()) {
            list.filter(path -> path.getFileName().toString().endsWith(".yml") && Files.isRegularFile(path))
                    .forEach(path -> files.put(path.getFileName().toString().replace(".yml", ""), path));
        } catch (IOException e) {
            report.error("", "", "无法列出附魔目录 " + enchantDir + "：" + e.getMessage());
        }

        Map<String, Map<String, Object>> yamls = new LinkedHashMap<>();
        Map<String, EnchantConfig> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, Path> file : files.entrySet()) {
            String id = file.getKey();
            Map<String, Object> data;
            try {
                data = readYaml(file.getValue());
            } catch (IOException | RuntimeException e) {
                report.error(id, "", file.getValue().getFileName() + " 解析失败，保留旧配置：" + firstLine(e.getMessage()));
                continue;
            }
            if (data == null) {
                report.error(id, "", file.getValue().getFileName() + " 是空文件，保留旧配置");
                continue;
            }
            yamls.put(id, data);
            ConfigValidator.validateEnchant(id, file.getValue().getFileName().toString(), data, registries)
                    .forEach(report::issue);
            EnchantConfig config = EnchantConfigLoader.parse(id, data);
            if (config == null) {
                report.error(id, "", "缺少必填字段（name、max_level、anvil_cost、applicable_items）或 trigger/target 不是文字，保留旧配置");
                continue;
            }
            if (logger != null) config.setLogger(logger);
            parsed.put(id, config);
        }

        Map<Key, EnchantConfig> next = new HashMap<>();
        Map<String, RegisteredEnchant> baseline = new HashMap<>(initial ? Map.of() : registered);
        for (Map.Entry<String, EnchantConfig> entry : parsed.entrySet()) {
            String id = entry.getKey();
            EnchantConfig config = entry.getValue();
            Key key = Key.key("meowsenchants", id);
            if (initial) {
                next.put(key, config);
                baseline.put(id, registered.getOrDefault(id, RegisteredEnchant.of(config)));
                continue;
            }
            RegisteredEnchant base = registered.get(id);
            if (base == null) {
                report.restart(id, List.of("file"), "added");
                continue;
            }
            List<String> changed = base.changedFields(config);
            if (!changed.isEmpty()) report.restart(id, changed, "changed");
            EnchantConfig effective = changed.isEmpty() || changed.equals(List.of("name"))
                    ? config : config.withRegistered(base);
            next.put(key, effective);
            EnchantConfig old = previous.enchants().get(key);
            if (old == null || hotChanged(id, old.getRawData(), config.getRawData())) report.applied(id);
        }

        // 解析失败、文件消失：保留旧配置
        for (Map.Entry<Key, EnchantConfig> entry : previous.enchants().entrySet()) {
            String id = entry.getKey().value();
            if (next.containsKey(entry.getKey())) continue;
            if (files.containsKey(id) && parsed.containsKey(id)) continue; // 新增或其它原因未加入
            next.put(entry.getKey(), entry.getValue());
        }
        if (!initial) {
            for (String id : registered.keySet()) {
                if (!files.containsKey(id)) report.restart(id, List.of("file"), "removed");
            }
        }

        // ---- 专用机制：文件读不出来时沿用旧参数
        ArrowRainSettings arrowRain = yamls.containsKey(ArrowRainConfig.SECTION)
                ? ArrowRainConfig.settings(yamls.get(ArrowRainConfig.SECTION))
                : (initial ? null : previous.arrowRain());
        TrackingSettings tracking = yamls.containsKey(TrackingConfig.SECTION)
                ? TrackingConfig.settings(yamls.get(TrackingConfig.SECTION))
                : (initial ? null : previous.tracking());
        if (arrowRain == null) {
            report.note("落地箭雨未启用：arrow_rain.yml 没有 mechanic: " + ArrowRainConfig.MECHANIC
                    + "（旧的 volley 或自定义配置保持原行为）");
        }
        if (tracking == null) {
            report.note("跟踪未启用：tracking.yml 不存在或没有 mechanic: " + TrackingConfig.MECHANIC);
        }

        return new Outcome(new RuntimeConfig(next, global, arrowRain, tracking), report, Map.copyOf(baseline));
    }

    /**
     * 可热生效的部分有没有变（决定是否列进 applied，契约 §9）：schema 里不是 {@code apply: restart} 的顶层字段，
     * 按声明读取（含默认值和旧别名，动作还比较顺序）；箭雨、跟踪的文件再比较读出来的专用参数。
     * 只改了注册字段（包括 name：界面名虽然立即换，物品上的名字要重启）时返回 false。
     */
    static boolean hotChanged(String id, Map<String, Object> before, Map<String, Object> after) {
        for (Param<?> param : EnchantSchema.PARAMS) {
            if (param.apply() == Param.Apply.RESTART) continue;
            if (!Objects.equals(hotValue(param, before), hotValue(param, after))) return true;
        }
        if (id.equals(ArrowRainConfig.SECTION)) {
            return !Objects.equals(ArrowRainConfig.settings(before), ArrowRainConfig.settings(after));
        }
        if (id.equals(TrackingConfig.SECTION)) {
            return !Objects.equals(TrackingConfig.settings(before), TrackingConfig.settings(after));
        }
        return false;
    }

    private static Object hotValue(Param<?> param, Map<String, Object> data) {
        Object value;
        try {
            value = param.read(data);
        } catch (IllegalArgumentException e) {
            value = param.raw(data);
        }
        // 动作按 yml 里的顺序执行，只换顺序也算变了
        return param == EnchantSchema.ACTIONS && value instanceof Map<?, ?> map ? List.copyOf(map.entrySet()) : value;
    }

    private static Map<String, Object> readYaml(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            Object loaded = new Yaml().load(input);
            if (loaded == null) return null;
            if (!(loaded instanceof Map<?, ?>)) throw new IllegalArgumentException("顶层不是键值段");
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) loaded;
            return map;
        }
    }

    private static String firstLine(String message) {
        if (message == null) return "";
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
