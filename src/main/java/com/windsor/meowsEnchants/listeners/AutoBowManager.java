package com.windsor.meowsEnchants.listeners;

import com.windsor.meowsEnchants.actions.impl.AutoBowAction;
import com.windsor.meowsEnchants.utils.SchedulerCompat;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护“自动连射”的逐 tick 任务。每个玩家最多一个会话，
 * 任务运行在玩家所属的 region/主线程调度器上。
 * <p>
 * 每 tick 根据实际蓄力 tick 与 {@code draw_speed} 计算弓的 power，
 * 达到满值才发射。
 */
public final class AutoBowManager {

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private AutoBowManager() {
    }

    public static void start(Player player, Plugin plugin, Key enchantKey,
                             AutoBowAction action, double drawSpeed) {
        if (player == null || plugin == null) return;
        stop(player);

        Session session = new Session(
                enchantKey,
                drawSpeed,
                action.isRefire(),
                action.getRefireDelay(),
                action.getMaxShots()
        );
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
        if (!player.isOnline() || session.clientStopped) {
            stop(player);
            return;
        }

        // 两次射击之间的延迟；此时玩家没有 activeItem
        if (session.delay > 0) {
            session.delay--;
            if (session.delay == 0 && player.isOnline()) {
                player.startUsingItem(EquipmentSlot.HAND);
            }
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
        double effectiveTicks = used * session.drawSpeed;
        if (AutoBowAction.powerForTicks(effectiveTicks) < 1.0) {
            return;
        }
        if (session.maxShots > 0 && session.shots >= session.maxShots) {
            stop(player);
            return;
        }

        // 提前蓄力：把剩余时间改成“已使用 20 tick”，让原版按满蓄力计算
        if (used < 20) {
            int remaining = player.getActiveItemRemainingTime();
            player.setActiveItemRemainingTime(Math.max(0, remaining - (20 - used)));
        }

        session.firing = true;
        try {
            player.completeUsingActiveItem();
        } finally {
            session.firing = false;
        }
        session.shots++;

        if (!session.refire || (session.maxShots > 0 && session.shots >= session.maxShots)) {
            stop(player);
            return;
        }

        if (session.refireDelay > 0) {
            session.delay = session.refireDelay;
        } else {
            player.startUsingItem(EquipmentSlot.HAND);
        }
    }

    /** 玩家自己松开右键（不是我们自动完成的那次）时，停止自动续力。 */
    public static void onStopUsing(Player player) {
        if (player == null) return;
        Session session = SESSIONS.get(player.getUniqueId());
        if (session != null && !session.firing) {
            session.clientStopped = true;
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
        private final double drawSpeed;
        private final boolean refire;
        private final int refireDelay;
        private final int maxShots;

        private SchedulerCompat.Task task;
        private int shots;
        private int delay;
        private boolean firing;
        private boolean clientStopped;

        private Session(Key enchantKey, double drawSpeed, boolean refire,
                        int refireDelay, int maxShots) {
            this.enchantKey = enchantKey;
            this.drawSpeed = drawSpeed;
            this.refire = refire;
            this.refireDelay = refireDelay;
            this.maxShots = maxShots;
        }
    }
}
