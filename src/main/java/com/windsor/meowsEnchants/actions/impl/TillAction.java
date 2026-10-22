package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.actions.ScalingFunctionLoader;
import com.windsor.meowsEnchants.config.EnchantConfig;
import com.windsor.meowsEnchants.utils.SkylliaCompatibility;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在右键方块时，把半径内的一批方块转换为另一种方块。
 * <p>
 * 所有转换规则都可以在附魔 YAML 的 {@code actions.<key>.transforms} 中声明，
 * 因此每个附魔都可以拥有自己独立的转换表。
 */
public class TillAction implements Action {

    /** 不写 transforms 时使用的默认表，保持与旧版本行为一致。 */
    private static final Map<Material, Material> VANILLA_TRANSFORMS;

    static {
        Map<Material, Material> map = new LinkedHashMap<>();
        map.put(Material.DIRT, Material.FARMLAND);
        map.put(Material.GRASS_BLOCK, Material.FARMLAND);
        map.put(Material.PODZOL, Material.FARMLAND);
        map.put(Material.MYCELIUM, Material.FARMLAND);
        map.put(Material.COARSE_DIRT, Material.DIRT);
        map.put(Material.ROOTED_DIRT, Material.DIRT);
        VANILLA_TRANSFORMS = Map.copyOf(map);
    }

    /** 方块标签展开缓存，避免每次触发都扫描注册表。 */
    private static final Map<String, List<Material>> BLOCK_TAG_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<Material>> ITEM_TAG_CACHE = new ConcurrentHashMap<>();

    private final ScalingFunction radius;
    private final Map<Material, Material> transforms;
    private final Set<Material> requiredItemTypes;
    private final boolean useLegacyHoeCheck;
    private final boolean consumeDurability;
    private final boolean protectionCheck;
    private final boolean cancelVanilla;
    private final boolean dropRoots;

    private static final ThreadLocal<Boolean> processing = ThreadLocal.withInitial(() -> false);

    public TillAction(Map<String, Object> params) {
        Object radiusObj = params.get("radius");
        if (radiusObj == null) {
            throw new IllegalArgumentException("Missing 'radius' parameter for till action");
        }
        this.radius = ScalingFunctionLoader.load(radiusObj);

        this.transforms = Map.copyOf(parseTransforms(params));

        this.requiredItemTypes = resolveRequiredItemTypes(params);
        this.useLegacyHoeCheck = !params.containsKey("require_item_types")
                && !params.containsKey("require_item_tags");

        this.consumeDurability = !Boolean.FALSE.equals(params.get("consume_durability"));
        this.protectionCheck = !Boolean.FALSE.equals(params.get("protection_check"));
        this.cancelVanilla = !Boolean.FALSE.equals(params.get("cancel_vanilla"));
        this.dropRoots = !Boolean.FALSE.equals(params.get("drop_roots"));
    }

    public static boolean isProcessing() {
        return processing.get();
    }

    @Override
    public boolean execute(ActionContext context) {
        if (processing.get()) {
            return false;
        }

        if (!(context.getOriginalEvent() instanceof PlayerInteractEvent originalEvent)) {
            return false;
        }
        if (originalEvent.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return false;
        }

        Player player = context.getPlayer();
        if (player == null) return false;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().isAir()) return false;
        if (!isValidTool(tool.getType())) {
            return false;
        }

        Block clickedBlock = originalEvent.getClickedBlock();
        if (clickedBlock == null) return false;

        int rad = (int) this.radius.getValue(context.getEnchantLevel());
        if (rad < 0) return false;

        Location center = clickedBlock.getLocation();
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        World world = clickedBlock.getWorld();

        Material clickedOriginal = clickedBlock.getType();
        Material clickedTarget = transforms.get(clickedOriginal);
        boolean clickedMatches = clickedTarget != null && clickedTarget != clickedOriginal;

        // 点击的方块在表里时必须拦截原版右键；即使保护插件拒绝了我们，也不能让原版绕过去
        if (cancelVanilla && clickedMatches) {
            originalEvent.setCancelled(true);
        }

        boolean changed = false;
        processing.set(true);
        try {
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (!SkylliaCompatibility.isInSameRegion(centerX, centerZ, x, z)) continue;

                    Block block = world.getBlockAt(x, centerY, z);
                    // Folia：只修改当前 region 拥有的方块，跨 region 的交给对方线程处理
                    if (!Bukkit.isOwnedByCurrentRegion(block)) continue;

                    Material original = block.getType();
                    Material target = transforms.get(original);
                    if (target == null || target == original) continue;

                    if (protectionCheck && isDeniedByProtection(player, tool, block)) {
                        continue;
                    }

                    block.setType(target);
                    changed = true;

                    if (consumeDurability) {
                        damageTool(tool, player);
                    }
                    // 缠根泥土掉落根（与原版一致）
                    if (dropRoots && original == Material.ROOTED_DIRT) {
                        block.getWorld().dropItemNaturally(block.getLocation(),
                                new ItemStack(Material.HANGING_ROOTS, 1));
                    }
                }
            }
        } finally {
            processing.set(false);
        }

        // 半径内发生了转换时也拦截原版右键，避免同一次右键又被原版处理一次
        if (changed && cancelVanilla) {
            originalEvent.setCancelled(true);
        }
        return changed;
    }

    private boolean isValidTool(Material type) {
        if (useLegacyHoeCheck) {
            return type.name().contains("HOE");
        }
        return requiredItemTypes.contains(type);
    }

    private boolean isDeniedByProtection(Player player, ItemStack tool, Block block) {
        PlayerInteractEvent interactEvent = new PlayerInteractEvent(
                player,
                org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                tool,
                block,
                BlockFace.UP,
                EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(interactEvent);
        return interactEvent.isCancelled();
    }

    private void damageTool(ItemStack tool, Player player) {
        if (tool == null || tool.getType().isAir()) return;
        if (!(tool.getItemMeta() instanceof Damageable)) return;
        tool.damage(1, player);
    }

    // ------------------------------------------------------------------
    // 配置解析
    // ------------------------------------------------------------------

    /** 计算某个 till 动作最终生效的转换表（含 inherit_defaults 与标签展开）。 */
    public static Map<Material, Material> parseTransforms(Map<String, Object> params) {
        Map<Material, Material> result = new LinkedHashMap<>();
        if (!Boolean.FALSE.equals(params.get("inherit_defaults"))) {
            result.putAll(VANILLA_TRANSFORMS);
        }

        Object raw = params.get("transforms");
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String fromKey = String.valueOf(entry.getKey());
                Material to = resolveBlockMaterial(String.valueOf(entry.getValue()));
                List<Material> froms = resolveBlockMaterials(fromKey);
                if (froms.isEmpty()) {
                    throw new IllegalArgumentException("Unknown block or tag in till transforms: " + fromKey);
                }
                for (Material from : froms) {
                    result.put(from, to);
                }
            }
        }
        return result;
    }

    private static Set<Material> resolveRequiredItemTypes(Map<String, Object> params) {
        Set<Material> result = new LinkedHashSet<>();

        for (Object value : asList(params.get("require_item_types"))) {
            Material material = Material.matchMaterial(String.valueOf(value));
            if (material == null) {
                throw new IllegalArgumentException("Unknown item type in require_item_types: " + value);
            }
            result.add(material);
        }

        for (Object value : asList(params.get("require_item_tags"))) {
            result.addAll(resolveItemTag(String.valueOf(value)));
        }

        return Set.copyOf(result);
    }

    private static List<?> asList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return list;
        return List.of(value);
    }

    private static Material resolveBlockMaterial(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isBlock()) {
            throw new IllegalArgumentException("Unknown block material: " + name);
        }
        return material;
    }

    private static List<Material> resolveBlockMaterials(String name) {
        if (name.startsWith("#")) {
            return resolveTag(name.substring(1), true);
        }
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isBlock()) {
            throw new IllegalArgumentException("Unknown block material: " + name);
        }
        return List.of(material);
    }

    private static List<Material> resolveItemTag(String name) {
        String key = name.startsWith("#") ? name.substring(1) : name;
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }
        List<Material> values = ITEM_TAG_CACHE.computeIfAbsent(key, k -> {
            NamespacedKey namespacedKey = NamespacedKey.fromString(k);
            Tag<Material> tag = namespacedKey == null ? null
                    : Bukkit.getTag(Tag.REGISTRY_ITEMS, namespacedKey, Material.class);
            return tag == null ? List.of() : List.copyOf(tag.getValues());
        });
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Unknown item tag: " + name);
        }
        return values;
    }

    private static List<Material> resolveTag(String key, boolean blockTag) {
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }
        String cacheKey = key;
        Map<String, List<Material>> cache = blockTag ? BLOCK_TAG_CACHE : ITEM_TAG_CACHE;
        List<Material> values = cache.computeIfAbsent(cacheKey, k -> {
            NamespacedKey namespacedKey = NamespacedKey.fromString(k);
            if (namespacedKey == null) return List.of();
            Tag<Material> tag = Bukkit.getTag(blockTag ? Tag.REGISTRY_BLOCKS : Tag.REGISTRY_ITEMS,
                    namespacedKey, Material.class);
            return tag == null ? List.of() : List.copyOf(tag.getValues());
        });
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Unknown " + (blockTag ? "block" : "item") + " tag: " + key);
        }
        return values;
    }

    // ------------------------------------------------------------------
    // 加载期校验
    // ------------------------------------------------------------------

    /**
     * 校验多个 till 附魔的转换表是否重叠。
     * <p>
     * 同一个源方块被不同附魔映射到不同目标时打印警告。运行时的顺序取决于 Map 遍历顺序，
     * 因此重叠配置的结果不可预期，建议每个附魔使用互不重叠的表。
     */
    public static void validateOverlap(Map<Key, EnchantConfig> configs, Logger logger) {
        Map<Material, String> owners = new LinkedHashMap<>();

        for (EnchantConfig config : configs.values()) {
            for (Map<String, Object> action : config.getActions()) {
                if (!"till".equalsIgnoreCase(String.valueOf(action.get("type")))) continue;

                Map<Material, Material> transforms;
                try {
                    transforms = parseTransforms(action);
                } catch (RuntimeException e) {
                    logger.warn("附魔 {} 的 till 转换表解析失败: {}", config.getId(), e.getMessage());
                    continue;
                }

                for (Map.Entry<Material, Material> entry : transforms.entrySet()) {
                    String current = config.getId() + " -> " + entry.getValue();
                    String previous = owners.putIfAbsent(entry.getKey(), current);
                    if (previous != null && !previous.endsWith(" -> " + entry.getValue())) {
                        logger.warn("till 转换表重叠: {} 同时被 [{}] 和 [{}] 定义，运行时结果不确定",
                                entry.getKey(), previous, current);
                    }
                }
            }
        }
    }
}
