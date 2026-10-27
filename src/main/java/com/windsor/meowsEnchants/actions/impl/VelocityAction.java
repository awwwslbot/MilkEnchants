package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.MeowsEnchants;
import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.utils.SchedulerCompat;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityAction implements Action {
    public static final Param<String> DIRECTION = Param.enumeration("direction", "方向",
                    List.of("UP", "DOWN", "FORWARD", "LOOK", "BACKWARD", "AWAY"))
            .help("UP/DOWN 绝对竖直；FORWARD/LOOK 视线方向；BACKWARD 视线反方向；AWAY 水平远离目标").required().build();
    public static final Param<ScalingFunction> POWER = Param.scalableNumber("power", "力度")
            .help("速度大小；竖直分量会再乘 0.25").min(0).required().build();
    public static final Param<String> APPLY_TO = Param.enumeration("apply_to", "作用于", List.of("SELF", "TARGET"))
            .help("SELF 自己；其他任何值按 TARGET（事件目标）处理").def("SELF").build();
    public static final Param<Boolean> NO_FALL_DAMAGE = Param.bool("no_fall_damage", "免摔落伤害")
            .help("作用于玩家时，5 秒内的下一次摔落伤害被取消").def(false).build();
    public static final Param<Boolean> ON_GROUND = Param.bool("on_ground", "需要接近地面")
            .help("true 时触发者必须站在地上或离支撑方块不超过 ground_distance 格（SELF 和 TARGET 都检查触发者）；旧名 onGround，两个都写时以 onGround 为准")
            .alias("onGround").def(true).build();
    public static final Param<Double> VERTICAL_SCALE = Param.number("vertical_scale", "竖直分量倍率")
            .help("算出的速度竖直分量再乘它（所以 UP 方向 power 5 实际向上 1.25）").min(0).def(0.25).build();
    public static final Param<Integer> GROUND_DISTANCE = Param.integer("ground_distance", "离地判定")
            .help("on_ground 开启时，脚下多少格内有支撑方块就算接近地面").unit(Param.Unit.BLOCK).min(0).def(1).build();
    public static final Param<Integer> NO_FALL_DAMAGE_TICKS = Param.integer("no_fall_damage_ticks", "免摔时长")
            .help("no_fall_damage 开启时保护持续的 tick").unit(Param.Unit.TICK).min(1).def(100).build();
    public static final List<Param<?>> PARAMS = List.of(DIRECTION, POWER, APPLY_TO, NO_FALL_DAMAGE, ON_GROUND,
            VERTICAL_SCALE, GROUND_DISTANCE, NO_FALL_DAMAGE_TICKS);
    private final String direction;
    private final ScalingFunction power;
    private final String applyTo;
    private final boolean noFallDamage;
    private final boolean onGround;
    private final double verticalScale;
    private final int groundDistance;
    private final int noFallDamageTicks;

    private static final ConcurrentHashMap<UUID, Long> fallDamageProtection = new ConcurrentHashMap<>();

    public VelocityAction(Map<String, Object> params) {
        this.direction = DIRECTION.read(params).toUpperCase();
        this.power = POWER.read(params);
        this.applyTo = APPLY_TO.read(params).toUpperCase();
        this.noFallDamage = NO_FALL_DAMAGE.read(params);
        this.onGround = ON_GROUND.read(params);
        this.verticalScale = VERTICAL_SCALE.read(params);
        this.groundDistance = GROUND_DISTANCE.read(params);
        this.noFallDamageTicks = Math.max(1, NO_FALL_DAMAGE_TICKS.read(params));
    }

    @Override
    public boolean execute(ActionContext context) {
        try {
            Player player = context.getPlayer();
            if (player == null) return false;
            if (onGround && !isWithinGroundDistance(player)) return false;

            // 确定应用目标
            List<Entity> targets = context.getTargets();
            Entity targetEntity = (targets != null && !targets.isEmpty()) ? targets.getFirst() : null;

            Entity applyEntity;
            if (applyTo.equalsIgnoreCase("SELF")) {
                applyEntity = player;
            } else { // TARGET
                if (targetEntity == null) return false;
                applyEntity = targetEntity;
            }

            if (!(applyEntity instanceof LivingEntity)) return false;

            // 计算速度向量
            Vector velocity = calculateVelocity(player, targetEntity, applyEntity, context.getEnchantLevel());
            if (velocity.lengthSquared() == 0) return false;
            velocity.setY(velocity.getY() * verticalScale);

            // 应用速度
            applyEntity.setVelocity(velocity);

            // 如果需要取消摔落伤害，且应用目标是玩家
            if (noFallDamage && applyEntity instanceof Player targetPlayer) {
                UUID uuid = targetPlayer.getUniqueId();
                fallDamageProtection.put(uuid, System.currentTimeMillis() + noFallDamageTicks * 50L);
                SchedulerCompat.runDelayed(targetPlayer, MeowsEnchants.getInstance(),
                        _ -> fallDamageProtection.remove(uuid),
                        noFallDamageTicks);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private Vector calculateVelocity(Player player, Entity target, Entity applyEntity, int level) {
        double powerValue = this.power.getValue(level);
        if (powerValue <= 0) return new Vector(0, 0, 0);

        Vector directionVector;
        switch (direction) {
            case "UP":
                directionVector = new Vector(0, 1, 0);
                break;
            case "DOWN":
                directionVector = new Vector(0, -1, 0);
                break;
            case "FORWARD":
            case "LOOK": {
                Location eyeLoc = player.getEyeLocation();
                directionVector = eyeLoc.getDirection().clone();
                if (directionVector.lengthSquared() > 0) {
                    directionVector.normalize();
                } else {
                    directionVector = new Vector(0, 0, 1);
                }
                break;
            }
            case "BACKWARD": {
                Location eyeLoc = player.getEyeLocation();
                directionVector = eyeLoc.getDirection().clone().multiply(-1);
                if (directionVector.lengthSquared() > 0) {
                    directionVector.normalize();
                } else {
                    directionVector = new Vector(0, 0, -1);
                }
                break;
            }
            case "AWAY": {
                if (target == null) return new Vector(0, 0, 0);
                Vector fromTarget = applyEntity.getLocation().toVector().subtract(target.getLocation().toVector());
                fromTarget.setY(0);
                if (fromTarget.lengthSquared() > 0) {
                    fromTarget.normalize();
                } else {
                    return new Vector(0, 0, 0);
                }
                directionVector = fromTarget;
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported direction: " + direction);
        }

        return directionVector.multiply(powerValue);
    }

    private boolean isWithinGroundDistance(Player player) {
        if (player.isOnGround()) return true;

        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) return false;

        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();
        int feetBlockY = location.getBlockY();
        for (int distance = 1; distance <= groundDistance; distance++) {
            int supportY = feetBlockY - distance;
            if (supportY < world.getMinHeight()) return false;
            if (!world.getBlockAt(blockX, supportY, blockZ).isPassable()) return true;
        }
        return false;
    }

    public static boolean shouldCancelFallDamage(Player player) {
        UUID uuid = player.getUniqueId();
        Long expiry = fallDamageProtection.get(uuid);
        if (expiry != null && System.currentTimeMillis() < expiry) {
            fallDamageProtection.remove(uuid);
            return true;
        } else {
            fallDamageProtection.remove(uuid);
            return false;
        }
    }
}
