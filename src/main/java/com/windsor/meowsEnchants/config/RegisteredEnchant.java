package com.windsor.meowsEnchants.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动时写进原版注册表的字段（{@code apply: restart}）。注册表此后冻结，reload 拿新配置和它比较，
 * 变了的字段只记入 restartRequired，不生效。
 */
public record RegisteredEnchant(String name, int maxLevel, int anvilCost, String applicableItem,
                                List<String> conflicts, EnchantConfig.RegistrySettings registry) {

    public RegisteredEnchant {
        conflicts = List.copyOf(conflicts);
    }

    public static RegisteredEnchant of(EnchantConfig config) {
        return new RegisteredEnchant(config.getDisplayName(), config.getMaxLevel(), config.getAnvilCost(),
                config.getApplicableItem(), config.getConflictIds(), config.getRegistrySettings());
    }

    /** 和新配置相比变了的字段（schema 键名）；name 只影响物品上的名字，GUI 名照常生效。 */
    public List<String> changedFields(EnchantConfig next) {
        List<String> fields = new ArrayList<>();
        if (!name.equals(next.getDisplayName())) fields.add("name");
        if (maxLevel != next.getMaxLevel()) fields.add("max_level");
        if (anvilCost != next.getAnvilCost()) fields.add("anvil_cost");
        if (!applicableItem.equals(next.getApplicableItem())) fields.add("applicable_items");
        if (!conflicts.equals(next.getConflictIds())) fields.add("conflicts");
        EnchantConfig.RegistrySettings r = next.getRegistrySettings();
        if (registry.weight() != r.weight()) fields.add("registry.weight");
        if (registry.minCost() != r.minCost()) fields.add("registry.min_cost");
        if (registry.minCostPerLevel() != r.minCostPerLevel()) fields.add("registry.min_cost_per_level");
        if (registry.maxCost() != r.maxCost()) fields.add("registry.max_cost");
        if (registry.maxCostPerLevel() != r.maxCostPerLevel()) fields.add("registry.max_cost_per_level");
        if (!registry.activeSlots().equals(r.activeSlots())) fields.add("registry.active_slots");
        return fields;
    }
}
