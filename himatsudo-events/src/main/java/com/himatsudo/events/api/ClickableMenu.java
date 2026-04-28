package com.himatsudo.events.api;

import org.bukkit.entity.Player;

/** Implemented by any GUI that handles inventory clicks via {@link ShopMenuManager}. */
public interface ClickableMenu {
    void handleClick(int slot, Player player);
}
