package com.windsor.meowsEnchants.config;

import com.windsor.meowsEnchants.schema.Param;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/** 加载期校验用的注册表查询，按插件运行时实际的解析方式判断。 */
public final class BukkitRegistries implements ConfigValidator.Registries {
    public static final BukkitRegistries INSTANCE = new BukkitRegistries();

    private BukkitRegistries() {}

    @Override
    public Boolean exists(Param.Registry registry, String value) {
        try {
            return switch (registry) {
                case SOUND -> Registry.SOUNDS.get(Key.key(value)) != null;
                case PARTICLE -> Registry.PARTICLE_TYPE.get(Key.key(value.toLowerCase(Locale.ROOT))) != null
                        || enumExists(value);
                case MOB_EFFECT -> PotionEffectType.getByName(value.toUpperCase(Locale.ROOT)) != null;
                case ATTRIBUTE -> Registry.ATTRIBUTE.get(NamespacedKey.minecraft(value.toLowerCase(Locale.ROOT))) != null;
                case ITEM -> {
                    Material material = Material.matchMaterial(value);
                    yield material != null && material.isItem();
                }
                case BLOCK -> {
                    Material material = Material.matchMaterial(value);
                    yield material != null && material.isBlock();
                }
                case ENTITY_TYPE -> entityType(value);
                case ENCHANTMENT -> RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                        .get(Key.key(value)) != null;
            };
        } catch (RuntimeException e) {
            // 键名格式不合法（含大写、空格等）也算不存在
            return false;
        }
    }

    private static boolean enumExists(String value) {
        try {
            Particle.valueOf(value.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean entityType(String value) {
        String name = value.toUpperCase(Locale.ROOT);
        if (name.startsWith("MINECRAFT:")) name = name.substring("MINECRAFT:".length());
        try {
            EntityType.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
