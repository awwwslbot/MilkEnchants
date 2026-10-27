package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

public class AttributeModifierAction implements Action {
    public static final Param<String> ATTRIBUTE = Param.registry("attribute", "属性", Param.Registry.ATTRIBUTE)
            .help("原版属性名（minecraft 命名空间，不分大小写），如 movement_speed、scale").required().build();
    public static final Param<ScalingFunction> AMOUNT = Param.scalableNumber("amount", "数值")
            .help("修饰符数值；为 0 时不添加修饰符").required().build();
    public static final Param<String> OPERATION = Param.enumeration("operation", "运算方式",
                    List.of("ADD_NUMBER", "ADD_SCALAR", "MULTIPLY_SCALAR_1"))
            .help("ADD_NUMBER 直接加；ADD_SCALAR 按基础值比例加；MULTIPLY_SCALAR_1 按总值乘 (1+数值)").required().build();
    public static final Param<String> KEY = Param.string("key", "修饰符键")
            .help("写进物品的修饰符键为 attr_<附魔>_<key>；同一附魔内唯一").required()
            .danger("改名后旧修饰符按旧键留在已有物品上，会和新修饰符叠加；只在新建动作时填写，之后不要改").build();
    public static final Param<Boolean> EQUIPPED = Param.bool("equipped", "仅穿戴时生效")
            .help("true 时修饰符槽位组为盔甲（ARMOR）；false 时为任意槽位（ANY），拿在手上也生效").def(false).build();
    public static final Param<Map<String, Object>> CONDITIONS = Param.section("conditions", "条件", EQUIPPED).build();
    public static final List<Param<?>> PARAMS = List.of(ATTRIBUTE, AMOUNT, OPERATION, KEY, CONDITIONS);

    private final Attribute attribute;
    private final ScalingFunction amount;
    private final AttributeModifier.Operation operation;
    private final String key;
    private final boolean equipped;

    public AttributeModifierAction(Map<String, Object> params) {
        String attrName = ATTRIBUTE.read(params);
        NamespacedKey attrKey = NamespacedKey.minecraft(attrName.toLowerCase());
        Attribute attr = Registry.ATTRIBUTE.get(attrKey);
        if (attr == null) {
            throw new IllegalArgumentException("Unknown attribute: " + attrName);
        }
        this.attribute = attr;

        this.amount = AMOUNT.read(params);
        this.operation = getOperation(OPERATION.read(params));
        this.key = KEY.read(params);
        this.equipped = EQUIPPED.read(CONDITIONS.read(params));
    }

    static AttributeModifier.Operation getOperation(String op) {
        return switch (op.toUpperCase()) {
            case "ADD_NUMBER" -> AttributeModifier.Operation.ADD_NUMBER;
            case "ADD_SCALAR" -> AttributeModifier.Operation.ADD_SCALAR;
            case "MULTIPLY_SCALAR_1" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            default -> throw new IllegalArgumentException("Unknown operation: " + op);
        };
    }

    @Override
    public boolean execute(ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        Plugin plugin = context.getPlugin();
        if (plugin == null) return false;

        AttributeModifierHelper.applyModifierForPlayer(
                player,
                plugin,
                context.getEnchantKey(),
                attribute,
                amount,
                operation,
                key,
                equipped
        );
        return true;
    }
}