package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.listeners.ProjectileListener;
import com.windsor.meowsEnchants.utils.SkylliaCompatibility;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileHitEvent;

import java.util.List;
import java.util.Map;

public class ExplosionAction implements Action {
    public static final Param<ScalingFunction> POWER = Param.scalableNumber("power", "爆炸威力")
            .help("原版爆炸威力（TNT 为 4，闪电苦力怕为 6）；弹射物命中触发时在命中点爆炸").min(0).warnAbove(6).required().build();
    public static final Param<Boolean> FIRE = Param.bool("fire", "引燃").def(false).build();
    public static final Param<Boolean> BREAK_BLOCKS = Param.bool("break_blocks", "破坏方块").def(false).build();
    public static final List<Param<?>> PARAMS = List.of(POWER, FIRE, BREAK_BLOCKS);
    private final ScalingFunction power;
    private final boolean fire;
    private final boolean breakBlocks;

    public ExplosionAction(Map<String, Object> params) {
        this.power = POWER.read(params);
        this.fire = FIRE.read(params);
        this.breakBlocks = BREAK_BLOCKS.read(params);
    }

    @Override
    public boolean execute(ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        // 如果是 PROJECTILE_HIT 触发，检查投射物是否由该附魔射出
        if (context.getOriginalEvent() instanceof ProjectileHitEvent projectileEvent) {
            Projectile projectile = projectileEvent.getEntity();
            if (!ProjectileListener.isExplosionProjectile(projectile)) {
                return false; // 不是由爆炸附魔射出的投射物，不触发
            }
        }

        // 二重保险（PlaceholderAPI 检查）
        if (!SkylliaCompatibility.canUseIslandRestrictedAction(player)) {
            return false;
        }

        List<Entity> targets = context.getTargets();
        Location loc = null;

        if (context.getOriginalEvent() instanceof ProjectileHitEvent projectileEvent) {
            // 命中方块时，获取方块位置（方块中心 + 0.5）
            if (projectileEvent.getHitBlock() != null) {
                loc = projectileEvent.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
            } else if (projectileEvent.getHitEntity() != null) {
                loc = projectileEvent.getHitEntity().getLocation();
            }
        }else if (targets != null && !targets.isEmpty()) {
            loc = targets.getFirst().getLocation();
        }

        World world = null;
        if (loc != null) {
            world = loc.getWorld();
        }
        if (world == null) return false;

        float powerValue = (float) this.power.getValue(context.getEnchantLevel());
        if (powerValue <= 0) return false;

        // 在 Folia 中，World.createExplosion 在区域线程中调用是安全的
        world.createExplosion(loc, powerValue, fire, breakBlocks);
        return true;
    }

}
