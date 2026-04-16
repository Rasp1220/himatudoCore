package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Map;

/**
 * SecurityAlertModule — 不正コマンド試行の Discord アラート (⑩ Security Alert unit).
 *
 * 機能:
 *   - 権限のないプレイヤーが管理者コマンドを実行しようとした際に検知
 *   - コマンド種別ごとに異なるアラートタイトル・色で Discord Embed を送信
 *   - OP 保持者・himatsudo.admin 権限保持者は対象外
 *
 * 設定キー (config.yml の security-alert: セクション):
 *   security-alert.enabled             — 機能 ON/OFF
 *   security-alert.monitored-commands  — 監視対象コマンドリスト (先頭一致)
 *
 * DiscordModule の enabled と webhook-url が設定されている必要があります。
 */
public class SecurityAlertModule implements Listener {

    // -------------------------------------------------------------------------
    // コマンド別アラート定義
    // -------------------------------------------------------------------------

    private record CommandAlert(String title, int color) {}

    /**
     * 監視コマンドに対応するアラート設定。
     * color は Discord Embed の枠線色 (24bit RGB)。
     *
     * 🔴 赤   (0xFF0000) — 最高危険度 (サーバー権限・停止)
     * 🟠 橙   (0xFF6000) — 高危険度   (BAN 操作・ゲームモード)
     * 🟡 黄橙 (0xFF9000) — 中危険度   (ゲームルール・ホワイトリスト)
     */
    private static final Map<String, CommandAlert> COMMAND_ALERTS = Map.ofEntries(
        Map.entry("op",        new CommandAlert("🚨 OP権限付与の試み",           0xFF0000)),
        Map.entry("deop",      new CommandAlert("🚨 OP権限剥奪の試み",           0xFF0000)),
        Map.entry("stop",      new CommandAlert("🔴 サーバー停止コマンドの試み", 0xFF0000)),
        Map.entry("restart",   new CommandAlert("🔴 サーバー再起動の試み",       0xFF0000)),
        Map.entry("ban",       new CommandAlert("🔨 プレイヤー BANの試み",       0xFF6000)),
        Map.entry("ban-ip",    new CommandAlert("🔨 IP BAN の試み",             0xFF6000)),
        Map.entry("pardon",    new CommandAlert("🔓 BAN 解除の試み",            0xFF6000)),
        Map.entry("pardon-ip", new CommandAlert("🔓 IP BAN 解除の試み",        0xFF6000)),
        Map.entry("gamemode",  new CommandAlert("⚠️ ゲームモード変更の試み",    0xFF8000)),
        Map.entry("gamerule",  new CommandAlert("⚠️ ゲームルール変更の試み",    0xFF9000)),
        Map.entry("whitelist", new CommandAlert("⚠️ ホワイトリスト操作の試み",  0xFF9000))
    );

    private final HimatsudoCore plugin;
    private final boolean enabled;

    public SecurityAlertModule(HimatsudoCore plugin) {
        this.plugin  = plugin;
        this.enabled = plugin.getConfig().getBoolean("security-alert.enabled", true);

        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[SecurityAlertModule] Unauthorized command monitoring active.");
        } else {
            plugin.getLogger().info("[SecurityAlertModule] Disabled via config.");
        }
    }

    // -------------------------------------------------------------------------
    // イベントハンドラ
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        // OP または管理者権限を持つプレイヤーはアラート対象外
        if (player.isOp() || player.hasPermission("himatsudo.admin")) return;

        // 先頭の "/" を除去して小文字化
        String raw     = event.getMessage();               // 例: "/op Steve"
        String command = raw.substring(1).toLowerCase();  // 例: "op steve"

        // 設定された監視コマンドリストと照合
        List<String> monitored = plugin.getConfig()
                .getStringList("security-alert.monitored-commands");

        String matchedCmd = null;
        for (String cmd : monitored) {
            if (command.equals(cmd) || command.startsWith(cmd + " ")) {
                matchedCmd = cmd;
                break;
            }
        }
        if (matchedCmd == null) return;

        sendAlert(player, raw, matchedCmd);
    }

    // -------------------------------------------------------------------------
    // Discord アラート送信
    // -------------------------------------------------------------------------

    private void sendAlert(Player player, String rawCommand, String matchedCmd) {
        DiscordModule discord = plugin.getDiscordModule();
        if (discord == null || !discord.isEnabled()) return;

        // コマンド別タイトル・色を取得 (未登録コマンドはデフォルト)
        CommandAlert alert = COMMAND_ALERTS.getOrDefault(
                matchedCmd,
                new CommandAlert("⚠️ 不審なコマンドの試み", 0xFFA500));

        String rankDisplay  = getRankDisplay(player);
        String locationText = formatLocation(player);

        List<DiscordModule.EmbedField> fields = List.of(
            new DiscordModule.EmbedField("👤 プレイヤー名", player.getName(),            true),
            new DiscordModule.EmbedField("🎖️ ランク",      rankDisplay,                 true),
            new DiscordModule.EmbedField("💻 実行コマンド", "`" + rawCommand + "`",      false),
            new DiscordModule.EmbedField("🌍 ワールド",     player.getWorld().getName(), true),
            new DiscordModule.EmbedField("📍 座標",         locationText,                true)
        );

        discord.sendEmbed(
                alert.title(),
                "権限のないプレイヤーが管理者コマンドを実行しようとしました。",
                alert.color(),
                fields);

        plugin.getLogger().warning(String.format(
                "[SecurityAlert] %s attempted: %s",
                player.getName(), rawCommand));
    }

    // -------------------------------------------------------------------------
    // ユーティリティ
    // -------------------------------------------------------------------------

    private String getRankDisplay(Player player) {
        ChatModule cm = plugin.getChatModule();
        if (cm != null) return cm.getRankPrefix(player);
        return player.isOp() ? "OP" : "一般";
    }

    private String formatLocation(Player player) {
        return String.format("X:%d Y:%d Z:%d",
                player.getLocation().getBlockX(),
                player.getLocation().getBlockY(),
                player.getLocation().getBlockZ());
    }

    public void shutdown() {
        // Bukkit が plugin disable 時に自動でリスナーを解除する
    }
}
