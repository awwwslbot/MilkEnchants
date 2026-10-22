package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.FixedScaling;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.actions.ScalingFunctionLoader;
import com.windsor.meowsEnchants.listeners.AutoBowManager;
import com.windsor.meowsEnchants.listeners.EnchantTriggerHelper;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 拉弓时启动逐 tick 监控：每 tick 根据玩家的实际蓄力进度计算弓的 power，
 * 当 power 达到满值后自动发射，并可自动开始下一轮蓄力。
 * <p>
 * {@code draw_speed} 是蓄力速度倍率：原版 20 tick 拉满，倍率 2.0 即 10 tick 拉满。
 * 发射走 {@link Player#completeUsingActiveItem()}，保留原版箭矢消耗、Infinity、
 * Power/Punch/Flame 附魔以及 {@code EntityShootBowEvent}。
 */
public class AutoBowAction implements Action {

    private static final int VANILLA_FULL_DRAW_TICKS = 20;

    private final ScalingFunction drawSpeed;
    private final boolean refire;
    private final int refireDelay;
    private final int maxShots;

    public AutoBowAction(Map<String, Object> params) {
        Object drawSpeedObj = params.get("draw_speed");
        this.drawSpeed = drawSpeedObj == null
                ? new FixedScaling(1.0)
                : ScalingFunctionLoader.load(drawSpeedObj);

        this.refire = !Boolean.FALSE.equals(params.get("refire"));
        this.refireDelay = Math.max(0, getInt(params.get("refire_delay"), 0));
        this.maxShots = Math.max(0, getInt(params.get("max_shots"), 0));
    }

    @Override
    public boolean execute(ActionContext context) {
        if (!(context.getOriginalEvent() instanceof PlayerInteractEvent event)) {
            return false;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return false;
        }

        Player player = context.getPlayer();
        if (player == null) return false;

        Key enchantKey = context.getEnchantKey();
        if (enchantKey == null) return false;

        if (!isEnchantedBow(player.getInventory().getItemInMainHand(), enchantKey)
                && !isEnchantedBow(player.getInventory().getItemInOffHand(), enchantKey)) {
            return false;
        }

        double speed = Math.max(0.01, drawSpeed.getValue(context.getEnchantLevel()));
        AutoBowManager.start(player, context.getPlugin(), enchantKey, this, speed);
        return true;
    }

    /** 原版弓的 power 曲线，ticks 为有效蓄力 tick。 */
    public static double powerForTicks(double ticks) {
        double t = ticks / VANILLA_FULL_DRAW_TICKS;
        double power = (t * t + 2.0 * t) / 3.0;
        return Math.min(1.0, Math.max(0.0, power));
    }

    private static boolean isEnchantedBow(ItemStack item, Key enchantKey) {
        return item != null
                && item.getType() == Material.BOW
                && EnchantTriggerHelper.getCustomEnchantsOnItem(item).contains(enchantKey);
    }

    private static int getInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public boolean isRefire() {
        return refire;
    }

    public int getRefireDelay() {
        return refireDelay;
    }

    public int getMaxShots() {
        return maxShots;
    }
}
