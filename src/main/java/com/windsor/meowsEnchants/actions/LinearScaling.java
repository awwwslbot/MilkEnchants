package com.windsor.meowsEnchants.actions;

/** {@code base + perLevel × (等级 − 1)}。 */
public record LinearScaling(double base, double perLevel) implements ScalingFunction {
    @Override
    public double getValue(int level) {
        return base + perLevel * (level - 1);
    }
}
