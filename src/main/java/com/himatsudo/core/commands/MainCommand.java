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

import java.util.ArrayList;
import java.util.List;

/**
 * MainCommand — /hc のエントリポイント。
 *
 * プレイヤー向け (default: true):
 *   /hc menu              — Nexus Menu を開く
 *   /hc profile [player]  — プロフィール GUI を開く
 *   /hc board             — スコアボード表示切替
 *
 * 管理者向け (himatsudo.admin):
 *   /hc reload   — コンフィグ再読み込み
 *   /hc status   — モジュール状態確認
 *   /hc text ... — フロートテキスト管理
 *   /hc help     — ヘルプ表示
 */
public class MainCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_ADMIN   = "himatsudo.admin";
    private static final String PERM_MENU    = "himatsudo.menu";
    private static final String PERM_PROFILE = "himatsudo.profile";
    private static final String PERM_BOARD   = "himatsudo.board";

    private final HimatsudoCore plugin;
    private final TextCommand textCommand;

    public MainCommand(HimatsudoCore plugin) {
        this.plugin      = plugin;
        this.textCommand = new TextCommand(plugin);
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
            case "menu"    -> handleMenu(sender);
            case "profile" -> handleProfile(sender, args);
            case "board"   -> handleBoard(sender);
            case "reload"  -> { if (checkAdmin(sender)) handleReload(sender); }
            case "status"  -> { if (checkAdmin(sender)) handleStatus(sender); }
            case "text"    -> { if (checkAdmin(sender)) textCommand.handle(sender, args); }
            case "help"    -> sendHelp(sender);
            default        -> sender.sendMessage(Component.text(
                    "不明なサブコマンドです。/hc help を参照してください。", NamedTextColor.RED));
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Player sub-commands
    // -------------------------------------------------------------------------

    private void handleMenu(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission(PERM_MENU)) { noPermission(player); return; }
        if (plugin.getMenuModule() == null) { moduleUnavailable(player, "メニュー"); return; }
        plugin.getMenuModule().openMenu(player);
    }

    private void handleProfile(CommandSender sender, String[] args) {
        Player viewer = requirePlayer(sender);
        if (viewer == null) return;
        if (!viewer.hasPermission(PERM_PROFILE)) { noPermission(viewer); return; }
        if (plugin.getProfileModule() == null) { moduleUnavailable(viewer, "プロフィール"); return; }

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
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission(PERM_BOARD)) { noPermission(player); return; }
        if (plugin.getBoardModule() == null) { moduleUnavailable(player, "スコアボード"); return; }

        boolean visible = plugin.getBoardModule().toggleBoard(player);
        player.sendMessage(visible
                ? Component.text("スコアボードを表示しました。", NamedTextColor.GREEN)
                : Component.text("スコアボードを非表示にしました。", NamedTextColor.YELLOW));
    }

    // -------------------------------------------------------------------------
    // Admin sub-commands
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
                    "[HimatsudoCore] エラー: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().severe("Reload error: " + e.getMessage());
        }
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(Component.text("--- HimatsudoCore ステータス ---", NamedTextColor.GOLD));
        sender.sendMessage(statusLine("DiscordModule",      plugin.getDiscordModule()      != null));
        sender.sendMessage(statusLine("AnnounceModule",     plugin.getAnnounceModule()     != null));
        sender.sendMessage(statusLine("BoardModule",        plugin.getBoardModule()        != null));
        sender.sendMessage(statusLine("MenuModule",         plugin.getMenuModule()         != null));
        sender.sendMessage(statusLine("AfkModule",          plugin.getAfkModule()          != null));
        sender.sendMessage(statusLine("TextModule",         plugin.getTextModule()         != null));
        if (plugin.getTextModule() != null) {
            sender.sendMessage(Component.text(
                    "    └ 設置テキスト数: " + plugin.getTextModule().getIds().size(),
                    NamedTextColor.GRAY));
        }
        sender.sendMessage(statusLine("JoinMessageModule",  plugin.getJoinMessageModule()  != null));
        sender.sendMessage(statusLine("ChatModule",         plugin.getChatModule()         != null));
    }

    // -------------------------------------------------------------------------
    // Help & tab completion
    // -------------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("--- /hc コマンド一覧 ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /hc menu            — メニューを開く",     NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  /hc profile [名前]  — プロフィールを開く", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  /hc board           — スコアボード切替",   NamedTextColor.AQUA));
        if (sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(Component.text("  /hc reload          — コンフィグ再読み込み",   NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /hc status          — モジュール状態確認",     NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /hc text ...        — フロートテキスト管理",   NamedTextColor.YELLOW));
        }
        sender.sendMessage(Component.text("  /hc help            — このヘルプを表示",   NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("menu", "profile", "board", "help"));
            if (sender.hasPermission(PERM_ADMIN)) subs.addAll(List.of("reload", "status", "text"));
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args[0].equalsIgnoreCase("profile") && args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args[0].equalsIgnoreCase("text") && sender.hasPermission(PERM_ADMIN)) {
            return textCommand.tabComplete(sender, args);
        }
        return List.of();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean checkAdmin(CommandSender sender) {
        if (sender.hasPermission(PERM_ADMIN)) return true;
        noPermission(sender);
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行できます。", NamedTextColor.RED));
        return null;
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
    }

    private void moduleUnavailable(CommandSender sender, String name) {
        sender.sendMessage(Component.text(name + "機能は現在無効です。", NamedTextColor.RED));
    }

    private Component statusLine(String name, boolean loaded) {
        return Component.text("  " + name + ": ", NamedTextColor.GRAY)
                .append(Component.text(loaded ? "✔ 稼働中" : "✘ 未起動",
                        loaded ? NamedTextColor.GREEN : NamedTextColor.RED));
    }
}
