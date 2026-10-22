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
 * 拉弓时启动一个逐 tick 的监控：达到配置的蓄力 tick 后自动发射，
 * 并可自动开始下一轮蓄力。
 * <p>
 * 发射走 {@link Player#completeUsingActiveItem()}，因此会保留原版的箭矢消耗、
 * Infinity、Power/Punch/Flame 附魔以及 {@code EntityShootBowEvent}。
 * 当 {@code draw_ticks < 20} 且 {@code full_power: true} 时，会把剩余使用时间
 * 改成“已拉满 20 tick”，让原版按满蓄力计算箭速。
 */
public class AutoBowAction implements Action {

    private final ScalingFunction drawTicks;
    private final boolean fullPower;
    private final boolean refire;
    private final int refireDelay;
    private final int maxShots;

    public AutoBowAction(Map<String, Object> params) {
        Object drawTicksObj = params.get("draw_ticks");
        this.drawTicks = drawTicksObj == null
                ? new FixedScaling(20)
                : ScalingFunctionLoader.load(drawTicksObj);

        this.fullPower = !Boolean.FALSE.equals(params.get("full_power"));
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

        int ticks = Math.max(1, (int) drawTicks.getValue(context.getEnchantLevel()));
        AutoBowManager.start(player, context.getPlugin(), enchantKey, this, ticks);
        return true;
    }

    private static boolean isEnchantedBow(ItemStack item, Key enchantKey) {
        return item != null
                && item.getType() == Material.BOW
                && EnchantTriggerHelper.getCustomEnchantsOnItem(item).contains(enchantKey);
    }

    private static int getInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public boolean isFullPower() {
        return fullPower;
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
