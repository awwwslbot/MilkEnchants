package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.listeners.EnchantTriggerHelper;
import com.windsor.meowsEnchants.utils.SkylliaCompatibility;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class MultiBreakAction implements Action {
    public static final Param<ScalingFunction> RADIUS = Param.scalableInt("radius", "半径")
            .help("以被挖方块为中心的立方体（含上下）半径，截断为整数；0 或负数不生效").unit(Param.Unit.BLOCK)
            .min(0).warnAbove(2).required().build();
    public static final Param<Boolean> MATCH_TOOL = Param.bool("match_tool", "只挖对应工具的方块")
            .help("true 时只破坏手中工具适合挖掘的方块（镐/锹/斧/锄的可挖掘标签）").def(false).build();
    public static final Param<List<String>> EXCLUDE = Param.stringList("exclude", "排除状态")
            .help("玩家处于其中任一状态时整次跳过：sneaking、sprinting、swimming、flying、gliding（不分大小写）")
            .def(List.of()).build();
    public static final Param<Integer> DURABILITY_PER_BLOCK = Param.integer("durability_per_block", "每块耗损耐久")
            .help("每多破坏一个方块扣工具的耐久（受耐久附魔影响）").min(0).def(2).build();
    public static final Param<Boolean> SNEAKING = Param.bool("sneaking", "需要潜行").def(false).build();
    public static final Param<Map<String, Object>> CONDITIONS = Param.section("conditions", "条件", SNEAKING).build();
    public static final List<Param<?>> PARAMS = List.of(RADIUS, MATCH_TOOL, EXCLUDE, DURABILITY_PER_BLOCK, CONDITIONS);

    private final ScalingFunction radius;
    private final Set<String> excludeStates;
    private final boolean sneakingRequired;
    private final boolean matchTool;
    private final int durabilityPerBlock;

    // 线程本地标志，防止递归
    private static final ThreadLocal<Boolean> processing = ThreadLocal.withInitial(() -> false);

    public MultiBreakAction(Map<String, Object> params) {
        this.radius = RADIUS.read(params);

        this.excludeStates = new HashSet<>();
        for (String state : EXCLUDE.read(params)) {
            excludeStates.add(state.toLowerCase());
        }

        this.sneakingRequired = SNEAKING.read(CONDITIONS.read(params));

        this.matchTool = MATCH_TOOL.read(params);
        this.durabilityPerBlock = DURABILITY_PER_BLOCK.read(params);
    }

    @Override
    public boolean execute(ActionContext context) {
        // 防止递归调用
        if (processing.get()) {
            return false;
        }

        Player player = context.getPlayer();
        if (player == null) return false;

        // 排除状态
        if (excludeStates.contains("sneaking") && player.isSneaking()) return false;
        if (excludeStates.contains("sprinting") && player.isSprinting()) return false;
        if (excludeStates.contains("swimming") && player.isSwimming()) return false;
        if (excludeStates.contains("flying") && player.isFlying()) return false;
        if (excludeStates.contains("gliding") && player.isGliding()) return false;

        if (sneakingRequired && !player.isSneaking()) return false;

        if (!(context.getOriginalEvent() instanceof BlockBreakEvent originalEvent)) {
            return false;
        }
        Block targetBlock = originalEvent.getBlock();

        int rad = (int) this.radius.getValue(context.getEnchantLevel());
        if (rad < 0) return false;
        if (rad == 0) return false;

        World world = targetBlock.getWorld();
        Location center = targetBlock.getLocation();
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        boolean anyBroken = false;
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean hasVacuum = EnchantTriggerHelper.hasActionOnPlayer(player, "vacuum_drops");

        // 设置处理标志
        processing.set(true);
        try {
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dy = -rad; dy <= rad; dy++) {
                    for (int dz = -rad; dz <= rad; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        int x = centerX + dx;
                        int y = centerY + dy;
                        int z = centerZ + dz;

                        if (!SkylliaCompatibility.isInSameRegion(centerX, centerZ, x, z)) continue;

                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType() == Material.AIR) continue;
                        if (block.getType().getHardness() < 0) continue;

                        if (matchTool && !isCorrectTool(block, tool)) {
                            continue;
                        }

                        // 触发 BlockBreakEvent 让保护插件拦截
                        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
                        Bukkit.getPluginManager().callEvent(breakEvent);
                        if (breakEvent.isCancelled()) continue;

                        // 获取自然掉落物（考虑时运、精准采集）
                        Collection<ItemStack> drops = block.getDrops(tool);

                        // 提取容器类方块（箱子、漏斗、熔炉等）的储存物品
                        // 注意：潜影盒破坏时仅掉落自身（物品已含 NBT 内容），不应提取内部物品
                        if (!Tag.SHULKER_BOXES.isTagged(block.getType())
                                && block.getState() instanceof InventoryHolder holder) {
                            for (ItemStack stack : holder.getInventory().getContents()) {
                                if (stack != null && !stack.getType().isAir()) {
                                    drops.add(stack.clone());
                                }
                            }
                            holder.getInventory().clear();
                        }

                        // 处理掉落
                        if (hasVacuum) {
                            // 直接放入玩家背包，溢出掉落原地
                            for (ItemStack drop : drops) {
                                if (drop == null || drop.getAmount() <= 0) continue;
                                Map<Integer, ItemStack> remaining = player.getInventory().addItem(drop.clone());
                                for (ItemStack leftover : remaining.values()) {
                                    if (leftover != null && leftover.getAmount() > 0) {
                                        world.dropItemNaturally(block.getLocation(), leftover);
                                    }
                                }
                            }
                        } else {
                            // 自然掉落
                            for (ItemStack drop : drops) {
                                if (drop != null && drop.getAmount() > 0) {
                                    world.dropItemNaturally(block.getLocation(), drop);
                                }
                            }
                        }
                        block.setType(Material.AIR);
                        if (durabilityPerBlock > 0 && tool.getType().getMaxDurability() > 0) {
                            tool.damage(durabilityPerBlock, player);
                        }

                        anyBroken = true;
                    }
                }
            }
        } finally {
            // 清除处理标志
            processing.set(false);
        }
        return anyBroken;
    }

    /**
     * 使用原版 Tag 系统判断工具是否适合该方块。
     */
    private boolean isCorrectTool(Block block, ItemStack tool) {
        Material toolMat = tool.getType();
        if (Tag.MINEABLE_PICKAXE.isTagged(block.getType())) {
            return Tag.ITEMS_PICKAXES.isTagged(toolMat);
        } else if (Tag.MINEABLE_SHOVEL.isTagged(block.getType())) {
            return Tag.ITEMS_SHOVELS.isTagged(toolMat);
        } else if (Tag.MINEABLE_AXE.isTagged(block.getType())) {
            return Tag.ITEMS_AXES.isTagged(toolMat);
        } else if (Tag.MINEABLE_HOE.isTagged(block.getType())) {
            return Tag.ITEMS_HOES.isTagged(toolMat);
        } else {
            // 无特定工具要求（如泥土、沙子、农作物等）
            return true;
        }
    }

    /**
     * 供监听器检查当前是否正在处理中，用于二次防御（可选）。
     */
    public static boolean isProcessing() {
        return processing.get();
    }
}
