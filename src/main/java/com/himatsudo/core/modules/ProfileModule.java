package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ProfileModule — プレイヤープロフィール表示 GUI.
 *
 * 機能:
 *   - /profile [player] コマンド、またはメニューのプロフィールボタンで開く
 *   - 27スロットのインベントリ UI でプロフィールを表示
 *   - 各アイテムにカーソルを合わせると詳細情報をツールチップで確認できる
 *   - 「プレイヤー一覧」ボタンから他プレイヤーのプロフィールを閲覧可能
 *
 * レイアウト (プロフィール, 3×9):
 *   [G][G][G][G][ G ][G][G][G][G]
 *   [G][RANK][G][G][SKULL][G][G][STATUS][G]
 *   [G][G][LOC][G][ G ][G][LIST][G][CLOSE]
 */
public class ProfileModule implements Listener {

    // プロフィールインベントリのスロット定義
    private static final int SLOT_SKULL  = 13;
    private static final int SLOT_RANK   = 10;
    private static final int SLOT_STATUS = 16;
    private static final int SLOT_LOC    = 20;
    private static final int SLOT_LIST   = 24;
    private static final int SLOT_CLOSE  = 26;

    private static final int PROFILE_SIZE = 27;
    private static final int LIST_SIZE    = 54; // 最大45プレイヤー + ナビ行

    private final HimatsudoCore plugin;

    /** viewer UUID → 閲覧中のプロフィールの target UUID */
    private final Map<UUID, UUID> profileViewers = new HashMap<>();
    /** プレイヤー一覧インベントリを開いているviewerのUUID */
    private final Set<UUID> listViewers = new HashSet<>();

    public ProfileModule(HimatsudoCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[ProfileModule] Profile UI active.");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * viewer に対して target のプロフィールインベントリを開く。
     * viewer と target が同じ場合は自分のプロフィールを表示する。
     *
     * 注意: openInventory は InventoryCloseEvent を同期的に発火するため、
     * 状態の追加は openInventory の呼び出しの後に行う。
     */
    public void openProfile(Player viewer, Player target) {
        Inventory inv = Bukkit.createInventory(null, PROFILE_SIZE,
                parse("&8[ &6プロフィール: &f" + target.getName() + " &8]"));

        fillGlass(inv, PROFILE_SIZE);
        inv.setItem(SLOT_SKULL,  buildSkull(target));
        inv.setItem(SLOT_RANK,   buildRankItem(target));
        inv.setItem(SLOT_STATUS, buildStatusItem(target));
        inv.setItem(SLOT_LOC,    buildLocationItem(target));
        inv.setItem(SLOT_LIST,   buildListButton());
        inv.setItem(SLOT_CLOSE,  buildCloseButton());

        // openInventory が InventoryCloseEvent を発火して古い状態をクリアするため、
        // 新しい状態の登録は openInventory の後に行う。
        viewer.openInventory(inv);
        profileViewers.put(viewer.getUniqueId(), target.getUniqueId());
    }

    /**
     * オンラインプレイヤー一覧インベントリを開く。
     * スカルをクリックするとそのプレイヤーのプロフィールへ遷移する。
     */
    public void openPlayerList(Player viewer) {
        Inventory inv = Bukkit.createInventory(null, LIST_SIZE,
                parse("&8[ &bプレイヤー一覧 &8]"));
        fillGlass(inv, LIST_SIZE);

        int slot = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (slot >= LIST_SIZE - 9) break; // 最終行はナビゲーション用
            inv.setItem(slot++, buildSkull(p));
        }

        // 最終行中央に閉じるボタン
        inv.setItem(LIST_SIZE - 5, buildCloseButton());

        // openInventory が InventoryCloseEvent を発火して古い状態をクリアするため、
        // 新しい状態の登録は openInventory の後に行う。
        viewer.openInventory(inv);
        listViewers.add(viewer.getUniqueId());
    }

    public void shutdown() {
        // Bukkit が plugin disable 時に自動でリスナーを解除する
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        UUID uuid = viewer.getUniqueId();

        boolean inProfile = profileViewers.containsKey(uuid);
        boolean inList    = listViewers.contains(uuid);
        if (!inProfile && !inList) return;

        // 常にキャンセルしてアイテムの取得を防止
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (inProfile) {
            handleProfileClick(viewer, event.getSlot());
        } else {
            handleListClick(viewer, clicked);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        profileViewers.remove(uuid);
        listViewers.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // クリック処理
    // -------------------------------------------------------------------------

    private void handleProfileClick(Player viewer, int slot) {
        switch (slot) {
            // 1tickの遅延でinventory操作を行い、イベントハンドラ内の競合を回避
            case SLOT_CLOSE -> Bukkit.getScheduler().runTaskLater(plugin,
                    viewer::closeInventory, 1L);
            case SLOT_LIST  -> Bukkit.getScheduler().runTaskLater(plugin,
                    () -> openPlayerList(viewer), 1L);
            default         -> { /* その他スロットは無視 */ }
        }
    }

    private void handleListClick(Player viewer, ItemStack clicked) {
        if (clicked.getType() == Material.BARRIER) {
            Bukkit.getScheduler().runTaskLater(plugin, viewer::closeInventory, 1L);
            return;
        }
        if (clicked.getType() != Material.PLAYER_HEAD) return;

        SkullMeta meta = (SkullMeta) clicked.getItemMeta();
        if (meta.getOwningPlayer() == null) return;

        Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
        if (target == null) {
            viewer.sendMessage(Component.text(
                    meta.getOwningPlayer().getName() + " はもうオンラインではありません。",
                    NamedTextColor.RED));
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> openProfile(viewer, target), 1L);
    }

    // -------------------------------------------------------------------------
    // アイテムビルダー
    // -------------------------------------------------------------------------

    /**
     * プレイヤースカル。ホバー時のツールチップにID・ランク・AFK状態を表示する。
     */
    private ItemStack buildSkull(Player target) {
        AfkModule  afk  = plugin.getAfkModule();
        ChatModule chat = plugin.getChatModule();

        boolean isAfk  = afk  != null && afk.isAfk(target);
        String  rank   = chat != null ? chat.getRankPrefix(target) : "&7[一般]&r";
        String  status = isAfk ? "&c● AFK中" : "&a● アクティブ";

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(tip("&6&l" + target.getName()));
        meta.lore(List.of(
                Component.empty(),
                tip("&7ID&8: &f" + target.getName()),
                tip(rank),
                tip(status)
        ));
        skull.setItemMeta(meta);
        return skull;
    }

    /** ランクアイテム。ホバー時にランク名をカラー付きで表示する。 */
    private ItemStack buildRankItem(Player target) {
        ChatModule chat = plugin.getChatModule();
        String rank = chat != null ? chat.getRankPrefix(target) : "&7[一般]&r";

        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(tip("&6&lランク"));
        meta.lore(List.of(Component.empty(), tip(rank)));
        item.setItemMeta(meta);
        return item;
    }

    /** ステータスアイテム。アクティブ=緑染料, AFK=赤染料 で色分けして表示する。 */
    private ItemStack buildStatusItem(Player target) {
        AfkModule afk   = plugin.getAfkModule();
        boolean   isAfk = afk != null && afk.isAfk(target);

        ItemStack item = new ItemStack(isAfk ? Material.RED_DYE : Material.LIME_DYE);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(tip("&f&lステータス"));
        meta.lore(List.of(
                Component.empty(),
                tip(isAfk ? "&c● AFK中" : "&a● アクティブ")
        ));
        item.setItemMeta(meta);
        return item;
    }

    /** 現在地アイテム。ホバー時にワールド名と座標を表示する。 */
    private ItemStack buildLocationItem(Player target) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(tip("&b&l現在地"));
        meta.lore(List.of(
                Component.empty(),
                tip("&7ワールド&8: &f" + target.getWorld().getName()),
                tip("&7X &f" + target.getLocation().getBlockX()
                    + "  &7Y &f" + target.getLocation().getBlockY()
                    + "  &7Z &f" + target.getLocation().getBlockZ())
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildListButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(tip("&b&lプレイヤー一覧"));
        meta.lore(List.of(
                Component.empty(),
                tip("&7オンラインプレイヤーの"),
                tip("&7プロフィールを確認する")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(tip("&c&l閉じる"));
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // ユーティリティ
    // -------------------------------------------------------------------------

    /**
     * &-カラーコードをパースし、斜体を無効化した Component を返す。
     * lore / displayName 用のツールチップテキストに使用する。
     */
    private Component tip(String raw) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(raw)
                .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private Component parse(String raw) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    private void fillGlass(Inventory inv, int size) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        for (int i = 0; i < size; i++) inv.setItem(i, pane);
    }
}
