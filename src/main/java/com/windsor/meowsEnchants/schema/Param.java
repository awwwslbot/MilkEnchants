package com.windsor.meowsEnchants.schema;

import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.actions.ScalingFunctionLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 一个可配置项的唯一声明：键、类型、默认值、生效方式等。
 * <p>
 * 运行时读取（{@link #read(Map)}）和导出 {@code editor/enchant-schema.json} 都用这份声明，
 * 所以导出的默认值永远等于代码实际使用的值。
 *
 * @param <T> 运行时读取得到的值类型
 */
public final class Param<T> {

    /** schema 里的 {@code type}。 */
    public enum Type {
        INT, NUMBER, BOOL, STRING, TEXT, TEXT_LIST, ENUM, REGISTRY, STRING_LIST, REGISTRY_LIST, MAP, SECTION;

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    /** schema 里的 {@code unit}，供编辑器显示和占位符 {@code |s} 换算。 */
    public enum Unit {
        TICK, MS, BLOCK, RATIO, LEVEL, COUNT, DEGREE;

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    /** 改了以后 {@code /enchants reload} 能否生效。 */
    public enum Apply {
        RELOAD, RESTART;

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    /** {@code type=registry / registry_list} 时取值所在的原版注册表。 */
    public enum Registry {
        SOUND, PARTICLE, MOB_EFFECT, ITEM, BLOCK, ATTRIBUTE, ENTITY_TYPE, ENCHANTMENT;

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    private final String key;
    private final List<String> aliases;
    private final String label;
    private final String help;
    private final Type type;
    private final boolean scalable;
    private final boolean hasDefault;
    private final Object defaultJson;
    private final T defaultValue;
    private final boolean required;
    private final Unit unit;
    private final Number min;
    private final Number max;
    private final Number warnAbove;
    private final List<String> enumValues;
    private final Registry registry;
    private final Apply apply;
    private final String danger;
    private final Map<String, List<String>> dangerWhen;
    private final boolean overlayList;
    private final List<Param<?>> children;
    private final Function<Object, T> converter;

    private Param(Builder<T> b) {
        this.key = b.key;
        this.aliases = List.copyOf(b.aliases);
        this.label = Objects.requireNonNull(b.label, b.key);
        this.help = b.help;
        this.type = b.type;
        this.scalable = b.scalable;
        this.required = b.required;
        this.unit = b.unit;
        this.min = b.min;
        this.max = b.max;
        this.warnAbove = b.warnAbove;
        this.enumValues = b.enumValues == null ? null : List.copyOf(b.enumValues);
        this.registry = b.registry;
        this.apply = b.apply;
        this.danger = b.danger;
        this.dangerWhen = b.dangerWhen == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(b.dangerWhen));
        this.overlayList = b.overlayList;
        this.children = List.copyOf(b.children);
        this.converter = b.converter;
        this.hasDefault = b.hasDefault;
        this.defaultJson = b.hasDefault ? Json.normalize(b.defaultJson) : null;
        if (b.hasDefault) {
            T converted = b.converter.apply(b.defaultJson);
            if (converted == null) {
                throw new IllegalStateException("Default of '" + b.key + "' does not match its type: " + b.defaultJson);
            }
            this.defaultValue = converted;
        } else {
            this.defaultValue = null;
        }
    }

    // ---------------------------------------------------------------- reading

    /**
     * 原始值；不存在时 null。旧别名优先于新键名（milk.12 的 velocity 同时写了 onGround 和 on_ground 时读 onGround），
     * 两个都写时加载期校验会告警。
     */
    public Object raw(Map<?, ?> map) {
        if (map == null) return null;
        for (String alias : aliases) {
            Object value = map.get(alias);
            if (value != null) return value;
        }
        return map.get(key);
    }

    public boolean isPresent(Map<?, ?> map) {
        return raw(map) != null;
    }

    /**
     * 按声明读取：缺省时用内置默认值；必填项缺失、按等级取值写法不合法时抛
     * {@link IllegalArgumentException}（与 milk.12 一致：该动作被跳过）。其余类型不符时退回默认值，
     * 由加载期校验报告。
     */
    @SuppressWarnings("unchecked")
    public T read(Map<?, ?> map) {
        Object raw = raw(map);
        if (raw == null) {
            if (required) {
                throw new IllegalArgumentException("Missing '" + key + "' parameter");
            }
            if (!hasDefault && (type == Type.SECTION || type == Type.MAP)) {
                return (T) Map.of();
            }
            return defaultValue;
        }
        T value = converter.apply(raw);
        if (value == null) {
            if (scalable || required) {
                throw new IllegalArgumentException("Invalid '" + key + "' parameter: " + raw);
            }
            return defaultValue;
        }
        return value;
    }

    /** 读取一个原始值（不经 map）。 */
    public T convert(Object raw) {
        return raw == null ? defaultValue : converter.apply(raw);
    }

    // ---------------------------------------------------------------- metadata

    public String key() { return key; }
    public List<String> aliases() { return aliases; }
    public String label() { return label; }
    public String help() { return help; }
    public Type type() { return type; }
    public boolean scalable() { return scalable; }
    public boolean hasDefault() { return hasDefault; }
    public Object defaultJson() { return defaultJson; }
    public T defaultValue() { return defaultValue; }
    public boolean required() { return required; }
    public Unit unit() { return unit; }
    public Number min() { return min; }
    public Number max() { return max; }
    public Number warnAbove() { return warnAbove; }
    public List<String> enumValues() { return enumValues; }
    public Registry registry() { return registry; }
    public Apply apply() { return apply; }
    public String danger() { return danger; }
    public Map<String, List<String>> dangerWhen() { return dangerWhen; }
    /** 逐级列表按「逐项覆盖默认列表」解释（tracking.radius），而不是 §4 的按等级取项。 */
    public boolean overlayList() { return overlayList; }
    public List<Param<?>> children() { return children; }

    /** 子项按键查找（含别名）。 */
    public Param<?> child(String childKey) {
        for (Param<?> child : children) {
            if (child.key.equals(childKey) || child.aliases.contains(childKey)) return child;
        }
        return null;
    }

    /** 导出为 schema 里的一个 Param 对象，键顺序固定。 */
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("key", key);
        if (!aliases.isEmpty()) json.put("aliases", aliases);
        json.put("label", label);
        json.put("help", help);
        json.put("type", type.id());
        json.put("scalable", scalable);
        if (hasDefault) json.put("default", defaultJson);
        json.put("required", required);
        json.put("unit", unit == null ? null : unit.id());
        json.put("min", min);
        json.put("max", max);
        json.put("warnAbove", warnAbove);
        if (enumValues != null) json.put("enum", enumValues);
        if (registry != null) json.put("registry", registry.id());
        json.put("apply", apply.id());
        json.put("danger", danger);
        if (dangerWhen != null) json.put("dangerWhen", dangerWhen);
        if (overlayList) json.put("listSemantics", "overlay");
        if (type == Type.SECTION || type == Type.MAP) {
            List<Object> list = new ArrayList<>(children.size());
            for (Param<?> child : children) list.add(child.toJson());
            json.put("children", list);
        }
        return json;
    }

    @Override
    public String toString() {
        return "Param[" + key + "]";
    }

    // ---------------------------------------------------------------- builders

    public static Builder<Integer> integer(String key, String label) {
        return new Builder<Integer>(key, label, Type.INT, raw -> raw instanceof Number n ? n.intValue() : null);
    }

    /** {@code type=int}，按 long 读取（毫秒冷却等）。 */
    public static Builder<Long> longInt(String key, String label) {
        return new Builder<Long>(key, label, Type.INT, raw -> raw instanceof Number n ? n.longValue() : null);
    }

    public static Builder<Double> number(String key, String label) {
        return new Builder<Double>(key, label, Type.NUMBER, raw -> raw instanceof Number n ? n.doubleValue() : null);
    }

    public static Builder<Boolean> bool(String key, String label) {
        return new Builder<Boolean>(key, label, Type.BOOL, Param::toBoolean);
    }

    public static Builder<String> string(String key, String label) {
        return new Builder<String>(key, label, Type.STRING, Param::toScalarString);
    }

    public static Builder<String> text(String key, String label) {
        return new Builder<String>(key, label, Type.TEXT, Param::toScalarString);
    }

    public static Builder<List<String>> textList(String key, String label) {
        return new Builder<List<String>>(key, label, Type.TEXT_LIST, Param::toStringList);
    }

    public static Builder<String> enumeration(String key, String label, List<String> values) {
        Builder<String> builder = new Builder<>(key, label, Type.ENUM, Param::toScalarString);
        builder.enumValues = values;
        return builder;
    }

    public static Builder<String> registry(String key, String label, Registry registry) {
        Builder<String> builder = new Builder<>(key, label, Type.REGISTRY, Param::toScalarString);
        builder.registry = registry;
        return builder;
    }

    public static Builder<List<String>> stringList(String key, String label) {
        return new Builder<List<String>>(key, label, Type.STRING_LIST, Param::toStringList);
    }

    public static Builder<List<String>> registryList(String key, String label, Registry registry) {
        Builder<List<String>> builder = new Builder<>(key, label, Type.REGISTRY_LIST, Param::toStringList);
        builder.registry = registry;
        return builder;
    }

    /** 按等级取值的整数参数（取值后由动作自行 {@code (int)} 截断）。 */
    public static Builder<ScalingFunction> scalableInt(String key, String label) {
        Builder<ScalingFunction> builder = new Builder<>(key, label, Type.INT, Param::toScaling);
        builder.scalable = true;
        return builder;
    }

    /** 按等级取值的小数参数。 */
    public static Builder<ScalingFunction> scalableNumber(String key, String label) {
        Builder<ScalingFunction> builder = new Builder<>(key, label, Type.NUMBER, Param::toScaling);
        builder.scalable = true;
        return builder;
    }

    /** 嵌套段；读取结果是原始 map（不是 map 时视为缺省），子项再用各自的声明读取。 */
    public static Builder<Map<String, Object>> section(String key, String label, Param<?>... children) {
        Builder<Map<String, Object>> builder = new Builder<>(key, label, Type.SECTION, Param::toMap);
        builder.children.addAll(List.of(children));
        builder.hasDefault = false;
        return builder;
    }

    /**
     * 键名自由的 map；{@code valueSpec} 描述每个值（键为 {@code *}），为 null 时值不限。
     */
    public static Builder<Map<String, Object>> map(String key, String label, Param<?> valueSpec) {
        Builder<Map<String, Object>> builder = new Builder<>(key, label, Type.MAP, Param::toMap);
        if (valueSpec != null) builder.children.add(valueSpec);
        return builder;
    }

    // ---------------------------------------------------------------- converters

    private static Boolean toBoolean(Object raw) {
        if (raw instanceof Boolean value) return value;
        if (raw instanceof String text) {
            if (text.equalsIgnoreCase("true")) return true;
            if (text.equalsIgnoreCase("false")) return false;
        }
        return null;
    }

    private static String toScalarString(Object raw) {
        if (raw instanceof String text) return text;
        if (raw instanceof Number || raw instanceof Boolean) return raw.toString();
        return null;
    }

    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) return null;
        List<String> result = new ArrayList<>(list.size());
        for (Object value : list) {
            if (value instanceof String text) result.add(text);
        }
        return Collections.unmodifiableList(result);
    }

    private static ScalingFunction toScaling(Object raw) {
        try {
            return ScalingFunctionLoader.load(raw);
        } catch (IllegalArgumentException | ClassCastException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    // ---------------------------------------------------------------- builder

    public static final class Builder<T> {
        private final String key;
        private final List<String> aliases = new ArrayList<>();
        private final String label;
        private final Type type;
        private Function<Object, T> converter;
        private final List<Param<?>> children = new ArrayList<>();
        private String help;
        private boolean scalable;
        private boolean hasDefault;
        private Object defaultJson;
        private boolean required;
        private Unit unit;
        private Number min;
        private Number max;
        private Number warnAbove;
        private List<String> enumValues;
        private Registry registry;
        private Apply apply = Apply.RELOAD;
        private String danger;
        private Map<String, List<String>> dangerWhen;
        private boolean overlayList;

        private Builder(String key, String label, Type type, Function<Object, T> converter) {
            this.key = key;
            this.label = label;
            this.type = type;
            this.converter = converter;
        }

        public Builder<T> help(String help) { this.help = help; return this; }
        public Builder<T> unit(Unit unit) { this.unit = unit; return this; }
        public Builder<T> min(Number min) { this.min = min; return this; }
        public Builder<T> max(Number max) { this.max = max; return this; }
        public Builder<T> range(Number min, Number max) { this.min = min; this.max = max; return this; }
        public Builder<T> warnAbove(Number warnAbove) { this.warnAbove = warnAbove; return this; }
        public Builder<T> restart() { this.apply = Apply.RESTART; return this; }
        public Builder<T> danger(String danger) { this.danger = danger; return this; }
        public Builder<T> alias(String alias) { this.aliases.add(alias); return this; }

        /** 危险只在同一附魔的顶层字段 {@code field} 取 {@code values} 之一时成立。 */
        public Builder<T> dangerWhen(String field, List<String> values) {
            this.dangerWhen = Map.of(field, List.copyOf(values));
            return this;
        }

        /** 自定义原始值 → 运行时值的转换（返回 null = 类型不符）。 */
        public Builder<T> convertWith(Function<Object, T> converter) {
            this.converter = converter;
            return this;
        }

        /** 逐级列表按逐项覆盖默认列表解释，见 {@link Param#overlayList()}。 */
        public Builder<T> overlayList() {
            this.overlayList = true;
            return this;
        }

        /** 列表参数也接受单个值（当作只有一项的列表）。 */
        public Builder<T> acceptSingle() {
            Function<Object, T> listConverter = this.converter;
            this.converter = raw -> listConverter.apply(raw instanceof List<?> ? raw : List.of(raw));
            return this;
        }

        public Builder<T> required() {
            this.required = true;
            this.hasDefault = false;
            this.defaultJson = null;
            return this;
        }

        /**
         * 缺省值，写成 YAML 里的形式：普通参数是值本身；按等级取值的参数可以是数字、
         * {@code Map.of("base", .., "per_level", ..)} 或列表。
         */
        public Builder<T> def(Object defaultYaml) {
            this.hasDefault = true;
            this.defaultJson = defaultYaml;
            return this;
        }

        public Param<T> build() {
            if (scalable && !hasDefault && !required) {
                throw new IllegalStateException("Scalable param '" + key + "' needs a default or required()");
            }
            return new Param<>(this);
        }
    }

    /** 线性写法的默认值。 */
    public static Map<String, Object> linear(Number base, Number perLevel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("base", base);
        map.put("per_level", perLevel);
        return map;
    }
}
