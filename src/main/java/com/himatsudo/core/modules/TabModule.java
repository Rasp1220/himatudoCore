package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * TabModule — タブリスト表示管理 (Tab List unit).
 *
 * 機能:
 *   - ヘッダーにサーバー名・オンライン人数をリアルタイム表示
 *   - 各エントリに [ランク] 名前 の形式で権限を表示
 *   - アクティブプレイヤー: 緑色 / AFK プレイヤー: 赤色 + [AFK] サフィックス
 *
 * 設定キー (config.yml の tab: セクション):
 *   tab.enabled — 機能 ON/OFF
 *   tab.header  — ヘッダー文字列 ({count} = オンライン人数 / \n で改行)
 *   tab.footer  — フッター文字列
 *
 * AfkModule が AFK 状態を変化させるたびに refreshPlayer(Player) を呼び出すこと。
 */
public class TabModule implements Listener {

    private final HimatsudoCore plugin;
    private final boolean enabled;

    public TabModule(HimatsudoCore plugin) {
        this.plugin  = plugin;
        this.enabled = plugin.getConfig().getBoolean("tab.enabled", true);

        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[TabModule] Tab list management active.");
        } else {
            plugin.getLogger().info("[TabModule] Disabled via config.");
        }
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        // 少し遅らせてクライアントが準備完了してから送信
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            refreshPlayer(event.getPlayer());
            sendHeaderToAll();
        }, 5L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled) return;
        // 退出後にカウントが確定してからヘッダーを更新
        Bukkit.getScheduler().runTaskLater(plugin, this::sendHeaderToAll, 1L);
    }

    // -------------------------------------------------------------------------
    // Public API — AfkModule / HimatsudoCore から呼び出す
    // -------------------------------------------------------------------------

    /**
     * 指定プレイヤーのタブリスト表示名を更新する。
     * AFK 状態が変化したとき AfkModule から呼び出される。
     * 必ずメインスレッドから呼ぶこと。
     */
    public void refreshPlayer(Player player) {
        if (!enabled) return;
        player.playerListName(buildEntry(player));
    }

    /** 全プレイヤーのタブ名とヘッダーを再描画する (リロード時・起動時に使用)。 */
    public void refreshAll() {
        if (!enabled) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playerListName(buildEntry(p));
        }
        sendHeaderToAll();
    }

    public void shutdown() {
        // デフォルト表示に戻す
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playerListName(null);
        }
    }

    // -------------------------------------------------------------------------
    // 内部ヘルパー
    // -------------------------------------------------------------------------

    /**
     * タブリストの 1 エントリを構築する。
     *
     * アクティブ: &a[ランク]&r &a名前
     * AFK      : &c[ランク]&r &c名前 &7[AFK]
     *
     * ChatModule が無効な場合は "&7[一般]&r" をフォールバックとして使用する。
     */
    private Component buildEntry(Player player) {
        AfkModule  afkModule  = plugin.getAfkModule();
        ChatModule chatModule = plugin.getChatModule();

        boolean isAfk      = afkModule  != null && afkModule.isAfk(player);
        String  rankPrefix = chatModule != null
                ? chatModule.getRankPrefix(player)
                : "&7[一般]&r";

        if (isAfk) {
            return parse(rankPrefix + " &c" + player.getName() + " &7[AFK]");
        } else {
            return parse(rankPrefix + " &a" + player.getName());
        }
    }

    private void sendHeaderToAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            sendHeader(p);
        }
    }

    private void sendHeader(Player player) {
        int count = Bukkit.getOnlinePlayers().size();

        String rawHeader = plugin.getConfig().getString(
                "tab.header",
                "&6&lHimatsudoSMP\n&7オンライン: &a{count}&7 人");
        String rawFooter = plugin.getConfig().getString(
                "tab.footer",
                "&7himatsudo.net");

        Component header = parse(rawHeader.replace("{count}", String.valueOf(count)));
        Component footer = parse(rawFooter);
        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    private Component parse(String raw) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }
}
