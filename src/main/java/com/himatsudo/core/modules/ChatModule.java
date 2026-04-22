package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ChatModule — チャットフォーマット整形 (⑧ Chat Format unit).
 *
 * デフォルトチャットを [ランク] プレイヤー名: メッセージ 形式に変換します。
 * ランクはパーミッション単位で複数定義でき、上から優先順に評価されます。
 *
 * 設定キー (config.yml の chat: セクション):
 *   chat.enabled   — 機能 ON/OFF
 *   chat.format    — フォーマット文字列
 *                    プレースホルダー: {prefix} {player} {message}
 *   chat.ranks     — ランク定義リスト (name / permission / prefix-color)
 *
 * DiscordModule の AsyncPlayerChatEvent と競合しません。
 * 両方が有効な場合、チャット整形 と Discord 通知は独立して動作します。
 */
public class ChatModule implements Listener {

    private final HimatsudoCore plugin;
    private final boolean enabled;

    /**
     * ランク定義。
     * permission が空文字の場合は「デフォルトランク」として最後に適用されます。
     */
    private record Rank(String name, String permission, String prefixColor) {}

    private final List<Rank> ranks = new ArrayList<>();

    public ChatModule(HimatsudoCore plugin) {
        this.plugin  = plugin;
        this.enabled = plugin.getConfig().getBoolean("chat.enabled", true);

        if (enabled) {
            loadRanks();
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[ChatModule] Chat formatter active. Ranks: " + ranks.size());
        } else {
            plugin.getLogger().info("[ChatModule] Disabled via config.");
        }
    }

    // -------------------------------------------------------------------------
    // イベントハンドラ
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Rank   rank   = resolveRank(player);

        String format = plugin.getConfig().getString(
                "chat.format", "&8[{prefix}&8] &f{player}&7: &f{message}");

        // {prefix} と {player} を置換 ({message} は Adventure API で処理)
        String resolved = format
                .replace("{prefix}", rank.prefixColor() + rank.name())
                .replace("{player}", player.getName());

        // {message} プレースホルダーの位置で前後に分割し、
        // 実際のチャットコンポーネントを挟み込む
        int msgIdx = resolved.indexOf("{message}");
        if (msgIdx >= 0) {
            Component prefix  = parse(resolved.substring(0, msgIdx));
            Component suffix  = parse(resolved.substring(msgIdx + "{message}".length()));

            event.renderer((source, displayName, message, viewer) ->
                    prefix.append(message).append(suffix));
        } else {
            // {message} 未定義の場合はフォーマット文字列の後にメッセージを追加
            Component header = parse(resolved);
            event.renderer((source, displayName, message, viewer) ->
                    header.append(message));
        }
    }

    // -------------------------------------------------------------------------
    // ランク解決
    // -------------------------------------------------------------------------

    /**
     * プレイヤーに一致するランクを上から順に評価して返す。
     * どのランクにもマッチしない場合はデフォルトランクを返す。
     */
    private Rank resolveRank(Player player) {
        // OP は権限プラグインの設定に関わらず常に先頭ランク(管理者)を付与
        if (player.isOp()) {
            return ranks.stream()
                    .filter(r -> !r.permission().isEmpty())
                    .findFirst()
                    .orElse(new Rank("管理者", "himatsudo.admin", "&c"));
        }
        for (Rank rank : ranks) {
            if (rank.permission().isEmpty()) continue;
            if (player.hasPermission(rank.permission())) return rank;
        }
        return ranks.stream()
                .filter(r -> r.permission().isEmpty())
                .findFirst()
                .orElse(new Rank("一般", "", "&7"));
    }

    // -------------------------------------------------------------------------
    // ライフサイクル
    // -------------------------------------------------------------------------

    /**
     * 指定プレイヤーのランクプレフィックスを "&c[ランク名]&r" 形式で返す。
     * AfkModule・TabModule などから利用する。
     */
    public String getRankPrefix(Player player) {
        Rank rank = resolveRank(player);
        return rank.prefixColor() + "[" + rank.name() + "]&r";
    }

    public void reload() {
        loadRanks();
        plugin.getLogger().info("[ChatModule] Reloaded. Ranks: " + ranks.size());
    }

    public void shutdown() {
        // Bukkit が plugin disable 時に自動でリスナーを解除するため不要
    }

    // -------------------------------------------------------------------------
    // 内部ヘルパー
    // -------------------------------------------------------------------------

    private void loadRanks() {
        ranks.clear();

        List<Map<?, ?>> rankList = plugin.getConfig().getMapList("chat.ranks");
        for (Map<?, ?> entry : rankList) {
            // Map<?,?> wildcards prevent passing a String to getOrDefault's default-value
            // parameter in Java 21 — use get() + null check instead.
            String name        = mapGet(entry, "name",         "一般");
            String permission  = mapGet(entry, "permission",   "");
            String prefixColor = mapGet(entry, "prefix-color", "&7");
            ranks.add(new Rank(name, permission, prefixColor));
        }

        // ranks が空の場合のフォールバック (設定ミス対策)
        if (ranks.isEmpty()) {
            ranks.add(new Rank("一般", "", "&7"));
        }
    }

    private Component parse(String raw) {
        raw = raw.replaceAll(
                "&#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])",
                "&x&$1&$2&$3&$4&$5&$6");
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    private static String mapGet(Map<?, ?> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : defaultValue;
    }
}
