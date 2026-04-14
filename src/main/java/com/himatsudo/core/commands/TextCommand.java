package com.himatsudo.core.commands;

import com.himatsudo.core.HimatsudoCore;
import com.himatsudo.core.modules.TextModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

/**
 * TextCommand — /hc text サブコマンドをすべて処理するハンドラ。
 *
 * MainCommand から args[0]=="text" の場合に委譲される。
 *
 * サブコマンド一覧:
 *   create  <id> [--look] <テキスト...>
 *   edit    <id> text <テキスト...>
 *   edit    <id> scale <数値>
 *   edit    <id> billboard <CENTER|FIXED|HORIZONTAL|VERTICAL>
 *   edit    <id> background <on|off|#RRGGBB|#AARRGGBB>
 *   edit    <id> shadow <on|off>
 *   edit    <id> align <left|center|right>
 *   list    [ページ]
 *   delete  <id>
 *   move    <id> <dx> <dy> <dz>
 *   info    <id>
 *   tp      <id>
 *   help
 */
public class TextCommand {

    private static final int PAGE_SIZE = 8;

    private final HimatsudoCore plugin;

    public TextCommand(HimatsudoCore plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // エントリポイント (MainCommand から呼ばれる)
    // -------------------------------------------------------------------------

    /**
     * @param args コマンド引数全体 (args[0]=="text")
     */
    public boolean handle(CommandSender sender, String[] args) {
        TextModule tm = plugin.getTextModule();
        if (tm == null) {
            sender.sendMessage(Component.text("[TextModule] が有効ではありません。", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "create"                    -> handleCreate(sender, args, tm);
            case "edit"                      -> handleEdit(sender, args, tm);
            case "list"                      -> handleList(sender, args, tm);
            case "delete", "del", "remove"   -> handleDelete(sender, args, tm);
            case "move"                      -> handleMove(sender, args, tm);
            case "info"                      -> handleInfo(sender, args, tm);
            case "tp", "teleport"            -> handleTp(sender, args, tm);
            case "help"                      -> sendHelp(sender);
            default -> sender.sendMessage(Component.text(
                    "不明なサブコマンドです。 /hc text help で一覧を確認してください。",
                    NamedTextColor.RED));
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    /**
     * /hc text create <id> [--look] <テキスト...>
     *
     * --look フラグなし: プレイヤーのアイレベルの位置に設置
     * --look フラグあり: 視線の先のブロック表面に設置
     */
    private void handleCreate(CommandSender sender, String[] args, TextModule tm) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行できます。", NamedTextColor.RED));
            return;
        }
        // args: [text, create, <id>, [--look], <text...>]
        if (args.length < 4) {
            sender.sendMessage(Component.text(
                    "使用法: /hc text create <id> [--look] <テキスト...>", NamedTextColor.YELLOW));
            return;
        }

        String id = args[2];
        if (!isValidId(id)) {
            sender.sendMessage(Component.text(
                    "ID には半角英数字・ハイフン・アンダースコアのみ使用できます。", NamedTextColor.RED));
            return;
        }
        if (tm.exists(id)) {
            sender.sendMessage(Component.text(
                    "ID '" + id + "' はすでに存在します。別の ID を指定してください。", NamedTextColor.RED));
            return;
        }

        // --look フラグの判定
        boolean lookMode      = args.length > 3 && args[3].equalsIgnoreCase("--look");
        int     textStartIdx  = lookMode ? 4 : 3;

        if (args.length <= textStartIdx) {
            sender.sendMessage(Component.text("テキストを入力してください。", NamedTextColor.RED));
            return;
        }

        String rawText = String.join(" ", Arrays.copyOfRange(args, textStartIdx, args.length));

        Location loc;
        if (lookMode) {
            Block target = player.getTargetBlockExact(10);
            if (target != null) {
                // ブロック表面の中心、1段上 (文字がブロックに重ならないよう)
                loc = target.getLocation().add(0.5, 1.0, 0.5);
            } else {
                // 視線の先にブロックがなければ 5ブロック前方
                loc = player.getEyeLocation()
                        .add(player.getLocation().getDirection().multiply(5));
            }
        } else {
            // プレイヤーのアイレベル (足元より0.1上) に設置
            loc = player.getEyeLocation();
        }

        boolean ok = tm.create(id, loc, rawText,
                Display.Billboard.CENTER, 1.0f,
                true, Color.fromARGB(64, 0, 0, 0));

        if (ok) {
            sender.sendMessage(Component.text(
                    "[TextModule] '" + id + "' を設置しました。", NamedTextColor.GREEN)
                    .append(Component.text(
                            "  (/hc text edit で編集できます)", NamedTextColor.GRAY)));
        } else {
            sender.sendMessage(Component.text("[TextModule] 設置に失敗しました。", NamedTextColor.RED));
        }
    }

    // -------------------------------------------------------------------------
    // edit
    // -------------------------------------------------------------------------

    /**
     * /hc text edit <id> <property> <value...>
     *
     * Properties:
     *   text       — テキスト内容 (&カラーコード / &#RRGGBB / \n 改行 対応)
     *   scale      — スケール倍率 (例: 0.5, 1.0, 2.0)
     *   billboard  — CENTER / FIXED / HORIZONTAL / VERTICAL
     *   background — on / off / #RRGGBB / #AARRGGBB
     *   shadow     — on / off
     *   align      — left / center / right
     */
    private void handleEdit(CommandSender sender, String[] args, TextModule tm) {
        if (args.length < 5) {
            sender.sendMessage(Component.text(
                    "使用法: /hc text edit <id> <text|scale|billboard|background|shadow|align> <値>",
                    NamedTextColor.YELLOW));
            return;
        }

        String id = args[2];
        if (!tm.exists(id)) {
            sender.sendMessage(idNotFound(id));
            return;
        }

        String property = args[3].toLowerCase();
        String value     = String.join(" ", Arrays.copyOfRange(args, 4, args.length));

        switch (property) {
            case "text" -> {
                tm.editText(id, value);
                sender.sendMessage(ok("'" + id + "' のテキストを更新しました。"));
            }
            case "scale" -> {
                try {
                    float scale = Float.parseFloat(value);
                    if (scale <= 0f || scale > 20f) throw new NumberFormatException();
                    tm.editScale(id, scale);
                    sender.sendMessage(ok("'" + id + "' のスケールを " + scale + " に設定しました。"));
                } catch (NumberFormatException e) {
                    sender.sendMessage(err("スケールは 0.01〜20 の数値で指定してください。"));
                }
            }
            case "billboard" -> {
                try {
                    Display.Billboard bb = Display.Billboard.valueOf(value.toUpperCase());
                    tm.editBillboard(id, bb);
                    sender.sendMessage(ok("'" + id + "' のビルボードを " + bb.name() + " に設定しました。"));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(err("CENTER / FIXED / HORIZONTAL / VERTICAL のいずれかを指定してください。"));
                }
            }
            case "background", "bg" -> {
                if (value.equalsIgnoreCase("off")) {
                    tm.editBackground(id, false, Color.fromARGB(0, 0, 0, 0));
                    sender.sendMessage(ok("'" + id + "' の背景を非表示にしました。"));
                } else if (value.equalsIgnoreCase("on")) {
                    tm.editBackground(id, true, Color.fromARGB(64, 0, 0, 0));
                    sender.sendMessage(ok("'" + id + "' の背景を表示しました。"));
                } else if (value.startsWith("#")) {
                    Color color = parseHexColor(value);
                    if (color == null) {
                        sender.sendMessage(err("色の形式は #RRGGBB または #AARRGGBB です。"));
                        return;
                    }
                    tm.editBackground(id, true, color);
                    sender.sendMessage(ok("'" + id + "' の背景色を " + value + " に設定しました。"));
                } else {
                    sender.sendMessage(err("on / off / #RRGGBB のいずれかを指定してください。"));
                }
            }
            case "shadow" -> {
                if (value.equalsIgnoreCase("on")) {
                    tm.editShadow(id, true);
                    sender.sendMessage(ok("'" + id + "' のシャドウを ON にしました。"));
                } else if (value.equalsIgnoreCase("off")) {
                    tm.editShadow(id, false);
                    sender.sendMessage(ok("'" + id + "' のシャドウを OFF にしました。"));
                } else {
                    sender.sendMessage(err("on / off で指定してください。"));
                }
            }
            case "align" -> {
                try {
                    TextDisplay.TextAlignment align =
                            TextDisplay.TextAlignment.valueOf(value.toUpperCase());
                    tm.editAlign(id, align);
                    sender.sendMessage(ok("'" + id + "' の整列を " + align.name() + " に設定しました。"));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(err("LEFT / CENTER / RIGHT のいずれかを指定してください。"));
                }
            }
            default -> sender.sendMessage(err(
                    "不明なプロパティです。text / scale / billboard / background / shadow / align が使えます。"));
        }
    }

    // -------------------------------------------------------------------------
    // list
    // -------------------------------------------------------------------------

    /**
     * /hc text list [ページ]
     * 登録済みテキストの一覧を PAGE_SIZE 件ずつ表示する。
     */
    private void handleList(CommandSender sender, String[] args, TextModule tm) {
        List<String> ids = tm.getIds().stream()
                .sorted(String::compareToIgnoreCase)
                .toList();

        if (ids.isEmpty()) {
            sender.sendMessage(Component.text("登録されているテキストはありません。", NamedTextColor.YELLOW));
            return;
        }

        int page = 1;
        if (args.length >= 3) {
            try { page = Math.max(1, Integer.parseInt(args[2])); }
            catch (NumberFormatException ignored) {}
        }

        int totalPages = (int) Math.ceil((double) ids.size() / PAGE_SIZE);
        page = Math.min(page, totalPages);
        int start = (page - 1) * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, ids.size());

        sender.sendMessage(Component.text(
                "--- テキスト一覧 [" + page + "/" + totalPages + "] ---", NamedTextColor.GOLD));

        for (int i = start; i < end; i++) {
            String id = ids.get(i);
            TextDisplay td = tm.getDisplay(id);
            String pos = td != null
                    ? String.format("%s  %.1f, %.1f, %.1f",
                            td.getWorld().getName(), td.getX(), td.getY(), td.getZ())
                    : "不明";

            sender.sendMessage(
                    Component.text("  " + (i + 1) + ". ", NamedTextColor.GRAY)
                    .append(Component.text(id, NamedTextColor.YELLOW))
                    .append(Component.text("  (" + pos + ")", NamedTextColor.DARK_GRAY)));
        }

        if (page < totalPages) {
            sender.sendMessage(Component.text(
                    "  /hc text list " + (page + 1) + " で次のページ", NamedTextColor.GRAY));
        }
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    /** /hc text delete <id> */
    private void handleDelete(CommandSender sender, String[] args, TextModule tm) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("使用法: /hc text delete <id>", NamedTextColor.YELLOW));
            return;
        }
        String id = args[2];
        if (tm.delete(id)) {
            sender.sendMessage(ok("'" + id + "' を削除しました。"));
        } else {
            sender.sendMessage(idNotFound(id));
        }
    }

    // -------------------------------------------------------------------------
    // move
    // -------------------------------------------------------------------------

    /**
     * /hc text move <id> <dx> <dy> <dz>
     * 相対移動。例: /hc text move welcome 0 0.5 0 → 0.5ブロック上に移動
     */
    private void handleMove(CommandSender sender, String[] args, TextModule tm) {
        if (args.length < 6) {
            sender.sendMessage(Component.text(
                    "使用法: /hc text move <id> <dx> <dy> <dz>", NamedTextColor.YELLOW));
            return;
        }
        String id = args[2];
        try {
            double dx = Double.parseDouble(args[3]);
            double dy = Double.parseDouble(args[4]);
            double dz = Double.parseDouble(args[5]);
            if (tm.move(id, dx, dy, dz)) {
                sender.sendMessage(ok(String.format(
                        "'%s' を (%.2f, %.2f, %.2f) 移動しました。", id, dx, dy, dz)));
            } else {
                sender.sendMessage(idNotFound(id));
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(err("dx / dy / dz には数値を入力してください。"));
        }
    }

    // -------------------------------------------------------------------------
    // info
    // -------------------------------------------------------------------------

    /** /hc text info <id> — テキストの詳細情報を表示 */
    private void handleInfo(CommandSender sender, String[] args, TextModule tm) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("使用法: /hc text info <id>", NamedTextColor.YELLOW));
            return;
        }
        String id = args[2];
        TextDisplay td = tm.getDisplay(id);
        if (td == null) {
            sender.sendMessage(idNotFound(id));
            return;
        }

        Vector3f scale = td.getTransformation().getScale();

        sender.sendMessage(Component.text("--- " + id + " ---", NamedTextColor.GOLD));
        sender.sendMessage(info("ワールド",    td.getWorld().getName()));
        sender.sendMessage(info("座標",
                String.format("%.3f, %.3f, %.3f", td.getX(), td.getY(), td.getZ())));
        sender.sendMessage(info("ビルボード", td.getBillboard().name()));
        sender.sendMessage(info("スケール",   String.format("%.3f", scale.x)));
        sender.sendMessage(info("シャドウ",   String.valueOf(td.isShadowed())));
        sender.sendMessage(info("整列",       td.getAlignment().name()));
        Color bg = td.getBackgroundColor();
        if (bg != null) {
            sender.sendMessage(info("背景色 (ARGB)",
                    String.format("#%02X%02X%02X%02X",
                            bg.getAlpha(), bg.getRed(), bg.getGreen(), bg.getBlue())));
        }
    }

    // -------------------------------------------------------------------------
    // tp
    // -------------------------------------------------------------------------

    /** /hc text tp <id> — テキストの設置場所にテレポート */
    private void handleTp(CommandSender sender, String[] args, TextModule tm) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行できます。", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("使用法: /hc text tp <id>", NamedTextColor.YELLOW));
            return;
        }
        String id = args[2];
        TextDisplay td = tm.getDisplay(id);
        if (td == null) {
            sender.sendMessage(idNotFound(id));
            return;
        }
        player.teleport(td.getLocation());
        player.sendMessage(ok("'" + id + "' の位置へテレポートしました。"));
    }

    // -------------------------------------------------------------------------
    // ヘルプ
    // -------------------------------------------------------------------------

    public void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("--- /hc text コマンド ---", NamedTextColor.GOLD));
        helpLine(sender, "create <id> [--look] <テキスト>",  "現在地/視線先に設置");
        helpLine(sender, "edit <id> text <テキスト>",         "テキスト内容を変更");
        helpLine(sender, "edit <id> scale <数値>",            "サイズ変更 (例: 2.0)");
        helpLine(sender, "edit <id> billboard <モード>",      "CENTER|FIXED|HORIZONTAL|VERTICAL");
        helpLine(sender, "edit <id> background <on|off|#RGB>","背景色の設定");
        helpLine(sender, "edit <id> shadow <on|off>",         "文字シャドウ切替");
        helpLine(sender, "edit <id> align <left|center|right>","テキスト整列");
        helpLine(sender, "list [ページ]",                     "設置済みテキスト一覧");
        helpLine(sender, "delete <id>",                       "テキストを削除");
        helpLine(sender, "move <id> <dx> <dy> <dz>",          "相対位置移動");
        helpLine(sender, "info <id>",                         "詳細情報を表示");
        helpLine(sender, "tp <id>",                           "設置場所へテレポート");
        sender.sendMessage(Component.text(
                "  ヒント: テキストに改行を入れるには \\n を使用してください。",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "  カラー: &aカラーコード, &#FF5500 RGB カラー どちらも使えます。",
                NamedTextColor.GRAY));
    }

    // -------------------------------------------------------------------------
    // タブ補完
    // -------------------------------------------------------------------------

    /**
     * MainCommand のタブ補完から呼ばれる。
     * args[0] == "text" が前提。
     */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        TextModule tm = plugin.getTextModule();

        // /hc text <?>
        if (args.length == 2) {
            return filterPrefix(args[1], List.of(
                    "create", "edit", "list", "delete", "move", "info", "tp", "help"));
        }

        // /hc text <sub> <?>
        if (args.length == 3) {
            return switch (args[1].toLowerCase()) {
                case "edit", "delete", "del", "remove",
                     "move", "info", "tp", "teleport" ->
                        tm != null ? filterPrefix(args[2], tm.getIds()) : List.of();
                default -> List.of();
            };
        }

        // /hc text edit <id> <?>
        if (args.length == 4 && args[1].equalsIgnoreCase("edit")) {
            return filterPrefix(args[3], List.of(
                    "text", "scale", "billboard", "background", "shadow", "align"));
        }

        // /hc text edit <id> <property> <?>
        if (args.length == 5 && args[1].equalsIgnoreCase("edit")) {
            return switch (args[3].toLowerCase()) {
                case "billboard"           -> filterPrefix(args[4],
                        List.of("CENTER", "FIXED", "HORIZONTAL", "VERTICAL"));
                case "background", "bg"    -> filterPrefix(args[4],
                        List.of("on", "off", "#000000", "#40000000"));
                case "shadow"              -> filterPrefix(args[4], List.of("on", "off"));
                case "align"               -> filterPrefix(args[4],
                        List.of("LEFT", "CENTER", "RIGHT"));
                default                    -> List.of();
            };
        }

        return List.of();
    }

    // -------------------------------------------------------------------------
    // 内部ユーティリティ
    // -------------------------------------------------------------------------

    /** #RRGGBB または #AARRGGBB 形式のカラー文字列を Bukkit Color にパース */
    private Color parseHexColor(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            if (h.length() == 6) {
                int rgb = Integer.parseUnsignedInt(h, 16);
                return Color.fromARGB(64,
                        (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }
            if (h.length() == 8) {
                long argb = Long.parseUnsignedLong(h, 16);
                return Color.fromARGB(
                        (int)(argb >> 24) & 0xFF,
                        (int)(argb >> 16) & 0xFF,
                        (int)(argb >>  8) & 0xFF,
                        (int)(argb)       & 0xFF);
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private boolean isValidId(String id) {
        return id.matches("[a-zA-Z0-9_\\-]+");
    }

    private List<String> filterPrefix(String prefix, List<String> options) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // メッセージビルダー
    // -------------------------------------------------------------------------

    private Component ok(String msg) {
        return Component.text("[TextModule] " + msg, NamedTextColor.GREEN);
    }

    private Component err(String msg) {
        return Component.text(msg, NamedTextColor.RED);
    }

    private Component idNotFound(String id) {
        return Component.text("ID '" + id + "' が見つかりません。/hc text list で確認してください。",
                NamedTextColor.RED);
    }

    private Component info(String label, String value) {
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private void helpLine(CommandSender sender, String usage, String desc) {
        sender.sendMessage(
                Component.text("  " + usage, NamedTextColor.YELLOW)
                .append(Component.text(" — " + desc, NamedTextColor.GRAY)));
    }
}
