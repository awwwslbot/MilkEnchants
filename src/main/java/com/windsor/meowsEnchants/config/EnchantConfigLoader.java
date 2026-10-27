package com.windsor.meowsEnchants.config;

import com.windsor.meowsEnchants.schema.EnchantSchema;
import com.windsor.meowsEnchants.schema.Param;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 附魔 YAML 配置的读取工具。
 * <p>
 * bootstrap 与插件运行时（onEnable）都需要读取配置：前者用于注册附魔，后者用于构建
 * 触发逻辑所需的配置表。PlugmanX 热重载时 bootstrap 不会再次执行，因此必须由 onEnable
 * 重新读取，否则配置表为空、所有自定义附魔的触发器都会失效。
 */
public final class EnchantConfigLoader {

    private static final String ENCHANTS_DIR = "plugins/MeowsEnchants/enchants";

    private EnchantConfigLoader() {
    }

    /** 附魔配置文件所在目录。 */
    public static @NotNull Path enchantDir() {
        return Path.of(ENCHANTS_DIR);
    }

    /**
     * 读取目录下所有 *.yml 附魔配置，格式错误的文件会被跳过。
     *
     * @return 以附魔 Key 为键的配置表，目录不存在或无配置文件时返回空表
     */
    public static @NotNull Map<Key, EnchantConfig> loadAll(@NotNull Path dir, @NotNull Logger logger) {
        Map<Key, EnchantConfig> configMap = new HashMap<>();

        File[] files = dir.toFile().listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            logger.info("No custom enchantment config files found.");
            return configMap;
        }

        Yaml yaml = new Yaml();
        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            try (InputStream input = new FileInputStream(file)) {
                Map<String, Object> data = yaml.load(input);
                if (data == null) {
                    logger.warn("Config file " + file.getName() + " is empty, skipping.");
                    continue;
                }

                EnchantConfig config = parse(id, data);
                if (config == null) {
                    logger.warn("Config file " + file.getName() + " is missing required fields, skipping.");
                    continue;
                }
                config.setLogger(logger);

                Key key = Key.key("meowsenchants", id);
                configMap.put(key, config);
                logger.info("Loaded enchantment config: " + id);
            } catch (Exception e) {
                logger.warn("Failed to read config file " + file.getName() + ": " + e.getMessage());
            }
        }
        return configMap;
    }

    /**
     * 按 {@link EnchantSchema} 的声明解析一个附魔文件。
     *
     * @return 缺少 name、max_level、anvil_cost、applicable_items 任一必填项时返回 null
     */
    public static @Nullable EnchantConfig parse(@NotNull String id, @NotNull Map<String, Object> data) {
        String name = EnchantSchema.NAME.raw(data) instanceof String text ? text : null;
        List<String> description = EnchantSchema.DESCRIPTION.read(data);
        Number maxLevelNum = EnchantSchema.MAX_LEVEL.raw(data) instanceof Number number ? number : null;
        Number anvilCostNum = EnchantSchema.ANVIL_COST.raw(data) instanceof Number number ? number : null;
        String applicable = parseApplicableItems(EnchantSchema.APPLICABLE_ITEMS.raw(data));
        String trigger = EnchantSchema.TRIGGER.read(data);
        String target = EnchantSchema.TARGET.read(data);
        long cooldown = EnchantSchema.COOLDOWN.read(data);
        Map<String, Map<String, Object>> actions = parseActions(EnchantSchema.ACTIONS.raw(data));
        List<String> conflicts = EnchantSchema.CONFLICTS.read(data);
        Map<String, Object> soundData = asStringObjectMap(EnchantSchema.SOUND.raw(data));
        Map<String, Object> particleData = asStringObjectMap(EnchantSchema.PARTICLE.raw(data));
        EnchantConfig.EnchantingTableConfig enchantingTableConfig = parseEnchantingTableConfig(data);
        EnchantConfig.VillagerTradeConfig villagerTradeConfig = parseVillagerTradeConfig(data);

        if (name == null || maxLevelNum == null || anvilCostNum == null || applicable == null) {
            return null;
        }
        // milk.12 把 trigger/target 强转成字符串，写成别的类型时整个文件被跳过
        if (notString(EnchantSchema.TRIGGER.raw(data)) || notString(EnchantSchema.TARGET.raw(data))) {
            return null;
        }

        return new EnchantConfig(
                id, name, description, maxLevelNum.intValue(), anvilCostNum.intValue(), applicable,
                trigger, target, cooldown,
                actions == null ? null : new ArrayList<>(actions.values()),
                actions == null ? null : new ArrayList<>(actions.keySet()),
                conflicts, soundData, particleData, enchantingTableConfig, villagerTradeConfig, data
        );
    }

    /** 当附魔目录不存在时，把插件 jar 内置的示例配置释放到磁盘。 */
    public static void extractBundled(@NotNull Path targetDir, @NotNull Logger logger) throws IOException {
        CodeSource codeSource = EnchantConfigLoader.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            Files.createDirectories(targetDir);
            logger.warn("Unable to locate plugin jar; created empty enchants directory.");
            return;
        }

        URI sourceUri;
        try {
            sourceUri = codeSource.getLocation().toURI();
        } catch (Exception e) {
            Files.createDirectories(targetDir);
            logger.warn("Unable to resolve plugin jar location; created empty enchants directory.");
            return;
        }

        Path sourcePath = Path.of(sourceUri);
        if (!Files.isRegularFile(sourcePath)) {
            Files.createDirectories(targetDir);
            logger.warn("Plugin code source is not a jar file; created empty enchants directory.");
            return;
        }

        boolean copiedAny = false;
        try (JarFile jarFile = new JarFile(sourcePath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("enchants/") || name.equals("enchants/") || entry.isDirectory()) {
                    continue;
                }

                Path relative = Path.of(name.substring("enchants/".length()));
                Path target = targetDir.resolve(relative).normalize();
                if (!target.startsWith(targetDir.normalize())) {
                    continue;
                }

                Files.createDirectories(target.getParent());
                try (InputStream input = jarFile.getInputStream(entry)) {
                    Files.copy(input, target);
                }
                copiedAny = true;
            }
        }

        if (copiedAny) {
            logger.info("Extracted bundled enchant configs to " + targetDir);
        } else {
            Files.createDirectories(targetDir);
            logger.warn("No bundled enchant configs found; created empty enchants directory.");
        }
    }

    private static String parseApplicableItems(Object rawApplicable) {
        if (rawApplicable instanceof String value) {
            return value;
        }
        if (rawApplicable instanceof List<?> list && !list.isEmpty()) {
            return list.getFirst().toString();
        }
        return null;
    }

    /** 动作键 → 动作参数，保持 yml 顺序；actions 不是 map 时返回 null（视为没有动作）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> parseActions(Object rawActions) {
        if (!(rawActions instanceof Map<?, ?> actionsMap)) {
            return null;
        }
        Map<String, Map<String, Object>> actions = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : actionsMap.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?>) {
                actions.put(String.valueOf(entry.getKey()), (Map<String, Object>) entry.getValue());
            }
        }
        return actions;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringObjectMap(Object rawMap) {
        if (rawMap instanceof Map<?, ?>) {
            return (Map<String, Object>) rawMap;
        }
        return null;
    }

    private static boolean notString(Object raw) {
        return raw != null && !(raw instanceof String);
    }

    /** 有值且类型正确时取新值，否则保留 current。 */
    private static <T> T override(Param<T> param, Map<?, ?> map, T current) {
        Object raw = param.raw(map);
        if (raw == null) return current;
        T value = param.convert(raw);
        return value == null ? current : value;
    }

    static EnchantConfig.EnchantingTableConfig parseEnchantingTableConfig(Map<String, Object> data) {
        boolean enabled = EnchantSchema.TABLE_ENABLED.defaultValue();
        double chance = EnchantSchema.TABLE_CHANCE.defaultValue();
        int minCost = EnchantSchema.TABLE_MIN_COST.defaultValue();
        int maxCost = EnchantSchema.TABLE_MAX_COST.defaultValue();

        if (EnchantSchema.LEGACY_TABLE_CHANCE.raw(data) instanceof Number number) {
            chance = number.doubleValue();
            enabled = chance > 0.0;
        }

        if (EnchantSchema.ENCHANTING_TABLE.raw(data) instanceof Map<?, ?> map) {
            enabled = override(EnchantSchema.TABLE_ENABLED, map, enabled);
            chance = override(EnchantSchema.TABLE_CHANCE, map, chance);
            minCost = override(EnchantSchema.TABLE_MIN_COST, map, minCost);
            maxCost = override(EnchantSchema.TABLE_MAX_COST, map, maxCost);
        }

        return new EnchantConfig.EnchantingTableConfig(enabled, chance, minCost, maxCost);
    }

    static EnchantConfig.VillagerTradeConfig parseVillagerTradeConfig(Map<String, Object> data) {
        Map<String, Object> section = EnchantSchema.VILLAGER_TRADES.read(data);
        return new EnchantConfig.VillagerTradeConfig(
                EnchantSchema.TRADE_ENABLED.read(section),
                EnchantSchema.TRADE_CHANCE.read(section),
                EnchantSchema.TRADE_TREASURE.read(section));
    }
}
