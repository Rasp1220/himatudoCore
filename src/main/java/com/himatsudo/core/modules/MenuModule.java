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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * MenuModule — creates and manages the /menu GUI (Nexus Menu unit).
 *
 * Configuration keys (under menu: in config.yml):
 *   menu.enabled — master toggle
 *   menu.title   — inventory title (supports &amp; color codes)
 *   menu.size    — inventory size (must be multiple of 9)
 *
 * Design philosophy:
 *   Each "button" is represented by a {@link MenuItem} record.
 *   To add new buttons, register them in {@link #buildItems()} without
 *   touching any other method.
 */
public class MenuModule implements Listener {

    private final HimatsudoCore plugin;
    private final boolean enabled;

    // Track open menus so we can handle clicks only for our inventory
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

    /** Opens the main menu for the given player. */
    public void openMenu(Player player) {
        if (!enabled) {
            player.sendMessage(Component.text("メニューは現在無効です。", NamedTextColor.RED));
            return;
        }

        int size = plugin.getConfig().getInt("menu.size", 27);
        String rawTitle = plugin.getConfig().getString("menu.title", "&8[ &6Nexus Menu &8]");
        Component title = LegacyComponentSerializer.legacyAmpersand().deserialize(rawTitle);

        Inventory inv = Bukkit.createInventory(null, size, title);

        for (MenuItem item : buildItems()) {
            if (item.slot() >= 0 && item.slot() < size) {
                inv.setItem(item.slot(), item.stack());
            }
        }

        // Fill empty slots with glass panes for a polished look
        ItemStack filler = buildFiller();
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }

        openMenus.add(player.getUniqueId());
        player.openInventory(inv);
    }

    public void shutdown() {
        // Close all open menus gracefully
        for (UUID uuid : openMenus) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.closeInventory();
        }
        openMenus.clear();
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openMenus.contains(player.getUniqueId())) return;

        event.setCancelled(true); // Never allow item movement inside the menu

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        handleClick(player, event.getSlot(), clicked);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Button definitions — add new buttons here
    // -------------------------------------------------------------------------

    /**
     * Defines every button in the menu.
     * Slot numbering starts at 0 (top-left) and increases left-to-right, top-to-bottom.
     *
     * To add a new page or warp button, append a new MenuItem entry here.
     */
    private List<MenuItem> buildItems() {
        return List.of(
                new MenuItem(
                        10,
                        buildItem(Material.COMPASS,
                                "&a&lワープ",
                                List.of("&7各ワールドへのワープ", "&7(準備中)")),
                        "warp"
                ),
                new MenuItem(
                        12,
                        buildItem(Material.BOOK,
                                "&b&lルールブック",
                                List.of("&7サーバールールを確認する")),
                        "rules"
                ),
                new MenuItem(
                        14,
                        buildItem(Material.PLAYER_HEAD,
                                "&e&lプロフィール",
                                List.of("&7自分の情報を見る")),
                        "profile"
                ),
                new MenuItem(
                        16,
                        buildItem(Material.BARRIER,
                                "&c&lメニューを閉じる",
                                List.of("&7メニューを閉じます")),
                        "close"
                )
        );
    }

    /**
     * Handles a button click based on the button's action ID.
     * Add new cases here when new buttons are added in {@link #buildItems()}.
     */
    private void handleClick(Player player, int slot, ItemStack item) {
        // Resolve action by slot
        buildItems().stream()
                .filter(mi -> mi.slot() == slot)
                .findFirst()
                .ifPresent(mi -> executeAction(player, mi.action()));
    }

    private void executeAction(Player player, String action) {
        switch (action) {
            case "warp"    -> player.sendMessage(Component.text("ワープ機能は準備中です。", NamedTextColor.YELLOW));
            case "rules"   -> {
                player.closeInventory();
                player.sendMessage(Component.text("ルール: https://example.com/rules", NamedTextColor.AQUA));
            }
            case "profile" -> {
                player.closeInventory();
                com.himatsudo.core.modules.ProfileModule pm = plugin.getProfileModule();
                if (pm != null) {
                    // インベントリ close の処理が完了してから新しい UI を開く
                    Bukkit.getScheduler().runTaskLater(plugin, () -> pm.openProfile(player, player), 1L);
                }
            }
            case "close"   -> player.closeInventory();
            default        -> player.sendMessage(Component.text("不明なアクション: " + action, NamedTextColor.RED));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ItemStack buildItem(Material material, String rawName, List<String> rawLore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(rawName)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = rawLore.stream()
                .map(line -> (Component) LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(line)
                        .decoration(TextDecoration.ITALIC, false))
                .toList();
        meta.lore(lore);

        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildFiller() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        return pane;
    }

    // -------------------------------------------------------------------------
    // Data record
    // -------------------------------------------------------------------------

    /**
     * Represents a single menu button.
     *
     * @param slot   inventory slot index
     * @param stack  the ItemStack to display
     * @param action string identifier used in {@link #executeAction}
     */
    private record MenuItem(int slot, ItemStack stack, String action) {}
}
