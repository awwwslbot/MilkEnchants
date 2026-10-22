package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.FixedScaling;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.actions.ScalingFunctionLoader;
import com.windsor.meowsEnchants.listeners.DrawAccelerateManager;
import com.windsor.meowsEnchants.listeners.EnchantTriggerHelper;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 拉弓时每 tick 额外推进蓄力进度，让弓更早达到原版 power 满值。
 * <p>
 * 不改变客户端的拉弓动画，只影响服务端的 {@code useItemRemaining}：
 * 因此配合 {@code auto_bow} 时会更早判定满蓄力；单独使用时，
 * 玩家提前松手也能按加速后的进度计算箭速。
 */
public class DrawAccelerateAction implements Action {

    private final ScalingFunction extraTicks;

    public DrawAccelerateAction(Map<String, Object> params) {
        Object raw = params.get("extra_ticks");
        this.extraTicks = raw == null ? new FixedScaling(1.0) : ScalingFunctionLoader.load(raw);
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

        double extra = extraTicks.getValue(context.getEnchantLevel());
        if (extra <= 0) return false;

        DrawAccelerateManager.start(player, context.getPlugin(), enchantKey, extra);
        return true;
    }

    private static boolean isEnchantedBow(ItemStack item, Key enchantKey) {
        return item != null
                && item.getType() == Material.BOW
                && EnchantTriggerHelper.getCustomEnchantsOnItem(item).contains(enchantKey);
    }
}
