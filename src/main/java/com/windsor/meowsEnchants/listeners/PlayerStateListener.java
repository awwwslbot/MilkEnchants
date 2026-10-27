package com.windsor.meowsEnchants.listeners;

import com.windsor.meowsEnchants.config.Trigger;
import com.windsor.meowsEnchants.MeowsEnchants;
import com.windsor.meowsEnchants.config.EnchantConfig;
import com.windsor.meowsEnchants.utils.SchedulerCompat;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStateListener implements Listener {

    private final MeowsEnchants plugin;
    // 各玩家的事件在自己的区域线程上到达，所以用并发表
    private final Map<UUID, SchedulerCompat.Task> sneakHoldTasks = new ConcurrentHashMap<>();
    private final Map<UUID, SchedulerCompat.Task> sprintHoldTasks = new ConcurrentHashMap<>();

    public PlayerStateListener(MeowsEnchants plugin) {
        this.plugin = plugin;
    }

    // -------- SNEAK / SNEAK_HOLD --------
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            // SNEAK 单次触发（仍然保留）
            EnchantTriggerHelper.checkAndTrigger(player, Trigger.SNEAK.name(),
                    EnchantTriggerHelper.getSlotsForTrigger(player),
                    event, null);
            startHoldTask(player, Trigger.SNEAK_HOLD.name(), sneakHoldTasks, true);
        } else {
            cancelHoldTask(player, sneakHoldTasks);
        }
    }

    // -------- SPRINT / SPRINT_HOLD --------
    @EventHandler
    public void onSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        if (event.isSprinting()) {
            EnchantTriggerHelper.checkAndTrigger(player, Trigger.SPRINT.name(),
                    EnchantTriggerHelper.getSlotsForTrigger(player),
                    event, null);
            startHoldTask(player, Trigger.SPRINT_HOLD.name(), sprintHoldTasks, false);
        } else {
            cancelHoldTask(player, sprintHoldTasks);
        }
    }

    // -------- 通用周期性任务启动/取消 --------
    private void startHoldTask(Player player, String triggerName, Map<UUID, SchedulerCompat.Task> taskMap, boolean isSneak) {
        cancelHoldTask(player, taskMap);
        UUID uuid = player.getUniqueId();

        // 获取该附魔的冷却（用于周期）
        long cooldownTicks = getMaxCooldownTicksForPlayer(player, triggerName);
        long period = Math.max(MeowsEnchants.getGlobalSettings().holdMinPeriodTicks(), cooldownTicks);
        long initialDelay = period; // 首次延迟等于周期

        SchedulerCompat.Task task = SchedulerCompat.runAtFixedRate(player, plugin,
                scheduledTask -> {
                    if (!player.isOnline() || (isSneak && !player.isSneaking()) || (!isSneak && !player.isSprinting())) {
                        scheduledTask.cancel();
                        taskMap.remove(uuid);
                        return;
                    }
                    // 触发检查（内部会处理冷却）
                    EnchantTriggerHelper.checkAndTrigger(player, triggerName,
                            EnchantTriggerHelper.getSlotsForTrigger(player));
                },
                initialDelay,
                period
        );
        taskMap.put(uuid, task);
    }

    private void cancelHoldTask(Player player, Map<UUID, SchedulerCompat.Task> taskMap) {
        UUID uuid = player.getUniqueId();
        SchedulerCompat.Task task = taskMap.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    // 计算玩家身上所有匹配该触发器的附魔的最大冷却 tick
    private long getMaxCooldownTicksForPlayer(Player player, String triggerName) {
        long max = 0;
        for (ItemStack item : EnchantTriggerHelper.getSlotsForTrigger(player)) {
            for (Key key : EnchantTriggerHelper.getCustomEnchantsOnItem(item)) {
                EnchantConfig config = MeowsEnchants.getEnchantConfigs().get(key);
                if (config != null && config.getTrigger() != null &&
                        config.getTrigger().equalsIgnoreCase(triggerName)) {
                    long ticks = config.getCooldownTicks();
                    if (ticks > max) max = ticks;
                }
            }
        }
        return max;
    }

    /** reload 后按新的冷却重排正在进行的持续潜行/疾跑任务；在玩家自己的区域线程上调用。 */
    public void restartHoldTasks(Player player) {
        if (sneakHoldTasks.containsKey(player.getUniqueId()) && player.isSneaking()) {
            startHoldTask(player, Trigger.SNEAK_HOLD.name(), sneakHoldTasks, true);
        }
        if (sprintHoldTasks.containsKey(player.getUniqueId()) && player.isSprinting()) {
            startHoldTask(player, Trigger.SPRINT_HOLD.name(), sprintHoldTasks, false);
        }
    }

    public void cancelAllTasks() {
        for (SchedulerCompat.Task task : sneakHoldTasks.values()) task.cancel();
        for (SchedulerCompat.Task task : sprintHoldTasks.values()) task.cancel();
        sneakHoldTasks.clear();
        sprintHoldTasks.clear();
    }
}
