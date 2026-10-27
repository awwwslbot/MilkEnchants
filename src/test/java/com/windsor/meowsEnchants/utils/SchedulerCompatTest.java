package com.windsor.meowsEnchants.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.windsor.meowsEnchants.testsupport.HiddenTasks;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Folia 的任务实现类（FoliaEntityScheduler$EntityScheduledTask）不是 public：以前在实现类上反射找 cancel，
 * 调用抛 IllegalAccessException 又被吞掉，取消全部不生效（reload 后 PASSIVE 叠加、潜行疾跑和连射任务不停）。
 * 这里用同样不是 public、也不在同一个包里的实现类（{@link HiddenTasks}），确认取消走的是 {@link ScheduledTask} 接口。
 */
class SchedulerCompatTest {
    Plugin plugin;
    Player player;
    EntityScheduler scheduler;
    ScheduledTask scheduled;

    @BeforeEach void setUp() {
        plugin = mock(Plugin.class);
        player = mock(Player.class);
        scheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(scheduler);
        scheduled = HiddenTasks.create(plugin, true);
        assertFalse(Modifier.isPublic(scheduled.getClass().getModifiers()));
        assertNotEquals(SchedulerCompat.class.getPackage(), scheduled.getClass().getPackage());
    }

    @Test void cancelGoesThroughTheScheduledTaskInterface() {
        when(scheduler.runAtFixedRate(eq(plugin), any(), isNull(), eq(20L), eq(20L))).thenReturn(scheduled);
        SchedulerCompat.Task task = SchedulerCompat.runAtFixedRate(player, plugin, t -> { }, 20L, 20L);
        task.cancel();
        assertTrue(scheduled.isCancelled(), "cancel must reach a non-public ScheduledTask implementation");
    }

    @Test @SuppressWarnings("unchecked") void theTaskHandedToTheBodyCancelsTheSameScheduledTask() {
        ArgumentCaptor<Consumer<ScheduledTask>> body = ArgumentCaptor.forClass(Consumer.class);
        when(scheduler.runAtFixedRate(eq(plugin), body.capture(), isNull(), anyLong(), anyLong())).thenReturn(scheduled);
        AtomicReference<SchedulerCompat.Task> inner = new AtomicReference<>();
        SchedulerCompat.runAtFixedRate(player, plugin, inner::set, 1L, 1L);
        body.getValue().accept(scheduled); // 调度器执行一次任务体
        inner.get().cancel();               // 任务体里自己取消（潜行结束、session 结束、玩家下线）
        assertTrue(scheduled.isCancelled());
    }

    @Test @SuppressWarnings("unchecked") void runAndRunDelayedUseTheEntitySchedulerAndCancelThroughTheInterface() {
        ScheduledTask once = HiddenTasks.create(plugin, false);
        ScheduledTask later = HiddenTasks.create(plugin, false);
        ArgumentCaptor<Consumer<ScheduledTask>> body = ArgumentCaptor.forClass(Consumer.class);
        when(scheduler.run(eq(plugin), body.capture(), isNull())).thenReturn(once);
        when(scheduler.runDelayed(eq(plugin), any(), isNull(), eq(3L))).thenReturn(later);
        int[] runs = {0};
        SchedulerCompat.run(player, plugin, t -> runs[0]++).cancel();
        SchedulerCompat.runDelayed(player, plugin, t -> { }, 3L).cancel();
        body.getValue().accept(once);
        assertEquals(1, runs[0]);
        assertTrue(once.isCancelled());
        assertTrue(later.isCancelled());
        verify(scheduler).run(eq(plugin), any(), isNull());
        verify(scheduler).runDelayed(eq(plugin), any(), isNull(), eq(3L));
    }

    @Test void aRetiredEntityStillFallsBackToTheBukkitScheduler() {
        when(scheduler.runAtFixedRate(any(), any(), any(), anyLong(), anyLong())).thenReturn(null);
        BukkitScheduler bukkit = mock(BukkitScheduler.class);
        BukkitTask bukkitTask = mock(BukkitTask.class);
        when(bukkit.runTaskTimer(eq(plugin), any(Runnable.class), eq(5L), eq(10L))).thenReturn(bukkitTask);
        try (var server = mockStatic(Bukkit.class)) {
            server.when(Bukkit::getScheduler).thenReturn(bukkit);
            SchedulerCompat.runAtFixedRate(player, plugin, t -> { }, 5L, 10L).cancel();
        }
        verify(bukkitTask).cancel();
    }
}
