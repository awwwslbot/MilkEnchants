package com.windsor.meowsEnchants.listeners;

import com.windsor.meowsEnchants.MeowsEnchants;
import com.windsor.meowsEnchants.config.EnchantConfig;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

/**
 * 专用机制附魔（箭雨、跟踪）读 {@code cooldown} 的共用入口。计时就是通用附魔那一套：
 * {@link EnchantTriggerHelper} 里按「玩家 + 附魔」记的冷却。
 *
 * <p>机制在射击事件里先问 {@link #ready}：在冷却中就不接管这一箭，它照常作为普通箭射出，没有任何提示。
 * 机制真正触发时调 {@link #start}，冷却从这一刻开始算。
 *
 * <p>弩的多重射击一次射出几支，每支各有一次射击和发射事件。同一玩家在同一个区域 game tick 里射出的算同一次
 * 触发：进入冷却的那个 tick 里，后面几支照样放行，也不刷新冷却。
 */
public final class MechanicCooldown {
    /** 该附魔当前配置的 cooldown（毫秒）。 */
    private final ToLongFunction<Key> cooldownOf;
    private final LongSupplier clock;
    /** 每个「玩家 + 附魔」最近一次进入冷却时的区域 game tick。 */
    private final Map<UUID, Map<Key, Long>> startTicks = new ConcurrentHashMap<>();

    /**
     * @param cooldownOf 附魔 key → cooldown（毫秒），每次触发时现读，reload 后的新值从下一次触发起生效
     * @param clock      当前毫秒时间；测试可以换成可控的时钟
     */
    public MechanicCooldown(ToLongFunction<Key> cooldownOf, LongSupplier clock) {
        this.cooldownOf = cooldownOf;
        this.clock = clock;
    }

    /** 读当前运行时配置（{@link MeowsEnchants#getEnchantConfigs()}）里的 cooldown。 */
    public static MechanicCooldown configured() {
        return new MechanicCooldown(key -> {
            EnchantConfig config = MeowsEnchants.getEnchantConfigs().get(key);
            return config == null ? 0 : config.getCooldown();
        }, System::currentTimeMillis);
    }

    /**
     * 射击事件里调用（射手所在区域线程）：这一箭能不能交给机制。
     * 不在冷却中，或者冷却正是同一 tick 的同一次射击开始的，返回 true。
     */
    public boolean ready(Player player, Key enchant) {
        if (!EnchantTriggerHelper.isOnCooldown(player, enchant, clock.getAsLong())) return true;
        Map<Key, Long> ticks = startTicks.get(player.getUniqueId());
        Long started = ticks == null ? null : ticks.get(enchant);
        return started != null && started == gameTick(player);
    }

    /** 机制真正触发时调用（射手所在区域线程）：从现在开始进入冷却；cooldown 为 0 时什么都不做。 */
    public void start(Player player, Key enchant) {
        long cooldown = cooldownOf.applyAsLong(enchant);
        if (cooldown <= 0) return;
        long now = clock.getAsLong();
        long tick = gameTick(player);
        Long previous = startTicks.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>()).put(enchant, tick);
        // 同一次多重射击的后几支：冷却已经从第一支开始算了
        if (previous != null && previous == tick && EnchantTriggerHelper.isOnCooldown(player, enchant, now)) return;
        EnchantTriggerHelper.setCooldown(player, enchant, cooldown, now);
    }

    private static long gameTick(Player player) {
        return player.getWorld().getGameTime();
    }
}
