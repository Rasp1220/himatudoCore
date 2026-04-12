package com.himatsudo.core.commands;

import com.himatsudo.core.HimatsudoCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * MainCommand — handles /hc (HimatsudoCore admin command).
 *
 * Sub-commands:
 *   /hc reload   — reloads config and all modules
 *   /hc status   — shows current module status
 *   /hc version  — shows plugin version
 *   /hc help     — shows this help
 *
 * Permission: himatsudo.admin
 */
public class MainCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "himatsudo.admin";

    private final HimatsudoCore plugin;

    public MainCommand(HimatsudoCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload"  -> handleReload(sender);
            case "status"  -> handleStatus(sender);
            case "version" -> handleVersion(sender);
            case "help"    -> sendHelp(sender);
            default -> {
                sender.sendMessage(Component.text(
                        "不明なサブコマンドです。/hc help を参照してください。", NamedTextColor.RED));
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers
    // -------------------------------------------------------------------------

    private void handleReload(CommandSender sender) {
        sender.sendMessage(Component.text("[HimatsudoCore] コンフィグを再読み込み中...", NamedTextColor.YELLOW));

        try {
            plugin.reloadConfig();
            if (plugin.getAnnounceModule() != null) plugin.getAnnounceModule().reload();
            if (plugin.getAfkModule()      != null) plugin.getAfkModule().reload();
            sender.sendMessage(Component.text("[HimatsudoCore] 再読み込みが完了しました。", NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text(
                    "[HimatsudoCore] 再読み込み中にエラーが発生しました: " + e.getMessage(),
                    NamedTextColor.RED));
            plugin.getLogger().severe("Reload error: " + e.getMessage());
        }
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(Component.text("--- HimatsudoCore ステータス ---", NamedTextColor.GOLD));
        sender.sendMessage(statusLine("DiscordModule",  plugin.getDiscordModule()  != null));
        sender.sendMessage(statusLine("AnnounceModule", plugin.getAnnounceModule() != null));
        sender.sendMessage(statusLine("BoardModule",    plugin.getBoardModule()    != null));
        sender.sendMessage(statusLine("MenuModule",     plugin.getMenuModule()     != null));
        sender.sendMessage(statusLine("AfkModule",      plugin.getAfkModule()      != null));
    }

    private Component statusLine(String name, boolean loaded) {
        String state = loaded ? "✔ 稼働中" : "✘ 未起動";
        NamedTextColor color = loaded ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text("  " + name + ": ", NamedTextColor.GRAY)
                .append(Component.text(state, color));
    }

    private void handleVersion(CommandSender sender) {
        String version = plugin.getPluginMeta().getVersion();
        sender.sendMessage(Component.text(
                "HimatsudoCore v" + version, NamedTextColor.AQUA));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("--- /hc コマンド一覧 ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /hc reload  — コンフィグ再読み込み", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hc status  — モジュール状態確認", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hc version — バージョン確認", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hc help    — このヘルプを表示", NamedTextColor.YELLOW));
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("reload", "status", "version", "help")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
