package com.windsor.meowsEnchants.listeners;

import com.windsor.meowsEnchants.actions.impl.AutoBowAction;
import com.windsor.meowsEnchants.utils.SchedulerCompat;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护“自动连射”的逐 tick 任务。每个玩家最多一个会话，
 * 任务运行在玩家所属的 region/主线程调度器上。
 * <p>
 * 每 tick 读取实际蓄力 tick 计算弓的 power，只有满蓄力（原版 20 tick）才发射。
 * 本类不改变蓄力速度。
 */
public final class AutoBowManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoBowManager.class);

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private static volatile Method getHandleMethod;
    private static volatile Method releaseUsingItemMethod;
    private static volatile boolean releaseUnavailable;

    private AutoBowManager() {
    }

    public static void start(Player player, Plugin plugin, Key enchantKey, AutoBowAction action) {
        if (player == null || plugin == null) return;
        stop(player);

        Session session = new Session(
                enchantKey,
                action.isRefire(),
                action.getRefireDelay(),
                action.getMaxShots(),
                action.getFullDrawTicks()
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
        if (AutoBowAction.powerForTicks(used, session.fullDrawTicks) < 1.0) {
            return; // 还没拉满
        }
        if (session.maxShots > 0 && session.shots >= session.maxShots) {
            stop(player);
            return;
        }

        session.firing = true;
        try {
            releaseUsingItem(player);
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

    /**
     * 调用 NMS 的 {@code LivingEntity#releaseUsingItem()}。
     * <p>
     * Bukkit 的 {@code completeUsingActiveItem()} 走的是“消耗品完成”路径
     * （{@code finishUsingItem}），对弓不会发射；必须走 releaseUsingItem 才会
     * 触发 {@code BowItem.releaseUsing} 和 {@code EntityShootBowEvent}。
     */
    private static void releaseUsingItem(Player player) {
        if (releaseUnavailable) {
            return;
        }
        try {
            Method handleGetter = getHandleMethod;
            if (handleGetter == null) {
                handleGetter = player.getClass().getMethod("getHandle");
                getHandleMethod = handleGetter;
            }
            Object handle = handleGetter.invoke(player);

            Method release = releaseUsingItemMethod;
            if (release == null) {
                release = handle.getClass().getMethod("releaseUsingItem");
                releaseUsingItemMethod = release;
            }
            release.invoke(handle);
        } catch (ReflectiveOperationException | RuntimeException e) {
            releaseUnavailable = true;
            LOGGER.error("无法调用 releaseUsingItem，rapid_fire 将无法发射箭矢", e);
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
        private final boolean refire;
        private final int refireDelay;
        private final int maxShots;
        private final int fullDrawTicks;

        private SchedulerCompat.Task task;
        private int shots;
        private int delay;
        private boolean firing;
        private boolean clientStopped;

        private Session(Key enchantKey, boolean refire, int refireDelay, int maxShots, int fullDrawTicks) {
            this.enchantKey = enchantKey;
            this.refire = refire;
            this.refireDelay = refireDelay;
            this.maxShots = maxShots;
            this.fullDrawTicks = fullDrawTicks;
        }
    }
}
