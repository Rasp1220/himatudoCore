package com.himatsudo.events.merchant;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LegendaryMerchantShopMenuManager implements Listener {

    private final Map<UUID, LegendaryMerchantShopMenu> openMenus = new HashMap<>();

    public void track(Player player, LegendaryMerchantShopMenu menu) {
        openMenus.put(player.getUniqueId(), menu);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        LegendaryMerchantShopMenu menu = openMenus.get(player.getUniqueId());
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
