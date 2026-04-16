package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
 * AfkModule — AFK status tracker (独立モジュール ⑤).
 *
 * 機能:
 *   - 設定秒数の無操作でプレイヤーを放置状態に移行
 *   - 移行・復帰時にサーバー全体チャットへ通知
 *   - スコアボードチームAPIで他プレイヤーから見た頭上の名前に
 *     [AFK] プレフィックスを表示
 *   - 段階的タイムドメッセージ（例: 5分後・10分後）
 *
 * 設定キー (config.yml の afk: セクション):
 *   afk.enabled              — 機能ON/OFF
 *   afk.timeout-seconds      — 放置判定までの秒数 (デフォルト: 20)
 *   afk.check-interval-ticks — 判定タスクの実行間隔 (デフォルト: 100)
 *   afk.message-go           — 放置移行時のメッセージ ({player} 置換可)
 *   afk.message-return       — 復帰時のメッセージ ({player} 置換可)
 *   afk.display-prefix       — 頭上に表示するプレフィックス
 *   afk.timed-messages       — 段階通知リスト (seconds / message)
 */
public class AfkModule implements Listener {

    /** Scoreboard team name — 他プラグインと衝突しない固有名 */
    private static final String TEAM_NAME = "hc_afk";

    private final HimatsudoCore plugin;
    private final boolean enabled;

    /** 最終アクティビティ時刻 (UUID -> epoch ms) */
    private final Map<UUID, Long> lastActivity = new HashMap<>();

    /** 現在 AFK 状態のプレイヤー */
    private final Set<UUID> afkPlayers = new HashSet<>();

    /**
     * 段階メッセージ送信済みフラグ
     * UUID -> 既に送信した seconds 閾値の集合
     */
    private final Map<UUID, Set<Integer>> sentTimedMessages = new HashMap<>();

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
        // サーバー停止時はサイレントに AFK 解除
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
    // Activity detection — 操作を検知したらタイマーをリセット
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // 頭の向きだけの変化は除外 — ブロック座標が変わった場合のみ
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
        sentTimedMessages.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // AFK 判定ロジック
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

            // 段階的メッセージ — すでに AFK の場合のみ
            if (afkPlayers.contains(uuid)) {
                checkTimedMessages(player, (int) (idleMs / 1000L));
            }
        }
    }

    private void markActivity(Player player) {
        UUID uuid = player.getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());
        sentTimedMessages.remove(uuid);

        if (afkPlayers.contains(uuid)) {
            removeAfkStatus(player, true);
        }
    }

    private void applyAfkStatus(Player player) {
        afkPlayers.add(player.getUniqueId());
        applyNameTag(player);

        String msg = resolve(plugin.getConfig()
                .getString("afk.message-go", "&7[AFK] &f{player} &7が放置状態になりました。"),
                player);
        Bukkit.broadcast(parse(msg));

        // タブリストの表示を更新 (メインスレッドが保証されている)
        notifyTabModule(player);
    }

    private void removeAfkStatus(Player player, boolean broadcast) {
        afkPlayers.remove(player.getUniqueId());
        removeNameTag(player);

        if (broadcast) {
            String msg = resolve(plugin.getConfig()
                    .getString("afk.message-return", "&7[AFK] &f{player} &7が放置から戻りました。"),
                    player);
            Bukkit.broadcast(parse(msg));
        }

        // タブリストの表示を更新 (非同期スレッドから呼ばれる可能性があるためスケジュール)
        plugin.getServer().getScheduler().runTask(plugin, () -> notifyTabModule(player));
    }

    private void notifyTabModule(Player player) {
        TabModule tm = plugin.getTabModule();
        if (tm != null) tm.refreshPlayer(player);
    }

    /**
     * 段階的タイムドメッセージを確認・送信する。
     * config の afk.timed-messages リストを上から評価し、
     * 閾値を超えていてまだ未送信なら全体放送する。
     */
    private void checkTimedMessages(Player player, int idleSeconds) {
        var entries = plugin.getConfig().getMapList("afk.timed-messages");
        Set<Integer> sent = sentTimedMessages.computeIfAbsent(
                player.getUniqueId(), k -> new HashSet<>());

        for (var entry : entries) {
            Object secObj = entry.get("seconds");
            Object msgObj = entry.get("message");
            if (secObj == null || msgObj == null) continue;

            int threshold;
            try {
                threshold = Integer.parseInt(secObj.toString());
            } catch (NumberFormatException e) {
                continue;
            }

            if (idleSeconds >= threshold && sent.add(threshold)) {
                String msg = resolve(msgObj.toString(), player);
                Bukkit.broadcast(parse(msg));
            }
        }
    }

    // -------------------------------------------------------------------------
    // スコアボードチームによる頭上プレフィックス管理
    // -------------------------------------------------------------------------

    /**
     * 新しいスコアボードに対して AFK チームを登録し、
     * 現在 AFK 中のプレイヤーを全員そのチームに追加する。
     *
     * BoardModule が新しいスコアボードを生成するたびに呼び出すこと。
     */
    public void syncAfkPlayersToScoreboard(Scoreboard scoreboard) {
        ensureTeam(scoreboard);
        for (UUID uuid : afkPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) ensureTeam(scoreboard).addEntry(p.getName());
        }
    }

    private void applyNameTag(Player target) {
        // メインスコアボード
        applyTeamEntry(Bukkit.getScoreboardManager().getMainScoreboard(), target.getName(), true);

        // BoardModule が管理するカスタムスコアボード
        BoardModule bm = plugin.getBoardModule();
        if (bm != null) {
            for (Scoreboard sb : bm.getActiveScoreboards()) {
                applyTeamEntry(sb, target.getName(), true);
            }
        }
    }

    private void removeNameTag(Player target) {
        applyTeamEntry(Bukkit.getScoreboardManager().getMainScoreboard(), target.getName(), false);

        BoardModule bm = plugin.getBoardModule();
        if (bm != null) {
            for (Scoreboard sb : bm.getActiveScoreboards()) {
                applyTeamEntry(sb, target.getName(), false);
            }
        }
    }

    private void applyTeamEntry(Scoreboard sb, String entry, boolean add) {
        Team team = ensureTeam(sb);
        if (add) {
            team.addEntry(entry);
        } else {
            team.removeEntry(entry);
        }
    }

    private Team ensureTeam(Scoreboard sb) {
        Team team = sb.getTeam(TEAM_NAME);
        if (team == null) {
            team = sb.registerNewTeam(TEAM_NAME);
        }
        // プレフィックスを常に最新の config 値に更新
        String rawPrefix = plugin.getConfig().getString("afk.display-prefix", "&7[AFK] ");
        team.prefix(parse(rawPrefix));
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
    // 公開 API
    // -------------------------------------------------------------------------

    public boolean isAfk(Player player) {
        return afkPlayers.contains(player.getUniqueId());
    }

    public Set<UUID> getAfkPlayers() {
        return Collections.unmodifiableSet(afkPlayers);
    }

    // -------------------------------------------------------------------------
    // ユーティリティ
    // -------------------------------------------------------------------------

    private Component parse(String raw) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    private String resolve(String template, Player player) {
        // ChatModule が有効な場合はランクプレフィックスをプレイヤー名の前に付ける
        ChatModule cm = plugin.getChatModule();
        String playerDisplay = cm != null
                ? cm.getRankPrefix(player) + " &f" + player.getName()
                : player.getName();
        return template.replace("{player}", playerDisplay);
    }
}
