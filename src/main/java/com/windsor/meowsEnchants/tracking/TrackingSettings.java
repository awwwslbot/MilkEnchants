package com.windsor.meowsEnchants.tracking;

import com.windsor.meowsEnchants.actions.ListScaling;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.actions.ScalingFunctionLoader;
import com.windsor.meowsEnchants.schema.Param;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** Tunables under {@code tracking:} in tracking.yml; a key missing from an older file keeps its default. */
public record TrackingSettings(
        ScalingFunction radii,
        int scanIntervalTicks,
        int lookaheadTicks,
        double maxTurnDegrees,
        double directAimDistance,
        double giveUpAngleDegrees,
        int maxTrackingTicks,
        int pvpCombatSeconds,
        Set<String> alwaysPvpWorlds,
        String particle,
        int particleCountMin,
        int particleCountMax,
        double particleOffset,
        double particleSpeed,
        double tailOffset,
        String sound,
        String soundCategory,
        float soundVolume,
        float soundPitch,
        Set<String> neverTracked,
        Set<String> neutralUntilAttacking) {

    private static final List<Double> DEFAULT_RADII = List.of(1.5, 2.5, 3.5);
    public static final Param<ScalingFunction> RADIUS = Param.scalableNumber("radius", "判定半径")
            .help("飞行路线两侧的判定半径，量到目标碰撞箱最近的点。写成列表时逐项覆盖默认 [1.5, 2.5, 3.5]：缺的或不大于 0 的项沿用默认的同位项，更高等级取最后一项")
            .unit(Param.Unit.BLOCK).min(0).warnAbove(8).def(DEFAULT_RADII).overlayList()
            .convertWith(TrackingSettings::radiusScaling).build();
    public static final Param<Integer> SCAN_INTERVAL_TICKS = Param.integer("scan_interval_ticks", "搜索间隔")
            .unit(Param.Unit.TICK).min(1).def(5).build();
    public static final Param<Integer> LOOKAHEAD_TICKS = Param.integer("lookahead_ticks", "预测路线长度")
            .help("每次搜索按原版物理预测接下来多少 tick 的飞行路线").unit(Param.Unit.TICK).min(1).def(10).build();
    public static final Param<Double> MAX_TURN_DEGREES = Param.number("max_turn_degrees", "每 tick 最大转角")
            .unit(Param.Unit.DEGREE).range(0.1, 180.0).def(20.0).build();
    public static final Param<Double> DIRECT_AIM_DISTANCE = Param.number("direct_aim_distance", "直接对准距离")
            .help("离目标身体中心这么近时不限转角、直接对准").unit(Param.Unit.BLOCK).min(0.0).def(6.0).build();
    public static final Param<Double> GIVE_UP_ANGLE_DEGREES = Param.number("give_up_angle_degrees", "放弃角度")
            .help("目标和飞行方向的夹角超过它就放弃该目标").unit(Param.Unit.DEGREE).range(0.1, 180.0).def(90.0).build();
    public static final Param<Integer> MAX_TRACKING_TICKS = Param.integer("max_tracking_ticks", "最长跟踪时间")
            .unit(Param.Unit.TICK).min(1).def(200).build();
    public static final Param<Integer> PVP_COMBAT_SECONDS = Param.integer("pvp_combat_seconds", "PvP 状态时长")
            .help("和其他玩家互相造成伤害后多少秒内算 PvP 状态（秒）").min(0).def(15).build();
    public static final Param<List<String>> ALWAYS_PVP_WORLDS = Param.stringList("always_pvp_worlds", "常驻 PvP 世界")
            .help("这些世界里的玩家一直算 PvP 状态；世界 key（milk:pvp）或 Bukkit 世界名，不分大小写")
            .def(List.of("milk:pvp")).build();
    public static final Param<String> PARTICLE_TYPE = Param.registry("type", "粒子", Param.Registry.PARTICLE)
            .help("Bukkit 粒子枚举名；需要额外数据的粒子会退回 ENCHANT").def("ENCHANT").build();
    public static final Param<Integer> PARTICLE_COUNT_MIN = Param.integer("count_min", "每 tick 最少粒子数")
            .unit(Param.Unit.COUNT).min(0).def(1).build();
    public static final Param<Integer> PARTICLE_COUNT_MAX = Param.integer("count_max", "每 tick 最多粒子数")
            .help("小于 count_min 时按 count_min").unit(Param.Unit.COUNT).min(0).def(2).build();
    public static final Param<Double> PARTICLE_OFFSET = Param.number("offset", "粒子散布").unit(Param.Unit.BLOCK)
            .min(0.0).def(0.05).build();
    public static final Param<Double> PARTICLE_SPEED = Param.number("speed", "粒子速度").min(0.0).def(0.0).build();
    public static final Param<Double> TAIL_OFFSET = Param.number("tail_offset", "箭尾距离")
            .help("粒子生成在弹射物身后多远").unit(Param.Unit.BLOCK).min(0.0).def(0.3).build();
    public static final Param<Map<String, Object>> PARTICLE = Param.section("particle", "锁定粒子",
                    PARTICLE_TYPE, PARTICLE_COUNT_MIN, PARTICLE_COUNT_MAX, PARTICLE_OFFSET, PARTICLE_SPEED, TAIL_OFFSET)
            .help("锁定后每 tick 在弹射物身后生成，所有人可见").build();
    public static final Param<String> SOUND_TYPE = Param.registry("type", "提示音", Param.Registry.SOUND)
            .def("block.note_block.chime").build();
    public static final Param<String> SOUND_CATEGORY = Param.enumeration("category", "声音类别",
                    List.of("MASTER", "MUSIC", "RECORDS", "WEATHER", "BLOCKS", "HOSTILE", "NEUTRAL", "PLAYERS",
                            "AMBIENT", "VOICE", "UI"))
            .help("不认识的值按 PLAYERS").def("PLAYERS").build();
    public static final Param<Double> SOUND_VOLUME = Param.number("volume", "音量")
            .help("1.0 已是单个声音的最大响度，更大只会让更远处也听得到").min(0.0).def(1.0).build();
    public static final Param<Double> SOUND_PITCH = Param.number("pitch", "音调").range(0.5, 2.0).def(1.0).build();
    public static final Param<Map<String, Object>> SOUND = Param.section("sound", "锁定提示音",
                    SOUND_TYPE, SOUND_CATEGORY, SOUND_VOLUME, SOUND_PITCH)
            .help("锁定时只对射手播放；同一射手同一 tick 只响一次，雨箭不响").build();

    public static final Param<List<String>> NEVER_TRACKED = Param.registryList("never_tracked", "永不跟踪的生物",
                    Param.Registry.ENTITY_TYPE)
            .help("实体类型（大写枚举名，按类型精确匹配）：这些非敌对生物永远不被选为目标，跟踪烟花的爆炸也不伤它们（对敌对生物无效）；已驯服、拴绳、被玩家骑乘、有名字的生物另有规则")
            .def(List.of("VILLAGER", "WANDERING_TRADER", "IRON_GOLEM", "SNOW_GOLEM", "COPPER_GOLEM", "ALLAY"))
            .danger("删掉一项后，跟踪弓弩的箭会自动射向这种生物，烟花爆炸也会伤到它们（例如村民、铁傀儡）").build();
    public static final Param<List<String>> NEUTRAL_UNTIL_ATTACKING = Param.registryList("neutral_until_attacking",
                    "中立怪（攻击玩家时才跟踪）", Param.Registry.ENTITY_TYPE)
            .help("实体类型（大写枚举名，按类型精确匹配，写 ZOMBIE 不含尸壳、溺尸）：这些敌对生物只在正在攻击玩家时才算目标")
            .def(List.of("ENDERMAN", "ZOMBIFIED_PIGLIN", "PIGLIN"))
            .danger("删掉一项后，跟踪的箭会主动射向没在攻击任何人的这种生物，可能把整群中立怪（如僵尸猪灵）引来").build();

    /** {@code tracking:} 段的全部参数。 */
    public static final List<Param<?>> PARAMS = List.of(RADIUS, SCAN_INTERVAL_TICKS, LOOKAHEAD_TICKS,
            MAX_TURN_DEGREES, DIRECT_AIM_DISTANCE, GIVE_UP_ANGLE_DEGREES, MAX_TRACKING_TICKS, PVP_COMBAT_SECONDS,
            ALWAYS_PVP_WORLDS, PARTICLE, SOUND, NEVER_TRACKED, NEUTRAL_UNTIL_ATTACKING);

    public static final TrackingSettings DEFAULTS = fromYaml(null);

    public TrackingSettings {
        alwaysPvpWorlds = Set.copyOf(alwaysPvpWorlds);
        neverTracked = Set.copyOf(neverTracked);
        neutralUntilAttacking = Set.copyOf(neutralUntilAttacking);
    }

    public static TrackingSettings from(ConfigurationSection section) {
        return fromYaml(section == null ? null : toMap(section));
    }

    /** {@code tracking:} 段解析后的原始 map；null 时全部取默认。 */
    public static TrackingSettings fromYaml(Map<String, Object> section) {
        Map<String, Object> s = section == null ? Map.of() : section;
        Set<String> worlds = new LinkedHashSet<>();
        for (String world : ALWAYS_PVP_WORLDS.read(s)) worlds.add(world.toLowerCase(Locale.ROOT));
        Map<String, Object> particle = PARTICLE.read(s);
        Map<String, Object> sound = SOUND.read(s);
        int countMin = Math.max(0, PARTICLE_COUNT_MIN.read(particle));
        return new TrackingSettings(
                lenient(RADIUS, s),
                Math.max(1, SCAN_INTERVAL_TICKS.read(s)),
                Math.max(1, LOOKAHEAD_TICKS.read(s)),
                Math.clamp(MAX_TURN_DEGREES.read(s), 0.1, 180.0),
                Math.max(0.0, DIRECT_AIM_DISTANCE.read(s)),
                Math.clamp(GIVE_UP_ANGLE_DEGREES.read(s), 0.1, 180.0),
                Math.max(1, MAX_TRACKING_TICKS.read(s)),
                Math.max(0, PVP_COMBAT_SECONDS.read(s)),
                worlds,
                PARTICLE_TYPE.read(particle),
                countMin,
                Math.max(countMin, PARTICLE_COUNT_MAX.read(particle)),
                Math.max(0.0, PARTICLE_OFFSET.read(particle)),
                Math.max(0.0, PARTICLE_SPEED.read(particle)),
                Math.max(0.0, TAIL_OFFSET.read(particle)),
                SOUND_TYPE.read(sound),
                SOUND_CATEGORY.read(sound),
                (float) Math.max(0.0, SOUND_VOLUME.read(sound)),
                (float) Math.clamp(SOUND_PITCH.read(sound), 0.5, 2.0),
                entityTypes(NEVER_TRACKED.read(s)),
                entityTypes(NEUTRAL_UNTIL_ATTACKING.read(s)));
    }

    /** 实体类型名统一成大写、去掉 minecraft: 前缀。 */
    private static Set<String> entityTypes(List<String> names) {
        Set<String> types = new LinkedHashSet<>();
        for (String name : names) {
            String type = name.toUpperCase(Locale.ROOT);
            types.add(type.startsWith("MINECRAFT:") ? type.substring("MINECRAFT:".length()) : type);
        }
        return types;
    }

    /** 写法不合法时用默认（milk.12 对非列表的 radius 同样取默认）。 */
    private static ScalingFunction lenient(Param<ScalingFunction> param, Map<String, Object> section) {
        ScalingFunction value = param.convert(param.raw(section));
        return value != null ? value : param.defaultValue();
    }

    /**
     * 列表写法保留 milk.12 的规则：逐项覆盖默认列表（缺的、不大于 0 的项取默认），更高等级取最后一项。
     * 数字和 {base, per_level} 按通用写法。
     */
    private static ScalingFunction radiusScaling(Object raw) {
        if (raw instanceof List<?> configured) {
            List<Double> radii = new ArrayList<>(DEFAULT_RADII);
            for (int i = 0; i < configured.size(); i++) {
                if (configured.get(i) instanceof Number number && number.doubleValue() > 0.0) {
                    if (i < radii.size()) radii.set(i, number.doubleValue());
                    else radii.add(number.doubleValue());
                }
            }
            return new ListScaling(radii);
        }
        try {
            return ScalingFunctionLoader.load(raw);
        } catch (IllegalArgumentException | ClassCastException e) {
            return null;
        }
    }

    private static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            map.put(key, value instanceof ConfigurationSection child ? toMap(child) : value);
        }
        return map;
    }

    /** Levels above the configured list reuse its last radius. */
    public double radius(int level) {
        return radii.getValue(level);
    }

    public double maxTurnRadians() { return Math.toRadians(maxTurnDegrees); }

    public double giveUpAngleRadians() { return Math.toRadians(giveUpAngleDegrees); }

    public long pvpCombatNanos() { return TimeUnit.SECONDS.toNanos(pvpCombatSeconds); }

    /** Matches either the namespaced world key ({@code milk:pvp}) or the Bukkit world name ({@code milk_pvp}). */
    public boolean isAlwaysPvp(World world) {
        if (world == null) return false;
        return alwaysPvpWorlds.contains(world.getKey().toString().toLowerCase(Locale.ROOT))
                || alwaysPvpWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }
}
