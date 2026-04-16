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
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
    // Webhook helper
    // -------------------------------------------------------------------------

    /**
     * Sends a plain-text message to the configured Discord webhook.
     * Runs asynchronously to avoid blocking the main server thread.
     *
     * @param message the message to send
     */
    public void sendMessage(String message) {
        if (!enabled || webhookUrl.isEmpty()) return;

        String jsonPayload = "{\"content\":\"" + escapeJson(message) + "\"}";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(webhookUrl).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    plugin.getLogger().warning(
                            "[DiscordModule] Webhook returned HTTP " + responseCode);
                }
                connection.disconnect();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "[DiscordModule] Failed to send webhook message.", e);
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
