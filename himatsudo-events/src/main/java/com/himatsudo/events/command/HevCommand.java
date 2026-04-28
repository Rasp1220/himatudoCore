package com.himatsudo.events.command;

import com.himatsudo.events.HimatsudoEvents;
import com.himatsudo.events.merchant.LegendaryMerchantManager;
import com.himatsudo.events.merchant.LegendaryMerchantShopMenu;
import com.himatsudo.events.treasure.TreasureShopMenu;
import com.himatsudo.events.treasure.TreasureSpot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * /hev コマンド
 *
 *   /hev place                      — 現在地に宝を設置 (管理者)
 *   /hev remove                     — 近くの宝を削除 (管理者)
 *   /hev list                       — 設置済み宝の一覧 (管理者)
 *   /hev reload                     — 設定ファイル再読み込み (管理者)
 *   /hev progress [player]          — 宝探し進捗確認
 *   /hev reset <player>             — 進捗リセット (管理者)
 *   /hev shop [player]              — 宝探しショップを強制開放 (管理者テスト用)
 *   /hev merchant spawn             — 現在地に伝説の商人をスポーン (管理者)
 *   /hev merchant despawn           — 伝説の商人を退場させる (管理者)
 *   /hev merchant info              — 伝説の商人の状態確認 (管理者)
 *   /hev merchant reload            — legendary-merchant.yml 再読み込み (管理者)
 *   /hev merchant shop [player]     — 伝説の商人ショップを強制開放 (管理者テスト用)
 */
public class HevCommand implements CommandExecutor, TabCompleter {

    private final HimatsudoEvents plugin;

    public HevCommand(HimatsudoEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "place"    -> handlePlace(sender);
            case "remove"   -> handleRemove(sender);
            case "list"     -> handleList(sender);
            case "reload"   -> handleReload(sender);
            case "progress" -> handleProgress(sender, args);
            case "reset"    -> handleReset(sender, args);
            case "shop"     -> handleShop(sender, args);
            case "merchant" -> handleMerchant(sender, args);
            default         -> sendHelp(sender);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // place
    // -------------------------------------------------------------------------

    private void handlePlace(CommandSender sender) {
        if (!sender.hasPermission("hev.admin")) { noPermission(sender); return; }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("プレイヤーのみ実行できます。", NamedTextColor.RED));
            return;
        }
        Location loc = player.getLocation().getBlock().getLocation();
        if (plugin.getTreasureManager().placeTreasure(loc)) {
            sender.sendMessage(Component.text(
                    "宝を設置しました！" + formatLoc(loc), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("この場所にはすでに宝があります。", NamedTextColor.YELLOW));
        }
    }

    // -------------------------------------------------------------------------
    // remove
    // -------------------------------------------------------------------------

    private void handleRemove(CommandSender sender) {
        if (!sender.hasPermission("hev.admin")) { noPermission(sender); return; }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("プレイヤーのみ実行できます。", NamedTextColor.RED));
            return;
        }
        if (plugin.getTreasureManager().removeNearest(player.getLocation())) {
            sender.sendMessage(Component.text("近くの宝を削除しました。", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("2ブロック以内に宝が見つかりません。", NamedTextColor.YELLOW));
        }
    }

    // -------------------------------------------------------------------------
    // list
    // -------------------------------------------------------------------------

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("hev.admin")) { noPermission(sender); return; }
        Map<String, TreasureSpot> spots = plugin.getTreasureManager().getSpots();
        if (spots.isEmpty()) {
            sender.sendMessage(Component.text("設置済みの宝はありません。", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text(
                "─── 宝の設置一覧 (" + spots.size() + " 個) ───", NamedTextColor.GOLD));
        for (TreasureSpot spot : spots.values()) {
            sender.sendMessage(Component.text("  " + spot.key(), NamedTextColor.GRAY));
        }
    }

    // -------------------------------------------------------------------------
    // reload
    // -------------------------------------------------------------------------

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("hev.admin")) { noPermission(sender); return; }
        plugin.reloadConfig();
        plugin.getShopRegistry().load();
        sender.sendMessage(Component.text(
                "config.yml と treasure-shop.yml を再読み込みしました。", NamedTextColor.GREEN));
    }

    // -------------------------------------------------------------------------
    // progress
    // -------------------------------------------------------------------------

    private void handleProgress(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("hev.admin")) { noPermission(sender); return; }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) { notOnline(sender, args[1]); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("プレイヤー名を指定してください。", NamedTextColor.RED));
            return;
        }

        int     required = plugin.getConfig().getInt("treasure-hunt.required-count", 10);
        int     count    = plugin.getProgressManager().getCount(target.getUniqueId());
        boolean unlocked = plugin.getProgressManager().hasUnlocked(target.getUniqueId());

        sender.sendMessage(Component.text(
                target.getName() + " の宝探し進捗: " + count + "/" + required
                + (unlocked ? " [解放済み]" : ""),
                NamedTextColor.AQUA));
    }

    // -------------------------------------------------------------------------
    // reset
    // -------------------------------------------------------------------------

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hev.admin")) { noPermission(sender); return; }
        if (args.length < 2) {
            sender.sendMessage(Component.text("使い方: /hev reset <player>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { notOnline(sender, args[1]); return; }

        plugin.getProgressManager().resetProgress(target.getUniqueId());
        sender.sendMessage(Component.text(
                target.getName() + " の宝探し進捗をリセットしました。", NamedTextColor.GREEN));
        target.sendMessage(Component.text("宝探しの進捗がリセットされました。", NamedTextColor.YELLOW));
    }

    // -------------------------------------------------------------------------
    // shop
    // -------------------------------------------------------------------------

    private void handleShop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hev.admin")) { noPermission(sender); return; }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) { notOnline(sender, args[1]); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("プレイヤー名を指定してください。", NamedTextColor.RED));
            return;
        }
        new TreasureShopMenu(plugin, target).open();
    }

    // -------------------------------------------------------------------------
    // merchant
    // -------------------------------------------------------------------------

    private void handleMerchant(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hev.admin")) { noPermission(sender); return; }

        String sub = args.length >= 2 ? args[1].toLowerCase() : "help";
        switch (sub) {
            case "spawn"   -> handleMerchantSpawn(sender);
            case "despawn" -> handleMerchantDespawn(sender);
            case "info"    -> handleMerchantInfo(sender);
            case "reload"  -> handleMerchantReload(sender);
            case "shop"    -> handleMerchantShop(sender, args);
            default        -> sendMerchantHelp(sender);
        }
    }

    private void handleMerchantSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("プレイヤーのみ実行できます。", NamedTextColor.RED));
            return;
        }
        LegendaryMerchantManager manager = plugin.getMerchantManager();
        if (manager == null) {
            sender.sendMessage(Component.text("Citizens が無効なため伝説の商人機能は使用できません。", NamedTextColor.RED));
            return;
        }
        if (!manager.spawn(player.getLocation())) {
            sender.sendMessage(Component.text("伝説の商人はすでに出現中です。", NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text(
                "伝説の商人を " + formatLoc(player.getLocation()) + " にスポーンしました。",
                NamedTextColor.GREEN));
    }

    private void handleMerchantDespawn(CommandSender sender) {
        LegendaryMerchantManager manager = plugin.getMerchantManager();
        if (manager == null) {
            sender.sendMessage(Component.text("Citizens が無効なため伝説の商人機能は使用できません。", NamedTextColor.RED));
            return;
        }
        if (!manager.isActive()) {
            sender.sendMessage(Component.text("伝説の商人は現在出現していません。", NamedTextColor.YELLOW));
            return;
        }
        manager.despawn(true);
        sender.sendMessage(Component.text("伝説の商人を退場させました。", NamedTextColor.GREEN));
    }

    private void handleMerchantInfo(CommandSender sender) {
        LegendaryMerchantManager manager = plugin.getMerchantManager();
        if (manager == null) {
            sender.sendMessage(Component.text("Citizens が無効なため伝説の商人機能は使用できません。", NamedTextColor.RED));
            return;
        }
        boolean active = manager.isActive();
        boolean inWindow = manager.isWithinTimeWindow();
        int start = plugin.getConfig().getInt("legendary-merchant.spawn-hour-start", 0);
        int end   = plugin.getConfig().getInt("legendary-merchant.spawn-hour-end", 24);

        sender.sendMessage(Component.text("─── 伝説の商人 ステータス ───", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
                "  状態: " + (active ? "出現中 (NPC ID: " + manager.getNpcId() + ")" : "不在"),
                active ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "  時間帯: " + start + ":00 〜 " + end + ":00 " + (inWindow ? "[現在許可中]" : "[現在対象外]"),
                inWindow ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(
                "  商品数: " + plugin.getMerchantShopRegistry().getItems().size() + " 件",
                NamedTextColor.AQUA));
    }

    private void handleMerchantReload(CommandSender sender) {
        plugin.getMerchantShopRegistry().load();
        sender.sendMessage(Component.text("legendary-merchant.yml を再読み込みしました。", NamedTextColor.GREEN));
    }

    private void handleMerchantShop(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) { notOnline(sender, args[2]); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("プレイヤー名を指定してください。", NamedTextColor.RED));
            return;
        }
        new LegendaryMerchantShopMenu(plugin, target).open();
    }

    // -------------------------------------------------------------------------
    // Help & Tab
    // -------------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("─── /hev コマンド一覧 ───", NamedTextColor.GOLD));
        if (sender.hasPermission("hev.admin")) {
            sender.sendMessage(Component.text("  /hev place                  — 現在地に宝を設置", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /hev remove                 — 近くの宝を削除", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /hev list                   — 設置済み宝の一覧", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /hev reload                 — treasure-shop.yml 再読み込み", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /hev reset <player>         — 進捗リセット", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /hev shop [player]          — 宝探しショップを開く (テスト)", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  /hev merchant <sub>         — 伝説の商人管理", NamedTextColor.GOLD));
        }
        sender.sendMessage(Component.text("  /hev progress [player]      — 宝探し進捗確認", NamedTextColor.YELLOW));
    }

    private void sendMerchantHelp(CommandSender sender) {
        sender.sendMessage(Component.text("─── /hev merchant サブコマンド ───", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /hev merchant spawn         — 現在地にスポーン", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hev merchant despawn       — 退場させる", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hev merchant info          — 状態確認", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hev merchant reload        — legendary-merchant.yml 再読み込み", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hev merchant shop [player] — ショップを開く (テスト)", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> opts = sender.hasPermission("hev.admin")
                    ? List.of("place", "remove", "list", "reload", "progress", "reset", "shop", "merchant")
                    : List.of("progress");
            return filter(opts, args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("merchant") && sender.hasPermission("hev.admin")) {
                return filter(List.of("spawn", "despawn", "info", "reload", "shop"), args[1]);
            }
            if (List.of("progress", "reset", "shop").contains(args[0].toLowerCase())) {
                return onlinePlayers(args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("merchant")
                && args[1].equalsIgnoreCase("shop")) {
            return onlinePlayers(args[2]);
        }
        return List.of();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void noPermission(CommandSender sender) {
        sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
    }

    private void notOnline(CommandSender sender, String name) {
        sender.sendMessage(Component.text(name + " はオンラインではありません。", NamedTextColor.RED));
    }

    private String formatLoc(Location loc) {
        return " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private List<String> filter(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }

    private List<String> onlinePlayers(String prefix) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }
}
