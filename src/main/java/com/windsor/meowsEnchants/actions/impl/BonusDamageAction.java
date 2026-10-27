package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BonusDamageAction implements Action {
    public static final Param<ScalingFunction> AMOUNT = Param.scalableNumber("amount", "额外伤害")
            .help("加到本次伤害上；multiplier 为 true 时改为乘以本次伤害").required().build();
    public static final Param<Boolean> MULTIPLIER = Param.bool("multiplier", "按倍率").def(false).build();
    public static final Param<Boolean> SNEAKING = Param.bool("sneaking", "需要潜行").def(false).build();
    public static final Param<Boolean> NIGHT_TIME = Param.bool("night_time", "仅夜晚")
            .help("世界时间在 night_start–night_end 之间").def(false).build();
    public static final Param<Integer> NIGHT_START = Param.integer("night_start", "夜晚开始")
            .help("一天内的时间（0–24000）").unit(Param.Unit.TICK).range(0, 24000).def(13000).build();
    public static final Param<Integer> NIGHT_END = Param.integer("night_end", "夜晚结束")
            .unit(Param.Unit.TICK).range(0, 24000).def(23000).build();
    public static final Param<Double> LOW_HEALTH = Param.number("low_health", "低血量阈值")
            .help("大于 0 时，自己生命值不高于此值才生效").min(0).def(0.0).build();
    public static final Param<String> DIMENSION = Param.string("dimension", "限定世界")
            .help("Bukkit 世界名（不分大小写），如 world_nether").build();
    public static final Param<Boolean> PASSIVE_MOBS = Param.bool("passive_mobs", "仅被动生物")
            .help("只对动物、环境生物、鱼、鱿鱼、雪傀儡、铁傀儡、海豚生效").def(false).build();
    public static final Param<List<String>> TARGET_TYPES = Param.registryList("target_types", "限定目标类型",
                    Param.Registry.ENTITY_TYPE)
            .help("实体类型的枚举名（大写，如 ZOMBIE）；不写不限").def(List.of()).build();
    public static final Param<Map<String, Object>> CONDITIONS = Param.section("conditions", "条件",
            SNEAKING, NIGHT_TIME, NIGHT_START, NIGHT_END, LOW_HEALTH, DIMENSION, PASSIVE_MOBS, TARGET_TYPES).build();
    public static final List<Param<?>> PARAMS = List.of(AMOUNT, MULTIPLIER, CONDITIONS);
    private final ScalingFunction amount;
    private final boolean multiplier;
    private final boolean sneaking;
    private final boolean nightTime;
    private final int nightStart;
    private final int nightEnd;
    private final double lowHealth;
    private final String dimension;
    private final Set<String> targetTypes;
    private final boolean passiveMobs;

    @SuppressWarnings("unchecked")
    public BonusDamageAction(Map<String, Object> params) {
        this.amount = AMOUNT.read(params);
        this.multiplier = MULTIPLIER.read(params);

        Map<String, Object> conditions = CONDITIONS.read(params);
        this.sneaking = SNEAKING.read(conditions);
        this.nightTime = NIGHT_TIME.read(conditions);
        this.nightStart = NIGHT_START.read(conditions);
        this.nightEnd = NIGHT_END.read(conditions);
        this.lowHealth = LOW_HEALTH.read(conditions);
        this.dimension = DIMENSION.read(conditions);
        this.passiveMobs = PASSIVE_MOBS.read(conditions);
        this.targetTypes = new HashSet<>(TARGET_TYPES.read(conditions));
    }

    @Override
    public boolean execute(ActionContext context) {
        // 必须为伤害事件
        if (!(context.getOriginalEvent() instanceof EntityDamageByEntityEvent event)) {
            return false;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return false;
        }

        // 如果事件已被其他插件取消，不执行伤害修改
        if (event.isCancelled()) {
            return false;
        }

        // ---- 条件检查 ----
        if (sneaking && !player.isSneaking()) {
            return false;
        }

        if (nightTime) {
            World world = player.getWorld();
            long time = world.getTime();
            // 夜晚时间：night_start ~ night_end（默认 13000 ~ 23000）
            if (time < nightStart || time > nightEnd) {
                return false;
            }
        }

        if (lowHealth > 0 && player.getHealth() > lowHealth) {
            return false;
        }

        if (dimension != null && !dimension.isEmpty()) {
            String worldName = player.getWorld().getName();
            if (!worldName.equalsIgnoreCase(dimension)) {
                return false;
            }
        }

        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity)) {
            return false;
        }

        // 目标类型过滤
        if (!targetTypes.isEmpty()) {
            String typeName = target.getType().name();
            if (!targetTypes.contains(typeName)) {
                return false;
            }
        }

        // 被动生物过滤
        if (passiveMobs && !isPassiveMob(target)) {
            return false;
        }

        // ---- 计算伤害 ----
        double baseDamage = event.getDamage();
        double amountValue = this.amount.getValue(context.getEnchantLevel());
        double finalDamage = multiplier ? baseDamage * amountValue : baseDamage + amountValue;
        if (finalDamage < 0) finalDamage = 0;
        event.setDamage(finalDamage);
        return true;
    }

    /**
     * 判断目标是否为被动生物（可扩展）。
     */
    private boolean isPassiveMob(Entity entity) {
        return entity instanceof Animals ||
                entity instanceof Ambient ||
                entity instanceof Fish ||
                entity instanceof Squid ||
                entity instanceof Snowman ||
                entity instanceof IronGolem ||
                entity instanceof Dolphin;
    }
}