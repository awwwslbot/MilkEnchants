package com.windsor.meowsEnchants.config;

import com.windsor.meowsEnchants.schema.Param;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@code plugins/MeowsEnchants/config.yml} 的全部可配置项；运行时整体替换。 */
public record GlobalSettings(
        boolean skylliaCompatibility,
        int skylliaRegionSize,
        Set<String> skylliaProtectedActions,
        boolean tableUsePlayerLevel,
        double tablePlayerLevelFactor,
        boolean tableBalanceBooks,
        double tableLevelChanceDivisor,
        double tableBookDivisor,
        int tradePriceMinBase,
        int tradePriceMinPerLevel,
        int tradePriceMaxBase,
        int tradePriceMaxPerLevel,
        int tradeTreasureMultiplier,
        int tradePriceFloor,
        int tradePriceCap,
        int tradeDefaultMaxUses,
        String tradePollutedMessage,
        int passiveMinPeriodTicks,
        int holdMinPeriodTicks) {

    public static final Param<Boolean> SKYLLIA_COMPATIBILITY = Param.bool("skyllia_compatibility", "Skyllia 空岛兼容")
            .help("开启后非 OP 在主城区域内禁用 skyllia_protected_actions 里的动作，传送、冲击波、爆炸还要求玩家在岛上（需 PlaceholderAPI）；范围、多格破坏和传送不跨岛屿")
            .def(false).build();
    public static final Param<Integer> SKYLLIA_REGION_SIZE = Param.integer("skyllia_region_size", "Skyllia 岛屿边长")
            .help("主城区域为 (0,0)–(边长,边长)，岛屿按边长对齐分格").unit(Param.Unit.BLOCK).min(1).def(512).build();
    public static final Param<List<String>> SKYLLIA_PROTECTED_ACTIONS = Param.stringList("skyllia_protected_actions",
                    "主城禁用的动作")
            .help("动作类型名；开启 Skyllia 兼容时，非 OP 在主城区域内触发附魔会跳过这些动作")
            .def(List.of("explosion", "hook", "multi_arrow", "multi_break", "potion_effect", "shockwave", "till",
                    "vacuum_drops"))
            .build();

    public static final Param<Boolean> USE_PLAYER_LEVEL = Param.bool("use_player_level", "附魔台按玩家等级")
            .help("开启后附魔台概率再乘以 玩家等级 ÷ player_level_factor").def(false).build();
    public static final Param<Double> PLAYER_LEVEL_FACTOR = Param.number("player_level_factor", "玩家参照等级")
            .help("小于等于 0 时按 500 处理").unit(Param.Unit.LEVEL).def(500.0).build();
    public static final Param<Boolean> BALANCE_BOOKS = Param.bool("balance_books", "附魔书概率降低")
            .help("开启后给书附魔时概率再除以 book_divisor").def(false).build();
    public static final Param<Double> LEVEL_CHANCE_DIVISOR = Param.number("level_chance_divisor", "逐级概率底数")
            .help("从最高级往下逐级判定，每级概率 = 附魔的 chance ÷ 底数^等级").min(1).def(2.0).build();
    public static final Param<Double> BOOK_DIVISOR = Param.number("book_divisor", "附魔书概率除数")
            .help("balance_books 开启时生效").min(1).def(4.0).build();
    public static final Param<Map<String, Object>> ENCHANTING_TABLE = Param.section("enchanting_table", "附魔台",
            USE_PLAYER_LEVEL, PLAYER_LEVEL_FACTOR, BALANCE_BOOKS, LEVEL_CHANCE_DIVISOR, BOOK_DIVISOR).build();

    public static final Param<Integer> PRICE_MIN_BASE = Param.integer("price_min_base", "最低价基数")
            .help("附魔书价格在 [最低价, 最高价] 内均匀随机；最低价 = 基数 + 每级 × 等级").min(0).def(2).build();
    public static final Param<Integer> PRICE_MIN_PER_LEVEL = Param.integer("price_min_per_level", "最低价每级")
            .min(0).def(3).build();
    public static final Param<Integer> PRICE_MAX_BASE = Param.integer("price_max_base", "最高价基数")
            .help("最高价 = 基数 + 每级 × 等级，不低于最低价").min(0).def(6).build();
    public static final Param<Integer> PRICE_MAX_PER_LEVEL = Param.integer("price_max_per_level", "最高价每级")
            .min(0).def(13).build();
    public static final Param<Integer> TREASURE_MULTIPLIER = Param.integer("treasure_multiplier", "宝藏价倍数")
            .help("附魔的 villager_trades.treasure 为 true 时价格乘以它").min(1).def(2).build();
    public static final Param<Integer> PRICE_FLOOR = Param.integer("price_floor", "价格下限").min(1).max(64).def(1).build();
    public static final Param<Integer> PRICE_CAP = Param.integer("price_cap", "价格上限")
            .help("绿宝石一组最多 64").min(1).max(64).def(64).build();
    public static final Param<Integer> DEFAULT_MAX_USES = Param.integer("default_max_uses", "默认最大交易次数")
            .help("原交易没有次数上限时使用").unit(Param.Unit.COUNT).min(1).def(12).build();
    public static final Param<String> POLLUTED_MESSAGE = Param.text("polluted_message", "污染交易提示")
            .help("玩家和带有未开放附魔交易的村民交易时，交易被取消、村民被杀，并向玩家发送这句；& 颜色码")
            .def("&f[&b附魔&f] &c该村民交易已被污染！").build();
    public static final Param<Map<String, Object>> VILLAGER_TRADES = Param.section("villager_trades", "村民交易",
            PRICE_MIN_BASE, PRICE_MIN_PER_LEVEL, PRICE_MAX_BASE, PRICE_MAX_PER_LEVEL, TREASURE_MULTIPLIER,
            PRICE_FLOOR, PRICE_CAP, DEFAULT_MAX_USES, POLLUTED_MESSAGE).build();

    public static final Param<Integer> PASSIVE_MIN_PERIOD = Param.integer("passive_min_period_ticks", "PASSIVE 最短周期")
            .help("PASSIVE 附魔的触发周期 = 玩家身上 PASSIVE 附魔的最大冷却，但不短于此值；进服或 reload 时确定")
            .unit(Param.Unit.TICK).min(1).def(20).build();
    public static final Param<Integer> HOLD_MIN_PERIOD = Param.integer("hold_min_period_ticks", "持续潜行/疾跑最短周期")
            .help("SNEAK_HOLD / SPRINT_HOLD 的触发周期 = 同触发器附魔的最大冷却，但不短于此值；首次触发也要等一个周期")
            .unit(Param.Unit.TICK).min(1).def(20).build();
    public static final Param<Map<String, Object>> TRIGGERS = Param.section("triggers", "触发周期",
            PASSIVE_MIN_PERIOD, HOLD_MIN_PERIOD).build();

    public static final List<Param<?>> PARAMS = List.of(SKYLLIA_COMPATIBILITY, SKYLLIA_REGION_SIZE,
            SKYLLIA_PROTECTED_ACTIONS, ENCHANTING_TABLE, VILLAGER_TRADES, TRIGGERS);

    public static final GlobalSettings DEFAULTS = from(Map.of());

    public GlobalSettings {
        skylliaProtectedActions = Set.copyOf(skylliaProtectedActions);
    }

    public static GlobalSettings from(Map<String, Object> yaml) {
        Map<String, Object> table = ENCHANTING_TABLE.read(yaml);
        Map<String, Object> trades = VILLAGER_TRADES.read(yaml);
        Map<String, Object> triggers = TRIGGERS.read(yaml);
        double factor = PLAYER_LEVEL_FACTOR.read(table);
        return new GlobalSettings(
                SKYLLIA_COMPATIBILITY.read(yaml),
                Math.max(1, SKYLLIA_REGION_SIZE.read(yaml)),
                Set.copyOf(SKYLLIA_PROTECTED_ACTIONS.read(yaml)),
                USE_PLAYER_LEVEL.read(table),
                factor <= 0 ? PLAYER_LEVEL_FACTOR.defaultValue() : factor,
                BALANCE_BOOKS.read(table),
                LEVEL_CHANCE_DIVISOR.read(table),
                BOOK_DIVISOR.read(table),
                PRICE_MIN_BASE.read(trades),
                PRICE_MIN_PER_LEVEL.read(trades),
                PRICE_MAX_BASE.read(trades),
                PRICE_MAX_PER_LEVEL.read(trades),
                TREASURE_MULTIPLIER.read(trades),
                PRICE_FLOOR.read(trades),
                PRICE_CAP.read(trades),
                Math.max(1, DEFAULT_MAX_USES.read(trades)),
                POLLUTED_MESSAGE.read(trades),
                Math.max(1, PASSIVE_MIN_PERIOD.read(triggers)),
                Math.max(1, HOLD_MIN_PERIOD.read(triggers)));
    }

    /** 附魔书的绿宝石价格，{@code roll} 返回 [0, bound) 的随机整数。 */
    public int tradePrice(int level, boolean treasure, java.util.function.IntUnaryOperator roll) {
        int min = tradePriceMinBase + level * tradePriceMinPerLevel;
        int max = Math.max(min, tradePriceMaxBase + level * tradePriceMaxPerLevel);
        int price = min + roll.applyAsInt(max - min + 1);
        if (treasure) {
            price *= tradeTreasureMultiplier;
        }
        return Math.clamp(price, tradePriceFloor, Math.max(tradePriceFloor, tradePriceCap));
    }

    /** 附魔台第 level 级的判定概率（尚未乘玩家等级、未除书本系数）。 */
    public double tableLevelChance(double chance, int level) {
        return chance / Math.pow(tableLevelChanceDivisor, level);
    }
}
