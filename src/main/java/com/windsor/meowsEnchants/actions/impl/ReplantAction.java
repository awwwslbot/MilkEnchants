package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.utils.SchedulerCompat;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReplantAction implements Action {
    public static final Param<Boolean> FERTILIZER = Param.bool("fertilizer", "骨粉催熟")
            .help("补种时背包里有足够骨粉就消耗骨粉直接长成熟").def(false).build();
    public static final Param<Integer> BONE_MEAL = Param.integer("bone_meal", "催熟骨粉数")
            .help("背包里骨粉不少于此数才催熟，并消耗这么多（优先副手）").unit(Param.Unit.COUNT).min(1).def(5).build();
    public static final Param<Integer> DELAY_TICKS = Param.integer("delay_ticks", "补种延迟")
            .help("收割后等这么多 tick 再补种").unit(Param.Unit.TICK).min(0).def(3).build();
    public static final Param<String> CROP_SEED = Param.registry("seed", "种子", Param.Registry.ITEM)
            .help("补种时从背包扣 1 个").required().build();
    public static final Param<List<String>> CROP_SOIL = Param.registryList("soil", "可补种的土壤", Param.Registry.BLOCK)
            .required().build();
    public static final Param<Map<String, Object>> CROPS = Param.map("crops", "作物表",
                    Param.section("*", "作物", CROP_SEED, CROP_SOIL).build())
            .help("键是作物方块（材质名），值是种子和土壤；只处理表里、带生长阶段的作物")
            .def(defaultCrops()).build();
    public static final List<Param<?>> PARAMS = List.of(FERTILIZER, BONE_MEAL, DELAY_TICKS, CROPS);

    private static Map<String, Object> defaultCrops() {
        Map<String, Object> crops = new LinkedHashMap<>();
        crops.put("WHEAT", crop("WHEAT_SEEDS", "FARMLAND"));
        crops.put("CARROTS", crop("CARROT", "FARMLAND"));
        crops.put("POTATOES", crop("POTATO", "FARMLAND"));
        crops.put("BEETROOTS", crop("BEETROOT_SEEDS", "FARMLAND"));
        crops.put("NETHER_WART", crop("NETHER_WART", "SOUL_SAND", "SOUL_SOIL"));
        return crops;
    }

    private static Map<String, Object> crop(String seed, String... soil) {
        Map<String, Object> crop = new LinkedHashMap<>();
        crop.put("seed", seed);
        crop.put("soil", List.of(soil));
        return crop;
    }

    /** 作物方块 → 种子与土壤。 */
    record CropType(Material cropMaterial, Material seedMaterial, Set<Material> soil) {}

    private final boolean fertilizer;
    private final int boneMeal;
    private final long delayTicks;
    final Map<Material, CropType> crops;

    public ReplantAction(Map<String, Object> params) {
        this.fertilizer = FERTILIZER.read(params);
        this.boneMeal = Math.max(1, BONE_MEAL.read(params));
        this.delayTicks = Math.max(0, DELAY_TICKS.read(params));
        this.crops = parseCrops(CROPS.read(params));
    }

    @Override
    public boolean execute(ActionContext context) {
        if (!(context.getOriginalEvent() instanceof BlockBreakEvent event)) {
            return false;
        }
        if (event.isCancelled()) {
            return false;
        }

        Block block = event.getBlock();
        CropType cropType = crops.get(block.getType());
        if (cropType == null) {
            return false;
        }
        if (!(block.getBlockData() instanceof Ageable)) {
            return false;
        }

        Player player = context.getPlayer();
        if (player == null) return false;
        if (!isSuitableSoil(block.getRelative(BlockFace.DOWN).getType(), cropType)) {
            return false;
        }

        Material seedMaterial = cropType.seedMaterial();
        if (!player.getInventory().contains(seedMaterial)) {
            return false;
        }

        Plugin plugin = context.getPlugin();
        if (plugin == null) return false;

        Material cropMaterial = cropType.cropMaterial();
        SchedulerCompat.runDelayed(player, plugin, _ -> {
            if (!player.isOnline()) return;
            tryReplant(player, block, cropType, seedMaterial, cropMaterial);
        }, delayTicks);
        return true;
    }

    private void tryReplant(Player player, Block targetBlock, CropType cropType,
                            Material seedMaterial, Material cropMaterial) {
        Block currentBlock = targetBlock.getWorld().getBlockAt(targetBlock.getLocation());
        if (!currentBlock.isEmpty()) {
            return;
        }
        if (!isSuitableSoil(currentBlock.getRelative(BlockFace.DOWN).getType(), cropType)) {
            return;
        }
        if (!player.getInventory().contains(seedMaterial)) {
            return;
        }

        player.getInventory().removeItem(new ItemStack(seedMaterial, 1));

        BlockData cropData = cropMaterial.createBlockData();
        if (!(cropData instanceof Ageable ageable)) {
            return;
        }

        if (fertilizer && consumeBoneMeal(player.getInventory(), boneMeal)) {
            ageable.setAge(ageable.getMaximumAge());
        } else {
            ageable.setAge(0);
        }

        currentBlock.setBlockData(ageable);
    }

    private boolean consumeBoneMeal(PlayerInventory inv, int amount) {
        int boneMealCount = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == Material.BONE_MEAL) {
                boneMealCount += item.getAmount();
            }
        }
        if (boneMealCount < amount) {
            return false;
        }

        int toRemove = amount;
        ItemStack offhandItem = inv.getItemInOffHand();
        if (offhandItem.getType() == Material.BONE_MEAL) {
            int offhandAmount = offhandItem.getAmount();
            if (offhandAmount >= toRemove) {
                offhandItem.setAmount(offhandAmount - toRemove);
                toRemove = 0;
            } else {
                offhandItem.setAmount(0);
                toRemove -= offhandAmount;
            }
        }
        if (toRemove > 0) {
            inv.removeItem(new ItemStack(Material.BONE_MEAL, toRemove));
        }
        return true;
    }

    private boolean isSuitableSoil(Material belowType, CropType cropType) {
        return cropType.soil().contains(belowType);
    }

    /** 解析作物表；材质名不认识的条目跳过（加载期校验会报告）。 */
    static Map<Material, CropType> parseCrops(Map<String, Object> table) {
        Map<Material, CropType> crops = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : table.entrySet()) {
            Material crop = Material.matchMaterial(entry.getKey());
            if (crop == null || !(entry.getValue() instanceof Map<?, ?> spec)) continue;
            String seedName = CROP_SEED.raw(spec) instanceof String name ? name : null;
            Material seed = seedName == null ? null : Material.matchMaterial(seedName);
            if (seed == null) continue;
            Set<Material> soil = new HashSet<>();
            for (Object value : CROP_SOIL.raw(spec) instanceof List<?> list ? list : List.of()) {
                Material material = Material.matchMaterial(String.valueOf(value));
                if (material != null) soil.add(material);
            }
            crops.put(crop, new CropType(crop, seed, Set.copyOf(soil)));
        }
        return crops;
    }
}
