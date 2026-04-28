package com.himatsudo.events.api;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Single global inventory click/close handler shared by all event shop menus.
 * Registered once in HimatsudoEvents; each menu registers itself via {@link #track}.
 */
public class ShopMenuManager implements Listener {

    private final Map<UUID, ClickableMenu> openMenus = new HashMap<>();

    public void track(Player player, ClickableMenu menu) {
        openMenus.put(player.getUniqueId(), menu);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ClickableMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        menu.handleClick(event.getSlot(), player);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }
}
