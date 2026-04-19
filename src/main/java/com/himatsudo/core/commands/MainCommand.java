package com.himatsudo.core.commands;

import com.himatsudo.core.HimatsudoCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * MainCommand — handles /hc.
 *
 * Player sub-commands (default: true):
 *   /hc menu              — Nexus Menu を開く
 *   /hc profile [player]  — プロフィール GUI を開く
 *   /hc board             — スコアボード表示切替
 *
 * Admin sub-commands (himatsudo.admin):
 *   /hc reload   — コンフィグ再読み込み
 *   /hc status   — モジュール状態確認
 *   /hc version  — バージョン確認
 *   /hc text ... — フロートテキスト管理
 *   /hc help     — ヘルプ表示
 */
public class MainCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "himatsudo.admin";

    private final HimatsudoCore plugin;
    private final TextCommand textCommand;

    public MainCommand(HimatsudoCore plugin) {
        this.plugin       = plugin;
        this.textCommand  = new TextCommand(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            // --- player sub-commands (no admin permission required) ---
            case "menu"    -> handleMenu(sender);
            case "profile" -> handleProfile(sender, args);
            case "board"   -> handleBoard(sender);
            // --- admin sub-commands ---
            case "reload"  -> { if (checkAdmin(sender)) handleReload(sender); }
            case "status"  -> { if (checkAdmin(sender)) handleStatus(sender); }
            case "version" -> { if (checkAdmin(sender)) handleVersion(sender); }
            case "text"    -> { if (checkAdmin(sender)) textCommand.handle(sender, args); }
            case "help"    -> sendHelp(sender);
            default -> sender.sendMessage(Component.text(
                    "不明なサブコマンドです。/hc help を参照してください。", NamedTextColor.RED));
        }
        return true;
    }

    private boolean checkAdmin(CommandSender sender) {
        if (sender.hasPermission(PERMISSION)) return true;
        sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
        return false;
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers — player
    // -------------------------------------------------------------------------

    private void handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行できます。", NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("himatsudo.menu")) {
            player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return;
        }
        if (plugin.getMenuModule() == null) {
            player.sendMessage(Component.text("メニューモジュールが読み込まれていません。", NamedTextColor.RED));
            return;
        }
        plugin.getMenuModule().openMenu(player);
    }

    private void handleProfile(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行できます。", NamedTextColor.RED));
            return;
        }
        if (!viewer.hasPermission("himatsudo.profile")) {
            viewer.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return;
        }
        if (plugin.getProfileModule() == null) {
            viewer.sendMessage(Component.text("プロフィール機能は現在無効です。", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            plugin.getProfileModule().openProfile(viewer, viewer);
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            viewer.sendMessage(Component.text(
                    "プレイヤー「" + args[1] + "」はオンラインではありません。", NamedTextColor.RED));
            return;
        }
        plugin.getProfileModule().openProfile(viewer, target);
    }

    private void handleBoard(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行できます。", NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("himatsudo.board")) {
            player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return;
        }
        if (plugin.getBoardModule() == null) {
            player.sendMessage(Component.text("スコアボード機能は現在無効です。", NamedTextColor.RED));
            return;
        }
        boolean nowVisible = plugin.getBoardModule().toggleBoard(player);
        player.sendMessage(nowVisible
                ? Component.text("スコアボードを表示しました。", NamedTextColor.GREEN)
                : Component.text("スコアボードを非表示にしました。", NamedTextColor.YELLOW));
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers — admin
    // -------------------------------------------------------------------------

    private void handleReload(CommandSender sender) {
        sender.sendMessage(Component.text("[HimatsudoCore] コンフィグを再読み込み中...", NamedTextColor.YELLOW));

        try {
            plugin.reloadConfig();
            if (plugin.getAnnounceModule() != null) plugin.getAnnounceModule().reload();
            if (plugin.getAfkModule()      != null) plugin.getAfkModule().reload();
            if (plugin.getChatModule()     != null) plugin.getChatModule().reload();
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
        sender.sendMessage(statusLine("TextModule",        plugin.getTextModule()        != null));
        if (plugin.getTextModule() != null) {
            int count = plugin.getTextModule().getIds().size();
            sender.sendMessage(Component.text(
                    "    └ 設置テキスト数: " + count, NamedTextColor.GRAY));
        }
        sender.sendMessage(statusLine("JoinMessageModule", plugin.getJoinMessageModule() != null));
        sender.sendMessage(statusLine("ChatModule",        plugin.getChatModule()        != null));
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
        sender.sendMessage(Component.text("  /hc menu            — メニューを開く", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  /hc profile [名前]  — プロフィールを開く", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  /hc board           — スコアボード切替", NamedTextColor.AQUA));
        if (sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("  /hc reload          — コンフィグ再読み込み", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /hc status          — モジュール状態確認", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /hc version         — バージョン確認", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /hc text ...        — フロートテキスト管理", NamedTextColor.YELLOW));
        }
        sender.sendMessage(Component.text("  /hc help            — このヘルプを表示", NamedTextColor.GRAY));
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
            return List.of("menu", "profile", "board", "reload", "status", "version", "text", "help")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args[0].equalsIgnoreCase("profile") && args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        // /hc text ... のタブ補完を TextCommand に委譲
        if (args[0].equalsIgnoreCase("text")) {
            return textCommand.tabComplete(sender, args);
        }
        return List.of();
    }
}
