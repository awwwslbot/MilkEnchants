package com.windsor.meowsEnchants.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小的 JSON 写出器：导出 schema（多行、稳定顺序）和 reload 结果（单行）都用它。
 * 只支持 Map（按迭代顺序）、List、String、Number、Boolean 和 null。
 */
public final class Json {
    private Json() {}

    /** 单行。 */
    public static String compact(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, -1, 0);
        return out.toString();
    }

    /** 两格缩进；只含标量的数组和空容器写在一行。 */
    public static String pretty(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, 2, 0);
        return out.append('\n').toString();
    }

    /** 复制成只含 LinkedHashMap / ArrayList / 标量的结构，整数统一为 Integer/Long。 */
    public static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) copy.add(normalize(item));
            return copy;
        }
        if (value instanceof Short || value instanceof Byte) return ((Number) value).intValue();
        if (value instanceof Float f) return f.doubleValue();
        return value;
    }

    private static void write(StringBuilder out, Object value, int indent, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            quote(out, text);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new IllegalArgumentException("Not a JSON number: " + d);
            out.append(Double.toString(d));
        } else if (value instanceof Number) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            writeMap(out, map, indent, depth);
        } else if (value instanceof List<?> list) {
            writeList(out, list, indent, depth);
        } else {
            throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
        }
    }

    private static void writeMap(StringBuilder out, Map<?, ?> map, int indent, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) out.append(',');
            first = false;
            newline(out, indent, depth + 1);
            quote(out, String.valueOf(entry.getKey()));
            out.append(indent < 0 ? ":" : ": ");
            write(out, entry.getValue(), indent, depth + 1);
        }
        newline(out, indent, depth);
        out.append('}');
    }

    private static void writeList(StringBuilder out, List<?> list, int indent, int depth) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        boolean scalars = list.stream().noneMatch(item -> item instanceof Map<?, ?> || item instanceof List<?>);
        int itemIndent = scalars ? -1 : indent;
        out.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) out.append(scalars && indent >= 0 ? ", " : ",");
            first = false;
            newline(out, itemIndent, depth + 1);
            write(out, item, indent, depth + 1);
        }
        newline(out, itemIndent, depth);
        out.append(']');
    }

    private static void newline(StringBuilder out, int indent, int depth) {
        if (indent < 0) return;
        out.append('\n');
        out.append(" ".repeat(indent * depth));
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
