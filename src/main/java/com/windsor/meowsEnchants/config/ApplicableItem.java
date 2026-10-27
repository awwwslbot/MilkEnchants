package com.windsor.meowsEnchants.config;

/**
 * 附魔 yml 的 {@code applicable_items} 合法值，对应注册时的物品标签（{@link EnchantConfig#resolveItemTagKey()}）。
 * 其他任何值都落到 {@code #enchantable/durability}（有耐久的物品）。
 */
public enum ApplicableItem {
    SWORDS("剑"),
    AXES("斧"),
    MACE("锤"),
    TRIDENT("三叉戟"),
    MELEE_WEAPON("近战武器（#enchantable/melee_weapon）"),
    SHARP_WEAPON("剑、长矛、斧头"),
    WEAPON("武器（#enchantable/weapon）"),
    PICKAXES("镐"),
    SHOVELS("锹"),
    HOES("锄"),
    BOW("弓"),
    CROSSBOW("弩"),
    RANGED("弓、弩"),
    TOOLS("挖掘工具（#enchantable/mining）"),
    FISHING_ROD("钓鱼竿"),
    HELMETS("头盔"),
    CHESTPLATES("胸甲"),
    LEGGINGS("护腿"),
    BOOTS("靴子"),
    ARMOR("盔甲"),
    EQUIPPABLE("可穿戴物品（#enchantable/equippable）");

    private final String label;

    ApplicableItem(String label) { this.label = label; }

    public String label() { return label; }
}
