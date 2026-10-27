package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.utils.XpManager;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class RepairAction implements Action {
    public static final Param<ScalingFunction> AMOUNT = Param.scalableNumber("amount", "修复概率")
            .help("每个周期对每件带此附魔、有损耗的物品掷一次，命中修复 1 点耐久").unit(Param.Unit.RATIO).range(0, 1)
            .required().build();
    public static final Param<Boolean> EXP_MODE = Param.bool("exp_mode", "消耗经验")
            .help("true 时每修 1 点扣 1 点经验，经验不足时停止").def(false).build();
    public static final Param<Integer> REPAIR_AMOUNT = Param.integer("repair_amount", "每次修复耐久")
            .help("掷中后修复的耐久点数（不超过已损耗的量）").min(1).def(1).build();
    public static final Param<Integer> EXP_COST = Param.integer("exp_cost", "每次扣经验")
            .help("exp_mode 开启时每次修复扣的经验点，经验不足时停止").min(0).def(1).build();
    public static final List<Param<?>> PARAMS = List.of(AMOUNT, EXP_MODE, REPAIR_AMOUNT, EXP_COST);
    private final ScalingFunction amount;
    private final boolean expMode;
    private final int repairAmount;
    private final int expCost;
    private final Random random = new Random();

    public RepairAction(Map<String, Object> params) {
        this.amount = AMOUNT.read(params);
        this.expMode = EXP_MODE.read(params);
        this.repairAmount = Math.max(1, REPAIR_AMOUNT.read(params));
        this.expCost = Math.max(0, EXP_COST.read(params));
    }

    @Override
    public boolean execute(ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        Key enchantKey = context.getEnchantKey();
        if (enchantKey == null) return false;

        PlayerInventory inv = player.getInventory();
        ItemStack[] slots = {
                inv.getItemInMainHand(),
                inv.getItemInOffHand(),
                inv.getHelmet(),
                inv.getChestplate(),
                inv.getLeggings(),
                inv.getBoots()
        };
        boolean anyRepaired = false;

        for (ItemStack item : slots) {
            if (item == null || item.getType() == Material.AIR) continue;

            // 只修复带有该附魔的物品
            if (!hasEnchant(item, enchantKey)) continue;

            if (!(item.getItemMeta() instanceof Damageable meta)) continue;

            int currentDamage = meta.getDamage();
            int maxDurability = item.getType().getMaxDurability();
            if (currentDamage <= 0 || maxDurability <= 0) continue;

            double chance = this.amount.getValue(context.getEnchantLevel());
            if (chance <= 0 || random.nextDouble() >= chance) continue;

            // 经验模式检查（每次修复前检查）
            if (expMode) {
                if (player.calculateTotalExperiencePoints() < expCost) {
                    break; // 经验不足，停止后续所有修复
                }
            }

            // 修复 repair_amount 点耐久
            meta.setDamage(Math.max(0, currentDamage - repairAmount));
            item.setItemMeta(meta);
            anyRepaired = true;

            // 扣除经验（如启用）
            if (expMode) {
                XpManager.takePlayerExp(player, expCost);
            }
        }

        return anyRepaired;
    }

    /**
     * 检查物品是否包含指定的附魔。
     */
    private boolean hasEnchant(ItemStack item, Key enchantKey) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasEnchants()) return false;
        return item.getItemMeta().getEnchants().keySet().stream()
                .anyMatch(ench -> ench.getKey().equals(enchantKey));
    }
}