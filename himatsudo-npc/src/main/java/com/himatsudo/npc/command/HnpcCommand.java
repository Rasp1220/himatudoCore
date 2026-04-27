package com.himatsudo.npc.command;

import com.himatsudo.npc.HimatsudoNpc;
import com.himatsudo.npc.action.NpcAction;
import com.himatsudo.npc.action.ServerTransferAction;
import com.himatsudo.npc.action.ShopAction;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
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
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * /hnpc コマンド
 *
 *   /hnpc assign <npc-id> server <server-name>  — サーバー転送 NPC を設定
 *   /hnpc assign <npc-id> shop <shop-id>         — ショップ NPC を設定
 *   /hnpc unassign <npc-id>                      — 設定を解除
 *   /hnpc list                                   — 設定一覧
 *   /hnpc reload                                 — npcs.yml 再読み込み
 *   /hnpc points give <player> <amount>          — ポイントを付与
 *   /hnpc points take <player> <amount>          — ポイントを削除
 *   /hnpc points check [player]                  — 残高確認
 */
public class HnpcCommand implements CommandExecutor, TabCompleter {

    private final HimatsudoNpc plugin;

    public HnpcCommand(HimatsudoNpc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("hnpc.admin")) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "assign"   -> handleAssign(sender, args);
            case "unassign" -> handleUnassign(sender, args);
            case "list"     -> handleList(sender);
            case "reload"   -> handleReload(sender);
            case "points"   -> handlePoints(sender, args);
            default         -> sendHelp(sender);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // assign
    // -------------------------------------------------------------------------

    private void handleAssign(CommandSender sender, String[] args) {
        // /hnpc assign <npc-id> server <server> | shop <shop-id>
        if (args.length < 4) { sender.sendMessage(usage("assign <npc-id> server <server-name>")); return; }

        int npcId;
        try { npcId = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) {
            sender.sendMessage(Component.text("NPC ID は数値で指定してください。", NamedTextColor.RED));
            return;
        }

        NpcAction action = switch (args[2].toLowerCase()) {
            case "server" -> new ServerTransferAction(plugin, args[3]);
            case "shop"   -> new ShopAction(plugin, args[3]);
            default -> null;
        };

        if (action == null) {
            sender.sendMessage(Component.text("タイプは server または shop を指定してください。", NamedTextColor.RED));
            return;
        }

        plugin.getNpcManager().assign(npcId, action);
        sender.sendMessage(Component.text(
                "NPC #" + npcId + " に " + action.getType() + " を設定しました。",
                NamedTextColor.GREEN));
    }

    // -------------------------------------------------------------------------
    // unassign
    // -------------------------------------------------------------------------

    private void handleUnassign(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(usage("unassign <npc-id>")); return; }
        int npcId;
        try { npcId = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) {
            sender.sendMessage(Component.text("NPC ID は数値で指定してください。", NamedTextColor.RED));
            return;
        }
        if (!plugin.getNpcManager().isAssigned(npcId)) {
            sender.sendMessage(Component.text("NPC #" + npcId + " は設定されていません。", NamedTextColor.YELLOW));
            return;
        }
        plugin.getNpcManager().unassign(npcId);
        sender.sendMessage(Component.text("NPC #" + npcId + " の設定を解除しました。", NamedTextColor.GREEN));
    }

    // -------------------------------------------------------------------------
    // list
    // -------------------------------------------------------------------------

    private void handleList(CommandSender sender) {
        Map<Integer, NpcAction> map = plugin.getNpcManager().getAssignments();
        if (map.isEmpty()) {
            sender.sendMessage(Component.text("設定済みの NPC はありません。", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("─── NPC 設定一覧 ───", NamedTextColor.GOLD));
        map.forEach((id, action) -> {
            String detail = switch (action) {
                case ServerTransferAction sta -> "→ server: " + sta.getServerName();
                case ShopAction sa            -> "→ shop: " + sa.getShopId();
                default                       -> "";
            };
            // Citizens NPC 名を取得 (Citizens が有効な場合)
            String npcName = getNpcName(id);
            sender.sendMessage(Component.text(
                    "  #" + id + " " + npcName + "  [" + action.getType() + "]  " + detail,
                    NamedTextColor.GRAY));
        });
    }

    // -------------------------------------------------------------------------
    // reload
    // -------------------------------------------------------------------------

    private void handleReload(CommandSender sender) {
        plugin.getShopRegistry().load();
        plugin.getNpcManager().load();
        sender.sendMessage(Component.text("shops.yml / npcs.yml を再読み込みしました。", NamedTextColor.GREEN));
    }

    // -------------------------------------------------------------------------
    // points
    // -------------------------------------------------------------------------

    private void handlePoints(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(usage("points <give|take|check> [player] [amount]"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "give"  -> pointsGive(sender, args);
            case "take"  -> pointsTake(sender, args);
            case "check" -> pointsCheck(sender, args);
            default      -> sender.sendMessage(usage("points <give|take|check> [player] [amount]"));
        }
    }

    private void pointsGive(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage(usage("points give <player> <amount>")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { notOnline(sender, args[2]); return; }
        long amount = parseLong(sender, args[3]); if (amount < 0) return;
        plugin.getCurrencyManager().add(target.getUniqueId(), amount);
        sender.sendMessage(Component.text(
                target.getName() + " に " + amount + " ポイントを付与しました。", NamedTextColor.GREEN));
        target.sendMessage(Component.text(amount + " ポイントを受け取りました。", NamedTextColor.AQUA));
    }

    private void pointsTake(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage(usage("points take <player> <amount>")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { notOnline(sender, args[2]); return; }
        long amount = parseLong(sender, args[3]); if (amount < 0) return;
        plugin.getCurrencyManager().deduct(target.getUniqueId(), amount);
        sender.sendMessage(Component.text(
                target.getName() + " から " + amount + " ポイントを削除しました。", NamedTextColor.GREEN));
    }

    private void pointsCheck(CommandSender sender, String[] args) {
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
        long balance = plugin.getCurrencyManager().getBalance(target.getUniqueId());
        sender.sendMessage(Component.text(
                target.getName() + " の所持ポイント: " + balance, NamedTextColor.AQUA));
    }

    // -------------------------------------------------------------------------
    // Help & Tab
    // -------------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("─── /hnpc コマンド一覧 ───", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /hnpc assign <id> server <server>  — サーバー転送 NPC 設定", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hnpc assign <id> shop <shop-id>   — ショップ NPC 設定", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hnpc unassign <id>                — 設定解除", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hnpc list                         — 設定一覧", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hnpc reload                       — 再読み込み", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hnpc points give <player> <amt>   — ポイント付与", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hnpc points take <player> <amt>   — ポイント削除", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /hnpc points check [player]        — 残高確認", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("assign", "unassign", "list", "reload", "points"), args[0]);
        if (args[0].equalsIgnoreCase("assign") && args.length == 3)
            return filter(List.of("server", "shop"), args[2]);
        if (args[0].equalsIgnoreCase("assign") && args.length == 4 && args[2].equalsIgnoreCase("shop"))
            return filter(plugin.getShopRegistry().getAll().keySet().stream().toList(), args[3]);
        if (args[0].equalsIgnoreCase("points") && args.length == 2)
            return filter(List.of("give", "take", "check"), args[1]);
        if (args[0].equalsIgnoreCase("points") && args.length == 3)
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase())).toList();
        return List.of();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Component usage(String usage) {
        return Component.text("使い方: /hnpc " + usage, NamedTextColor.RED);
    }

    private void notOnline(CommandSender sender, String name) {
        sender.sendMessage(Component.text(name + " はオンラインではありません。", NamedTextColor.RED));
    }

    private long parseLong(CommandSender sender, String s) {
        try { return Long.parseLong(s); }
        catch (NumberFormatException e) {
            sender.sendMessage(Component.text("数値を指定してください。", NamedTextColor.RED));
            return -1;
        }
    }

    private List<String> filter(List<String> list, String prefix) {
        return list.stream().filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase())).toList();
    }

    private String getNpcName(int id) {
        try {
            NPC npc = StreamSupport.stream(CitizensAPI.getNPCRegistry().spliterator(), false)
                    .filter(n -> n.getId() == id).findFirst().orElse(null);
            return npc != null ? "(" + npc.getName() + ")" : "";
        } catch (Exception e) {
            return "";
        }
    }
}
