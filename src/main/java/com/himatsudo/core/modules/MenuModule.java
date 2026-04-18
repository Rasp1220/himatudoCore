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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * MenuModule — /menu GUI (Nexus Menu).
 *
 * Layout (27 slots):
 *   [ ][W][ ][R][ ][P][ ][B][ ]   row 1
 *   [ ][ ][ ][ ][ ][ ][ ][ ][ ]   row 2 (filler)
 *   [ ][ ][ ][ ][ ][ ][ ][ ][X]   row 3 (close at slot 26)
 *
 *   W=ワープ(10), R=ルール(12), P=プロフィール(14), B=スコアボード(16), X=閉じる(26)
 */
public class MenuModule implements Listener {

    private final HimatsudoCore plugin;
    private final boolean enabled;

    private final Set<UUID> openMenus = new HashSet<>();

    public MenuModule(HimatsudoCore plugin) {
        this.plugin  = plugin;
        this.enabled = plugin.getConfig().getBoolean("menu.enabled", true);

        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[MenuModule] GUI handler registered.");
        } else {
            plugin.getLogger().info("[MenuModule] Disabled via config.");
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void openMenu(Player player) {
        if (!enabled) {
            player.sendMessage(Component.text("メニューは現在無効です。", NamedTextColor.RED));
            return;
        }

        int    size     = plugin.getConfig().getInt("menu.size", 27);
        String rawTitle = plugin.getConfig().getString("menu.title", "&8[ &6Nexus Menu &8]");

        Inventory inv = Bukkit.createInventory(null, size, Fmt.parse(rawTitle));
        fillFiller(inv, size);

        for (MenuItem item : buildItems(player)) {
            if (item.slot() >= 0 && item.slot() < size) {
                inv.setItem(item.slot(), item.stack());
            }
        }

        // State update AFTER openInventory so that InventoryCloseEvent
        // (fired synchronously by openInventory) doesn't clear the new state.
        player.openInventory(inv);
        openMenus.add(player.getUniqueId());
    }

    public void shutdown() {
        for (UUID uuid : openMenus) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.closeInventory();
        }
        openMenus.clear();
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openMenus.contains(player.getUniqueId())) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        buildItems(player).stream()
                .filter(mi -> mi.slot() == event.getSlot())
                .findFirst()
                .ifPresent(mi -> executeAction(player, mi.action()));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Button definitions
    // -------------------------------------------------------------------------

    private List<MenuItem> buildItems(Player player) {
        boolean boardOn = plugin.getBoardModule() != null
                && plugin.getBoardModule().isBoardActive(player);

        return List.of(
                new MenuItem(10, buildItem(Material.COMPASS,
                        "&a&lワープ",
                        List.of("&7各ワールドへのワープ", "&7(準備中)")),
                        "warp"),

                new MenuItem(12, buildItem(Material.BOOK,
                        "&b&lルールブック",
                        List.of("&7サーバールールを確認する")),
                        "rules"),

                new MenuItem(14, buildItem(Material.PLAYER_HEAD,
                        "&e&lプロフィール",
                        List.of("&7自分の情報を見る")),
                        "profile"),

                new MenuItem(16, buildItem(
                        boardOn ? Material.LIME_DYE : Material.GRAY_DYE,
                        boardOn ? "&a&lスコアボード &a[表示中]" : "&7&lスコアボード &7[非表示]",
                        List.of("&7クリックで表示/非表示を切り替え")),
                        "board"),

                new MenuItem(26, buildItem(Material.BARRIER,
                        "&c&lメニューを閉じる",
                        List.of("&7メニューを閉じます")),
                        "close")
        );
    }

    private void executeAction(Player player, String action) {
        switch (action) {
            case "warp"  -> player.sendMessage(Component.text("ワープ機能は準備中です。", NamedTextColor.YELLOW));
            case "rules" -> {
                player.closeInventory();
                player.sendMessage(Component.text("ルール: https://example.com/rules", NamedTextColor.AQUA));
            }
            case "profile" -> {
                player.closeInventory();
                ProfileModule pm = plugin.getProfileModule();
                if (pm != null) {
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> pm.openProfile(player, player), 1L);
                }
            }
            case "board" -> {
                BoardModule bm = plugin.getBoardModule();
                if (bm != null) {
                    boolean nowVisible = bm.toggleBoard(player);
                    player.sendMessage(nowVisible
                            ? Component.text("スコアボードを表示しました。", NamedTextColor.GREEN)
                            : Component.text("スコアボードを非表示にしました。", NamedTextColor.YELLOW));
                    // Re-open to reflect updated button state
                    Bukkit.getScheduler().runTaskLater(plugin, () -> openMenu(player), 1L);
                }
            }
            case "close" -> player.closeInventory();
            default      -> player.sendMessage(Component.text(
                    "不明なアクション: " + action, NamedTextColor.RED));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ItemStack buildItem(Material material, String rawName, List<String> rawLore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta  meta  = stack.getItemMeta();
        meta.displayName(Fmt.tooltip(rawName));
        meta.lore(rawLore.stream().map(Fmt::tooltip).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private void fillFiller(Inventory inv, int size) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        for (int i = 0; i < size; i++) inv.setItem(i, pane);
    }

    private record MenuItem(int slot, ItemStack stack, String action) {}
}
