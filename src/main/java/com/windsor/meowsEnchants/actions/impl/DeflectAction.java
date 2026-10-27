package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class DeflectAction implements Action {
    public static final Param<ScalingFunction> AMOUNT = Param.scalableNumber("amount", "偏转概率")
            .help("被弹射物击中时按此概率取消伤害并把弹射物随机弹开；不写时必定偏转（milk.12 行为）")
            .unit(Param.Unit.RATIO).range(0, 1).def(1.0).build();
    public static final Param<Double> YAW_SPREAD = Param.number("yaw_spread_degrees", "水平偏转范围")
            .help("弹开方向在反方向基础上水平随机旋转 0 到此角度").unit(Param.Unit.DEGREE).range(0, 360).def(360.0).build();
    public static final Param<Double> PITCH_SPREAD = Param.number("pitch_spread_degrees", "竖直偏转范围")
            .help("竖直方向随机旋转 ±此角度").unit(Param.Unit.DEGREE).range(0, 90).def(30.0).build();
    public static final List<Param<?>> PARAMS = List.of(AMOUNT, YAW_SPREAD, PITCH_SPREAD);
    private final Random random = new Random();
    private final ScalingFunction amount;
    private final double yawSpread;
    private final double pitchSpread;

    public DeflectAction(Map<String, Object> params) {
        this.amount = AMOUNT.read(params);
        this.yawSpread = Math.toRadians(YAW_SPREAD.read(params));
        this.pitchSpread = Math.toRadians(PITCH_SPREAD.read(params));
    }

    @Override
    public boolean execute(ActionContext context) {
        if (!(context.getOriginalEvent() instanceof EntityDamageByEntityEvent event)) {
            return false;
        }

        // 如果伤害已被其他插件取消，不执行反弹
        if (event.isCancelled()) {
            return false;
        }

        Entity damager = event.getDamager();
        if (!(damager instanceof Projectile projectile)) {
            return false;
        }

        if (!(event.getEntity() instanceof Player)) {
            return false;
        }

        // 概率没中：视为失败，不取消伤害、不进冷却
        double chance = amount.getValue(context.getEnchantLevel());
        if (chance <= 0 || random.nextDouble() >= chance) {
            return false;
        }

        // 取消伤害
        event.setCancelled(true);

        // 获取当前速度
        Vector velocity = projectile.getVelocity();
        if (velocity.lengthSquared() == 0) {
            return false;
        }

        // 反向（大致方向）
        Vector reversed = velocity.clone().multiply(-1);

        // 随机偏转：水平方向随机旋转 0~yaw_spread，垂直方向随机旋转 ±pitch_spread（默认 360° / ±30°）
        double yaw = random.nextDouble() * yawSpread;
        double pitch = (random.nextDouble() - 0.5) * 2 * pitchSpread;

        // 应用旋转
        Vector deflected = reversed.clone();
        deflected.rotateAroundY(yaw);
        // 构造水平旋转轴（垂直于当前方向）
        Vector axis = deflected.clone().crossProduct(new Vector(0, 1, 0));
        if (axis.lengthSquared() < 1e-10) {
            axis = deflected.clone().crossProduct(new Vector(1, 0, 0));
        }
        axis.normalize();
        deflected.rotateAroundAxis(axis, pitch);

        // 保持原速度大小
        deflected.normalize().multiply(velocity.length());
        projectile.setVelocity(deflected);
        return true;
    }
}