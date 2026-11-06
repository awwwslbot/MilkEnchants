package com.windsor.meowsEnchants.arrowrain;

import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import java.util.List;
import java.util.Map;

/**
 * Tunables under {@code arrow_rain:} in arrow_rain.yml; a missing key keeps its default, which is the milk.12
 * behaviour. Per-level values follow the general scaling rules, so the default lists cap the rain at level 5.
 */
public record ArrowRainSettings(
        ScalingFunction arrows,
        ScalingFunction radius,
        ScalingFunction extraDurability,
        ScalingFunction releaseTicks,
        double height,
        double minHeight,
        double speed,
        int lifetimeTicks,
        int sourceMaxTicks,
        String trailParticle,
        int trailCount,
        double trailOffset,
        double trailSpeed) {

    public static final Param<ScalingFunction> ARROWS = Param.scalableInt("arrows", "箭数")
            .help("每次箭雨最多的雨箭数；无无限时同时额外消耗这么多支箭，背包不够时按实际扣到的数量下落")
            .unit(Param.Unit.COUNT).min(0).warnAbove(256).def(List.of(8, 16, 32, 64, 128)).build();
    public static final Param<ScalingFunction> RADIUS = Param.scalableNumber("radius", "落点半径")
            .help("以原箭命中点为圆心的水平圆盘，按面积均匀随机").unit(Param.Unit.BLOCK).min(0).warnAbove(16)
            .def(List.of(1.5, 2.5, 3.5, 4.5, 5.5)).build();
    public static final Param<ScalingFunction> EXTRA_DURABILITY = Param.scalableInt("extra_durability", "额外耐久")
            .help("每次成功射出在原版 1 点之外额外扣弓的耐久（受耐久附魔影响）").min(0)
            .def(List.of(1, 3, 7, 15, 31)).build();
    public static final Param<ScalingFunction> RELEASE_TICKS = Param.scalableInt("release_ticks", "释放窗口")
            .help("雨箭在第 1 tick 到第这么多 tick 之间均匀生成").unit(Param.Unit.TICK).min(1)
            .def(List.of(20, 40, 60, 80, 100)).build();
    public static final Param<Double> HEIGHT = Param.number("height", "最高释放高度")
            .help("头顶空间够时雨箭生成在命中点上方多少格").unit(Param.Unit.BLOCK).min(0).def(20.0).build();
    public static final Param<Double> MIN_HEIGHT = Param.number("min_height", "最低头顶空间")
            .help("从命中点高度量到这一列天花板（往上第一块挡箭的方块的下沿，世界建筑上限也算）不够这么多格时，这支雨箭不生成；"
                    + "够但不到 height + 0.5 格时，雨箭生成在天花板下 0.5 格")
            .unit(Param.Unit.BLOCK).min(0).def(4.0).build();
    public static final Param<Double> SPEED = Param.number("speed", "下落初速度")
            .help("格/tick，之后按原版重力和阻力").min(0).def(2.5).build();
    public static final Param<Integer> LIFETIME_TICKS = Param.integer("lifetime_ticks", "雨箭寿命")
            .help("雨箭生成后多少 tick 被清除（落地后可见的时间也在其中）；轨迹粒子最多持续这么久")
            .unit(Param.Unit.TICK).min(1).def(30).build();
    public static final Param<Integer> SOURCE_MAX_TICKS = Param.integer("source_max_ticks", "原箭最长等待")
            .help("原箭射出后这么久还没命中就放弃这次箭雨").unit(Param.Unit.TICK).min(1).def(1200).build();
    public static final Param<String> TRAIL_TYPE = Param.registry("type", "轨迹粒子", Param.Registry.PARTICLE)
            .help("Bukkit 粒子枚举名；需要额外数据的粒子会退回 CRIT").def("CRIT").build();
    public static final Param<Integer> TRAIL_COUNT = Param.integer("count", "每 tick 粒子数").unit(Param.Unit.COUNT)
            .min(0).def(2).build();
    public static final Param<Double> TRAIL_OFFSET = Param.number("offset", "粒子散布").unit(Param.Unit.BLOCK).min(0)
            .def(0.02).build();
    public static final Param<Double> TRAIL_SPEED = Param.number("speed", "粒子速度").min(0).def(0.0).build();
    public static final Param<Map<String, Object>> TRAIL = Param.section("trail", "轨迹",
                    TRAIL_TYPE, TRAIL_COUNT, TRAIL_OFFSET, TRAIL_SPEED)
            .help("每支雨箭飞行时每 tick 生成，所有人可见；命中或被跟踪锁定后停止").build();

    /** {@code arrow_rain:} 段的全部参数。 */
    public static final List<Param<?>> PARAMS = List.of(ARROWS, RADIUS, EXTRA_DURABILITY, RELEASE_TICKS, HEIGHT,
            MIN_HEIGHT, SPEED, LIFETIME_TICKS, SOURCE_MAX_TICKS, TRAIL);

    public static final ArrowRainSettings DEFAULTS = fromYaml(null);

    /** {@code arrow_rain:} 段解析后的原始 map；null 时全部取默认。写法不合法的项用默认。 */
    public static ArrowRainSettings fromYaml(Map<String, Object> section) {
        Map<String, Object> s = section == null ? Map.of() : section;
        Map<String, Object> trail = TRAIL.read(s);
        return new ArrowRainSettings(
                lenient(ARROWS, s),
                lenient(RADIUS, s),
                lenient(EXTRA_DURABILITY, s),
                lenient(RELEASE_TICKS, s),
                HEIGHT.read(s),
                Math.max(0.0, MIN_HEIGHT.read(s)),
                SPEED.read(s),
                Math.max(1, LIFETIME_TICKS.read(s)),
                Math.max(1, SOURCE_MAX_TICKS.read(s)),
                TRAIL_TYPE.read(trail),
                Math.max(0, TRAIL_COUNT.read(trail)),
                Math.max(0.0, TRAIL_OFFSET.read(trail)),
                Math.max(0.0, TRAIL_SPEED.read(trail)));
    }

    private static ScalingFunction lenient(Param<ScalingFunction> param, Map<String, Object> section) {
        ScalingFunction value = param.convert(param.raw(section));
        return value != null ? value : param.defaultValue();
    }
}
