package com.windsor.meowsEnchants.schema;

import com.windsor.meowsEnchants.actions.ActionRegistry;
import com.windsor.meowsEnchants.arrowrain.ArrowRainConfig;
import com.windsor.meowsEnchants.config.ApplicableItem;
import com.windsor.meowsEnchants.config.GlobalSettings;
import com.windsor.meowsEnchants.config.TargetType;
import com.windsor.meowsEnchants.config.Trigger;
import com.windsor.meowsEnchants.tracking.TrackingConfig;
import com.windsor.meowsEnchants.tracking.TrackingSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 附魔 yml 顶层字段的声明，以及整份 {@code editor/enchant-schema.json} 的组装。
 */
public final class EnchantSchema {
    private EnchantSchema() {}

    public static final int SCHEMA_VERSION = 1;

    // ---------------------------------------------------------------- 顶层字段

    public static final Param<String> NAME = Param.text("name", "名称")
            .help("附魔显示名，按 MiniMessage 解析；/enchants 里立即生效，物品上的附魔名要重启")
            .required().restart().build();
    public static final Param<List<String>> DESCRIPTION = Param.textList("description", "说明")
            .help("/enchants 查询界面的 lore，每项一行，不显示在物品上；一行里有 & 颜色码时整行按 & 码解析，否则按 MiniMessage")
            .def(List.of()).build();
    public static final Param<Integer> MAX_LEVEL = Param.integer("max_level", "最高等级")
            .help("注册进原版的最高等级；附魔台和村民交易的随机等级也以它为上限")
            .unit(Param.Unit.LEVEL).range(1, 255).required().restart().build();
    public static final Param<Integer> ANVIL_COST = Param.integer("anvil_cost", "铁砧费用")
            .help("原版铁砧的基础费用").min(0).required().restart().build();
    public static final Param<String> APPLICABLE_ITEMS = Param.enumeration("applicable_items", "适用物品",
                    Arrays.stream(ApplicableItem.values()).map(Enum::name).toList())
            .help("注册时的物品标签；写成列表只取第一项；其他值会落到「有耐久的物品」")
            .required().restart().build();
    public static final Param<String> TRIGGER = Param.enumeration("trigger", "触发器",
                    Arrays.stream(Trigger.values()).map(Enum::name).toList())
            .help("不分大小写；不写或写错则永不触发").build();
    public static final Param<String> TARGET = Param.enumeration("target", "目标",
                    Arrays.stream(TargetType.values()).map(Enum::name).toList())
            .help("从触发事件里取被命中的实体再按类型过滤；事件没有被命中的实体时作用于自己").build();
    public static final Param<Long> COOLDOWN = Param.longInt("cooldown", "冷却")
            .help("全部动作成功后才进入冷却；PASSIVE、SNEAK_HOLD、SPRINT_HOLD 的冷却兼作触发周期（最短 1 秒）")
            .unit(Param.Unit.MS).min(0).def(0)
            .danger("PASSIVE 附魔的冷却就是触发周期，同一玩家身上所有 PASSIVE 附魔共用一个周期（取最大的 cooldown），改它会影响该玩家的其他被动附魔")
            .dangerWhen("trigger", List.of("PASSIVE"))
            .build();
    public static final Param<Map<String, Object>> ACTIONS = Param.map("actions", "动作", null)
            .help("键名自定（占位符用它引用），值是一个动作（type + 参数，见 schema 的 actions）；按顺序执行，任一失败则中止后续动作且不进冷却")
            .build();
    public static final Param<List<String>> CONFLICTS = Param.registryList("conflicts", "冲突附魔", Param.Registry.ENCHANTMENT)
            .help("完整附魔 key，如 minecraft:protection、meowsenchants:freeze").def(List.of()).restart().build();

    public static final List<String> EFFECT_LOCATIONS = List.of("SELF", "TARGET", "BLOCK");

    public static final Param<String> SOUND_TYPE = Param.registry("type", "音效", Param.Registry.SOUND)
            .help("原版音效注册名，如 entity.player.attack.crit；不写则不播放").build();
    public static final Param<Double> SOUND_VOLUME = Param.number("volume", "音量").min(0).def(1.0).build();
    public static final Param<Double> SOUND_PITCH = Param.number("pitch", "音调").range(0, 2).def(1.0).build();
    public static final Param<String> SOUND_LOCATION = Param.enumeration("location", "声源位置", EFFECT_LOCATIONS)
            .help("SELF 自己；TARGET 第一个目标；BLOCK 事件方块；取不到时退回自己").def("SELF").build();
    public static final Param<Map<String, Object>> SOUND = Param.section("sound", "音效",
                    SOUND_TYPE, SOUND_VOLUME, SOUND_PITCH, SOUND_LOCATION)
            .help("全部动作成功后只对触发者播放").build();

    public static final Param<String> PARTICLE_TYPE = Param.registry("type", "粒子", Param.Registry.PARTICLE)
            .help("原版粒子注册名或 Bukkit 枚举名；不写则不生成").build();
    public static final Param<Integer> PARTICLE_COUNT = Param.integer("count", "数量").unit(Param.Unit.COUNT).min(0)
            .def(10).build();
    public static final Param<Double> PARTICLE_SPREAD = Param.number("spread", "散布").unit(Param.Unit.BLOCK).min(0)
            .def(0.5).build();
    public static final Param<Double> PARTICLE_SPEED = Param.number("speed", "速度").min(0).def(0.1).build();
    public static final Param<String> PARTICLE_LOCATION = Param.enumeration("location", "生成位置", EFFECT_LOCATIONS)
            .help("SELF 自己；TARGET 第一个目标；BLOCK 事件方块；再上移 1 格").def("SELF").build();
    public static final Param<String> PARTICLE_MATERIAL = Param.string("material", "材质")
            .help("方块/物品类粒子需要，如 minecraft:nether_wart_block").build();
    public static final Param<String> PARTICLE_COLOR = Param.string("color", "颜色")
            .help("十六进制 RRGGBB（可带 #），dust 等粒子需要").build();
    public static final Param<String> PARTICLE_TO_COLOR = Param.string("to_color", "渐变终点颜色")
            .help("dust_color_transition 需要").build();
    public static final Param<Double> PARTICLE_SIZE = Param.number("size", "大小").min(0).def(1.0).build();
    public static final Param<Integer> PARTICLE_VALUE = Param.integer("value", "整数参数")
            .help("需要整数数据的粒子使用").build();
    public static final Param<Map<String, Object>> PARTICLE_DATA = Param.section("data", "粒子数据",
            PARTICLE_MATERIAL, PARTICLE_COLOR, PARTICLE_TO_COLOR, PARTICLE_SIZE, PARTICLE_VALUE).build();
    public static final Param<Map<String, Object>> PARTICLE = Param.section("particle", "粒子",
                    PARTICLE_TYPE, PARTICLE_COUNT, PARTICLE_SPREAD, PARTICLE_SPEED, PARTICLE_LOCATION, PARTICLE_DATA)
            .help("全部动作成功后在世界里生成，所有人可见").build();

    public static final Param<Boolean> TABLE_ENABLED = Param.bool("enabled", "附魔台可出").def(false).build();
    public static final Param<Double> TABLE_CHANCE = Param.number("chance", "附魔台基础概率")
            .help("从最高级往下逐级判定，每级实际概率 = chance ÷ 2^等级（chance 0.01 时一级约 0.5%）")
            .unit(Param.Unit.RATIO).range(0, 1).def(0.0).build();
    public static final Param<Integer> TABLE_MIN_COST = Param.integer("min_cost", "最低附魔等级")
            .help("本次附魔消耗的等级低于它时不出（只影响插件自己的附魔台逻辑）").unit(Param.Unit.LEVEL).min(0).def(0).build();
    public static final Param<Integer> TABLE_MAX_COST = Param.integer("max_cost", "最高附魔等级")
            .help("本次附魔消耗的等级高于它时不出").unit(Param.Unit.LEVEL).min(0).def(30).build();
    public static final Param<Map<String, Object>> ENCHANTING_TABLE = Param.section("enchanting_table", "附魔台",
            TABLE_ENABLED, TABLE_CHANCE, TABLE_MIN_COST, TABLE_MAX_COST).build();
    public static final Param<Double> LEGACY_TABLE_CHANCE = Param.number("enchanting_table_chance", "附魔台概率（旧写法）")
            .help("旧写法：大于 0 时等同 enchanting_table.enabled: true + chance；有 enchanting_table 段时以段里的为准")
            .unit(Param.Unit.RATIO).range(0, 1).build();

    public static final Param<Boolean> TRADE_ENABLED = Param.bool("enabled", "村民交易可出")
            .help("图书管理员获得附魔书交易时按概率换成本附魔")
            .danger("从 true 改成 false 后，已有本附魔交易的村民在玩家交易时会被插件杀死").def(false).build();
    public static final Param<Double> TRADE_CHANCE = Param.number("chance", "交易概率")
            .unit(Param.Unit.RATIO).range(0, 1).def(0.0).build();
    public static final Param<Boolean> TRADE_TREASURE = Param.bool("treasure", "宝藏价")
            .help("true 时绿宝石价格翻倍").def(false).build();
    public static final Param<Map<String, Object>> VILLAGER_TRADES = Param.section("villager_trades", "村民交易",
            TRADE_ENABLED, TRADE_CHANCE, TRADE_TREASURE).build();

    public static final List<String> SLOT_GROUPS = List.of("ANY", "MAINHAND", "OFFHAND", "HAND", "FEET", "LEGS", "CHEST",
            "HEAD", "ARMOR", "BODY", "SADDLE");
    public static final Param<Integer> REGISTRY_WEIGHT = Param.integer("weight", "权重")
            .help("原版附魔权重（原版附魔台、交易等随机选附魔时的相对概率；插件自己的附魔台逻辑不看它）")
            .range(1, 1024).def(1).restart().build();
    public static final Param<Integer> REGISTRY_MIN_COST = Param.integer("min_cost", "原版最低费用")
            .help("原版附魔台的最低附魔等级 = min_cost + min_cost_per_level × (等级 − 1)").unit(Param.Unit.LEVEL)
            .min(0).def(30).restart().build();
    public static final Param<Integer> REGISTRY_MIN_COST_PER_LEVEL = Param.integer("min_cost_per_level", "原版最低费用每级")
            .unit(Param.Unit.LEVEL).min(0).def(0).restart().build();
    public static final Param<Integer> REGISTRY_MAX_COST = Param.integer("max_cost", "原版最高费用")
            .help("原版附魔台的最高附魔等级 = max_cost + max_cost_per_level × (等级 − 1)").unit(Param.Unit.LEVEL)
            .min(0).def(30).restart().build();
    public static final Param<Integer> REGISTRY_MAX_COST_PER_LEVEL = Param.integer("max_cost_per_level", "原版最高费用每级")
            .unit(Param.Unit.LEVEL).min(0).def(0).restart().build();
    public static final Param<String> REGISTRY_ACTIVE_SLOTS = Param.enumeration("active_slots", "生效槽位", SLOT_GROUPS)
            .help("注册进原版的生效槽位组；插件自己的触发器始终检查主手、副手和四件盔甲").def("ANY").restart().build();
    public static final Param<Map<String, Object>> REGISTRY = Param.section("registry", "注册属性",
                    REGISTRY_WEIGHT, REGISTRY_MIN_COST, REGISTRY_MIN_COST_PER_LEVEL, REGISTRY_MAX_COST,
                    REGISTRY_MAX_COST_PER_LEVEL, REGISTRY_ACTIVE_SLOTS)
            .help("写进原版注册表的属性，只在启动时生效").restart().build();

    public static final Param<String> MECHANIC = Param.string("mechanic", "专用机制标记")
            .help("只用于箭雨（landing_arrow_rain_v1）和跟踪（tracking_v1）：写了才启用该专用机制，此时 actions 应留空")
            .build();

    /** 顶层字段，按 yml 里常见的顺序。 */
    public static final List<Param<?>> PARAMS = List.of(
            NAME, DESCRIPTION, MAX_LEVEL, ANVIL_COST, ENCHANTING_TABLE, LEGACY_TABLE_CHANCE, VILLAGER_TRADES,
            APPLICABLE_ITEMS, TRIGGER, TARGET, COOLDOWN, CONFLICTS, SOUND, PARTICLE, REGISTRY, MECHANIC, ACTIONS);

    // ---------------------------------------------------------------- 导出

    public static Map<String, Object> toJson(String pluginVersion) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("plugin", "MeowsEnchants");
        root.put("pluginVersion", pluginVersion);

        List<Object> triggers = new ArrayList<>();
        for (Trigger trigger : Trigger.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", trigger.name());
            item.put("label", trigger.label());
            item.put("periodic", trigger.periodic());
            triggers.add(item);
        }
        root.put("triggers", triggers);

        List<Object> targets = new ArrayList<>();
        for (TargetType target : TargetType.values()) {
            targets.add(idLabel(target.name(), target.label()));
        }
        root.put("targets", targets);

        List<Object> items = new ArrayList<>();
        for (ApplicableItem item : ApplicableItem.values()) {
            items.add(idLabel(item.name(), item.label()));
        }
        root.put("applicableItems", items);

        root.put("enchant", Map.of("params", params(PARAMS)));
        root.put("actionCommon", params(ActionRegistry.COMMON_PARAMS));

        Map<String, Object> actions = new LinkedHashMap<>();
        for (ActionRegistry.ActionType type : ActionRegistry.types()) {
            if (!type.editable()) continue;
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("label", type.label());
            action.put("params", params(type.params()));
            actions.put(type.id(), action);
        }
        root.put("actions", actions);

        Map<String, Object> mechanics = new LinkedHashMap<>();
        mechanics.put(ArrowRainConfig.SECTION, mechanic(ArrowRainConfig.FILE, ArrowRainConfig.MECHANIC,
                ArrowRainConfig.PARAMS));
        mechanics.put(TrackingConfig.SECTION, mechanic(TrackingConfig.FILE, TrackingConfig.MECHANIC,
                TrackingSettings.PARAMS));
        root.put("mechanics", mechanics);

        Map<String, Object> global = new LinkedHashMap<>();
        global.put("file", "config.yml");
        global.put("params", params(GlobalSettings.PARAMS));
        root.put("global", global);
        return root;
    }

    private static Map<String, Object> idLabel(String id, String label) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("label", label);
        return map;
    }

    private static Map<String, Object> mechanic(String file, String flag, List<Param<?>> params) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("file", file);
        map.put("flag", flag);
        map.put("params", params(params));
        return map;
    }

    static List<Object> params(List<Param<?>> params) {
        List<Object> list = new ArrayList<>(params.size());
        for (Param<?> param : params) list.add(param.toJson());
        return list;
    }
}
