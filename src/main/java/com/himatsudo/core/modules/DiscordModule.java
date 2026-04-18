package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

/**
 * DiscordModule — bridges in-game events to a Discord webhook.
 *
 * Configuration keys (under discord: in config.yml):
 *   discord.enabled       — master toggle
 *   discord.webhook-url   — Discord webhook URL
 *   discord.notify-join   — notify on player join
 *   discord.notify-quit   — notify on player quit
 *   discord.notify-death  — notify on player death
 *
 * To add a new notification type, add an @EventHandler method below
 * and call sendMessage() with the desired payload.
 */
public class DiscordModule implements Listener {

    private final HimatsudoCore plugin;
    private final boolean enabled;
    private final String webhookUrl;

    public DiscordModule(HimatsudoCore plugin) {
        this.plugin = plugin;

        this.enabled    = plugin.getConfig().getBoolean("discord.enabled", false);
        this.webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");

        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[DiscordModule] Webhook notifications active.");
        } else {
            plugin.getLogger().info("[DiscordModule] Disabled via config.");
        }
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled || !plugin.getConfig().getBoolean("discord.notify-join", true)) return;
        sendMessage(":green_circle: **" + event.getPlayer().getName() + "** がサーバーに参加しました。");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled || !plugin.getConfig().getBoolean("discord.notify-quit", true)) return;
        sendMessage(":red_circle: **" + event.getPlayer().getName() + "** がサーバーから退出しました。");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!enabled || !plugin.getConfig().getBoolean("discord.notify-death", false)) return;
        Component deathComponent = event.deathMessage();
        String deathMsg = deathComponent != null
                ? LegacyComponentSerializer.legacySection().serialize(deathComponent)
                : event.getEntity().getName() + " が死亡しました。";
        sendMessage(":skull: " + deathMsg);
    }

    // -------------------------------------------------------------------------
    // Webhook helpers
    // -------------------------------------------------------------------------

    /** Discord Embed の 1 フィールドを表すレコード。SecurityAlertModule から利用する。 */
    public record EmbedField(String name, String value, boolean inline) {}

    /** 機能が有効かどうかを返す (SecurityAlertModule などから参照)。 */
    public boolean isEnabled() { return enabled && !webhookUrl.isEmpty(); }

    /**
     * プレーンテキストメッセージを Discord webhook へ送信する。
     * 非同期実行のためメインスレッドをブロックしない。
     */
    public void sendMessage(String message) {
        if (!isEnabled()) return;
        sendRawPayload("{\"content\":\"" + escapeJson(message) + "\"}");
    }

    /**
     * Discord Embed を送信する。SecurityAlertModule などのアラート用途で使用。
     *
     * @param title       Embed タイトル
     * @param description Embed 説明文
     * @param color       Embed 枠線色 (24bit RGB 整数、例: 0xFF0000 = 赤)
     * @param fields      表示するフィールドのリスト
     */
    public void sendEmbed(String title, String description, int color,
                          List<EmbedField> fields) {
        if (!isEnabled()) return;

        StringBuilder fb = new StringBuilder("[");
        for (int i = 0; i < fields.size(); i++) {
            EmbedField f = fields.get(i);
            if (i > 0) fb.append(",");
            fb.append("{\"name\":\"").append(escapeJson(f.name()))
              .append("\",\"value\":\"").append(escapeJson(f.value()))
              .append("\",\"inline\":").append(f.inline()).append("}");
        }
        fb.append("]");

        String json = "{\"embeds\":[{"
                + "\"title\":\""       + escapeJson(title)       + "\","
                + "\"description\":\"" + escapeJson(description) + "\","
                + "\"color\":"         + color                   + ","
                + "\"fields\":"        + fb                      + ","
                + "\"timestamp\":\""   + Instant.now()           + "\""
                + "}]}";

        sendRawPayload(json);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void sendRawPayload(String json) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    plugin.getLogger().warning("[DiscordModule] Webhook returned HTTP " + code);
                }
                conn.disconnect();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "[DiscordModule] Failed to send webhook payload.", e);
            }
        });
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }

    public void shutdown() {
        // Listener unregistration is handled automatically by Bukkit on plugin disable.
    }
}
