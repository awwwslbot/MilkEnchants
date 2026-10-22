package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.listeners.AutoBowManager;
import com.windsor.meowsEnchants.listeners.EnchantTriggerHelper;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 拉弓时启动逐 tick 监控：每 tick 读取玩家的实际蓄力进度并计算弓的 power，
 * 只有 power 达到满值（原版 20 tick）后才自动发射，并可选自动开始下一轮蓄力。
 * <p>
 * 本动作不改变蓄力速度，也不会缩短/加快拉弓；它只负责“拉满后自动松手”。
 * 发射调用 NMS 的 {@code LivingEntity#releaseUsingItem()}，保留原版箭矢消耗、
 * Infinity、Power/Punch/Flame 附魔以及 {@code EntityShootBowEvent}。
 */
public class AutoBowAction implements Action {

    private static final int VANILLA_FULL_DRAW_TICKS = 20;

    private final boolean refire;
    private final int refireDelay;
    private final int maxShots;

    public AutoBowAction(Map<String, Object> params) {
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

        AutoBowManager.start(player, context.getPlugin(), enchantKey, this);
        return true;
    }

    /** 原版弓的 power 曲线，ticks 为实际蓄力 tick。 */
    public static double powerForTicks(int ticks) {
        double t = ticks / (double) VANILLA_FULL_DRAW_TICKS;
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
