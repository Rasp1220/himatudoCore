package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

/**
 * DiscordModule — bridges in-game events to a Discord webhook.
 *
 * Configuration keys (under discord: in config.yml):
 *   discord.enabled        — master toggle
 *   discord.webhook-url    — Discord webhook URL
 *   discord.notify-start   — notify on server start
 *   discord.notify-stop    — notify on server stop
 *   discord.notify-death   — notify on player death
 */
public class DiscordModule implements Listener {

    private final HimatsudoCore plugin;
    private final boolean enabled;
    private final String webhookUrl;

    public DiscordModule(HimatsudoCore plugin) {
        this.plugin     = plugin;
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
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("discord.notify-death", false)) return;
        Component deathComponent = event.deathMessage();
        String deathMsg = deathComponent != null
                ? LegacyComponentSerializer.legacySection().serialize(deathComponent)
                : event.getEntity().getName() + " が死亡しました。";
        sendMessage(":skull: " + deathMsg);
    }

    // -------------------------------------------------------------------------
    // Server lifecycle notifications
    // -------------------------------------------------------------------------

    /** onEnable() 後に呼ぶ。スケジューラーが使えるので非同期送信。 */
    public void notifyServerStart() {
        if (!isEnabled() || !plugin.getConfig().getBoolean("discord.notify-start", true)) return;
        sendMessage(":white_check_mark: **サーバーが起動しました。**");
    }

    /** onDisable() 中に呼ぶ。スケジューラーが停止しているため同期送信。 */
    public void notifyServerStop() {
        if (!isEnabled() || !plugin.getConfig().getBoolean("discord.notify-stop", true)) return;
        sendRawPayloadSync("{\"content\":\":octagonal_sign: **サーバーが停止します。**\"}");
    }

    public void shutdown() {
        notifyServerStop();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Discord Embed の 1 フィールドを表すレコード。SecurityAlertModule から利用する。 */
    public record EmbedField(String name, String value, boolean inline) {}

    /** 機能が有効かどうかを返す (SecurityAlertModule などから参照)。 */
    public boolean isEnabled() { return enabled && !webhookUrl.isEmpty(); }

    /** プレーンテキストメッセージを Discord webhook へ非同期送信する。 */
    public void sendMessage(String message) {
        if (!isEnabled()) return;
        sendRawPayload("{\"content\":\"" + escapeJson(message) + "\"}");
    }

    /** Discord Embed を非同期送信する。SecurityAlertModule のアラート用途で使用。 */
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
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> postJson(json));
    }

    private void sendRawPayloadSync(String json) {
        postJson(json);
    }

    private void postJson(String json) {
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
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }
}
