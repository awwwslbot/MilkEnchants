package com.windsor.meowsEnchants.actions;

public record FixedScaling(double value) implements ScalingFunction {
    @Override
    public double getValue(int level) {
        return value;
    }
}
