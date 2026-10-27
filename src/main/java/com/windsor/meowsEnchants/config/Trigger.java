package com.windsor.meowsEnchants.config;

/** 附魔 yml 的 {@code trigger} 可选值，由各监听器触发。 */
public enum Trigger {
    ATTACK_ENTITY("攻击实体", false),
    KILL_ENTITY("击杀实体", false),
    TAKE_DAMAGE("受到实体伤害", false),
    SHIELD_BLOCK("盾牌格挡", false),
    BOW_SHOOT("弓或弩射击", false),
    PROJECTILE_HIT("弹射物命中", false),
    FISHING("钓到东西", false),
    FISH_BITE("鱼咬钩", false),
    ITEM_SWAP("交换主副手", false),
    CONSUME("吃喝", false),
    DEATH("死亡", false),
    RESPAWN("重生", false),
    BLOCK_DIG("左键方块", false),
    BLOCK_BREAK("破坏方块", false),
    BLOCK_PLACE("放置方块", false),
    RIGHT_CLICK("右键", false),
    SNEAK("开始潜行", false),
    SNEAK_HOLD("持续潜行", true),
    SPRINT("开始疾跑", false),
    SPRINT_HOLD("持续疾跑", true),
    PASSIVE("被动（周期）", true);

    private final String label;
    private final boolean periodic;

    Trigger(String label, boolean periodic) {
        this.label = label;
        this.periodic = periodic;
    }

    public String label() { return label; }

    /** 周期触发：冷却兼作周期。 */
    public boolean periodic() { return periodic; }
}
