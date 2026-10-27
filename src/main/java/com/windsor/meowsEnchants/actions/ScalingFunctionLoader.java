package com.windsor.meowsEnchants.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 按等级取值的四种写法：数字（固定）、{@code {scaling: fixed, value}}（固定，旧写法）、
 * {@code {base, per_level}}（线性）、{@code [v1, v2, …]}（逐级列表）。
 */
public class ScalingFunctionLoader {

    public static ScalingFunction load(Object obj) {
        if (obj instanceof Number) {
            // 固定值
            return new FixedScaling(((Number) obj).doubleValue());
        } else if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            String scaling = (String) map.get("scaling");
            if ("fixed".equalsIgnoreCase(scaling)) {
                Object value = map.get("value");
                if (value instanceof Number) {
                    return new FixedScaling(((Number) value).doubleValue());
                }
            } else {
                // 默认线性 scaling: base, per_level
                Number base = (Number) map.get("base");
                Number perLevel = (Number) map.get("per_level");
                if (base != null && perLevel != null) {
                    return new LinearScaling(base.doubleValue(), perLevel.doubleValue());
                }
            }
        } else if (obj instanceof List<?> list && !list.isEmpty()) {
            List<Double> values = new ArrayList<>(list.size());
            for (Object item : list) {
                if (!(item instanceof Number number)) {
                    throw new IllegalArgumentException("Invalid scaling function: " + obj);
                }
                values.add(number.doubleValue());
            }
            return new ListScaling(values);
        }
        throw new IllegalArgumentException("Invalid scaling function: " + obj);
    }
}
