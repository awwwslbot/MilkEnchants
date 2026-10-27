package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.utils.SkylliaCompatibility;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ShockwaveAction implements Action {
    public static final Param<ScalingFunction> RADIUS = Param.scalableNumber("radius", "半径")
            .help("对半径内所有生物生效（不看 target）").unit(Param.Unit.BLOCK).min(0).warnAbove(10).required().build();
    public static final Param<ScalingFunction> POWER = Param.scalableNumber("power", "冲击力度")
            .help("1 格内的冲击速度，更远处按距离衰减").min(0).required().build();
    public static final Param<Integer> MAX_ENTITIES = Param.integer("max_entities", "最多影响实体")
            .help("只取离自己最近的这么多个生物").unit(Param.Unit.COUNT).min(1).def(64).build();
    public static final Param<Double> MAX_POWER = Param.number("max_power", "冲击力度上限").min(0).def(16.0).build();
    public static final Param<Double> KNOCKBACK_RESISTANCE_REDUCTION = Param.number("knockback_resistance_reduction",
                    "击退抗性最大减免")
            .help("目标击退抗性为 1 时冲击减少的比例，按抗性线性").unit(Param.Unit.RATIO).range(0, 1).def(0.5).build();
    public static final Param<Double> FALLOFF_START = Param.number("falloff_start", "衰减起点")
            .help("距离不超过它时满力度；更远处力度 = 力度 ÷ (距离 − 起点)^指数").unit(Param.Unit.BLOCK).min(0).def(1.0).build();
    public static final Param<Double> FALLOFF_EXPONENT = Param.number("falloff_exponent", "衰减指数").min(0).def(2.0).build();
    public static final Param<Double> LAUNCH_ANGLE = Param.number("launch_angle_degrees", "抛射仰角")
            .unit(Param.Unit.DEGREE).range(0, 90).def(45.0).build();
    public static final List<Param<?>> PARAMS = List.of(RADIUS, POWER, MAX_ENTITIES, MAX_POWER,
            KNOCKBACK_RESISTANCE_REDUCTION, FALLOFF_START, FALLOFF_EXPONENT, LAUNCH_ANGLE);
    private final ScalingFunction radius;
    private final ScalingFunction power;

    // 限制最大影响实体数量，防止性能崩溃
    private final int maxEntities;
    // 限制最大动量，避免过大的碰撞计算
    private final double maxMagnitude;
    // 击退抗性为 1 时最多减免的比例
    private final double maxKnockbackReduction;
    private final double falloffStart;
    private final double falloffExponent;
    private final double launchAngle;

    public ShockwaveAction(Map<String, Object> params) {
        this.radius = RADIUS.read(params);
        this.power = POWER.read(params);
        this.maxEntities = Math.max(1, MAX_ENTITIES.read(params));
        this.maxMagnitude = MAX_POWER.read(params);
        this.maxKnockbackReduction = KNOCKBACK_RESISTANCE_REDUCTION.read(params);
        this.falloffStart = FALLOFF_START.read(params);
        this.falloffExponent = FALLOFF_EXPONENT.read(params);
        this.launchAngle = Math.toRadians(LAUNCH_ANGLE.read(params));
    }

    @Override
    public boolean execute(ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        // 二重保险：检查玩家是否在岛屿上
        if (!SkylliaCompatibility.canUseIslandRestrictedAction(player)) {
            return false;
        }

        World world = player.getWorld();
        Location playerLoc = player.getLocation();

        double rad = this.radius.getValue(context.getEnchantLevel());
        double pow = this.power.getValue(context.getEnchantLevel());
        if (rad <= 0 || pow <= 0) return false;

        // 获取半径内的所有实体，并按距离排序，只取最近的 MAX_ENTITIES 个
        Collection<Entity> nearby = world.getNearbyEntities(playerLoc, rad, rad, rad);
        List<Entity> sortedEntities = nearby.stream()
                .filter(entity -> !entity.equals(player) && entity instanceof LivingEntity)
                .sorted(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(playerLoc)))
                .limit(maxEntities)
                .toList();

        // 限制最大动量
        double maxPower = Math.min(pow, maxMagnitude);

        for (Entity entity : sortedEntities) {
            LivingEntity livingEntity = (LivingEntity) entity;
            Location entityLoc = entity.getLocation();
            double distance = playerLoc.distance(entityLoc);
            if (distance > rad) continue;

            double magnitude;
            if (distance <= falloffStart) {
                magnitude = maxPower;
            } else {
                double denominator = Math.pow(distance - falloffStart, falloffExponent);
                if (denominator == 0) continue;
                magnitude = maxPower / denominator;
            }
            // 二次限制，防止个别极端值
            magnitude = Math.min(magnitude, maxMagnitude);
            magnitude *= getKnockbackMultiplier(livingEntity);

            Vector horizontal = entityLoc.toVector().subtract(playerLoc.toVector());
            horizontal.setY(0);
            if (horizontal.lengthSquared() == 0) continue;
            horizontal.normalize();

            Vector direction = horizontal.clone().multiply(Math.cos(launchAngle))
                    .add(new Vector(0, 1, 0).multiply(Math.sin(launchAngle)));
            direction.normalize();

            Vector velocity = direction.multiply(magnitude);
            entity.setVelocity(velocity);
        }
        return true;
    }

    private double getKnockbackMultiplier(LivingEntity entity) {
        AttributeInstance resistanceAttribute = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (resistanceAttribute == null) {
            return 1.0;
        }

        double resistance = Math.clamp(resistanceAttribute.getValue(), 0.0, 1.0);
        return 1.0 - resistance * maxKnockbackReduction;
    }

}
