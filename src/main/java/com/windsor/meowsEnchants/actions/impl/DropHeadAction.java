package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.actions.ScalingFunction;
import com.windsor.meowsEnchants.schema.Param;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class DropHeadAction implements Action {
    public static final Param<ScalingFunction> AMOUNT = Param.scalableNumber("amount", "掉落概率")
            .unit(Param.Unit.RATIO).range(0, 1).required().build();
    public static final Param<Map<String, Object>> HEADS = Param.map("heads", "头颅映射",
                    Param.registry("*", "掉落的头颅", Param.Registry.ITEM).help("物品材质名").build())
            .help("键是被击杀生物的实体类型（大写枚举名），值是掉落的物品；不在表里的生物不掉落（玩家另算）")
            .def(defaultHeads()).build();
    public static final Param<Boolean> PLAYER_HEADS = Param.bool("player_heads", "掉落玩家头颅")
            .help("击杀玩家时按同样的概率掉落该玩家的头").def(true).build();
    public static final Param<Boolean> PLAYER_HEAD_SUCCESS = Param.bool("player_head_success", "玩家头算触发成功")
            .help("false（milk.12 行为）：掉了玩家头也算动作失败，不进冷却、不播音效粒子、不执行后续动作").def(false).build();
    public static final List<Param<?>> PARAMS = List.of(AMOUNT, HEADS, PLAYER_HEADS, PLAYER_HEAD_SUCCESS);

    private static Map<String, Object> defaultHeads() {
        Map<String, Object> heads = new LinkedHashMap<>();
        heads.put("CREEPER", "CREEPER_HEAD");
        heads.put("ZOMBIE", "ZOMBIE_HEAD");
        heads.put("SKELETON", "SKELETON_SKULL");
        heads.put("WITHER_SKELETON", "WITHER_SKELETON_SKULL");
        heads.put("PIGLIN", "PIGLIN_HEAD");
        heads.put("ENDER_DRAGON", "DRAGON_HEAD");
        return heads;
    }

    private final ScalingFunction amount;
    private final Map<String, String> heads;
    private final boolean playerHeads;
    private final boolean playerHeadSuccess;
    private final Random random = new Random();

    public DropHeadAction(Map<String, Object> params) {
        this.amount = AMOUNT.read(params);
        this.heads = new HashMap<>();
        for (Map.Entry<String, Object> entry : HEADS.read(params).entrySet()) {
            heads.put(entityKey(entry.getKey()), String.valueOf(entry.getValue()));
        }
        this.playerHeads = PLAYER_HEADS.read(params);
        this.playerHeadSuccess = PLAYER_HEAD_SUCCESS.read(params);
    }

    @Override
    public boolean execute(ActionContext context) {
        List<Entity> targets = context.getTargets();
        if (targets == null || targets.isEmpty()) return false;

        Entity target = targets.getFirst();
        if (target == null) return false;

        double chance = this.amount.getValue(context.getEnchantLevel());
        if (chance <= 0 || random.nextDouble() >= chance) return false;

        if (target instanceof Player playerTarget) {
            if (!playerHeads) return false;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(playerTarget);
                head.setItemMeta(meta);
                target.getWorld().dropItemNaturally(target.getLocation(), head);
            }
            return playerHeadSuccess;
        }

        Material headMaterial = headMaterial(target.getType());
        if (headMaterial == null) return false;

        ItemStack head = new ItemStack(headMaterial, 1);
        target.getWorld().dropItemNaturally(target.getLocation(), head);
        return true;
    }

    /** 头颅映射表里该生物的头颅；不在表里时 null。 */
    Material headMaterial(EntityType type) {
        if (type == null) return null;
        String item = heads.get(type.name());
        return item == null ? null : Material.matchMaterial(item);
    }

    /** 实体类型枚举名，接受小写和 minecraft: 前缀。 */
    private static String entityKey(String raw) {
        String key = raw.toUpperCase(Locale.ROOT);
        return key.startsWith("MINECRAFT:") ? key.substring("MINECRAFT:".length()) : key;
    }
}