package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import com.himatsudo.core.util.Fmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/**
 * ProfileModule — player profile and player-list GUI.
 *
 * Profile layout (27 slots, 3×9):
 *   [G][G][G][G][SKULL][G][G][G][G]   row 0
 *   [G][RANK][G][G][ G ][G][G][STATUS][G]   row 1
 *   [G][G][LOC][G][ G ][G][LIST][G][CLOSE]  row 2
 */
public class ProfileModule implements Listener {

    private static final int SLOT_SKULL  = 13;
    private static final int SLOT_RANK   = 10;
    private static final int SLOT_STATUS = 16;
    private static final int SLOT_LOC    = 20;
    private static final int SLOT_LIST   = 24;
    private static final int SLOT_CLOSE  = 26;

    private static final int PROFILE_SIZE = 27;
    private static final int LIST_SIZE    = 54;

    private final HimatsudoCore plugin;

    /** viewer UUID → target UUID (profile screen) */
    private final Map<UUID, UUID> profileViewers = new HashMap<>();
    /** viewers currently on the player-list screen */
    private final Set<UUID> listViewers = new HashSet<>();

    public ProfileModule(HimatsudoCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[ProfileModule] Profile UI active.");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void openProfile(Player viewer, Player target) {
        Inventory inv = Bukkit.createInventory(null, PROFILE_SIZE,
                Fmt.parse("&8[ &6プロフィール: &f" + target.getName() + " &8]"));

        fillGlass(inv, PROFILE_SIZE);
        inv.setItem(SLOT_SKULL,  buildSkull(target));
        inv.setItem(SLOT_RANK,   buildRankItem(target));
        inv.setItem(SLOT_STATUS, buildStatusItem(target));
        inv.setItem(SLOT_LOC,    buildLocationItem(target));
        inv.setItem(SLOT_LIST,   buildListButton());
        inv.setItem(SLOT_CLOSE,  buildCloseButton());

        // openInventory fires InventoryCloseEvent synchronously, clearing old state.
        // New state must be registered after the call.
        viewer.openInventory(inv);
        profileViewers.put(viewer.getUniqueId(), target.getUniqueId());
    }

    public void openPlayerList(Player viewer) {
        Inventory inv = Bukkit.createInventory(null, LIST_SIZE,
                Fmt.parse("&8[ &bプレイヤー一覧 &8]"));
        fillGlass(inv, LIST_SIZE);

        int slot = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (slot >= LIST_SIZE - 9) break;
            inv.setItem(slot++, buildSkull(p));
        }
        inv.setItem(LIST_SIZE - 5, buildCloseButton());

        viewer.openInventory(inv);
        listViewers.add(viewer.getUniqueId());
    }

    public void shutdown() {}

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        UUID uuid = viewer.getUniqueId();

        boolean inProfile = profileViewers.containsKey(uuid);
        boolean inList    = listViewers.contains(uuid);
        if (!inProfile && !inList) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (inProfile) handleProfileClick(viewer, event.getSlot());
        else           handleListClick(viewer, clicked);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        profileViewers.remove(uuid);
        listViewers.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Click handlers
    // -------------------------------------------------------------------------

    private void handleProfileClick(Player viewer, int slot) {
        switch (slot) {
            case SLOT_CLOSE -> Bukkit.getScheduler().runTaskLater(plugin,
                    () -> viewer.closeInventory(), 1L);
            case SLOT_LIST  -> Bukkit.getScheduler().runTaskLater(plugin,
                    () -> openPlayerList(viewer), 1L);
            default -> {}
        }
    }

    private void handleListClick(Player viewer, ItemStack clicked) {
        if (clicked.getType() == Material.BARRIER) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> viewer.closeInventory(), 1L);
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
    // Item builders
    // -------------------------------------------------------------------------

    private ItemStack buildSkull(Player target) {
        AfkModule  afk  = plugin.getAfkModule();
        ChatModule chat = plugin.getChatModule();

        boolean isAfk  = afk  != null && afk.isAfk(target);
        String  rank   = chat != null ? chat.getRankPrefix(target) : "&7[一般]&r";
        String  status = isAfk ? "&c● AFK中" : "&a● アクティブ";

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(Fmt.tooltip("&6&l" + target.getName()));
        meta.lore(List.of(
                Component.empty(),
                Fmt.tooltip("&7ID&8: &f" + target.getName()),
                Fmt.tooltip(rank),
                Fmt.tooltip(status)));
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack buildRankItem(Player target) {
        ChatModule chat = plugin.getChatModule();
        String rank = chat != null ? chat.getRankPrefix(target) : "&7[一般]&r";

        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Fmt.tooltip("&6&lランク"));
        meta.lore(List.of(Component.empty(), Fmt.tooltip(rank)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildStatusItem(Player target) {
        AfkModule afk   = plugin.getAfkModule();
        boolean   isAfk = afk != null && afk.isAfk(target);

        ItemStack item = new ItemStack(isAfk ? Material.RED_DYE : Material.LIME_DYE);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Fmt.tooltip("&f&lステータス"));
        meta.lore(List.of(Component.empty(),
                Fmt.tooltip(isAfk ? "&c● AFK中" : "&a● アクティブ")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildLocationItem(Player target) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Fmt.tooltip("&b&l現在地"));
        meta.lore(List.of(
                Component.empty(),
                Fmt.tooltip("&7ワールド&8: &f" + target.getWorld().getName()),
                Fmt.tooltip("&7X &f" + target.getLocation().getBlockX()
                        + "  &7Y &f" + target.getLocation().getBlockY()
                        + "  &7Z &f" + target.getLocation().getBlockZ())));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildListButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Fmt.tooltip("&b&lプレイヤー一覧"));
        meta.lore(List.of(Component.empty(),
                Fmt.tooltip("&7オンラインプレイヤーの"),
                Fmt.tooltip("&7プロフィールを確認する")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Fmt.tooltip("&c&l閉じる"));
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void fillGlass(Inventory inv, int size) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        for (int i = 0; i < size; i++) inv.setItem(i, pane);
    }
}
