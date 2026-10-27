package com.windsor.meowsEnchants.actions.impl;

import com.windsor.meowsEnchants.actions.Action;
import com.windsor.meowsEnchants.actions.ActionContext;
import com.windsor.meowsEnchants.schema.Param;
import com.windsor.meowsEnchants.utils.PlaceholderAPIHook;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class CommandAction implements Action {
    public static final Param<String> COMMAND = Param.string("command", "命令")
            .help("支持 {player}、{target}、{level} 和 PlaceholderAPI 占位符").required().build();
    public static final Param<String> RUN_AS = Param.enumeration("run_as", "执行者", List.of("PLAYER", "CONSOLE"))
            .def("PLAYER").build();
    /** 不进 schema：编辑器不开放 command 动作。 */
    public static final List<Param<?>> PARAMS = List.of(COMMAND, RUN_AS);
    private final String command;
    private final boolean runAsConsole;

    public CommandAction(Map<String, Object> params) {
        this.command = COMMAND.read(params);
        this.runAsConsole = "CONSOLE".equalsIgnoreCase(RUN_AS.read(params));
    }

    @Override
    public boolean execute(ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        // 1. 获取目标实体名称（用于 {target} 占位符）
        List<Entity> targets = context.getTargets();
        String targetName = "";
        if (targets != null && !targets.isEmpty()) {
            Entity target = targets.getFirst();
            if (target instanceof Player) {
                targetName = target.getName();
            } else {
                targetName = target.getType().name().toLowerCase();
            }
        }

        // 2. 替换内置占位符
        String parsedCommand = command
                .replace("{player}", player.getName())
                .replace("{target}", targetName)
                .replace("{level}", String.valueOf(context.getEnchantLevel()));

        // 3. 使用 PlaceholderAPI 解析（若已加载）
        if (PlaceholderAPIHook.isAvailable()) {
            parsedCommand = PlaceholderAPIHook.setPlaceholders(player, parsedCommand);
        }

        // 4. 执行命令
        boolean success = false;
        try {
            if (runAsConsole) {
                success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand);
            } else {
                success = player.performCommand(parsedCommand);
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[MeowsEnchants] 执行命令失败: " + parsedCommand + " - " + e.getMessage());
            return false;
        }

        return success;
    }
}