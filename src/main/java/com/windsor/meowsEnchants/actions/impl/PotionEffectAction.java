package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.utils.SchedulerCompat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;

public class PotionEffectAction implements Action {
    public static final Param<String> EFFECT = Param.registry("effect", "药水效果", Param.Registry.MOB_EFFECT)
            .help("药水效果名，如 poison、strength（不分大小写）").required().build();
    public static final Param<ScalingFunction> DURATION = Param.scalableInt("duration", "持续时间")
            .help("单位 tick，20 tick = 1 秒；取值后截断为整数；-1 = 无限").unit(Param.Unit.TICK).min(-1).required().build();
    public static final Param<ScalingFunction> AMPLIFIER = Param.scalableInt("amplifier", "效果等级")
            .help("0 = I 级，1 = II 级；取值后截断为整数").unit(Param.Unit.LEVEL).range(0, 255).required().build();
    public static final Param<Boolean> PARTICLES = Param.bool("particles", "粒子（非环境效果）")
            .help("false 时效果按环境效果（ambient）施加，粒子变淡变少；要完全隐藏粒子用 icon: false").def(true).build();
    public static final Param<Boolean> ICON = Param.bool("icon", "粒子与图标")
            .help("false 时既不显示粒子，也不显示 HUD 图标（Bukkit 五参构造的 particles 位）").def(true).build();
    public static final List<Param<?>> PARAMS = List.of(EFFECT, DURATION, AMPLIFIER, PARTICLES, ICON);

    private final PotionEffectType effectType;
    private final ScalingFunction duration;
    private final ScalingFunction amplifier;
    private final boolean particles;
    private final boolean icon;

    public PotionEffectAction(Map<String, Object> params) {
        String effectName = EFFECT.read(params);
        this.effectType = PotionEffectType.getByName(effectName.toUpperCase());
        if (this.effectType == null) {
            throw new IllegalArgumentException("Unknown potion effect: " + effectName);
        }
        this.duration = DURATION.read(params);
        this.amplifier = AMPLIFIER.read(params);
        this.particles = PARTICLES.read(params);
        this.icon = ICON.read(params);
    }

    @Override
    public boolean execute(ActionContext context) {
        int level = context.getEnchantLevel();
        int durationTicks = (int) duration.getValue(level);
        int amplifierValue = (int) amplifier.getValue(level);

        PotionEffect effect = new PotionEffect(effectType, durationTicks, amplifierValue,
                !particles, icon);

        for (Entity target : context.getTargets()) {
            if (!(target instanceof LivingEntity living)) continue;
            // 使用实体调度器确保在正确的线程上执行
            SchedulerCompat.run(living, context.getPlugin(),
                    scheduledTask -> living.addPotionEffect(effect));
        }
        return true;
    }
}
