package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import com.himatsudo.core.util.Fmt;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * AnnounceModule — periodically broadcasts a rotating list of messages
 * to all online players (Info Cycle unit).
 *
 * Configuration keys (under announce: in config.yml):
 *   announce.enabled        — master toggle
 *   announce.interval-ticks — broadcast interval in server ticks (default 6000 = 5 min)
 *   announce.messages       — list of messages to cycle through
 *
 * To add messages, simply append entries to announce.messages in config.yml.
 * No code changes are needed.
 */
public class AnnounceModule {

    private final HimatsudoCore plugin;
    private BukkitTask task;
    private int messageIndex = 0;

    public AnnounceModule(HimatsudoCore plugin) {
        this.plugin = plugin;

        if (!plugin.getConfig().getBoolean("announce.enabled", true)) {
            plugin.getLogger().info("[AnnounceModule] Disabled via config.");
            return;
        }

        start();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    private void start() {
        long intervalTicks = plugin.getConfig().getLong("announce.interval-ticks", 6000L);

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::broadcast,
                intervalTicks, intervalTicks);

        plugin.getLogger().info(
                "[AnnounceModule] Started — interval: " + intervalTicks + " ticks.");
    }

    public void shutdown() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    // -------------------------------------------------------------------------
    // Broadcast logic
    // -------------------------------------------------------------------------

    private void broadcast() {
        List<String> messages = plugin.getConfig().getStringList("announce.messages");
        if (messages.isEmpty()) return;

        // Cycle through messages in order
        String raw = messages.get(messageIndex % messages.size());
        messageIndex = (messageIndex + 1) % messages.size();

        plugin.getServer().broadcast(Fmt.parse(raw));
    }

    // -------------------------------------------------------------------------
    // Public API — allows other code to trigger an immediate broadcast
    // -------------------------------------------------------------------------

    public void broadcastNow() {
        broadcast();
    }

    /** Reloads configuration and restarts the timer. */
    public void reload() {
        shutdown();
        messageIndex = 0;
        start();
    }
}
