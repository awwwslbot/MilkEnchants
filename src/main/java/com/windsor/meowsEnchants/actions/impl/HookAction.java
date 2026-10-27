package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

public class HookAction implements Action {
    public static final Param<ScalingFunction> POWER = Param.scalableNumber("power", "拉拽力度")
            .help("把目标以 45° 仰角拉向自己的速度；5 格内按距离衰减").min(0).required().build();
    public static final Param<Double> MIN_DISTANCE = Param.number("min_distance", "最近距离")
            .help("目标离自己不超过它时不拉").unit(Param.Unit.BLOCK).min(0).def(1.0).build();
    public static final Param<Double> FULL_POWER_DISTANCE = Param.number("full_power_distance", "满力度距离")
            .help("不近于它时用满力度；更近时力度 = power ÷ (满力度距离 − 距离)²").unit(Param.Unit.BLOCK).min(0).def(5.0).build();
    public static final Param<Double> LAUNCH_ANGLE = Param.number("launch_angle_degrees", "拉拽仰角")
            .unit(Param.Unit.DEGREE).range(0, 90).def(45.0).build();
    public static final List<Param<?>> PARAMS = List.of(POWER, MIN_DISTANCE, FULL_POWER_DISTANCE, LAUNCH_ANGLE);
    private final ScalingFunction power;
    private final double minDistance;
    private final double fullPowerDistance;
    private final double launchAngle;

    public HookAction(Map<String, Object> params) {
        this.power = POWER.read(params);
        this.minDistance = MIN_DISTANCE.read(params);
        this.fullPowerDistance = FULL_POWER_DISTANCE.read(params);
        this.launchAngle = Math.toRadians(LAUNCH_ANGLE.read(params));
    }

    @Override
    public boolean execute(ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        List<Entity> targets = context.getTargets();
        if (targets == null || targets.isEmpty()) return false;

        Entity target = targets.getFirst();
        if (!(target instanceof LivingEntity)) return false;

        // 计算距离（3D距离）
        double distance = player.getLocation().distance(target.getLocation());
        if (distance <= minDistance) return false; // 太近不拉

        double powerValue = this.power.getValue(context.getEnchantLevel());
        if (powerValue <= 0) return false;

        // 根据距离计算动量大小
        double magnitude;
        if (distance >= fullPowerDistance) {
            magnitude = powerValue;
        } else {
            magnitude = powerValue / Math.pow(fullPowerDistance - distance, 2);
        }

        // 计算水平方向（从目标指向玩家，忽略Y）
        Vector horizontal = player.getLocation().toVector().subtract(target.getLocation().toVector());
        horizontal.setY(0);
        if (horizontal.lengthSquared() < 0.0001) {
            return false; // 水平方向无法确定，不施加动量
        }
        horizontal.normalize();

        // 俯仰角与水平面夹角 launch_angle_degrees（默认 45 度）
        Vector direction = horizontal.clone().multiply(Math.cos(launchAngle))
                .add(new Vector(0, 1, 0).multiply(Math.sin(launchAngle)));
        direction.normalize();

        Vector velocity = direction.multiply(magnitude);
        target.setVelocity(velocity);
        return true;
    }
}