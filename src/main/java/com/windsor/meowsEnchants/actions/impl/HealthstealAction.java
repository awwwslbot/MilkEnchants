package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;
import java.util.Map;

/**
 * 生命偷取动作。
 * 根据对目标造成的伤害，按比例恢复玩家生命值。
 * 偷取比例限制在 0 ~ 1.0 之间（即不超过 100%）。
 * 注意：此动作不修改原始伤害值。
 */
public class HealthstealAction implements Action {
    public static final Param<ScalingFunction> AMOUNT = Param.scalableNumber("amount", "偷取比例")
            .help("按本次伤害（减伤前）的比例治疗自己").unit(Param.Unit.RATIO).range(0, 1).required().build();
    public static final Param<Double> MAX_FRACTION = Param.number("max_fraction", "比例上限")
            .help("amount 超过它时按它计算").unit(Param.Unit.RATIO).min(0).def(1.0).build();
    public static final List<Param<?>> PARAMS = List.of(AMOUNT, MAX_FRACTION);
    private final ScalingFunction amount;
    private final double maxFraction;

    public HealthstealAction(Map<String, Object> params) {
        this.amount = AMOUNT.read(params);
        this.maxFraction = MAX_FRACTION.read(params);
    }

    @Override
    public boolean execute(ActionContext context) {
        // 必须为伤害事件
        if (!(context.getOriginalEvent() instanceof EntityDamageByEntityEvent event)) {
            return false;
        }

        // 如果事件已被取消，不执行偷取
        if (event.isCancelled()) {
            return false;
        }

        Player player = context.getPlayer();
        if (player == null) return false;

        // 获取目标实体
        if (!(event.getEntity() instanceof LivingEntity)) {
            return false;
        }

        // 计算偷取比例，限制在 0 ~ 1.0
        double stealFraction = amount.getValue(context.getEnchantLevel());
        if (stealFraction <= 0) return false;
        if (stealFraction > maxFraction) stealFraction = maxFraction; // 强制上限

        // 当前伤害值（原始伤害，未经修改）
        double currentDamage = event.getDamage();
        if (currentDamage <= 0) return false;

        // 偷取量 = 伤害 * 比例
        double stealAmount = currentDamage * stealFraction;

        // 治疗玩家（不修改伤害事件）
        player.heal(stealAmount);
        return true;
    }
}