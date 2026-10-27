package com.windsor.meowsEnchants.utils;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

/**
 * 在实体自己的调度器上排任务（Folia 下即实体所在区域的线程，Paper 下即主线程）。
 * <p>
 * 直接用 Paper API 的 {@link EntityScheduler} 和 {@link ScheduledTask} 接口。以前这里用反射，
 * 在实现类上找 {@code cancel}：Folia 的任务实现类不是 public，调用抛 IllegalAccessException
 * 又被吞掉，所以 Folia 上所有取消都不生效。
 * 实体已经移除（调度器返回 null）时，和以前一样退回 Bukkit 调度器。
 */
public final class SchedulerCompat {

    private SchedulerCompat() {
    }

    public interface Task {
        void cancel();
    }

    public static Task run(Entity entity, Plugin plugin, Consumer<Task> task) {
        ScheduledTask scheduled = entity.getScheduler().run(plugin, wrap(task), null);
        if (scheduled != null) {
            return of(scheduled);
        }

        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, () -> task.accept(() -> {
        }));
        return bukkitTask::cancel;
    }

    public static Task runDelayed(Entity entity, Plugin plugin, Consumer<Task> task, long delayTicks) {
        ScheduledTask scheduled = entity.getScheduler().runDelayed(plugin, wrap(task), null, delayTicks);
        if (scheduled != null) {
            return of(scheduled);
        }

        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, () -> task.accept(() -> {
        }), delayTicks);
        return bukkitTask::cancel;
    }

    public static Task runAtFixedRate(Entity entity, Plugin plugin, Consumer<Task> task,
                                      long initialDelayTicks, long periodTicks) {
        ScheduledTask scheduled = entity.getScheduler().runAtFixedRate(plugin, wrap(task), null,
                initialDelayTicks, periodTicks);
        if (scheduled != null) {
            return of(scheduled);
        }

        final BukkitTask[] handle = new BukkitTask[1];
        handle[0] = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> task.accept(() -> {
                    if (handle[0] != null) {
                        handle[0].cancel();
                    }
                }),
                initialDelayTicks,
                periodTicks);
        return () -> handle[0].cancel();
    }

    /** 任务体拿到的 Task 取消的是同一个 ScheduledTask（经接口方法，不经实现类）。 */
    private static Consumer<ScheduledTask> wrap(Consumer<Task> task) {
        return scheduled -> task.accept(of(scheduled));
    }

    private static Task of(ScheduledTask scheduled) {
        return scheduled::cancel;
    }
}
