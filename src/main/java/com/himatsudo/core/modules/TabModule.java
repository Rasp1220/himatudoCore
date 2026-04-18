package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import com.himatsudo.core.util.Fmt;
import net.kyori.adventure.text.Component;
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
 * TabModule — per-player tab-list header/footer and entry names.
 *
 * - Header: server name + online count (from config)
 * - Footer: per-player ping + JST time (HH:mm), updated every second
 * - Entry:  [Rank] name  — green when active, red + [AFK] when AFK
 */
public class TabModule implements Listener {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final HimatsudoCore plugin;
    private final boolean enabled;
    private BukkitTask refreshTask;

    public TabModule(HimatsudoCore plugin) {
        this.plugin  = plugin;
        this.enabled = plugin.getConfig().getBoolean("tab.enabled", true);

        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            refreshTask = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, this::sendHeaderToAll, 20L, 20L);
            plugin.getLogger().info("[TabModule] Tab list management active.");
        } else {
            plugin.getLogger().info("[TabModule] Disabled via config.");
        }
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            refreshPlayer(event.getPlayer());
            sendHeaderToAll();
        }, 5L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled) return;
        Bukkit.getScheduler().runTaskLater(plugin, this::sendHeaderToAll, 1L);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Updates the tab entry and header/footer for a single player. */
    public void refreshPlayer(Player player) {
        if (!enabled) return;
        player.playerListName(buildEntry(player));
        sendHeader(player);
    }

    public void refreshAll() {
        if (!enabled) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playerListName(buildEntry(p));
        }
        sendHeaderToAll();
    }

    public void shutdown() {
        if (refreshTask != null && !refreshTask.isCancelled()) refreshTask.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) p.playerListName(null);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private Component buildEntry(Player player) {
        AfkModule  afk  = plugin.getAfkModule();
        ChatModule chat = plugin.getChatModule();

        boolean isAfk      = afk  != null && afk.isAfk(player);
        String  rankPrefix = chat != null ? chat.getRankPrefix(player) : "&7[一般]&r";

        return isAfk
                ? Fmt.parse(rankPrefix + " &c" + player.getName() + " &7[AFK]")
                : Fmt.parse(rankPrefix + " &a" + player.getName());
    }

    private void sendHeaderToAll() {
        for (Player p : Bukkit.getOnlinePlayers()) sendHeader(p);
    }

    private void sendHeader(Player player) {
        int    count     = Bukkit.getOnlinePlayers().size();
        int    ping      = player.getPing();
        String pingColor = ping < 50 ? "&a" : ping < 100 ? "&e" : ping < 150 ? "&6" : "&c";
        String time      = ZonedDateTime.now(JST).format(TIME_FMT);

        String rawHeader = plugin.getConfig().getString(
                "tab.header",
                "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━&r\n  &6&lHimatsudoSMP  \n&7オンライン&8: &a{count}&7 人\n&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━&r");
        String staticFooter = plugin.getConfig().getString("tab.footer", "&7himatsudo.net");

        Component header = Fmt.parse(rawHeader.replace("{count}", String.valueOf(count)));

        String footerRaw = "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━&r\n"
                + pingColor + "Ping&8: " + pingColor + ping + "ms  "
                + "&8|  &e⏰ &7" + time + " &8(JST)\n"
                + staticFooter + "\n"
                + "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━&r";

        player.sendPlayerListHeaderAndFooter(header, Fmt.parse(footerRaw));
    }
}
