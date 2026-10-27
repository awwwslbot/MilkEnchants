package com.windsor.meowsEnchants.listeners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.windsor.meowsEnchants.MeowsEnchants;
import com.windsor.meowsEnchants.actions.impl.AutoBowAction;
import com.windsor.meowsEnchants.testsupport.HiddenTasks;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 每个会重排任务的地方，旧任务都要真的被取消（Folia 的任务实现类不是 public，见 SchedulerCompatTest）：
 * reload 重排 PASSIVE 不叠加，潜行、疾跑结束后 HOLD 任务停止，连射、急速的 session 结束后逐 tick 任务停止。
 */
class TaskLifecycleTest {
    MeowsEnchants plugin;
    Player player;
    EntityScheduler scheduler;
    final List<ScheduledTask> scheduled = new ArrayList<>();

    @BeforeEach void setUp() {
        plugin = mock(MeowsEnchants.class);
        player = mock(Player.class);
        scheduler = mock(EntityScheduler.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getScheduler()).thenReturn(scheduler);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack empty = mock(ItemStack.class);
        when(inventory.getItemInMainHand()).thenReturn(empty);
        when(inventory.getItemInOffHand()).thenReturn(empty);
        when(inventory.getHelmet()).thenReturn(empty);
        when(inventory.getChestplate()).thenReturn(empty);
        when(inventory.getLeggings()).thenReturn(empty);
        when(inventory.getBoots()).thenReturn(empty);
        when(player.getInventory()).thenReturn(inventory);
        when(scheduler.runAtFixedRate(any(), any(), isNull(), anyLong(), anyLong())).thenAnswer(call -> {
            ScheduledTask task = HiddenTasks.create(plugin, true);
            scheduled.add(task);
            return task;
        });
    }

    @AfterEach void tearDown() {
        PassiveTaskManager.stopAll();
        AutoBowManager.stopAll();
        DrawAccelerateManager.stopAll();
    }

    private List<Boolean> cancelled() {
        return scheduled.stream().map(ScheduledTask::isCancelled).toList();
    }

    @Test void restartingPassiveCancelsThePreviousTask() {
        PassiveTaskManager.startTaskForPlayer(plugin, player); // 进服
        PassiveTaskManager.startTaskForPlayer(plugin, player); // reload
        PassiveTaskManager.startTaskForPlayer(plugin, player); // 再 reload
        assertEquals(List.of(true, true, false), cancelled(), "only one PASSIVE task per player");
        verify(scheduler, times(3)).runAtFixedRate(eq(plugin), any(), isNull(), eq(20L), eq(20L));
        PassiveTaskManager.stopTaskForPlayer(player); // 下线
        assertEquals(List.of(true, true, true), cancelled());
    }

    @Test void holdTasksStopWhenSneakingOrSprintingEnds() {
        PlayerStateListener listener = new PlayerStateListener(plugin);
        when(player.isSneaking()).thenReturn(true);
        listener.onSneak(new PlayerToggleSneakEvent(player, true));
        listener.onSneak(new PlayerToggleSneakEvent(player, false));
        assertEquals(List.of(true), cancelled(), "sneak hold task stops on release");

        when(player.isSprinting()).thenReturn(true);
        listener.onSprint(new PlayerToggleSprintEvent(player, true));
        listener.onSprint(new PlayerToggleSprintEvent(player, false));
        assertEquals(List.of(true, true), cancelled(), "sprint hold task stops when sprinting ends");
    }

    @Test void restartHoldTasksReplacesInsteadOfStacking() {
        PlayerStateListener listener = new PlayerStateListener(plugin);
        when(player.isSneaking()).thenReturn(true);
        listener.onSneak(new PlayerToggleSneakEvent(player, true));
        listener.restartHoldTasks(player); // reload
        listener.restartHoldTasks(player); // 再 reload
        assertEquals(List.of(true, true, false), cancelled());
        listener.cancelAllTasks();
        assertEquals(List.of(true, true, true), cancelled());
    }

    @Test void autoBowAndDrawAccelerateSessionsCancelTheirTickTasks() {
        Key key = Key.key("meowsenchants", "rapid_fire");
        AutoBowAction action = new AutoBowAction(Map.of("type", "auto_bow"));
        AutoBowManager.start(player, plugin, key, action);
        AutoBowManager.start(player, plugin, key, action); // 再次拉弓：上一个 session 结束
        AutoBowManager.stop(player);
        assertEquals(List.of(true, true), cancelled());

        DrawAccelerateManager.start(player, plugin, Key.key("meowsenchants", "quick_draw"), 2.0);
        DrawAccelerateManager.start(player, plugin, Key.key("meowsenchants", "quick_draw"), 2.0);
        DrawAccelerateManager.stop(player);
        assertEquals(List.of(true, true, true, true), cancelled());
    }
}
