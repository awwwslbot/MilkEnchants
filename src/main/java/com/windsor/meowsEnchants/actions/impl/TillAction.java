package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.config.EnchantConfig;
import com.windsor.meowsEnchants.schema.Param;
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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在右键方块时，把半径内的一批方块转换为另一种方块。
 * <p>
 * 转换规则在附魔 YAML 的 {@code actions.<key>.transforms} 中声明，源/目标都支持可选的方块状态段：
 * <pre>
 * transforms:
 *   "WATER[level=0]": ICE                                  # 源侧子集匹配
 *   "CUT_COPPER_STAIRS": "EXPOSED_CUT_COPPER_STAIRS[same]"  # same 生成器：拷贝同名状态
 *   "COPPER_BULB": "OXIDIZED_COPPER_BULB[same, lit=false]"  # same + 字面覆盖
 *   "#minecraft:wooden_stairs": "OAK_STAIRS[same]"
 * </pre>
 * 详细的 DSL 语义见 {@code docs/action_types_zh.md} 的 till 章节。
 */
public class TillAction implements Action {

    public static final Param<ScalingFunction> RADIUS = Param.scalableInt("radius", "半径")
            .help("以点击方块为中心、同一高度的正方形半径，截断为整数；0 = 只转换点击的方块").unit(Param.Unit.BLOCK)
            .min(0).warnAbove(4).required().build();
    public static final Param<Map<String, Object>> TRANSFORMS = Param.map("transforms", "转换表",
                    Param.string("*", "目标方块")
                            .help("键是源方块（可带 [状态] 子集匹配，或 #标签），值是目标方块（可带 [same] 拷贝同名状态和 [属性=值] 覆盖）")
                            .build())
            .help("源方块 → 目标方块；inherit_defaults 为 true 时并入原版耕地表（泥土类 → 耕地/泥土）").build();
    public static final Param<Boolean> INHERIT_DEFAULTS = Param.bool("inherit_defaults", "并入原版耕地表")
            .help("泥土、草方块、灰化土、菌丝 → 耕地；砂土、缠根泥土 → 泥土").def(true).build();
    public static final Param<List<String>> REQUIRE_ITEM_TYPES = Param.registryList("require_item_types", "限定工具",
                    Param.Registry.ITEM)
            .help("主手物品必须是其中之一；和 require_item_tags 都没写时，物品名含 HOE（锄头）才生效").acceptSingle().build();
    public static final Param<List<String>> REQUIRE_ITEM_TAGS = Param.stringList("require_item_tags", "限定工具标签")
            .help("物品标签，如 minecraft:hoes 或 #minecraft:hoes").acceptSingle().build();
    public static final Param<Boolean> CONSUME_DURABILITY = Param.bool("consume_durability", "消耗耐久")
            .help("每转换一个方块扣工具 durability_per_block 点耐久（受耐久附魔影响）").def(true).build();
    public static final Param<Integer> DURABILITY_PER_BLOCK = Param.integer("durability_per_block", "每块耗损耐久")
            .min(0).def(1).build();
    public static final Param<Boolean> PROTECTION_CHECK = Param.bool("protection_check", "领地保护检查")
            .help("每个方块先发一次右键事件，被保护插件取消的方块跳过").def(true).build();
    public static final Param<Boolean> CANCEL_VANILLA = Param.bool("cancel_vanilla", "拦截原版右键")
            .help("点击的方块在表里或发生了转换时取消原版右键，避免重复处理").def(true).build();
    public static final Param<Boolean> DROP_ROOTS = Param.bool("drop_roots", "缠根泥土掉落根")
            .help("缠根泥土被转换时掉落垂根（与原版一致）").def(true).build();
    public static final Param<Boolean> APPLY_PHYSICS = Param.bool("apply_physics", "触发方块更新").def(true).build();
    public static final List<Param<?>> PARAMS = List.of(RADIUS, TRANSFORMS, INHERIT_DEFAULTS, REQUIRE_ITEM_TYPES,
            REQUIRE_ITEM_TAGS, CONSUME_DURABILITY, DURABILITY_PER_BLOCK, PROTECTION_CHECK, CANCEL_VANILLA, DROP_ROOTS,
            APPLY_PHYSICS);

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

    /**
     * 规则表缓存：Action 在每次触发时都会重建，而状态段解析需要与注册表交互，
     * 因此按 transforms 原文缓存解析结果（配置内容不变即恒命中）。
     */
    private static final Map<String, RuleTable> TRANSFORM_CACHE = new ConcurrentHashMap<>();

    /** 运行期回退告警去重（每个 from->to 组合只警告一次）。 */
    private static final Set<String> WARNED_FALLBACKS = ConcurrentHashMap.newKeySet();

    private final ScalingFunction radius;
    private final Map<Material, List<Rule>> rules;
    private final Set<Material> requiredItemTypes;
    private final boolean useLegacyHoeCheck;
    private final boolean consumeDurability;
    private final int durabilityPerBlock;
    private final boolean protectionCheck;
    private final boolean cancelVanilla;
    private final boolean dropRoots;
    private final boolean applyPhysics;

    private static final ThreadLocal<Boolean> processing = ThreadLocal.withInitial(() -> false);

    public TillAction(Map<String, Object> params) {
        this.radius = RADIUS.read(params);

        this.rules = loadTransforms(params).byMaterial();

        this.requiredItemTypes = resolveRequiredItemTypes(params);
        this.useLegacyHoeCheck = !params.containsKey(REQUIRE_ITEM_TYPES.key())
                && !params.containsKey(REQUIRE_ITEM_TAGS.key());

        this.consumeDurability = CONSUME_DURABILITY.read(params);
        this.durabilityPerBlock = DURABILITY_PER_BLOCK.read(params);
        this.protectionCheck = PROTECTION_CHECK.read(params);
        this.cancelVanilla = CANCEL_VANILLA.read(params);
        this.dropRoots = DROP_ROOTS.read(params);
        this.applyPhysics = APPLY_PHYSICS.read(params);
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

        // 点击的方块在表里时必须拦截原版右键；即使保护插件拒绝了我们，也不能让原版绕过去
        boolean clickedMatches = resolve(clickedBlock.getType(), clickedBlock.getBlockData()) != null;
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
                    // 材质不在表里时直接跳过，避免对整片区域读取 BlockData
                    if (!this.rules.containsKey(original)) continue;

                    BlockData current = block.getBlockData();
                    TargetSpec target = resolve(original, current);
                    if (target == null) continue;

                    BlockData result = target.create(current);
                    if (result == null || result.equals(current)) continue;

                    if (protectionCheck && isDeniedByProtection(player, tool, block)) {
                        continue;
                    }

                    block.setBlockData(result, applyPhysics);
                    changed = true;

                    if (consumeDurability && durabilityPerBlock > 0) {
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

    /** 在某个材质对应的规则中取第一条命中的目标；没有命中返回 null。 */
    private TargetSpec resolve(Material material, BlockData data) {
        List<Rule> candidates = this.rules.get(material);
        if (candidates == null) return null;

        Map<String, String> sourceStates = null;
        for (Rule rule : candidates) {
            Map<String, String> required = rule.required();
            if (!required.isEmpty()) {
                if (sourceStates == null) sourceStates = statesOf(data.getAsString());
                if (!matches(sourceStates, required)) continue;
            }
            return rule.target();
        }
        return null;
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
        tool.damage(durabilityPerBlock, player);
    }

    // ------------------------------------------------------------------
    // 配置解析
    // ------------------------------------------------------------------

    /** 按 transforms 原文缓存的规则表。 */
    private static RuleTable loadTransforms(Map<String, Object> params) {
        return TRANSFORM_CACHE.computeIfAbsent(transformCacheKey(params), _ -> buildTransforms(params));
    }

    private static String transformCacheKey(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        sb.append(INHERIT_DEFAULTS.read(params) ? "1" : "0");
        Object raw = TRANSFORMS.raw(params);
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sb.append('|').append(entry.getKey()).append('=').append(entry.getValue());
            }
        }
        return sb.toString();
    }

    private static RuleTable buildTransforms(Map<String, Object> params) {
        List<Rule> all = new ArrayList<>();

        int index = 0;
        if (INHERIT_DEFAULTS.read(params)) {
            for (Map.Entry<Material, Material> entry : VANILLA_TRANSFORMS.entrySet()) {
                TargetSpec target = new TargetSpec(entry.getValue(), false, Map.of());
                all.add(new Rule(entry.getKey(), Map.of(), target, false, index++));
            }
        }

        Object raw = TRANSFORMS.raw(params);
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String fromSpec = String.valueOf(entry.getKey());
                String toSpec = String.valueOf(entry.getValue());

                List<SourceConstraint> sources = parseSource(fromSpec);
                TargetSpec target = parseTarget(toSpec);
                for (SourceConstraint source : sources) {
                    all.add(new Rule(source.material(), source.required(), target, true, index++));
                }
            }
        }

        // 同一材质内：状态约束多者先，其次用户显式配置优先于默认表，最后按声明顺序
        Comparator<Rule> ordering = Comparator
                .comparingInt((Rule rule) -> rule.required().size()).reversed()
                .thenComparing(rule -> rule.user() ? 0 : 1)
                .thenComparingInt(Rule::index);

        Map<Material, List<Rule>> byMaterial = new LinkedHashMap<>();
        for (Rule rule : all) {
            byMaterial.computeIfAbsent(rule.material(), _ -> new ArrayList<>()).add(rule);
        }
        byMaterial.values().forEach(list -> list.sort(ordering));

        return new RuleTable(Map.copyOf(byMaterial), List.copyOf(all));
    }

    /** 解析源侧：材质（或标签）+ 可选状态段，返回每个材质的子集约束。 */
    private static List<SourceConstraint> parseSource(String raw) {
        String text = raw.trim();
        int bracket = text.indexOf('[');
        String name = bracket < 0 ? text : text.substring(0, bracket).trim();
        String statePart = bracket < 0 ? "" : stripBrackets(text, bracket, raw);

        List<String> literalTokens = new ArrayList<>();
        for (String token : splitStateTokens(statePart)) {
            if (token.isEmpty()) continue;
            if (token.equalsIgnoreCase("same")) {
                throw new IllegalArgumentException("'same' is only allowed on the target side: " + raw);
            }
            if (token.indexOf('=') <= 0) {
                throw new IllegalArgumentException("Invalid state entry '" + token + "' in till source: " + raw);
            }
            literalTokens.add(token);
        }

        List<Material> materials = name.startsWith("#")
                ? resolveTag(name.substring(1), true)
                : List.of(requireBlockMaterial(name));

        List<SourceConstraint> result = new ArrayList<>(materials.size());
        for (Material material : materials) {
            result.add(new SourceConstraint(material, canonicalStates(material, literalTokens, raw)));
        }
        return result;
    }

    /** 解析目标侧：材质 + 可选状态段（支持 same 生成器与字面覆盖）。 */
    private static TargetSpec parseTarget(String raw) {
        String text = raw.trim();
        if (text.startsWith("#")) {
            throw new IllegalArgumentException("till target must be a concrete block, not a tag: " + raw);
        }

        int bracket = text.indexOf('[');
        String name = bracket < 0 ? text : text.substring(0, bracket).trim();
        String statePart = bracket < 0 ? "" : stripBrackets(text, bracket, raw);

        Material material = requireBlockMaterial(name);

        boolean copySame = false;
        List<String> literalTokens = new ArrayList<>();
        for (String token : splitStateTokens(statePart)) {
            if (token.isEmpty()) continue;
            if (token.equalsIgnoreCase("same")) {
                copySame = true;
                continue;
            }
            if (token.indexOf('=') <= 0) {
                throw new IllegalArgumentException("Invalid state entry '" + token + "' in till target: " + raw);
            }
            literalTokens.add(token);
        }

        Map<String, String> overrides = canonicalStates(material, literalTokens, raw);
        return new TargetSpec(material, copySame, overrides);
    }

    /**
     * 把状态段交给服务端解析器校验并规范化（属性名、属性值都由服务端判定）。
     * <p>
     * 只保留配置中实际写出的属性：{@code OAK_STAIRS[facing=east]} 规范化后仍是
     * {@code facing=east}，不会把未写出的属性一起带进匹配条件（源侧是子集匹配）。
     */
    private static Map<String, String> canonicalStates(Material material, List<String> tokens, String raw) {
        if (tokens.isEmpty()) return Map.of();

        List<String> writtenNames = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            writtenNames.add(token.substring(0, token.indexOf('=')).trim());
        }

        BlockData probe;
        try {
            probe = Bukkit.createBlockData(String.valueOf(material.getKey())
                    + "[" + String.join(",", tokens) + "]");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid block state for " + material.name()
                    + " in till spec '" + raw + "': " + e.getMessage(), e);
        }

        Map<String, String> resolved = statesOf(probe.getAsString());
        Map<String, String> canonical = new LinkedHashMap<>();
        for (String name : writtenNames) {
            String value = resolved.get(name);
            if (value == null) {
                throw new IllegalArgumentException("Unknown block state property '" + name
                        + "' for " + material.name() + " in till spec: " + raw);
            }
            canonical.put(name, value);
        }
        return Map.copyOf(canonical);
    }

    private static String stripBrackets(String text, int bracket, String raw) {
        if (!text.endsWith("]")) {
            throw new IllegalArgumentException("Unclosed block state section in till spec: " + raw);
        }
        return text.substring(bracket + 1, text.length() - 1);
    }

    private static List<String> splitStateTokens(String statePart) {
        List<String> tokens = new ArrayList<>();
        if (statePart.isEmpty()) return tokens;
        for (String token : statePart.split(",")) {
            tokens.add(token.trim());
        }
        return tokens;
    }

    /** 把 BlockData#getAsString() 的结果解析成 属性 -> 值。 */
    private static Map<String, String> statesOf(String asString) {
        int bracket = asString.indexOf('[');
        if (bracket < 0 || bracket + 1 >= asString.length()) return Map.of();
        int end = asString.lastIndexOf(']');
        if (end <= bracket) return Map.of();

        Map<String, String> states = new LinkedHashMap<>();
        for (String token : asString.substring(bracket + 1, end).split(",")) {
            int eq = token.indexOf('=');
            if (eq <= 0) continue;
            states.put(token.substring(0, eq).trim(), token.substring(eq + 1).trim());
        }
        return states;
    }

    private static boolean matches(Map<String, String> sourceStates, Map<String, String> required) {
        for (Map.Entry<String, String> entry : required.entrySet()) {
            if (!entry.getValue().equals(sourceStates.get(entry.getKey()))) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 规则模型
    // ------------------------------------------------------------------

    private record SourceConstraint(Material material, Map<String, String> required) {}

    private record Rule(Material material, Map<String, String> required, TargetSpec target, boolean user, int index) {}

    private record RuleTable(Map<Material, List<Rule>> byMaterial, List<Rule> allRules) {}

    /** 目标方块的生成方式：材质 + 可选 same 拷贝 + 字面覆盖。 */
    private static final class TargetSpec {
        private final Material material;
        private final boolean copySame;
        private final Map<String, String> overrides;
        private final Set<String> propertyNames;
        private final String display;

        TargetSpec(Material material, boolean copySame, Map<String, String> overrides) {
            this.material = material;
            this.copySame = copySame;
            this.overrides = Map.copyOf(overrides);
            this.propertyNames = Set.copyOf(statesOf(material.createBlockData().getAsString()).keySet());
            this.display = buildDisplay();
        }

        /**
         * 生成目标 BlockData。
         * <p>
         * {@code same} 只拷贝「目标方块也拥有」的同名属性；字面条目覆盖拷贝结果。
         * 生成失败时回退目标默认状态，避免破坏整次右键。
         */
        BlockData create(BlockData source) {
            BlockData fallback = material.createBlockData();
            if (!copySame && overrides.isEmpty()) return fallback;

            Map<String, String> merged = new LinkedHashMap<>();
            if (copySame) {
                Map<String, String> sourceStates = statesOf(source.getAsString());
                for (String name : propertyNames) {
                    String value = sourceStates.get(name);
                    if (value != null) merged.put(name, value);
                }
            }
            merged.putAll(overrides);

            // 先按 same + 字面覆盖生成；失败则退一步只保留字面覆盖；再失败回退目标默认状态
            try {
                BlockData data = build(merged);
                if (data != null) return data;
            } catch (IllegalArgumentException e) {
                warnFallback(merged, e);
            }

            if (!overrides.isEmpty() && !overrides.equals(merged)) {
                try {
                    BlockData data = build(overrides);
                    if (data != null) {
                        warnFallback(merged, null);
                        return data;
                    }
                } catch (IllegalArgumentException e) {
                    warnFallback(overrides, e);
                }
            }

            return fallback;
        }

        private BlockData build(Map<String, String> states) {
            if (states.isEmpty()) return null;

            StringBuilder sb = new StringBuilder(String.valueOf(material.getKey())).append('[');
            boolean first = true;
            for (Map.Entry<String, String> entry : states.entrySet()) {
                if (!first) sb.append(',');
                sb.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
            sb.append(']');

            try {
                return Bukkit.createBlockData(sb.toString());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(sb.toString(), e);
            }
        }

        private void warnFallback(Map<String, String> states, IllegalArgumentException cause) {
            String key = display + "|" + states + "|" + (cause == null ? "" : cause.getMessage());
            if (!WARNED_FALLBACKS.add(key)) return;
            Bukkit.getLogger().warning("[MeowsEnchants] till 目标状态生成失败，已降级处理: "
                    + display + " -> " + states
                    + (cause == null ? "" : " (" + cause.getMessage() + ")"));
        }

        private String buildDisplay() {
            StringBuilder sb = new StringBuilder(material.name());
            if (copySame || !overrides.isEmpty()) {
                sb.append('[');
                boolean first = true;
                if (copySame) {
                    sb.append("same");
                    first = false;
                }
                for (Map.Entry<String, String> entry : overrides.entrySet()) {
                    if (!first) sb.append(',');
                    sb.append(entry.getKey()).append('=').append(entry.getValue());
                    first = false;
                }
                sb.append(']');
            }
            return sb.toString();
        }

        String display() {
            return display;
        }

        /** 目标与原方块完全一致（同材质、全部属性直接拷贝、无字面覆盖）时是空操作。 */
        boolean isIdentityFor(Material source) {
            return material == source && copySame && overrides.isEmpty();
        }
    }

    private static Set<Material> resolveRequiredItemTypes(Map<String, Object> params) {
        Set<Material> result = new LinkedHashSet<>();

        for (Object value : asList(params.get(REQUIRE_ITEM_TYPES.key()))) {
            Material material = Material.matchMaterial(String.valueOf(value));
            if (material == null) {
                throw new IllegalArgumentException("Unknown item type in require_item_types: " + value);
            }
            result.add(material);
        }

        for (Object value : asList(params.get(REQUIRE_ITEM_TAGS.key()))) {
            result.addAll(resolveItemTag(String.valueOf(value)));
        }

        return Set.copyOf(result);
    }

    private static List<?> asList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return list;
        return List.of(value);
    }

    private static Material requireBlockMaterial(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || material.isLegacy() || !material.isBlock()) {
            throw new IllegalArgumentException("Unknown block material in till spec: " + name);
        }
        return material;
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
     * 同一源材质被不同附魔映射到不同目标，且两者的状态约束相容时打印警告。
     * 运行时跨附魔的顺序不确定，因此重叠配置的结果不可预期，建议每个附魔使用互不重叠的表。
     */
    public static void validateOverlap(Map<Key, EnchantConfig> configs, Logger logger) {
        record Entry(String owner, Material material, Map<String, String> required, String target) {}

        List<Entry> entries = new ArrayList<>();

        for (EnchantConfig config : configs.values()) {
            for (Map<String, Object> action : config.getActions()) {
                if (!"till".equalsIgnoreCase(String.valueOf(action.get("type")))) continue;

                RuleTable table;
                try {
                    table = loadTransforms(action);
                } catch (RuntimeException e) {
                    logger.warn("附魔 {} 的 till 转换表解析失败: {}", config.getId(), e.getMessage());
                    continue;
                }

                for (Rule rule : table.allRules()) {
                    // 目标与原方块完全一致（如 "#minecraft:wooden_stairs": "OAK_STAIRS[same]" 展开到橡木时）
                    // 是空操作，不会与他人争抢，不参与重叠判定
                    if (rule.target().isIdentityFor(rule.material())) continue;
                    entries.add(new Entry(config.getId(), rule.material(),
                            rule.required(), rule.target().display()));
                }
            }
        }

        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                Entry a = entries.get(i);
                Entry b = entries.get(j);
                if (a.owner().equals(b.owner())) continue;
                if (a.material() != b.material()) continue;
                if (a.target().equals(b.target())) continue;
                if (!constraintsOverlap(a.required(), b.required())) continue;

                logger.warn("till 转换表重叠: {} 同时被 [{}: {} -> {}] 和 [{}: {} -> {}] 定义，运行时结果不确定",
                        a.material().name(),
                        a.owner(), a.material().name() + describeStates(a.required()), a.target(),
                        b.owner(), b.material().name() + describeStates(b.required()), b.target());
            }
        }
    }

    /** 两个子集约束是否存在同时命中的可能（同名属性取值冲突则不重叠）。 */
    private static boolean constraintsOverlap(Map<String, String> a, Map<String, String> b) {
        for (Map.Entry<String, String> entry : a.entrySet()) {
            String other = b.get(entry.getKey());
            if (other != null && !other.equals(entry.getValue())) return false;
        }
        return true;
    }

    private static String describeStates(Map<String, String> states) {
        if (states.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, String> entry : states.entrySet()) {
            if (!first) sb.append(',');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }
}
