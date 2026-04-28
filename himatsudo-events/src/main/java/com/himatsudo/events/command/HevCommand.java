package com.himatsudo.events.command;

import com.himatsudo.events.HimatsudoEvents;
import com.himatsudo.events.merchant.LegendaryMerchantEvent;
import com.himatsudo.events.merchant.LegendaryMerchantManager;
import com.himatsudo.events.merchant.LegendaryMerchantShopMenu;
import com.himatsudo.events.treasure.TreasureHuntEvent;
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
 *   Treasure-hunt:
 *     /hev place                      — 現在地に宝を設置
 *     /hev remove                     — 近くの宝を削除
 *     /hev list                       — 設置済み宝の一覧
 *     /hev reload                     — treasure-shop.yml 再読み込み
 *     /hev progress [player]          — 宝探し進捗確認
 *     /hev reset <player>             — 進捗リセット
 *     /hev shop [player]              — ショップを強制開放 (テスト)
 *
 *   Legendary merchant:
 *     /hev merchant spawn             — 現在地にスポーン
 *     /hev merchant despawn           — 退場させる
 *     /hev merchant info              — 状態確認
 *     /hev merchant reload            — legendary-merchant.yml 再読み込み
 *     /hev merchant shop [player]     — ショップを強制開放 (テスト)
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

    // =========================================================================
    // Treasure-hunt subcommands
    // =========================================================================

    private void handlePlace(CommandSender sender) {
        if (!isAdmin(sender)) return;
        if (!(sender instanceof Player player)) { consoleOnly(sender); return; }
        Location loc = player.getLocation().getBlock().getLocation();
        if (treasure().getTreasureManager().placeTreasure(loc)) {
            ok(sender, "宝を設置しました！" + fmtLoc(loc));
        } else {
            warn(sender, "この場所にはすでに宝があります。");
        }
    }

    private void handleRemove(CommandSender sender) {
        if (!isAdmin(sender)) return;
        if (!(sender instanceof Player player)) { consoleOnly(sender); return; }
        if (treasure().getTreasureManager().removeNearest(player.getLocation())) {
            ok(sender, "近くの宝を削除しました。");
        } else {
            warn(sender, "2ブロック以内に宝が見つかりません。");
        }
    }

    private void handleList(CommandSender sender) {
        if (!isAdmin(sender)) return;
        Map<String, TreasureSpot> spots = treasure().getTreasureManager().getSpots();
        if (spots.isEmpty()) { warn(sender, "設置済みの宝はありません。"); return; }
        sender.sendMessage(Component.text("─── 宝の設置一覧 (" + spots.size() + " 個) ───", NamedTextColor.GOLD));
        for (TreasureSpot spot : spots.values()) {
            sender.sendMessage(Component.text("  " + spot.key(), NamedTextColor.GRAY));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!isAdmin(sender)) return;
        plugin.reloadConfig();
        treasure().getShopRegistry().load();
        ok(sender, "config.yml と treasure-shop.yml を再読み込みしました。");
    }

    private void handleProgress(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, 1);
        if (target == null) return;

        int     required = plugin.getConfig().getInt("treasure-hunt.required-count", 10);
        int     count    = treasure().getProgressManager().getCount(target.getUniqueId());
        boolean unlocked = treasure().getProgressManager().hasUnlocked(target.getUniqueId());

        sender.sendMessage(Component.text(
                target.getName() + " の宝探し進捗: " + count + "/" + required
                + (unlocked ? " [解放済み]" : ""),
                NamedTextColor.AQUA));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) return;
        if (args.length < 2) { err(sender, "使い方: /hev reset <player>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { notOnline(sender, args[1]); return; }
        treasure().getProgressManager().resetProgress(target.getUniqueId());
        ok(sender, target.getName() + " の宝探し進捗をリセットしました。");
        target.sendMessage(Component.text("宝探しの進捗がリセットされました。", NamedTextColor.YELLOW));
    }

    private void handleShop(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) return;
        Player target = resolveTarget(sender, args, 1);
        if (target == null) return;
        new TreasureShopMenu(treasure(), target).open();
    }

    // =========================================================================
    // Legendary merchant subcommands
    // =========================================================================

    private void handleMerchant(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) return;
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
        if (!(sender instanceof Player player)) { consoleOnly(sender); return; }
        LegendaryMerchantManager mgr = requireMerchantManager(sender);
        if (mgr == null) return;
        if (!mgr.spawn(player.getLocation())) {
            warn(sender, "伝説の商人はすでに出現中です。");
        } else {
            ok(sender, "伝説の商人を " + fmtLoc(player.getLocation()) + " にスポーンしました。");
        }
    }

    private void handleMerchantDespawn(CommandSender sender) {
        LegendaryMerchantManager mgr = requireMerchantManager(sender);
        if (mgr == null) return;
        if (!mgr.isActive()) { warn(sender, "伝説の商人は現在出現していません。"); return; }
        mgr.despawn(true);
        ok(sender, "伝説の商人を退場させました。");
    }

    private void handleMerchantInfo(CommandSender sender) {
        LegendaryMerchantManager mgr = requireMerchantManager(sender);
        if (mgr == null) return;
        int start = plugin.getConfig().getInt("legendary-merchant.spawn-hour-start", 0);
        int end   = plugin.getConfig().getInt("legendary-merchant.spawn-hour-end", 24);
        sender.sendMessage(Component.text("─── 伝説の商人 ステータス ───", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
                "  状態: " + (mgr.isActive() ? "出現中 (NPC ID: " + mgr.getNpcId() + ")" : "不在"),
                mgr.isActive() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "  時間帯: " + start + ":00 〜 " + end + ":00 "
                + (mgr.isWithinTimeWindow() ? "[現在許可中]" : "[現在対象外]"),
                mgr.isWithinTimeWindow() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(
                "  商品数: " + merchant().getShopRegistry().getItems().size() + " 件",
                NamedTextColor.AQUA));
    }

    private void handleMerchantReload(CommandSender sender) {
        merchant().getShopRegistry().load();
        ok(sender, "legendary-merchant.yml を再読み込みしました。");
    }

    private void handleMerchantShop(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, 2);
        if (target == null) return;
        new LegendaryMerchantShopMenu(merchant(), target).open();
    }

    // =========================================================================
    // Help & Tab
    // =========================================================================

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
        if (args.length == 3
                && args[0].equalsIgnoreCase("merchant")
                && args[1].equalsIgnoreCase("shop")) {
            return onlinePlayers(args[2]);
        }
        return List.of();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private TreasureHuntEvent      treasure() { return plugin.getTreasureHuntEvent(); }
    private LegendaryMerchantEvent merchant() { return plugin.getLegendaryMerchantEvent(); }

    private LegendaryMerchantManager requireMerchantManager(CommandSender sender) {
        LegendaryMerchantManager mgr = merchant().getManager();
        if (mgr == null) {
            err(sender, "Citizens が無効なため伝説の商人機能は使用できません。");
        }
        return mgr;
    }

    /**
     * Resolves the target player from args[argIndex], or falls back to the sender.
     * Returns null and sends an error message if resolution fails.
     */
    private Player resolveTarget(CommandSender sender, String[] args, int argIndex) {
        if (args.length > argIndex) {
            if (!sender.hasPermission("hev.admin")) { noPermission(sender); return null; }
            Player t = Bukkit.getPlayer(args[argIndex]);
            if (t == null) { notOnline(sender, args[argIndex]); return null; }
            return t;
        }
        if (sender instanceof Player p) return p;
        err(sender, "プレイヤー名を指定してください。");
        return null;
    }

    private boolean isAdmin(CommandSender sender) {
        if (sender.hasPermission("hev.admin")) return true;
        noPermission(sender);
        return false;
    }

    private void noPermission(CommandSender sender) { err(sender, "権限がありません。"); }
    private void consoleOnly(CommandSender sender)  { err(sender, "プレイヤーのみ実行できます。"); }
    private void notOnline(CommandSender s, String n) { err(s, n + " はオンラインではありません。"); }

    private void ok(CommandSender s, String msg)   { s.sendMessage(Component.text(msg, NamedTextColor.GREEN)); }
    private void warn(CommandSender s, String msg) { s.sendMessage(Component.text(msg, NamedTextColor.YELLOW)); }
    private void err(CommandSender s, String msg)  { s.sendMessage(Component.text(msg, NamedTextColor.RED)); }

    private String fmtLoc(Location loc) {
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
