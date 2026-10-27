package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class FeastAction implements Action {
    public static final Param<ScalingFunction> AMOUNT = Param.scalableNumber("amount", "触发概率")
            .help("饥饿值未满时按此概率恢复饥饿值和饱和度").unit(Param.Unit.RATIO).range(0, 1).required().build();
    public static final Param<Integer> FOOD = Param.integer("food", "恢复饥饿值").min(0).max(20).def(1).build();
    public static final Param<Double> SATURATION = Param.number("saturation", "恢复饱和度")
            .help("饱和度不超过恢复后的饥饿值").min(0).def(1.0).build();
    public static final List<Param<?>> PARAMS = List.of(AMOUNT, FOOD, SATURATION);
    private final ScalingFunction amount;
    private final int food;
    private final double saturation;
    private final Random random = new Random();

    public FeastAction(Map<String, Object> params) {
        this.amount = AMOUNT.read(params);
        this.food = FOOD.read(params);
        this.saturation = SATURATION.read(params);
    }

    @Override
    public boolean execute(ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        // 检查饥饿度是否已满
        int foodLevel = player.getFoodLevel();
        if (foodLevel >= 20) {
            return false; // 已满，无需恢复
        }

        // 概率判定
        double chance = this.amount.getValue(context.getEnchantLevel());
        if (chance <= 0 || random.nextDouble() >= chance) {
            return false;
        }

        // 恢复饥饿值（上限 20）
        int newFood = Math.min(20, foodLevel + food);
        player.setFoodLevel(newFood);

        // 恢复饱和度，但不超过当前饥饿值
        float current = player.getSaturation();
        float newSaturation = Math.min(newFood, current + (float) saturation);
        player.setSaturation(newSaturation);
        return true;
    }
}