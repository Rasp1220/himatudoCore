package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
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
 * BoardModule — per-player sidebar scoreboard (Overlay Board unit).
 *
 * レイアウト:
 *   ─────────────────   (装飾ライン)
 *    プレイヤー: 名前
 *    ランク    : [Rank]
 *                        (スペーサー)
 *    オンライン: N 人    ← 動的
 *    ワールド  : world   ← 動的
 *    Ping      : Nms     ← 動的
 *                        (スペーサー)
 *    himatsudo.net
 *    ⏰ HH:mm:ss  JST   ← 動的 (最下段)
 *
 * 設定キー (board:):
 *   board.enabled   — ON/OFF
 *   board.title     — タイトル (&カラーコード)
 *   board.auto-show — 参加時自動表示
 */
public class BoardModule implements Listener {

    // --- スコア割り当て (大きい値 = 上) ---
    private static final int S_SEP    = 13; // 装飾ライン
    private static final int S_PLAYER = 12; // プレイヤー名
    private static final int S_RANK   = 11; // ランク
    private static final int S_SP1    = 10; // スペーサー
    private static final int S_ONLINE =  9; // オンライン数  ← 動的
    private static final int S_WORLD  =  8; // ワールド       ← 動的
    private static final int S_PING   =  7; // Ping          ← 動的
    private static final int S_SP2    =  6; // スペーサー
    private static final int S_SITE   =  5; // URL
    private static final int S_SP3    =  4; // スペーサー
    private static final int S_TIME   =  1; // 現在時刻 (最下段) ← 動的

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final HimatsudoCore plugin;
    private final boolean enabled;

    private final Map<UUID, Scoreboard> activeBoards     = new HashMap<>();
    private final Map<UUID, Objective>  activeObjectives = new HashMap<>();

    // 動的行の現在キー (§-コード済み文字列) を追跡
    private final Map<UUID, String> keyOnline = new HashMap<>();
    private final Map<UUID, String> keyWorld  = new HashMap<>();
    private final Map<UUID, String> keyPing   = new HashMap<>();
    private final Map<UUID, String> keyTime   = new HashMap<>();

    private BukkitTask refreshTask;

    public BoardModule(HimatsudoCore plugin) {
        this.plugin  = plugin;
        this.enabled = plugin.getConfig().getBoolean("board.enabled", true);

        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            // 1秒ごとに動的行 (Ping・時刻・オンライン数・ワールド) を更新
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
        keyOnline.remove(uuid);
        keyWorld.remove(uuid);
        keyPing.remove(uuid);
        keyTime.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** スコアボードを生成して表示する。再呼び出しで完全再描画。 */
    public void showBoard(Player player) {
        UUID uuid = player.getUniqueId();

        // 既存の動的キーをクリア (再描画のため)
        keyOnline.remove(uuid);
        keyWorld.remove(uuid);
        keyPing.remove(uuid);
        keyTime.remove(uuid);

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();

        String rawTitle = plugin.getConfig().getString("board.title", "&8[ &6&lHimatsudoSMP &8]");
        Component title = parse(rawTitle);

        Objective obj = board.registerNewObjective(
                "himatsudo", "dummy", title, RenderType.INTEGER);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // 静的行を設置
        setStatic(obj, "§8§m━━━━━━━━━━━━━━━━",           S_SEP);
        setStatic(obj, toKey(" §f プレイヤー §8: §7" + player.getName()), S_PLAYER);
        setStatic(obj, toKey(buildRankRaw(player)),          S_RANK);
        setStatic(obj, " ",                                  S_SP1);
        setStatic(obj, "  ",                                 S_SP2);
        setStatic(obj, toKey(" §7 himatsudo.net"),           S_SITE);
        setStatic(obj, "   ",                                S_SP3);

        // 動的行の初期描画
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
        } else {
            showBoard(player);
            return true;
        }
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
    // 動的更新 (毎秒 tick)
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

    /**
     * オンライン数・ワールド・Ping・時刻を差分更新する。
     * キーが変化した行だけ resetScores → 再設置するため flickering を防ぐ。
     */
    private void applyDynamic(Scoreboard board, Objective obj, UUID uuid, Player player) {
        int ping = player.getPing();
        String pingColor = ping < 50 ? "§a" : ping < 100 ? "§e" : ping < 150 ? "§6" : "§c";

        String time = ZonedDateTime.now(JST).format(TIME_FMT);

        updateLine(board, obj, keyOnline, uuid,
                toKey(" §f オンライン §8: §a" + Bukkit.getOnlinePlayers().size() + " §7人"),
                S_ONLINE);
        updateLine(board, obj, keyWorld, uuid,
                toKey(" §f ワールド §8: §b" + player.getWorld().getName()),
                S_WORLD);
        updateLine(board, obj, keyPing, uuid,
                toKey(" §f Ping §8: " + pingColor + ping + "ms"),
                S_PING);
        updateLine(board, obj, keyTime, uuid,
                toKey(" §e ⏰ §f" + time + " §7(JST)"),
                S_TIME);
    }

    /**
     * 指定スロットの動的行を差分更新する。
     * 新旧キーが同じなら何もしない。
     */
    private void updateLine(Scoreboard board, Objective obj,
                            Map<UUID, String> keyMap, UUID uuid,
                            String newKey, int score) {
        String old = keyMap.get(uuid);
        if (newKey.equals(old)) return;
        if (old != null) board.resetScores(old);
        obj.getScore(newKey).setScore(score);
        keyMap.put(uuid, newKey);
    }

    // -------------------------------------------------------------------------
    // ヘルパー
    // -------------------------------------------------------------------------

    /** &-コードを §-コードに変換してスコアエントリキーとして返す。 */
    private String toKey(String ampersand) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(ampersand));
    }

    /** 静的スコアエントリを設置する (§-コード済みキーを直接受け取る版)。 */
    private void setStatic(Objective obj, String sectionKey, int score) {
        obj.getScore(sectionKey).setScore(score);
    }

    private String buildRankRaw(Player player) {
        ChatModule cm = plugin.getChatModule();
        if (cm != null) {
            return " §f ランク §8: " +
                   net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                           .legacySection().serialize(
                   net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                           .legacyAmpersand().deserialize(cm.getRankPrefix(player)));
        }
        return " §f ランク §8: §7一般";
    }

    private Component parse(String raw) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(raw);
    }
}
