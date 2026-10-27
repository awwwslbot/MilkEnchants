package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class ReflectDamageAction implements Action {
    public static final Param<ScalingFunction> PERCENTAGE = Param.scalableNumber("percentage", "反弹概率")
            .help("受到近战伤害时按此概率取消伤害，并对攻击者造成同等伤害").unit(Param.Unit.RATIO).range(0, 1)
            .required().build();
    public static final Param<Double> DAMAGE_RATIO = Param.number("damage_ratio", "反弹伤害比例")
            .help("对攻击者造成的伤害 = 本次伤害 × 比例").unit(Param.Unit.RATIO).min(0).def(1.0).build();
    public static final List<Param<?>> PARAMS = List.of(PERCENTAGE, DAMAGE_RATIO);
    private final ScalingFunction percentage;
    private final double damageRatio;
    private final Random random = new Random();

    public ReflectDamageAction(Map<String, Object> params) {
        this.percentage = PERCENTAGE.read(params);
        this.damageRatio = DAMAGE_RATIO.read(params);
    }

    @Override
    public boolean execute(ActionContext context) {
        // 必须为实体伤害事件
        if (!(context.getOriginalEvent() instanceof EntityDamageByEntityEvent event)) {
            return false;
        }

        // 如果事件已被其他插件取消，不执行反弹
        if (event.isCancelled()) {
            return false;
        }

        // 只处理近战伤害（排除投射物）
        Entity damager = event.getDamager();
        if (damager instanceof Projectile) {
            return false; // 投射物伤害不反弹
        }
        if (!(damager instanceof LivingEntity)) {
            return false; // 非生物攻击不反弹（如环境伤害）
        }

        // 受害者为玩家（由触发器保证，但二次检查）
        if (!(event.getEntity() instanceof Player player)) {
            return false;
        }

        // 概率判定
        double chance = this.percentage.getValue(context.getEnchantLevel());
        if (chance <= 0 || random.nextDouble() >= chance) {
            return false; // 未触发反弹
        }

        // 取消原伤害
        event.setCancelled(true);

        // 对攻击者造成同等伤害（取消后原伤害值仍可用）
        double damage = event.getDamage() * damageRatio;
        if (damage > 0) {
            ((LivingEntity) damager).damage(damage, player);
        }
        return true;
    }
}