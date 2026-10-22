package com.windsor.meowsEnchants.listeners;

import com.windsor.meowsEnchants.utils.SchedulerCompat;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护“加速蓄力”的逐 tick 任务。每个玩家最多一个会话。
 * <p>
 * 每 tick 额外扣减 {@code useItemRemaining}，使 {@code getActiveItemUsedTime()}
 * 增长得比原版快；不修改客户端动画，也不直接发射。
 * 与 {@code auto_bow} 同时存在时，后者会因此更早看到 power = 1.0。
 */
public final class DrawAccelerateManager {

    private static final int FULL_DRAW_TICKS = 20;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private DrawAccelerateManager() {
    }

    public static void start(Player player, Plugin plugin, Key enchantKey, double extraTicks) {
        if (player == null || plugin == null) return;
        stop(player);

        Session session = new Session(enchantKey, extraTicks);
        SESSIONS.put(player.getUniqueId(), session);

        session.task = SchedulerCompat.runAtFixedRate(player, plugin,
                task -> tick(player, session, task), 1L, 1L);

        if (session.task == null) {
            SESSIONS.remove(player.getUniqueId());
        }
    }

    private static void tick(Player player, Session session, SchedulerCompat.Task task) {
        if (SESSIONS.get(player.getUniqueId()) != session) {
            task.cancel();
            return;
        }
        if (!player.isOnline()) {
            stop(player);
            return;
        }

        ItemStack active = player.getActiveItem();
        if (active == null || active.getType() != Material.BOW) {
            stop(player);
            return;
        }
        if (!EnchantTriggerHelper.getCustomEnchantsOnItem(active).contains(session.enchantKey)) {
            stop(player);
            return;
        }

        int used = player.getActiveItemUsedTime();
        // 刚重新开始蓄力时不要抢跑
        if (used <= 0) return;

        int extra = (int) Math.floor(session.extraTicks);
        if (extra <= 0) return;

        int remaining = player.getActiveItemRemainingTime();
        int total = remaining + used;
        int fullRemaining = Math.max(0, total - FULL_DRAW_TICKS);
        int target = Math.max(fullRemaining, remaining - extra);
        if (target < remaining) {
            player.setActiveItemRemainingTime(target);
        }
    }

    public static void stop(Player player) {
        if (player == null) return;
        Session session = SESSIONS.remove(player.getUniqueId());
        if (session != null && session.task != null) {
            session.task.cancel();
        }
    }

    public static void stopAll() {
        for (Session session : SESSIONS.values()) {
            if (session.task != null) {
                session.task.cancel();
            }
        }
        SESSIONS.clear();
    }

    private static final class Session {
        private final Key enchantKey;
        private final double extraTicks;

        private SchedulerCompat.Task task;

        private Session(Key enchantKey, double extraTicks) {
            this.enchantKey = enchantKey;
            this.extraTicks = extraTicks;
        }
    }
}
