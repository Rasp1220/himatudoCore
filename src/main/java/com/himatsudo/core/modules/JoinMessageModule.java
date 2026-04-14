package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * JoinMessageModule — 入退室メッセージのカスタマイズ (⑦ Join Message unit).
 *
 * Bukkit デフォルトの "xxx joined the game" を完全に置き換えます。
 * 初参加と通常参加でメッセージを切り替えられます。
 *
 * 設定キー (config.yml の join-message: セクション):
 *   join-message.enabled                  — 機能 ON/OFF
 *   join-message.first-join.enabled        — 初参加メッセージの使用 ON/OFF
 *   join-message.first-join.message        — 初参加時メッセージ ({player} 置換可)
 *   join-message.join.message              — 通常参加時メッセージ ({player} 置換可)
 *   join-message.quit.message              — 退出時メッセージ ({player} 置換可)
 *
 * &カラーコード・&#RRGGBB HEX カラーどちらも使用可。
 * 空文字列 "" を指定するとメッセージを完全非表示にできます。
 */
public class JoinMessageModule implements Listener {

    private final HimatsudoCore plugin;
    private final boolean enabled;

    public JoinMessageModule(HimatsudoCore plugin) {
        this.plugin  = plugin;
        this.enabled = plugin.getConfig().getBoolean("join-message.enabled", true);

        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[JoinMessageModule] Custom join messages active.");
        } else {
            plugin.getLogger().info("[JoinMessageModule] Disabled via config.");
        }
    }

    // -------------------------------------------------------------------------
    // イベントハンドラ
    // -------------------------------------------------------------------------

    /**
     * 参加メッセージを差し替える。
     * HIGH 優先度で登録することで、他の処理より先に joinMessage を上書きします。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;

        // Player#hasPlayedBefore() は「過去に一度でも参加したか」を返す
        // 今回のログイン前に参加履歴がなければ初参加
        boolean isFirstJoin = !event.getPlayer().hasPlayedBefore();

        String rawMessage;
        if (isFirstJoin
                && plugin.getConfig().getBoolean("join-message.first-join.enabled", true)) {
            rawMessage = plugin.getConfig().getString(
                    "join-message.first-join.message",
                    "&6&l★ &e{player} &6&lが初めてHimatsudoに参加しました！ &6&l★");
        } else {
            rawMessage = plugin.getConfig().getString(
                    "join-message.join.message",
                    "&7&l» &r&f{player} &7がサーバーに参加しました。");
        }

        // 空文字 = メッセージ非表示
        if (rawMessage.isEmpty()) {
            event.joinMessage(null);
            return;
        }

        event.joinMessage(parse(rawMessage.replace("{player}", event.getPlayer().getName())));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        if (!enabled) return;

        String rawMessage = plugin.getConfig().getString(
                "join-message.quit.message",
                "&7&l« &r&f{player} &7がサーバーから退出しました。");

        if (rawMessage.isEmpty()) {
            event.quitMessage(null);
            return;
        }

        event.quitMessage(parse(rawMessage.replace("{player}", event.getPlayer().getName())));
    }

    // -------------------------------------------------------------------------
    // ユーティリティ
    // -------------------------------------------------------------------------

    private Component parse(String raw) {
        // &#RRGGBB → &x&R&R&G&G&B&B に変換してから legacy serializer に渡す
        raw = raw.replaceAll(
                "&#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])",
                "&x&$1&$2&$3&$4&$5&$6");
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    public void shutdown() {
        // Bukkit が plugin disable 時に自動でリスナーを解除するため不要
    }
}
