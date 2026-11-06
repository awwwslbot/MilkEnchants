package com.windsor.meowsEnchants.config;

import com.windsor.meowsEnchants.actions.ActionRegistry;
import com.windsor.meowsEnchants.arrowrain.ArrowRainConfig;
import com.windsor.meowsEnchants.arrowrain.ArrowRainSettings;
import com.windsor.meowsEnchants.schema.EnchantSchema;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.tracking.TrackingConfig;
import com.windsor.meowsEnchants.tracking.TrackingSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 加载期校验（契约 §10）：只报告，不拦。类型不符、缺必填参数、逐级列表为空是 error，
 * 其余（未知键、未知取值、注册表里没有、超出范围、无法解析的占位符）是 warning。
 */
public final class ConfigValidator {
    private ConfigValidator() {}

    /** 一条校验结果；enchant 是附魔 id（config.yml 的问题记为 "config.yml"）。 */
    public record Issue(String enchant, String path, String message, boolean error) {}

    /** 注册表查询；单元测试用假实现。 */
    public interface Registries {
        /** null = 无法判断（不报告）。 */
        Boolean exists(Param.Registry registry, String value);

        Registries NONE = (registry, value) -> null;
    }

    public static List<Issue> validateEnchant(String id, String fileName, Map<String, Object> data,
                                              Registries registries) {
        Context ctx = new Context(id, registries, maxLevel(data));
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (fileName.equals(ArrowRainConfig.FILE) && key.equals(ArrowRainConfig.SECTION)) {
                ctx.section(key, ArrowRainConfig.PARAMS, value);
                continue;
            }
            if (fileName.equals(TrackingConfig.FILE) && key.equals(TrackingConfig.SECTION)) {
                ctx.section(key, TrackingSettings.PARAMS, value);
                continue;
            }
            Param<?> param = find(EnchantSchema.PARAMS, key);
            if (param == null) {
                ctx.warn(key, "未知字段 " + key);
            } else if (param == EnchantSchema.ACTIONS) {
                ctx.actions(value);
            } else {
                ctx.value(key, param, value);
            }
        }
        if (value(data, EnchantSchema.MECHANIC) instanceof String flag && !EnchantSchema.isMechanicEnchant(id, flag)) {
            ctx.warn("mechanic", "mechanic: " + flag + " 不会启用任何专用机制（只有 " + ArrowRainConfig.FILE + " 写 "
                    + ArrowRainConfig.MECHANIC + "、" + TrackingConfig.FILE + " 写 " + TrackingConfig.MECHANIC
                    + " 才启用），这个附魔照常走通用触发器");
        }
        if (fileName.equals(ArrowRainConfig.FILE) && data.get(ArrowRainConfig.SECTION) instanceof Map<?, ?> rain) {
            arrowRainHeights(ctx, rain);
        }
        if (value(data, EnchantSchema.APPLICABLE_ITEMS) instanceof List<?> list && list.size() > 1) {
            ctx.warn("applicable_items", "applicable_items 写成列表时只取第一项 " + list.getFirst());
        }
        List<String> description = EnchantSchema.DESCRIPTION.read(data);
        for (int i = 0; i < description.size(); i++) {
            for (String raw : Placeholders.render(data, description.get(i)).unresolved()) {
                ctx.warn("description[" + i + "]", "无法解析的占位符 " + raw);
            }
        }
        return ctx.issues;
    }

    /** min_height 大于 height 时照规则算（头顶空间要够 min_height 才下，生成高度不超过 height），只告警。 */
    private static void arrowRainHeights(Context ctx, Map<?, ?> section) {
        double height = ArrowRainSettings.HEIGHT.read(section);
        double minHeight = ArrowRainSettings.MIN_HEIGHT.read(section);
        if (minHeight > height) {
            String prefix = ArrowRainConfig.SECTION + ".";
            ctx.warn(prefix + ArrowRainSettings.MIN_HEIGHT.key(), prefix + ArrowRainSettings.MIN_HEIGHT.key() + " = "
                    + trim(minHeight) + " 大于 " + prefix + ArrowRainSettings.HEIGHT.key() + " = " + trim(height)
                    + "：头顶空间不够 " + trim(minHeight) + " 格的列不生成雨箭，生成高度仍不超过命中点上方 "
                    + trim(height) + " 格");
        }
    }

    public static List<Issue> validateGlobal(Map<String, Object> data, Registries registries) {
        Context ctx = new Context("config.yml", registries, null);
        ctx.section(null, GlobalSettings.PARAMS, data);
        return ctx.issues;
    }

    private static Object value(Map<String, Object> data, Param<?> param) {
        return param.raw(data);
    }

    private static Integer maxLevel(Map<String, Object> data) {
        return EnchantSchema.MAX_LEVEL.raw(data) instanceof Number number ? number.intValue() : null;
    }

    private static Param<?> find(List<Param<?>> params, String key) {
        for (Param<?> param : params) {
            if (param.key().equals(key) || param.aliases().contains(key)) return param;
        }
        return null;
    }

    private static String join(String parent, String key) {
        return parent == null || parent.isEmpty() ? key : parent + "." + key;
    }

    private static final class Context {
        final String enchant;
        final Registries registries;
        final Integer maxLevel;
        final List<Issue> issues = new ArrayList<>();

        Context(String enchant, Registries registries, Integer maxLevel) {
            this.enchant = enchant;
            this.registries = registries;
            this.maxLevel = maxLevel;
        }

        void warn(String path, String message) { issues.add(new Issue(enchant, path, message, false)); }
        void error(String path, String message) { issues.add(new Issue(enchant, path, message, true)); }

        /** 一个段：未知子键告警，已知子键逐个校验。 */
        void section(String path, List<Param<?>> params, Object value) {
            if (!(value instanceof Map<?, ?> map)) {
                if (value != null) error(path, "类型不符：" + path + " 应为一个段");
                return;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String child = join(path, key);
                Param<?> param = find(params, key);
                if (param == null) warn(child, "未知字段 " + child);
                else value(child, param, entry.getValue());
            }
        }

        void actions(Object value) {
            if (!(value instanceof Map<?, ?> actions)) {
                if (value != null) error("actions", "类型不符：actions 应为一个段（键名 → 动作），写成列表会被当成没有动作");
                return;
            }
            for (Map.Entry<?, ?> entry : actions.entrySet()) {
                String path = "actions." + entry.getKey();
                if (!(entry.getValue() instanceof Map<?, ?> action)) {
                    error(path, "类型不符：动作 " + entry.getKey() + " 应为一个段");
                    continue;
                }
                Object type = action.get("type");
                if (!(type instanceof String typeName)) {
                    error(path + ".type", "动作 " + entry.getKey() + " 缺少 type");
                    continue;
                }
                ActionRegistry.ActionType actionType = ActionRegistry.type(typeName);
                if (actionType == null) {
                    warn(path + ".type", "未知动作类型 " + typeName + "（该动作会被跳过）");
                    continue;
                }
                for (Param<?> declared : actionType.params()) {
                    for (String alias : declared.aliases()) {
                        if (action.containsKey(alias) && action.containsKey(declared.key())) {
                            warn(path + "." + declared.key(), "同时写了 " + declared.key() + " 和旧名 " + alias + "，以 " + alias + " 为准");
                        }
                    }
                }
                for (Map.Entry<?, ?> param : action.entrySet()) {
                    String key = String.valueOf(param.getKey());
                    if (key.equals("type")) continue;
                    String child = path + "." + key;
                    Param<?> declared = key.equals(ActionRegistry.CHANCE.key()) ? ActionRegistry.CHANCE
                            : find(actionType.params(), key);
                    if (declared == null) warn(child, "未知参数 " + key + "（动作 " + typeName + "）");
                    else value(child, declared, param.getValue());
                }
                for (Param<?> declared : actionType.params()) {
                    if (declared.required() && !declared.isPresent(action)) {
                        error(path + "." + declared.key(), "缺少必填参数 " + declared.key() + "（该动作会被跳过）");
                    }
                }
            }
        }

        void value(String path, Param<?> param, Object value) {
            if (value == null) return;
            if (param.scalable()) {
                scalable(path, param, value);
                return;
            }
            switch (param.type()) {
                case SECTION -> section(path, param.children(), value);
                case MAP -> map(path, param, value);
                case INT, NUMBER -> {
                    if (!(value instanceof Number number)) {
                        error(path, "类型不符：" + path + " 应为数字，实际是 " + describe(value));
                        return;
                    }
                    if (param.type() == Param.Type.INT && number.doubleValue() != Math.rint(number.doubleValue())) {
                        warn(path, path + " 应为整数，" + number + " 会被截断");
                    }
                    range(path, param, number.doubleValue());
                }
                case BOOL -> {
                    if (!(value instanceof Boolean)) error(path, "类型不符：" + path + " 应为 true/false，实际是 " + describe(value));
                }
                case STRING, TEXT -> {
                    if (!(value instanceof String)) error(path, "类型不符：" + path + " 应为文字，实际是 " + describe(value));
                }
                case ENUM -> {
                    Object scalar = param == EnchantSchema.APPLICABLE_ITEMS && value instanceof List<?> list && !list.isEmpty()
                            ? list.getFirst() : value;
                    if (!(scalar instanceof String text)) {
                        error(path, "类型不符：" + path + " 应为文字，实际是 " + describe(value));
                        return;
                    }
                    if (param.enumValues().stream().noneMatch(v -> v.equalsIgnoreCase(text))) {
                        warn(path, "未知取值 " + text + "（" + path + " 可选 " + String.join("/", param.enumValues()) + "）");
                    }
                }
                case REGISTRY -> {
                    if (!(value instanceof String text)) {
                        error(path, "类型不符：" + path + " 应为文字，实际是 " + describe(value));
                        return;
                    }
                    registry(path, param.registry(), text);
                }
                case TEXT_LIST, STRING_LIST, REGISTRY_LIST -> {
                    List<?> list = value instanceof List<?> l ? l : null;
                    if (list == null && value instanceof String && param.convert(value) != null) list = List.of(value);
                    if (list == null) {
                        error(path, "类型不符：" + path + " 应为列表，实际是 " + describe(value));
                        return;
                    }
                    for (int i = 0; i < list.size(); i++) {
                        Object item = list.get(i);
                        String itemPath = path + "[" + i + "]";
                        if (!(item instanceof String text)) {
                            error(itemPath, "类型不符：" + itemPath + " 应为文字，实际是 " + describe(item));
                        } else if (param.type() == Param.Type.REGISTRY_LIST) {
                            registry(itemPath, param.registry(), text);
                        }
                    }
                }
            }
        }

        void map(String path, Param<?> param, Object value) {
            if (!(value instanceof Map<?, ?> map)) {
                error(path, "类型不符：" + path + " 应为一个段");
                return;
            }
            if (param.children().isEmpty()) return;
            Param<?> spec = param.children().getFirst();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                value(path + "." + entry.getKey(), spec, entry.getValue());
            }
        }

        void scalable(String path, Param<?> param, Object value) {
            if (value instanceof Number number) {
                range(path, param, number.doubleValue());
                return;
            }
            if (value instanceof List<?> list) {
                if (list.isEmpty()) {
                    error(path, "逐级列表为空：" + path);
                    return;
                }
                for (int i = 0; i < list.size(); i++) {
                    if (!(list.get(i) instanceof Number number)) {
                        error(path + "[" + i + "]", "类型不符：" + path + "[" + i + "] 应为数字，实际是 " + describe(list.get(i)));
                        return;
                    }
                    range(path + "[" + i + "]", param, number.doubleValue());
                }
                if (!param.overlayList() && maxLevel != null && list.size() < maxLevel) {
                    warn(path, "逐级列表 " + path + " 只有 " + list.size() + " 项，最高等级 " + maxLevel
                            + "，更高等级取最后一项");
                }
                return;
            }
            if (value instanceof Map<?, ?> map) {
                if ("fixed".equals(String.valueOf(map.get("scaling")).toLowerCase(Locale.ROOT))) {
                    if (map.get("value") instanceof Number number) range(path, param, number.doubleValue());
                    else error(path, "类型不符：" + path + " 的 {scaling: fixed} 缺少数字 value");
                    return;
                }
                if (map.get("base") instanceof Number base && map.get("per_level") instanceof Number perLevel) {
                    range(path, param, base.doubleValue());
                    if (maxLevel != null) {
                        range(path + "@" + maxLevel, param, base.doubleValue() + perLevel.doubleValue() * (maxLevel - 1));
                    }
                    return;
                }
                error(path, "类型不符：" + path + " 应为数字、{base, per_level}、{scaling: fixed, value} 或逐级列表");
                return;
            }
            error(path, "类型不符：" + path + " 应为数字、{base, per_level}、{scaling: fixed, value} 或逐级列表，实际是 " + describe(value));
        }

        void range(String path, Param<?> param, double value) {
            if (param.min() != null && value < param.min().doubleValue()
                    || param.max() != null && value > param.max().doubleValue()) {
                warn(path, path + " = " + trim(value) + " 超出范围 [" + bound(param.min(), "-∞") + ", "
                        + bound(param.max(), "∞") + "]");
            } else if (param.warnAbove() != null && value > param.warnAbove().doubleValue()) {
                warn(path, path + " = " + trim(value) + " 超过建议上限 " + trim(param.warnAbove().doubleValue()));
            }
        }

        void registry(String path, Param.Registry registry, String value) {
            Boolean exists = registries.exists(registry, value);
            if (Boolean.FALSE.equals(exists)) {
                warn(path, "注册表 " + registry.id() + " 里没有 " + value);
            }
        }
    }

    private static String describe(Object value) {
        if (value instanceof String) return "文字 \"" + value + "\"";
        if (value instanceof Number) return "数字 " + value;
        if (value instanceof Boolean) return "布尔 " + value;
        if (value instanceof List<?>) return "列表";
        if (value instanceof Map<?, ?>) return "段";
        return String.valueOf(value);
    }

    private static String bound(Number bound, String unbounded) {
        return bound == null ? unbounded : trim(bound.doubleValue());
    }

    private static String trim(double value) {
        return Placeholders.format(value, null);
    }
}
