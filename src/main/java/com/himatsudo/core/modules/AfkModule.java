package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import com.himatsudo.core.util.Fmt;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AfkModule — idle detection and AFK status management.
 *
 * Marks players as AFK after a configurable period of inactivity,
 * announces the state change, applies a nametag prefix via scoreboard teams,
 * and notifies TabModule to update the player's tab entry.
 */
public class AfkModule implements Listener {

    private static final String TEAM_NAME = "hc_afk";

    private final HimatsudoCore plugin;
    private final boolean enabled;

    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Set<UUID> afkPlayers = new HashSet<>();

    private BukkitTask checkTask;

    public AfkModule(HimatsudoCore plugin) {
        this.plugin  = plugin;
        this.enabled = plugin.getConfig().getBoolean("afk.enabled", true);

        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            startCheckTask();
            plugin.getLogger().info("[AfkModule] AFK tracking active.");
        } else {
            plugin.getLogger().info("[AfkModule] Disabled via config.");
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    private void startCheckTask() {
        long interval = plugin.getConfig().getLong("afk.check-interval-ticks", 100L);
        checkTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::runCheck, interval, interval);
    }

    public void shutdown() {
        if (checkTask != null && !checkTask.isCancelled()) checkTask.cancel();
        for (UUID uuid : new HashSet<>(afkPlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) removeAfkStatus(p, false);
        }
        cleanupTeams();
    }

    public void reload() {
        if (checkTask != null && !checkTask.isCancelled()) checkTask.cancel();
        startCheckTask();
    }

    // -------------------------------------------------------------------------
    // Activity detection
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;
        markActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        markActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p) markActivity(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        markActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        markActivity(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        removeAfkStatus(event.getPlayer(), false);
        lastActivity.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // AFK state logic
    // -------------------------------------------------------------------------

    private void runCheck() {
        long timeoutMs = plugin.getConfig().getLong("afk.timeout-seconds", 20L) * 1000L;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid   = player.getUniqueId();
            long idleMs = System.currentTimeMillis()
                    - lastActivity.getOrDefault(uuid, System.currentTimeMillis());
            if (idleMs >= timeoutMs && !afkPlayers.contains(uuid)) {
                applyAfkStatus(player);
            }
        }
    }

    private void markActivity(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (afkPlayers.contains(player.getUniqueId())) {
            removeAfkStatus(player, true);
        }
    }

    private void applyAfkStatus(Player player) {
        afkPlayers.add(player.getUniqueId());
        applyNameTag(player);

        String msg = resolve(plugin.getConfig()
                .getString("afk.message-go", "&7{player} &7が放置状態になりました。"), player);
        Bukkit.broadcast(Fmt.parse(msg));

        notifyTabModule(player);
    }

    private void removeAfkStatus(Player player, boolean broadcast) {
        afkPlayers.remove(player.getUniqueId());
        removeNameTag(player);

        if (broadcast) {
            String msg = resolve(plugin.getConfig()
                    .getString("afk.message-return", "&7{player} &7が放置から戻りました。"), player);
            Bukkit.broadcast(Fmt.parse(msg));
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> notifyTabModule(player));
    }

    private void notifyTabModule(Player player) {
        TabModule tm = plugin.getTabModule();
        if (tm != null) tm.refreshPlayer(player);
    }

    // -------------------------------------------------------------------------
    // Scoreboard team nametag management
    // -------------------------------------------------------------------------

    public void syncAfkPlayersToScoreboard(Scoreboard scoreboard) {
        ensureTeam(scoreboard);
        for (UUID uuid : afkPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) ensureTeam(scoreboard).addEntry(p.getName());
        }
    }

    private void applyNameTag(Player target) {
        applyTeamEntry(Bukkit.getScoreboardManager().getMainScoreboard(), target.getName(), true);
        BoardModule bm = plugin.getBoardModule();
        if (bm != null) {
            for (Scoreboard sb : bm.getActiveScoreboards()) applyTeamEntry(sb, target.getName(), true);
        }
    }

    private void removeNameTag(Player target) {
        applyTeamEntry(Bukkit.getScoreboardManager().getMainScoreboard(), target.getName(), false);
        BoardModule bm = plugin.getBoardModule();
        if (bm != null) {
            for (Scoreboard sb : bm.getActiveScoreboards()) applyTeamEntry(sb, target.getName(), false);
        }
    }

    private void applyTeamEntry(Scoreboard sb, String entry, boolean add) {
        Team team = ensureTeam(sb);
        if (add) team.addEntry(entry);
        else     team.removeEntry(entry);
    }

    private Team ensureTeam(Scoreboard sb) {
        Team team = sb.getTeam(TEAM_NAME);
        if (team == null) team = sb.registerNewTeam(TEAM_NAME);
        team.prefix(Fmt.parse(plugin.getConfig().getString("afk.display-prefix", "&7[AFK] ")));
        return team;
    }

    private void cleanupTeams() {
        cleanTeam(Bukkit.getScoreboardManager().getMainScoreboard());
        BoardModule bm = plugin.getBoardModule();
        if (bm != null) {
            for (Scoreboard sb : bm.getActiveScoreboards()) cleanTeam(sb);
        }
    }

    private void cleanTeam(Scoreboard sb) {
        Team t = sb.getTeam(TEAM_NAME);
        if (t != null) t.unregister();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean isAfk(Player player) {
        return afkPlayers.contains(player.getUniqueId());
    }

    public Set<UUID> getAfkPlayers() {
        return Collections.unmodifiableSet(afkPlayers);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolve(String template, Player player) {
        ChatModule cm = plugin.getChatModule();
        String playerDisplay = cm != null
                ? cm.getRankPrefix(player) + " &f" + player.getName()
                : player.getName();
        return template.replace("{player}", playerDisplay);
    }
}
