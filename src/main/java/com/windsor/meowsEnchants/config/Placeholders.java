package com.windsor.meowsEnchants.config;

import com.windsor.meowsEnchants.actions.ActionRegistry;
import com.windsor.meowsEnchants.actions.FixedScaling;
import com.windsor.meowsEnchants.actions.LinearScaling;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.arrowrain.ArrowRainConfig;
import com.windsor.meowsEnchants.schema.EnchantSchema;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.tracking.TrackingConfig;
import com.windsor.meowsEnchants.tracking.TrackingSettings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * description 里的说明占位符（契约 §6）：{@code {引用}}、{@code {引用@等级}}、{@code {引用|格式|…}}，
 * {@code {{} / {@code }}} 输出字面的花括号。无法解析的占位符原样输出并记一条问题。
 * <p>
 * 编辑器预览必须得到同样的结果，两边都跑 {@code editor/placeholder-vectors.json}。
 */
public final class Placeholders {
    private Placeholders() {}

    /** 一行的渲染结果；unresolved 是原样输出的 {@code {…}}。 */
    public record Result(String text, List<String> unresolved) {}

    /**
     * @param enchant 附魔 yml 解析后的原始内容（没写的字段按内置默认取值）
     */
    public static Result render(Map<String, Object> enchant, String line) {
        StringBuilder out = new StringBuilder(line.length());
        List<String> unresolved = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '{') {
                if (i + 1 < line.length() && line.charAt(i + 1) == '{') {
                    out.append('{');
                    i += 2;
                    continue;
                }
                int end = line.indexOf('}', i + 1);
                if (end < 0) {
                    String rest = line.substring(i);
                    out.append(rest);
                    unresolved.add(rest);
                    break;
                }
                String body = line.substring(i + 1, end);
                String value = evaluate(enchant, body);
                if (value == null) {
                    String raw = line.substring(i, end + 1);
                    out.append(raw);
                    unresolved.add(raw);
                } else {
                    out.append(value);
                }
                i = end + 1;
            } else if (c == '}') {
                out.append('}');
                i += (i + 1 < line.length() && line.charAt(i + 1) == '}') ? 2 : 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return new Result(out.toString(), List.copyOf(unresolved));
    }

    /** 把 description 的每一行都渲染一遍。 */
    public static List<Result> renderAll(Map<String, Object> enchant, List<String> lines) {
        List<Result> results = new ArrayList<>(lines.size());
        for (String line : lines) results.add(render(enchant, line));
        return results;
    }

    // ---------------------------------------------------------------- evaluation

    /** null = 无法解析。 */
    private static String evaluate(Map<String, Object> enchant, String body) {
        String[] parts = body.split("\\|", -1);
        String head = parts[0];
        String level = null;
        int at = head.indexOf('@');
        if (at >= 0) {
            level = head.substring(at + 1);
            head = head.substring(0, at);
        }
        Ref ref = resolve(enchant, head);
        if (ref == null) return null;

        boolean hasFormat = parts.length > 1;
        if (!ref.numeric()) {
            Object raw = ref.raw();
            if (level != null || hasFormat) return null;
            if (!(raw instanceof String || raw instanceof Boolean || raw instanceof Number)) return null;
            return String.valueOf(raw);
        }

        Double value = numericValue(enchant, ref, level);
        if (value == null) return null;

        double number = value;
        Integer decimals = null;
        StringBuilder suffix = new StringBuilder();
        for (int k = 1; k < parts.length; k++) {
            String format = parts[k];
            switch (format) {
                case "%" -> {
                    number *= 100;
                    suffix.append('%');
                }
                case "s" -> {
                    Param.Unit unit = ref.param().unit();
                    if (unit == Param.Unit.MS) number /= 1000;
                    else if (unit == Param.Unit.TICK) number /= 20;
                    else return null;
                }
                case "0", "1", "2", "3" -> decimals = Integer.parseInt(format);
                default -> {
                    return null;
                }
            }
        }
        return format(number, decimals) + suffix;
    }

    /** 数值引用在指定等级的值；整数参数按插件实际用法截断（{@code @step} 除外）。 */
    private static Double numericValue(Map<String, Object> enchant, Ref ref, String level) {
        Param<?> param = ref.param();
        boolean step = "step".equals(level);
        int lvl = 1;
        if (level != null && !step) {
            if (level.equals("max")) {
                Object max = EnchantSchema.MAX_LEVEL.raw(enchant);
                if (!(max instanceof Number number)) return null;
                lvl = number.intValue();
            } else {
                if (level.isEmpty() || !level.chars().allMatch(Character::isDigit)) return null;
                try {
                    lvl = Integer.parseInt(level);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (lvl < 1) return null;
            }
        }

        double value;
        if (param.scalable()) {
            Object converted = param.convert(ref.raw());
            if (!(converted instanceof ScalingFunction scaling)) return null;
            if (step) {
                if (scaling instanceof LinearScaling linear) return linear.perLevel();
                if (scaling instanceof FixedScaling) return 0.0;
                return null;
            }
            value = scaling.getValue(lvl);
        } else {
            if (!(ref.raw() instanceof Number number)) return null;
            if (step) return 0.0;
            value = number.doubleValue();
        }
        if (param.type() == Param.Type.INT) value = (double) (long) value;
        return value;
    }

    /** 固定小数位；没指定时整数不带小数，非整数最多 2 位并去掉末尾 0（都按十进制四舍五入）。 */
    static String format(double value, Integer decimals) {
        BigDecimal decimal = BigDecimal.valueOf(value);
        if (decimals != null) {
            return decimal.setScale(decimals, RoundingMode.HALF_UP).toPlainString();
        }
        BigDecimal rounded = decimal.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.signum() == 0) return "0";
        return rounded.toPlainString();
    }

    // ---------------------------------------------------------------- references

    /**
     * 引用的声明和原始值（没写时是默认值）。numeric 表示能按数值格式化。
     */
    private record Ref(Param<?> param, Object raw, boolean numeric) {}

    private static Ref resolve(Map<String, Object> enchant, String ref) {
        if (ref.isEmpty()) return null;
        String[] path = ref.split("\\.", -1);
        for (String segment : path) if (segment.isEmpty()) return null;

        switch (ref) {
            case "cooldown" -> { return top(enchant, EnchantSchema.COOLDOWN); }
            case "max_level" -> { return top(enchant, EnchantSchema.MAX_LEVEL); }
            case "anvil_cost" -> { return top(enchant, EnchantSchema.ANVIL_COST); }
            case "enchanting_table.chance" -> {
                double chance = EnchantConfigLoader.parseEnchantingTableConfig(enchant).getChance();
                return new Ref(EnchantSchema.TABLE_CHANCE, chance, true);
            }
            case "villager_trades.chance" -> {
                double chance = EnchantConfigLoader.parseVillagerTradeConfig(enchant).getChance();
                return new Ref(EnchantSchema.TRADE_CHANCE, chance, true);
            }
            default -> { }
        }

        if (path[0].equals("actions")) {
            if (path.length < 3) return null;
            if (!(EnchantSchema.ACTIONS.raw(enchant) instanceof Map<?, ?> actions)) return null;
            if (!(actions.get(path[1]) instanceof Map<?, ?> action)) return null;
            if (path.length == 3 && path[2].equals(ActionRegistry.CHANCE.key())) {
                return leaf(ActionRegistry.CHANCE, action);
            }
            ActionRegistry.ActionType type = ActionRegistry.type(String.valueOf(action.get("type")));
            if (type == null || !type.editable()) return null;
            return walk(type.params(), action, path, 2);
        }

        String mechanicFlag = EnchantSchema.MECHANIC.raw(enchant) instanceof String flag ? flag : null;
        if (path[0].equals(ArrowRainConfig.SECTION) && ArrowRainConfig.MECHANIC.equals(mechanicFlag)) {
            return walk(ArrowRainConfig.PARAMS, section(enchant.get(ArrowRainConfig.SECTION)), path, 1);
        }
        if (path[0].equals(TrackingConfig.SECTION) && TrackingConfig.MECHANIC.equals(mechanicFlag)) {
            return walk(TrackingSettings.PARAMS, section(enchant.get(TrackingConfig.SECTION)), path, 1);
        }
        return null;
    }

    private static Ref top(Map<String, Object> enchant, Param<?> param) {
        return leaf(param, enchant);
    }

    /** 沿参数路径走到叶子；中间只能是 section。 */
    private static Ref walk(List<Param<?>> params, Map<?, ?> values, String[] path, int from) {
        List<Param<?>> current = params;
        Map<?, ?> map = values;
        for (int k = from; k < path.length; k++) {
            Param<?> param = find(current, path[k]);
            if (param == null) return null;
            if (k == path.length - 1) return leaf(param, map);
            if (param.type() != Param.Type.SECTION) return null;
            map = section(param.raw(map));
            current = param.children();
        }
        return null;
    }

    private static Ref leaf(Param<?> param, Map<?, ?> map) {
        if (param.type() == Param.Type.SECTION || param.type() == Param.Type.MAP) return null;
        Object raw = param.raw(map);
        if (raw == null) raw = param.defaultJson();
        boolean numeric = param.scalable() || param.type() == Param.Type.INT || param.type() == Param.Type.NUMBER;
        if (!numeric && raw instanceof List<?>) return null;
        return new Ref(param, raw, numeric);
    }

    private static Param<?> find(List<Param<?>> params, String key) {
        for (Param<?> param : params) {
            if (param.key().equals(key) || param.aliases().contains(key)) return param;
        }
        return null;
    }

    private static Map<?, ?> section(Object raw) {
        return raw instanceof Map<?, ?> map ? map : Map.of();
    }
}
