package com.windsor.meowsEnchants.testsupport;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;

/** 造一个不能从外部反射调用的 {@link ScheduledTask}（见 {@link HiddenScheduledTask}）。 */
public final class HiddenTasks {
    private HiddenTasks() {}

    /** 只返回接口类型；取消与否看 {@link ScheduledTask#isCancelled()}。 */
    public static ScheduledTask create(Plugin plugin, boolean repeating) {
        return new HiddenScheduledTask(plugin, repeating);
    }
}
