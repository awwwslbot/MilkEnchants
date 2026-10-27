package com.windsor.meowsEnchants.actions;

import com.windsor.meowsEnchants.actions.impl.*;
import com.windsor.meowsEnchants.schema.Param;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 动作注册表，用于根据类型名称创建对应的 Action 实例。
 * <p>
 * 每种动作同时登记中文名和参数声明；{@code editor/enchant-schema.json} 的 {@code actions} 由这里导出。
 */
public final class ActionRegistry {

    /**
     * 一种动作类型。
     *
     * @param editable false 时不进 schema（编辑器不开放），插件照常执行
     */
    public record ActionType(String id, String label, List<Param<?>> params,
                             Function<Map<String, Object>, Action> factory, boolean editable) {}

    /** 每个动作都可写的触发概率；由 {@code EnchantTriggerHelper} 在执行动作前统一掷骰。 */
    public static final Param<ScalingFunction> CHANCE = Param.scalableNumber("chance", "触发概率")
            .help("执行这个动作前先掷骰，没中视为动作失败：中止后续动作、不进冷却、不播音效粒子")
            .unit(Param.Unit.RATIO).range(0, 1).def(1.0).build();
    /** schema 的 actionCommon。 */
    public static final List<Param<?>> COMMON_PARAMS = List.of(CHANCE);

    private static final Map<String, ActionType> types = new LinkedHashMap<>();

    static {
        add("potion_effect", "药水效果", PotionEffectAction.PARAMS, PotionEffectAction::new);
        add("bonus_damage", "额外伤害", BonusDamageAction.PARAMS, BonusDamageAction::new);
        add("healthsteal", "生命偷取", HealthstealAction.PARAMS, HealthstealAction::new);
        add("teleport", "传送", TeleportAction.PARAMS, TeleportAction::new);
        add("explosion", "爆炸", ExplosionAction.PARAMS, ExplosionAction::new);
        add("velocity", "速度", VelocityAction.PARAMS, VelocityAction::new);
        add("shockwave", "冲击波", ShockwaveAction.PARAMS, ShockwaveAction::new);
        add("break_armor", "破坏盔甲", BreakArmorAction.PARAMS, BreakArmorAction::new);
        add("drop_head", "掉落头颅", DropHeadAction.PARAMS, DropHeadAction::new);
        add("attribute_modifier", "属性修饰", AttributeModifierAction.PARAMS, AttributeModifierAction::new);
        add("multi_break", "范围破坏", MultiBreakAction.PARAMS, MultiBreakAction::new);
        add("vacuum_drops", "掉落物直入背包", VacuumDropsAction.PARAMS, VacuumDropsAction::new);
        add("replant", "自动补种", ReplantAction.PARAMS, ReplantAction::new);
        add("multi_arrow", "多重箭", MultiArrowAction.PARAMS, MultiArrowAction::new);
        add("repair", "自动修复", RepairAction.PARAMS, RepairAction::new);
        add("hook", "拉拽", HookAction.PARAMS, HookAction::new);
        add("feast", "恢复饥饿", FeastAction.PARAMS, FeastAction::new);
        add("deflect", "偏转弹射物", DeflectAction.PARAMS, DeflectAction::new);
        add("till", "范围方块转换", TillAction.PARAMS, TillAction::new);
        add("reflect_damage", "反弹伤害", ReflectDamageAction.PARAMS, ReflectDamageAction::new);
        types.put("command", new ActionType("command", "执行命令", CommandAction.PARAMS, CommandAction::new, false));
        add("block_break", "瞬间破坏方块", BlockBreakAction.PARAMS, BlockBreakAction::new);
        add("auto_bow", "自动连射", AutoBowAction.PARAMS, AutoBowAction::new);
        add("draw_accelerate", "加速蓄力", DrawAccelerateAction.PARAMS, DrawAccelerateAction::new);
    }

    private ActionRegistry() {}

    private static void add(String id, String label, List<Param<?>> params, Function<Map<String, Object>, Action> factory) {
        types.put(id, new ActionType(id, label, params, factory, true));
    }

    /**
     * 根据类型名称和参数创建 Action 实例。
     *
     * @param type   动作类型（如 "potion_effect"）
     * @param params 动作参数 Map（从 YAML 解析得到）
     * @return Action 实例
     * @throws IllegalArgumentException 如果类型未注册
     */
    public static Action create(String type, Map<String, Object> params) {
        ActionType actionType = types.get(type);
        if (actionType == null) {
            throw new IllegalArgumentException("Unknown action type: " + type);
        }
        return actionType.factory().apply(params);
    }

    /** 已登记的动作类型；不存在时 null。 */
    public static ActionType type(String id) {
        return types.get(id);
    }

    /** 全部动作类型，按登记顺序。 */
    public static Collection<ActionType> types() {
        return Collections.unmodifiableCollection(types.values());
    }

    /**
     * 注册新的动作类型（可扩展）。没有参数声明的类型不进 schema。
     */
    public static void register(String type, Function<Map<String, Object>, Action> factory) {
        types.put(type, new ActionType(type, type, List.of(), factory, false));
    }
}
