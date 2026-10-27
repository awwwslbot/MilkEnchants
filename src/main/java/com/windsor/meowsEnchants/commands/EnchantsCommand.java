package com.windsor.meowsEnchants.commands;

import com.windsor.meowsEnchants.MeowsEnchants;
import com.windsor.meowsEnchants.config.ReloadReport;
import com.windsor.meowsEnchants.gui.EnchantBrowser;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /enchants 命令：玩家打开附魔查询界面；{@code /enchants reload}（权限 meowsenchants.reload，
 * 控制台和 RCON 也可执行）重载配置。
 */
public final class EnchantsCommand implements BasicCommand {

    public static final String RELOAD_PERMISSION = "meowsenchants.reload";

    private final EnchantBrowser browser;
    private final MeowsEnchants plugin;

    public EnchantsCommand(@NotNull EnchantBrowser browser, @NotNull MeowsEnchants plugin) {
        this.browser = browser;
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack commandSourceStack, @NotNull String[] args) {
        CommandSender sender = commandSourceStack.getSender();
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reload(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行；重载配置用 /enchants reload。", NamedTextColor.RED));
            return;
        }
        browser.open(player, 0);
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            sender.sendMessage(Component.text("你没有权限重载附魔配置。", NamedTextColor.RED));
            return;
        }
        ReloadReport report = plugin.reloadConfiguration();
        List<String> lines = report.lines(plugin.getPluginMeta().getVersion(), false);
        for (String line : lines) {
            sender.sendMessage(Component.text(line));
        }
        // 控制台发送者已经把消息打进日志；其它发送者（玩家、RCON）另记一份，便于事后核对
        if (!(sender instanceof ConsoleCommandSender)) {
            plugin.getSLF4JLogger().info("{} 执行了 /enchants reload", sender.getName());
            lines.forEach(line -> plugin.getSLF4JLogger().info(line));
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack commandSourceStack, @NotNull String[] args) {
        if (args.length <= 1 && commandSourceStack.getSender().hasPermission(RELOAD_PERMISSION)) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            if ("reload".startsWith(prefix)) return List.of("reload");
        }
        return List.of();
    }
}
