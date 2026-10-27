package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.listeners.EnchantTriggerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.*;

public class BlockBreakAction implements Action {
    public static final Param<List<String>> TARGET_TYPES = Param.stringList("target_types", "可破坏方块")
            .help("方块材质名（如 GLASS）、方块标签（含冒号，如 minecraft:impermeable）或 includes:片段（材质名包含该片段）；不写不限")
            .def(List.of()).build();
    public static final Param<Boolean> SNEAKING = Param.bool("sneaking", "需要潜行").def(false).build();
    public static final Param<Map<String, Object>> CONDITIONS = Param.section("conditions", "条件", SNEAKING).build();
    public static final Param<Integer> DURABILITY = Param.integer("durability", "耗损耐久")
            .help("每破坏一个方块扣工具的耐久（受耐久附魔影响）").min(0).def(1).build();
    public static final List<Param<?>> PARAMS = List.of(TARGET_TYPES, DURABILITY, CONDITIONS);

    private final boolean sneakingRequired;
    private final Set<Material> targetTypes;
    private final Set<String> targetTags;
    private final List<String> includesPatterns;
    private final int durability;

    public BlockBreakAction(Map<String, Object> params) {
        this.sneakingRequired = SNEAKING.read(CONDITIONS.read(params));
        this.durability = DURABILITY.read(params);

        targetTypes = new HashSet<>();
        targetTags = new HashSet<>();
        includesPatterns = new ArrayList<>();

        for (String str : TARGET_TYPES.read(params)) {
            if (str.toLowerCase().startsWith("includes:")) {
                includesPatterns.add(str.substring("includes:".length()).toLowerCase());
                continue;
            }
            Material mat = Material.getMaterial(str.toUpperCase());
            if (mat != null) {
                targetTypes.add(mat);
                continue;
            }
            if (str.contains(":")) {
                targetTags.add(str);
            } else {
                try {
                    mat = Material.valueOf(str.toUpperCase());
                    targetTypes.add(mat);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    @Override
    public boolean execute(ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        Object extra = context.getExtra();
        if (!(extra instanceof Block targetBlock)) return false;

        // 检查潜行条件
        if (sneakingRequired && !player.isSneaking()) return false;

        // 检查目标类型过滤
        if (!targetTypes.isEmpty() || !targetTags.isEmpty() || !includesPatterns.isEmpty()) {
            Material blockType = targetBlock.getType();
            boolean matches = false;

            if (!targetTypes.isEmpty() && targetTypes.contains(blockType)) {
                matches = true;
            }
            if (!matches && !targetTags.isEmpty()) {
                for (String tagKey : targetTags) {
                    NamespacedKey namespacedKey = NamespacedKey.fromString(tagKey);
                    if (namespacedKey == null) continue;
                    Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, namespacedKey, Material.class);
                    if (tag != null && tag.isTagged(blockType)) {
                        matches = true;
                        break;
                    }
                }
            }
            if (!matches && !includesPatterns.isEmpty()) {
                String blockName = blockType.name().toLowerCase();
                for (String pattern : includesPatterns) {
                    if (blockName.contains(pattern)) {
                        matches = true;
                        break;
                    }
                }
            }
            if (!matches) return false;
        }

        // 忽略不可破坏的方块（硬度 < 0）
        if (targetBlock.getType().getHardness() < 0) {
            return false;
        }

        // 获取玩家手中的工具（优先主手，其次副手）
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) {
            tool = player.getInventory().getItemInOffHand();
        }

        // 触发 BlockBreakEvent 让保护插件拦截
        BlockBreakEvent breakEvent = new BlockBreakEvent(targetBlock, player);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) return false;

        // 获取掉落物（考虑时运、精准采集）
        Collection<ItemStack> drops = targetBlock.getDrops(tool);

        // 判断是否启用 VacuumDropsAction
        boolean hasVacuum = EnchantTriggerHelper.hasActionOnPlayer(player, "vacuum_drops");

        // 处理掉落物
        if (hasVacuum) {
            // 直接放入玩家背包，溢出掉落原地
            for (ItemStack drop : drops) {
                if (drop == null || drop.getAmount() <= 0) continue;
                Map<Integer, ItemStack> remaining = player.getInventory().addItem(drop.clone());
                for (ItemStack leftover : remaining.values()) {
                    if (leftover != null && leftover.getAmount() > 0) {
                        targetBlock.getWorld().dropItemNaturally(targetBlock.getLocation(), leftover);
                    }
                }
            }
        } else {
            // 自然掉落
            for (ItemStack drop : drops) {
                if (drop != null && drop.getAmount() > 0) {
                    targetBlock.getWorld().dropItemNaturally(targetBlock.getLocation(), drop);
                }
            }
        }

        // 销毁方块（手动设置空气）
        targetBlock.setType(Material.AIR);

        // 消耗工具耐久（每次破坏消耗1点，受 Unbreaking 附魔影响）
        if (durability > 0 && tool != null && tool.getType().getMaxDurability() > 0) {
            if (tool.getItemMeta() instanceof Damageable) {
                tool.damage(durability, player);
            }
        }

        return true;
    }
}