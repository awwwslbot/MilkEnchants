package com.windsor.meowsEnchants.testsupport;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;

/**
 * 和 Folia 的 FoliaEntityScheduler$EntityScheduledTask 一样：实现类不是 public，并且和调用方不在同一个包里。
 * 在它上面反射调用 cancel 会抛 IllegalAccessException，只有经 {@link ScheduledTask} 接口才能取消。
 */
final class HiddenScheduledTask implements ScheduledTask {
    private final Plugin plugin;
    private final boolean repeating;
    private volatile boolean cancelled;

    HiddenScheduledTask(Plugin plugin, boolean repeating) {
        this.plugin = plugin;
        this.repeating = repeating;
    }

    @Override public Plugin getOwningPlugin() { return plugin; }
    @Override public boolean isRepeatingTask() { return repeating; }
    @Override public CancelledState cancel() {
        cancelled = true;
        return CancelledState.CANCELLED_BY_CALLER;
    }
    @Override public ExecutionState getExecutionState() { return cancelled ? ExecutionState.CANCELLED : ExecutionState.IDLE; }
}
