package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BoardModule — manages per-player sidebar scoreboards (Overlay Board unit).
 *
 * Configuration keys (under board: in config.yml):
 *   board.enabled   — master toggle
 *   board.title     — scoreboard title (supports &amp; color codes)
 *   board.auto-show — show scoreboard automatically on join
 *
 * To add new display rows, edit {@link #buildLines(Player)} below and
 * reuse {@link #refreshBoard(Player)} to push updates.
 */
public class BoardModule implements Listener {

    private final HimatsudoCore plugin;
    private final boolean enabled;

    // Track which players currently have the board visible
    private final Map<UUID, Scoreboard> activeBoards = new HashMap<>();

    public BoardModule(HimatsudoCore plugin) {
        this.plugin  = plugin;
        this.enabled = plugin.getConfig().getBoolean("board.enabled", true);

        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[BoardModule] Scoreboard tracking active.");
        } else {
            plugin.getLogger().info("[BoardModule] Disabled via config.");
        }
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        if (plugin.getConfig().getBoolean("board.auto-show", true)) {
            // Slight delay to ensure the client is fully ready
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> showBoard(event.getPlayer()), 5L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeBoards.remove(event.getPlayer().getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Show (or refresh) the scoreboard for a player. */
    public void showBoard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();

        String rawTitle = plugin.getConfig().getString("board.title", "&6&lHimatsudoSMP");
        Component titleComponent = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(rawTitle);

        Objective objective = board.registerNewObjective(
                "himatsudo", "dummy", titleComponent, RenderType.INTEGER);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        applyLines(objective, buildLines(player));

        player.setScoreboard(board);
        activeBoards.put(player.getUniqueId(), board);

        // AfkModule に新しいスコアボードを通知し、現在 AFK 中のプレイヤーのチームを同期する
        AfkModule afkModule = plugin.getAfkModule();
        if (afkModule != null) afkModule.syncAfkPlayersToScoreboard(board);
    }

    /** Hide the scoreboard and restore the server default. */
    public void hideBoard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        activeBoards.remove(player.getUniqueId());
    }

    /** Toggle visibility for a player. Returns true if the board is now visible. */
    public boolean toggleBoard(Player player) {
        if (activeBoards.containsKey(player.getUniqueId())) {
            hideBoard(player);
            return false;
        } else {
            showBoard(player);
            return true;
        }
    }

    /** Refresh (re-render) an existing board for a player if they have one open. */
    public void refreshBoard(Player player) {
        if (activeBoards.containsKey(player.getUniqueId())) {
            showBoard(player);
        }
    }

    /** Refresh boards for all players who have the board open. */
    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshBoard(player);
        }
    }

    /**
     * AfkModule が頭上プレフィックスを全視点プレイヤーへ反映するために使用する。
     * 現在カスタムスコアボードを持つ全プレイヤーのスコアボードを返す。
     */
    public Collection<Scoreboard> getActiveScoreboards() {
        return Collections.unmodifiableCollection(activeBoards.values());
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideBoard(player);
        }
        activeBoards.clear();
    }

    // -------------------------------------------------------------------------
    // Display content — edit here to add/change rows
    // -------------------------------------------------------------------------

    /**
     * Returns an ordered list of display lines for the given player.
     * Each entry is a pair of (display-name, score-value).
     * Lower score values appear higher on the sidebar.
     *
     * To add a new row (e.g. player balance), simply add another entry here.
     */
    private Map<String, Integer> buildLines(Player player) {
        Map<String, Integer> lines = new HashMap<>();

        lines.put(" ", 10);
        lines.put("&7Player: &f" + player.getName(), 9);
        lines.put("&7Online: &a" + Bukkit.getOnlinePlayers().size(), 8);
        lines.put("&7World: &b" + player.getWorld().getName(), 7);
        lines.put("  ", 6);
        lines.put("&ehimatsudo.net", 5);

        return lines;
    }

    private void applyLines(Objective objective, Map<String, Integer> lines) {
        lines.forEach((rawLine, score) -> {
            // Translate & colour codes to § so the client renders colours in the
            // score-entry name (Score.customName(Component) is not available in
            // the paper-api 1.21.1-R0.1-SNAPSHOT we compile against).
            Component lineComponent = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(rawLine);
            String legacyLine = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(lineComponent);
            Score s = objective.getScore(legacyLine);
            s.setScore(score);
        });
    }
}
