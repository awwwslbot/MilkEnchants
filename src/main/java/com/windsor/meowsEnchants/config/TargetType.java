package com.windsor.meowsEnchants.config;

/** 附魔 yml 的 {@code target} 可选值。事件没有被命中的实体时（PASSIVE 等）一律作用于自己。 */
public enum TargetType {
    SELF("自己"),
    PLAYERS("被命中的玩家"),
    MOBS("被命中的非玩家生物"),
    ALL_ENTITIES("被命中的实体（自己除外）"),
    NONE("无");

    private final String label;

    TargetType(String label) { this.label = label; }

    public String label() { return label; }
}
