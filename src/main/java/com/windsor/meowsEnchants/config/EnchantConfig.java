package com.windsor.meowsEnchants.config;

import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import com.windsor.meowsEnchants.schema.EnchantSchema;
import org.slf4j.Logger;

public class EnchantConfig {
    private final String id;
    private final String displayName;
    private final List<String> description;
    private final List<String> displayDescription;
    private final int maxLevel;
    private final int anvilCost;
    private final String applicableItem;
    private final String trigger;
    private final String target;
    private final long cooldown;
    private final List<Map<String, Object>> actions;
    private final List<String> actionKeys;
    private final Map<String, Object> rawData;
    private final List<String> conflictIds;
    private final Map<String, Object> soundData;
    private final Map<String, Object> particleData;
    private final EnchantingTableConfig enchantingTableConfig;
    private final VillagerTradeConfig villagerTradeConfig;

    private Logger logger;
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    // 延迟解析后的配置对象，在注册事件中填充
    private SoundConfig soundConfig;
    private ParticleConfig particleConfig;

    private TagKey<ItemType> itemTagKey;

    // ---- 内部配置类 ----
    public static class SoundConfig {
        private final Sound sound;
        private final float volume;
        private final float pitch;
        private final ParticleConfig.LocationType location;
        public SoundConfig(Sound sound, float volume, float pitch, ParticleConfig.LocationType location) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            this.location = location;
        }
        public Sound getSound() { return sound; }
        public float getVolume() { return volume; }
        public float getPitch() { return pitch; }
        public ParticleConfig.LocationType getLocation() { return location; }
    }

    public static class ParticleConfig {
        private final Particle particle;
        private final int count;
        private final double spread;
        private final double speed;
        private final LocationType location;

        public enum LocationType { SELF, TARGET, BLOCK }
        private final Object data;

        public ParticleConfig(Particle particle, int count, double spread, double speed, LocationType location, Object data) {
            this.particle = particle;
            this.count = count;
            this.spread = spread;
            this.speed = speed;
            this.location = location;
            this.data = data;
        }
        public Particle getParticle() { return particle; }
        public int getCount() { return count; }
        public double getSpread() { return spread; }
        public double getSpeed() { return speed; }
        public LocationType getLocation() { return location; }
        public Object getData() { return data; }
    }

    public static class EnchantingTableConfig {
        private final boolean enabled;
        private final double chance;
        private final int minCost;
        private final int maxCost;

        public EnchantingTableConfig(boolean enabled, double chance, int minCost, int maxCost) {
            this.enabled = enabled;
            this.chance = Math.clamp(chance, 0.0, 1.0);
            this.minCost = Math.max(0, minCost);
            this.maxCost = Math.max(this.minCost, maxCost);
        }

        public boolean isEnabled() { return enabled; }
        public double getChance() { return chance; }
        public int getMinCost() { return minCost; }
        public int getMaxCost() { return maxCost; }
    }

    /** {@code registry:} 段：注册进原版的属性，只在 bootstrap 生效。 */
    public record RegistrySettings(int weight, int minCost, int minCostPerLevel, int maxCost, int maxCostPerLevel,
                                   String activeSlots) {
        public static RegistrySettings from(Map<String, Object> data) {
            Map<String, Object> section = EnchantSchema.REGISTRY.read(data);
            return new RegistrySettings(
                    Math.clamp(EnchantSchema.REGISTRY_WEIGHT.read(section), 1, 1024),
                    Math.max(0, EnchantSchema.REGISTRY_MIN_COST.read(section)),
                    Math.max(0, EnchantSchema.REGISTRY_MIN_COST_PER_LEVEL.read(section)),
                    Math.max(0, EnchantSchema.REGISTRY_MAX_COST.read(section)),
                    Math.max(0, EnchantSchema.REGISTRY_MAX_COST_PER_LEVEL.read(section)),
                    EnchantSchema.REGISTRY_ACTIVE_SLOTS.read(section).toUpperCase(Locale.ROOT));
        }

        /** 不认识的槽位组按 ANY。 */
        public EquipmentSlotGroup slotGroup() {
            EquipmentSlotGroup group = EquipmentSlotGroup.getByName(activeSlots.toLowerCase(Locale.ROOT));
            return group != null ? group : EquipmentSlotGroup.ANY;
        }
    }

    public static class VillagerTradeConfig {
        private final boolean enabled;
        private final double chance;
        private final boolean treasure;

        public VillagerTradeConfig(boolean enabled, double chance, boolean treasure) {
            this.enabled = enabled;
            this.chance = Math.clamp(chance, 0.0, 1.0);
            this.treasure = treasure;
        }

        public boolean isEnabled() { return enabled; }
        public double getChance() { return chance; }
        public boolean isTreasure() { return treasure; }
    }

    // ---- 构造方法 ----
    public EnchantConfig(@NotNull String id,
                         @NotNull String displayName,
                         @Nullable List<String> description,
                         int maxLevel,
                         int anvilCost,
                         @Nullable String applicableItem,
                         @Nullable String trigger,
                         @Nullable String target,
                         long cooldown,
                         @Nullable List<Map<String, Object>> actions,
                         @Nullable List<String> conflicts,
                         @Nullable Map<String, Object> soundData,
                         @Nullable Map<String, Object> particleData,
                         @Nullable EnchantingTableConfig enchantingTableConfig,
                         @Nullable VillagerTradeConfig villagerTradeConfig) {
        this(id, displayName, description, maxLevel, anvilCost, applicableItem, trigger, target, cooldown,
                actions, null, conflicts, soundData, particleData, enchantingTableConfig, villagerTradeConfig, null);
    }

    /**
     * @param actionKeys 与 actions 一一对应的动作键（yml 里 actions 下的键名），为 null 时按序号
     * @param rawData    整个 yml 文件解析后的原始内容，供说明占位符和加载期校验使用
     */
    public EnchantConfig(@NotNull String id,
                         @NotNull String displayName,
                         @Nullable List<String> description,
                         int maxLevel,
                         int anvilCost,
                         @Nullable String applicableItem,
                         @Nullable String trigger,
                         @Nullable String target,
                         long cooldown,
                         @Nullable List<Map<String, Object>> actions,
                         @Nullable List<String> actionKeys,
                         @Nullable List<String> conflicts,
                         @Nullable Map<String, Object> soundData,
                         @Nullable Map<String, Object> particleData,
                         @Nullable EnchantingTableConfig enchantingTableConfig,
                         @Nullable VillagerTradeConfig villagerTradeConfig,
                         @Nullable Map<String, Object> rawData) {
        this(id, displayName, description, maxLevel, anvilCost, applicableItem, trigger, target, cooldown, actions,
                actionKeys, conflicts, soundData, particleData, enchantingTableConfig, villagerTradeConfig, rawData, rawData);
    }

    /** renderData：渲染说明占位符用的内容（reload 保持注册值时和 rawData 不同）。 */
    private EnchantConfig(@NotNull String id,
                          @NotNull String displayName,
                          @Nullable List<String> description,
                          int maxLevel,
                          int anvilCost,
                          @Nullable String applicableItem,
                          @Nullable String trigger,
                          @Nullable String target,
                          long cooldown,
                          @Nullable List<Map<String, Object>> actions,
                          @Nullable List<String> actionKeys,
                          @Nullable List<String> conflicts,
                          @Nullable Map<String, Object> soundData,
                          @Nullable Map<String, Object> particleData,
                          @Nullable EnchantingTableConfig enchantingTableConfig,
                          @Nullable VillagerTradeConfig villagerTradeConfig,
                          @Nullable Map<String, Object> rawData,
                          @Nullable Map<String, Object> renderData) {
        this.id = id;
        this.displayName = displayName;
        this.description = description != null ? List.copyOf(description) : Collections.emptyList();
        this.maxLevel = maxLevel;
        this.anvilCost = anvilCost;
        this.applicableItem = applicableItem;
        this.trigger = trigger != null ? trigger.toUpperCase(Locale.ROOT) : null;
        this.target = target != null ? target.toUpperCase(Locale.ROOT) : null;
        this.cooldown = Math.max(0, cooldown);
        this.actions = actions != null ? List.copyOf(actions) : Collections.emptyList();
        if (actionKeys != null && actionKeys.size() == this.actions.size()) {
            this.actionKeys = List.copyOf(actionKeys);
        } else {
            List<String> keys = new ArrayList<>(this.actions.size());
            for (int i = 0; i < this.actions.size(); i++) keys.add(String.valueOf(i));
            this.actionKeys = List.copyOf(keys);
        }
        this.rawData = rawData != null ? Collections.unmodifiableMap(rawData) : Collections.emptyMap();
        if (renderData == null) {
            this.displayDescription = this.description;
        } else {
            List<String> rendered = new ArrayList<>(this.description.size());
            for (String line : this.description) rendered.add(Placeholders.render(renderData, line).text());
            this.displayDescription = List.copyOf(rendered);
        }
        this.conflictIds = conflicts != null ? List.copyOf(conflicts) : Collections.emptyList();
        this.soundData = soundData;
        this.particleData = particleData;
        this.enchantingTableConfig = enchantingTableConfig != null
                ? enchantingTableConfig
                : new EnchantingTableConfig(false, 0.0, 0, 30);
        this.villagerTradeConfig = villagerTradeConfig != null
                ? villagerTradeConfig
                : new VillagerTradeConfig(false, 0.0, false);
    }

    /**
     * reload 时注册相关字段变了：这些字段保持启动时注册的值（注册表已冻结），其余字段用新配置。
     * GUI 名照常用新的；说明占位符按保持后的 max_level / anvil_cost 渲染。
     */
    public @NotNull EnchantConfig withRegistered(@NotNull RegisteredEnchant registered) {
        Map<String, Object> render = new LinkedHashMap<>(rawData);
        render.put(EnchantSchema.MAX_LEVEL.key(), registered.maxLevel());
        render.put(EnchantSchema.ANVIL_COST.key(), registered.anvilCost());
        EnchantConfig copy = new EnchantConfig(id, displayName, description, registered.maxLevel(),
                registered.anvilCost(), registered.applicableItem(), trigger, target, cooldown, actions, actionKeys,
                registered.conflicts(), soundData, particleData, enchantingTableConfig, villagerTradeConfig, rawData,
                render);
        copy.setLogger(logger);
        return copy;
    }

    // ---- Getters ----
    public @NotNull String getId() { return id; }
    public @NotNull String getDisplayName() { return displayName; }
    public @NotNull List<String> getDescription() { return description; }
    /** 替换了说明占位符的 description，/enchants 界面显示用。 */
    public @NotNull List<String> getDisplayDescription() { return displayDescription; }
    public @Nullable String getApplicableItem() { return applicableItem; }
    public int getMaxLevel() { return maxLevel; }
    public int getAnvilCost() { return anvilCost; }
    public double getEnchantingTableChance() { return enchantingTableConfig.getChance(); }
    public @NotNull EnchantingTableConfig getEnchantingTableConfig() { return enchantingTableConfig; }
    public @NotNull VillagerTradeConfig getVillagerTradeConfig() { return villagerTradeConfig; }
    public @Nullable String getTrigger() { return trigger; }
    public @Nullable String getTarget() { return target; }
    public long getCooldown() { return cooldown; }
    public long getCooldownTicks() { return Math.max(1, (cooldown + 49) / 50); }
    public @NotNull List<Map<String, Object>> getActions() { return actions; }
    /** 与 {@link #getActions()} 一一对应的动作键。 */
    public @NotNull List<String> getActionKeys() { return actionKeys; }
    /** yml 文件解析后的原始内容。 */
    public @NotNull Map<String, Object> getRawData() { return rawData; }
    /** {@code registry:} 段（缺省 = milk.12 写死的值）。 */
    public @NotNull RegistrySettings getRegistrySettings() { return RegistrySettings.from(rawData); }
    /** {@code mechanic:} 原文；是否真的由专用机制接管看 {@link #isMechanicEnchant()}。 */
    public @Nullable String getMechanic() {
        return EnchantSchema.MECHANIC.raw(rawData) instanceof String flag ? flag : null;
    }
    /** 箭雨、跟踪写了各自的标记：由专用机制处理，不走通用触发器（{@link EnchantSchema#isMechanicEnchant}）。 */
    public boolean isMechanicEnchant() { return EnchantSchema.isMechanicEnchant(id, getMechanic()); }
    public @NotNull List<String> getConflictIds() { return conflictIds; }
    public @Nullable SoundConfig getSoundConfig() { return soundConfig; }
    public @Nullable ParticleConfig getParticleConfig() { return particleConfig; }

    // ---- 延迟解析方法（注册或重新加载配置后调用） ----
    public void resolveSound() {
        if (soundData == null || logger == null) return;
        String typeStr = EnchantSchema.SOUND_TYPE.read(soundData);
        if (typeStr == null) return;
        Sound sound;
        try {
            sound = Registry.SOUNDS.get(Key.key(typeStr));
        } catch (RuntimeException e) {
            sound = null;
        }
        if (sound == null) {
            logger.warn("[MeowsEnchants] 未知音效: " + typeStr);
            return;
        }
        float volume = EnchantSchema.SOUND_VOLUME.read(soundData).floatValue();
        float pitch = EnchantSchema.SOUND_PITCH.read(soundData).floatValue();
        String locStr = EnchantSchema.SOUND_LOCATION.read(soundData).toUpperCase();
        ParticleConfig.LocationType location;
        try {
            location = ParticleConfig.LocationType.valueOf(locStr);
        } catch (IllegalArgumentException e) {
            location = ParticleConfig.LocationType.SELF;
        }
        this.soundConfig = new SoundConfig(sound, volume, pitch, location);
    }

    public void resolveParticle() {
        if (particleData == null || logger == null) return;
        String typeStr = EnchantSchema.PARTICLE_TYPE.read(particleData);
        if (typeStr == null) return;

        // 解析粒子类型
        Particle particle;
        try {
            particle = Registry.PARTICLE_TYPE.get(Key.key(typeStr.toLowerCase()));
        } catch (RuntimeException e) {
            particle = null;
        }
        if (particle == null) {
            try {
                particle = Particle.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        if (particle == null) {
            logger.warn("[MeowsEnchants] 未知粒子类型: " + typeStr);
            return;
        }

        int count = EnchantSchema.PARTICLE_COUNT.read(particleData);
        double spread = EnchantSchema.PARTICLE_SPREAD.read(particleData);
        double speed = EnchantSchema.PARTICLE_SPEED.read(particleData);
        String locStr = EnchantSchema.PARTICLE_LOCATION.read(particleData).toUpperCase();
        ParticleConfig.LocationType location;
        try {
            location = ParticleConfig.LocationType.valueOf(locStr);
        } catch (IllegalArgumentException e) {
            location = ParticleConfig.LocationType.SELF;
        }

        // 解析 data
        Object dataObj = null;
        Map<String, Object> dataMap = EnchantSchema.PARTICLE_DATA.isPresent(particleData)
                ? EnchantSchema.PARTICLE_DATA.read(particleData) : null;
        if (dataMap != null) {
            dataObj = parseParticleData(particle, dataMap);
        }

        this.particleConfig = new ParticleConfig(particle, count, spread, speed, location, dataObj);
    }

    /**
     * 根据粒子类型和配置数据生成对应的数据对象。
     * 支持所有带选项的粒子类型，使用 Particle.getDataType() 进行分发。
     */
    private Object parseParticleData(Particle particle, Map<String, Object> dataMap) {
        Class<?> dataType = particle.getDataType();
        if (dataType == null || dataType == Void.class) {
            return null;
        }

        // 1. BlockData 类 - 返回 Material 以供延迟创建
        if (dataType == BlockData.class) {
            String materialStr = EnchantSchema.PARTICLE_MATERIAL.read(dataMap);
            if (materialStr == null) {
                logger.warn("[MeowsEnchants] 粒子 " + particle.name() + " 需要 material 参数");
                return null;
            }
            Material material = parseMaterial(materialStr);
            return material; // 返回 Material 而不是 BlockData
        }

        // 2. Color 类
        if (dataType == Color.class) {
            String colorStr = EnchantSchema.PARTICLE_COLOR.read(dataMap);
            if (colorStr == null) {
                logger.warn("[MeowsEnchants] 粒子 " + particle.name() + " 需要 color 参数");
                return null;
            }
            return parseColor(colorStr);
        }

        // 3. DustOptions 类
        if (dataType == Particle.DustOptions.class) {
            String colorStr = EnchantSchema.PARTICLE_COLOR.read(dataMap);
            Number sizeNum = EnchantSchema.PARTICLE_SIZE.read(dataMap);
            if (colorStr == null) {
                logger.warn("[MeowsEnchants] DUST 粒子需要 color 参数");
                return null;
            }
            Color color = parseColor(colorStr);
            if (color == null) return null;
            float size = sizeNum.floatValue();
            return new Particle.DustOptions(color, size);
        }

        // 4. DustTransition 类
        if (dataType == Particle.DustTransition.class) {
            String colorStr = EnchantSchema.PARTICLE_COLOR.read(dataMap);
            String toColorStr = EnchantSchema.PARTICLE_TO_COLOR.read(dataMap);
            Number sizeNum = EnchantSchema.PARTICLE_SIZE.read(dataMap);
            if (colorStr == null || toColorStr == null) {
                logger.warn("[MeowsEnchants] DUST_COLOR_TRANSITION 需要 color 和 to_color 参数");
                return null;
            }
            Color color = parseColor(colorStr);
            Color toColor = parseColor(toColorStr);
            if (color == null || toColor == null) return null;
            float size = sizeNum.floatValue();
            return new Particle.DustTransition(color, toColor, size);
        }

        // 5. ItemStack 类 - 延迟创建：仅存 Material，运行时再转为 ItemStack
        if (dataType == ItemStack.class) {
            String materialStr = EnchantSchema.PARTICLE_MATERIAL.read(dataMap);
            if (materialStr == null) {
                logger.warn("[MeowsEnchants] 粒子 " + particle.name() + " 需要 material 参数");
                return null;
            }
            Material material = parseMaterial(materialStr);
            if (material == null) return null;
            return material; // 返回 Material，运行时在 playEffects 中创建 ItemStack
        }

        // 6. Integer 类
        if (dataType == Integer.class) {
            Number intNum = EnchantSchema.PARTICLE_VALUE.read(dataMap);
            if (intNum == null) {
                logger.warn("[MeowsEnchants] 粒子 " + particle.name() + " 需要 value 参数");
                return null;
            }
            return intNum.intValue();
        }

        // 其他未支持的类型
        logger.warn("[MeowsEnchants] 暂不支持粒子数据类: " + dataType.getName() + " 对于 " + particle.name());
        return null;
    }

    private Material parseMaterial(String str) {
        Material material = Material.getMaterial(str.toUpperCase());
        if (material == null) {
            try {
                Key key = Key.key(str);
                material = Registry.MATERIAL.get(key);
            } catch (Exception ignored) {}
        }
        if (material == null) {
            logger.warn("[MeowsEnchants] 未知材质: " + str);
        }
        return material;
    }

    private Color parseColor(String hex) {
        if (hex == null) return null;
        if (hex.startsWith("#")) hex = hex.substring(1);
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return Color.fromRGB(r, g, b);
        } catch (Exception e) {
            logger.warn("[MeowsEnchants] 颜色格式错误: " + hex);
            return null;
        }
    }

    // ---- 冲突解析 ----
    public @NotNull RegistryKeySet<Enchantment> resolveConflictKeySet() {
        if (conflictIds.isEmpty()) {
            return RegistrySet.keySet(RegistryKey.ENCHANTMENT, Collections.emptyList());
        }
        Set<TypedKey<Enchantment>> typedKeys = new HashSet<>();
        Registry<Enchantment> registry = io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT);
        for (String id : conflictIds) {
            try {
                Key key = Key.key(id);
                Enchantment ench = registry.get(key);
                if (ench == null) {
                    if (logger != null) {
                        logger.warn("冲突附魔未注册，跳过: " + id);
                    }
                    continue;
                }
                typedKeys.add(TypedKey.create(RegistryKey.ENCHANTMENT, key));
            } catch (Exception e) {
                if (logger != null) {
                    logger.warn("冲突附魔ID格式错误，跳过: " + id);
                }
            }
        }
        if (typedKeys.isEmpty()) {
            return RegistrySet.keySet(RegistryKey.ENCHANTMENT, Collections.emptyList());
        }
        return RegistrySet.keySet(RegistryKey.ENCHANTMENT, typedKeys);
    }

    // ---- 物品标签解析 ----
    public @Nullable TagKey<ItemType> resolveItemTagKey() {
        if (itemTagKey != null) return itemTagKey;
        if (applicableItem == null) return null;
        itemTagKey = getTagKeyFromConfigItem(applicableItem);
        return itemTagKey;
    }

    @Nullable
    private TagKey<ItemType> getTagKeyFromConfigItem(@NotNull String item) {
        return switch (item.toUpperCase(Locale.ROOT)) {
            case "SWORDS" -> ItemTypeTagKeys.SWORDS;
            case "AXES" -> ItemTypeTagKeys.AXES;
            case "MACE" -> ItemTypeTagKeys.ENCHANTABLE_MACE;
            case "TRIDENT" -> ItemTypeTagKeys.ENCHANTABLE_TRIDENT;
            case "MELEE_WEAPON" -> ItemTypeTagKeys.ENCHANTABLE_MELEE_WEAPON;
            case "SHARP_WEAPON" -> ItemTypeTagKeys.ENCHANTABLE_SHARP_WEAPON;
            case "WEAPON" -> ItemTypeTagKeys.ENCHANTABLE_WEAPON;
            case "PICKAXES" -> ItemTypeTagKeys.PICKAXES;
            case "SHOVELS" -> ItemTypeTagKeys.SHOVELS;
            case "HOES" -> ItemTypeTagKeys.HOES;
            case "BOW" -> ItemTypeTagKeys.ENCHANTABLE_BOW;
            case "CROSSBOW" -> ItemTypeTagKeys.ENCHANTABLE_CROSSBOW;
            case "RANGED" -> ItemTypeTagKeys.WITHER_SKELETON_DISLIKED_WEAPONS;
            case "TOOLS" -> ItemTypeTagKeys.ENCHANTABLE_MINING;
            case "FISHING_ROD" -> ItemTypeTagKeys.ENCHANTABLE_FISHING;
            case "HELMETS" -> ItemTypeTagKeys.ENCHANTABLE_HEAD_ARMOR;
            case "CHESTPLATES" -> ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR;
            case "LEGGINGS" -> ItemTypeTagKeys.ENCHANTABLE_LEG_ARMOR;
            case "BOOTS" -> ItemTypeTagKeys.ENCHANTABLE_FOOT_ARMOR;
            case "ARMOR" -> ItemTypeTagKeys.ENCHANTABLE_ARMOR;
            case "EQUIPPABLE" -> ItemTypeTagKeys.ENCHANTABLE_EQUIPPABLE;
            default -> ItemTypeTagKeys.ENCHANTABLE_DURABILITY;
        };
    }
}
