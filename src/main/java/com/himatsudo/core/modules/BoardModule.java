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
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scheduler.BukkitTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BoardModule — per-player sidebar scoreboard.
 *
 * Layout (top → bottom by score value):
 *   ━━━━━━━━━━━━  (13)
 *   Player name   (12)
 *   Rank          (11)
 *                 (10) spacer
 *   Online count  ( 9) dynamic
 *   World name    ( 8) dynamic
 *   Ping          ( 7) dynamic
 *                 ( 6) spacer
 *   himatsudo.net ( 5)
 *                 ( 4) spacer
 *   HH:mm JST     ( 1) dynamic
 */
public class BoardModule implements Listener {

    // Score constants — higher value = higher row in sidebar
    private static final int S_SEP    = 13;
    private static final int S_PLAYER = 12;
    private static final int S_RANK   = 11;
    private static final int S_SP1    = 10;
    private static final int S_ONLINE =  9;
    private static final int S_WORLD  =  8;
    private static final int S_PING   =  7;
    private static final int S_SP2    =  6;
    private static final int S_SITE   =  5;
    private static final int S_SP3    =  4;
    private static final int S_TIME   =  1;

    // Indices into the per-player dynamic-key array
    private static final int K_ONLINE = 0;
    private static final int K_WORLD  = 1;
    private static final int K_PING   = 2;
    private static final int K_TIME   = 3;
    private static final int K_COUNT  = 4;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final HimatsudoCore plugin;
    private final boolean enabled;

    private final Map<UUID, Scoreboard> activeBoards     = new HashMap<>();
    private final Map<UUID, Objective>  activeObjectives = new HashMap<>();

    /** Per-player current §-coded keys for each dynamic row [online, world, ping, time]. */
    private final Map<UUID, String[]> dynamicKeys = new HashMap<>();

    private BukkitTask refreshTask;

    public BoardModule(HimatsudoCore plugin) {
        this.plugin  = plugin;
        this.enabled = plugin.getConfig().getBoolean("board.enabled", true);

        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            refreshTask = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, this::tickRefresh, 20L, 20L);
            plugin.getLogger().info("[BoardModule] Scoreboard tracking active.");
        } else {
            plugin.getLogger().info("[BoardModule] Disabled via config.");
        }
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        if (plugin.getConfig().getBoolean("board.auto-show", true)) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> showBoard(event.getPlayer()), 5L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        activeBoards.remove(uuid);
        activeObjectives.remove(uuid);
        dynamicKeys.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void showBoard(Player player) {
        UUID uuid = player.getUniqueId();
        dynamicKeys.remove(uuid);

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();

        String rawTitle = plugin.getConfig().getString("board.title", "&6&lHimatsudoSMP");
        Component title = Fmt.parse(rawTitle);

        Objective obj = board.registerNewObjective(
                "himatsudo", "dummy", title, RenderType.INTEGER);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        setStatic(obj, "§8§m━━━━━━━━━━━━━━━━",                          S_SEP);
        setStatic(obj, Fmt.toSectionKey(" §f プレイヤー §8: §7" + player.getName()), S_PLAYER);
        setStatic(obj, Fmt.toSectionKey(buildRankLine(player)),           S_RANK);
        setStatic(obj, " ",                                               S_SP1);
        setStatic(obj, "  ",                                              S_SP2);
        setStatic(obj, Fmt.toSectionKey(" §7 himatsudo.net"),             S_SITE);
        setStatic(obj, "   ",                                             S_SP3);

        applyDynamic(board, obj, uuid, player);

        player.setScoreboard(board);
        activeBoards.put(uuid, board);
        activeObjectives.put(uuid, obj);

        AfkModule afk = plugin.getAfkModule();
        if (afk != null) afk.syncAfkPlayersToScoreboard(board);
    }

    public void hideBoard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        UUID uuid = player.getUniqueId();
        activeBoards.remove(uuid);
        activeObjectives.remove(uuid);
    }

    public boolean toggleBoard(Player player) {
        if (activeBoards.containsKey(player.getUniqueId())) {
            hideBoard(player);
            return false;
        }
        showBoard(player);
        return true;
    }

    public boolean isBoardActive(Player player) {
        return activeBoards.containsKey(player.getUniqueId());
    }

    public void refreshBoard(Player player) {
        if (activeBoards.containsKey(player.getUniqueId())) showBoard(player);
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refreshBoard(p);
    }

    public Collection<Scoreboard> getActiveScoreboards() {
        return Collections.unmodifiableCollection(activeBoards.values());
    }

    public void shutdown() {
        if (refreshTask != null && !refreshTask.isCancelled()) refreshTask.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) hideBoard(p);
        activeBoards.clear();
        activeObjectives.clear();
    }

    // -------------------------------------------------------------------------
    // Dynamic update (every tick)
    // -------------------------------------------------------------------------

    private void tickRefresh() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            Scoreboard board = activeBoards.get(uuid);
            Objective  obj   = activeObjectives.get(uuid);
            if (board == null || obj == null) continue;
            applyDynamic(board, obj, uuid, p);
        }
    }

    private void applyDynamic(Scoreboard board, Objective obj, UUID uuid, Player player) {
        int    ping      = player.getPing();
        String pingColor = pingColor(ping);
        String time      = ZonedDateTime.now(JST).format(TIME_FMT);

        updateLine(board, obj, uuid, K_ONLINE,
                Fmt.toSectionKey(" §f オンライン §8: §a" + Bukkit.getOnlinePlayers().size() + " §7人"),
                S_ONLINE);
        updateLine(board, obj, uuid, K_WORLD,
                Fmt.toSectionKey(" §f ワールド §8: §b" + player.getWorld().getName()),
                S_WORLD);
        updateLine(board, obj, uuid, K_PING,
                Fmt.toSectionKey(" §f Ping §8: " + pingColor + ping + "ms"),
                S_PING);
        updateLine(board, obj, uuid, K_TIME,
                Fmt.toSectionKey(" §e ⏰ §f" + time + " §7(JST)"),
                S_TIME);
    }

    /** Updates one dynamic row only if its key changed, to avoid flickering. */
    private void updateLine(Scoreboard board, Objective obj,
                            UUID uuid, int keyIndex, String newKey, int score) {
        String[] keys = dynamicKeys.computeIfAbsent(uuid, u -> new String[K_COUNT]);
        String old = keys[keyIndex];
        if (newKey.equals(old)) return;
        if (old != null) board.resetScores(old);
        obj.getScore(newKey).setScore(score);
        keys[keyIndex] = newKey;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setStatic(Objective obj, String sectionKey, int score) {
        obj.getScore(sectionKey).setScore(score);
    }

    private String buildRankLine(Player player) {
        ChatModule cm = plugin.getChatModule();
        if (cm != null) {
            return " §f ランク §8: "
                    + Fmt.toSectionKey(cm.getRankPrefix(player));
        }
        return " §f ランク §8: §7一般";
    }

    private static String pingColor(int ping) {
        if (ping < 50)  return "§a";
        if (ping < 100) return "§e";
        if (ping < 150) return "§6";
        return "§c";
    }
}
