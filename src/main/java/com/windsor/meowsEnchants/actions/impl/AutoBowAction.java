package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.listeners.AutoBowManager;
import com.windsor.meowsEnchants.listeners.EnchantTriggerHelper;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
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
    public static final Param<Boolean> REFIRE = Param.bool("refire", "自动续射")
            .help("发射后自动重新开始拉弓").def(true).build();
    public static final Param<Integer> REFIRE_DELAY = Param.integer("refire_delay", "续射间隔")
            .help("两次发射之间等待的 tick").unit(Param.Unit.TICK).min(0).def(0).build();
    public static final Param<Integer> MAX_SHOTS = Param.integer("max_shots", "最多连射次数")
            .help("0 = 不限，直到松开右键").unit(Param.Unit.COUNT).min(0).def(0).build();
    public static final Param<Integer> FULL_DRAW_TICKS = Param.integer("full_draw_ticks", "满蓄力判定")
            .help("实际蓄力达到这么多 tick 才自动发射（原版弓 20 tick 满弓；更小会提前发射，箭速按实际蓄力算）")
            .unit(Param.Unit.TICK).min(1).def(20).build();
    public static final List<Param<?>> PARAMS = List.of(REFIRE, REFIRE_DELAY, MAX_SHOTS, FULL_DRAW_TICKS);

    private final boolean refire;
    private final int refireDelay;
    private final int maxShots;
    private final int fullDrawTicks;

    public AutoBowAction(Map<String, Object> params) {
        this.refire = REFIRE.read(params);
        this.refireDelay = Math.max(0, REFIRE_DELAY.read(params));
        this.maxShots = Math.max(0, MAX_SHOTS.read(params));
        this.fullDrawTicks = Math.max(1, FULL_DRAW_TICKS.read(params));
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

    /** 原版弓的 power 曲线，ticks 为实际蓄力 tick，fullDrawTicks 为满弓所需 tick（原版 20）。 */
    public static double powerForTicks(int ticks, int fullDrawTicks) {
        double t = ticks / (double) fullDrawTicks;
        double power = (t * t + 2.0 * t) / 3.0;
        return Math.min(1.0, Math.max(0.0, power));
    }

    private static boolean isEnchantedBow(ItemStack item, Key enchantKey) {
        return item != null
                && item.getType() == Material.BOW
                && EnchantTriggerHelper.getCustomEnchantsOnItem(item).contains(enchantKey);
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

    public int getFullDrawTicks() {
        return fullDrawTicks;
    }
}
