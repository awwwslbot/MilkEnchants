package com.windsor.meowsEnchants.actions;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 逐级列表：等级 L 取第 {@code min(L, 长度)} 项（1 起，低于 1 取第一项）。
 * 等级超出列表长度时取最后一项，并对同一份列表只告警一次。
 */
public record ListScaling(List<Double> values) implements ScalingFunction {
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile Consumer<String> warner = message -> {};

    public ListScaling {
        if (values.isEmpty()) throw new IllegalArgumentException("Empty per-level list");
        values = List.copyOf(values);
    }

    /** 插件启用时接上日志；未接时（单元测试）只计数不输出。 */
    public static void setWarner(Consumer<String> sink) {
        warner = sink == null ? message -> {} : sink;
    }

    @Override
    public double getValue(int level) {
        int size = values.size();
        if (level > size && WARNED.add(values.toString())) {
            warner.accept("逐级列表 " + values + " 只有 " + size + " 项，等级 " + level + " 及以上取最后一项");
        }
        return values.get(Math.clamp(level, 1, size) - 1);
    }
}
