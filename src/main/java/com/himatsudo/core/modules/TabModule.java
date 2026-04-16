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
import org.bukkit.scheduler.BukkitTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * TabModule — タブリスト表示管理 (Tab List unit).
 *
 * 機能:
 *   - ヘッダーにサーバー名・オンライン人数をリアルタイム表示
 *   - フッターに現在のPing・JST時刻を1秒ごとに更新
 *   - 各エントリに [ランク] 名前 の形式で権限を表示
 *   - アクティブプレイヤー: 緑色 / AFK プレイヤー: 赤色 + [AFK] サフィックス
 *
 * 設定キー (config.yml の tab: セクション):
 *   tab.enabled — 機能 ON/OFF
 *   tab.header  — ヘッダー文字列 ({count} = オンライン人数 / \n で改行)
 *   tab.footer  — フッター下段に表示するサイト名等の静的テキスト
 *
 * AfkModule が AFK 状態を変化させるたびに refreshPlayer(Player) を呼び出すこと。
 */
public class TabModule implements Listener {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final HimatsudoCore plugin;
    private final boolean enabled;
    private BukkitTask refreshTask;

    public TabModule(HimatsudoCore plugin) {
        this.plugin  = plugin;
        this.enabled = plugin.getConfig().getBoolean("tab.enabled", true);

        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            // 1秒ごとに全プレイヤーのヘッダー/フッター (Ping・時刻) を更新
            refreshTask = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, this::sendHeaderToAll, 20L, 20L);
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
     * 指定プレイヤーのタブリスト表示名とヘッダー/フッターを更新する。
     * AFK 状態が変化したとき AfkModule から呼び出される。
     * 必ずメインスレッドから呼ぶこと。
     */
    public void refreshPlayer(Player player) {
        if (!enabled) return;
        player.playerListName(buildEntry(player));
        sendHeader(player);
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
        if (refreshTask != null && !refreshTask.isCancelled()) refreshTask.cancel();
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

    /**
     * プレイヤー個別にヘッダー/フッターを送信する。
     * フッターには各プレイヤーの現在Pingとサーバー時刻(JST)を含む。
     */
    private void sendHeader(Player player) {
        int    count = Bukkit.getOnlinePlayers().size();
        int    ping  = player.getPing();
        String pingColor = ping < 50 ? "&a" : ping < 100 ? "&e" : ping < 150 ? "&6" : "&c";
        String time  = ZonedDateTime.now(JST).format(TIME_FMT);

        String rawHeader = plugin.getConfig().getString(
                "tab.header",
                "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━&r\n  &6&lHimatsudoSMP  \n&7オンライン&8: &a{count}&7 人\n&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━&r");

        String staticFooter = plugin.getConfig().getString("tab.footer", "&7himatsudo.net");

        Component header = parse(rawHeader.replace("{count}", String.valueOf(count)));

        // フッターはPing・時刻を含む動的コンテンツ + 静的テキスト
        String footerRaw = "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━&r\n"
                + pingColor + "Ping&8: " + pingColor + ping + "ms  "
                + "&8|  &e⏰ &7" + time + " &8(JST)\n"
                + staticFooter + "\n"
                + "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━&r";

        Component footer = parse(footerRaw);
        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    private Component parse(String raw) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }
}
