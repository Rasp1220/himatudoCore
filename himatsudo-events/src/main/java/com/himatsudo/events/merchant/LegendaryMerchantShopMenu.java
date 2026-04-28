package com.himatsudo.events.merchant;

import com.himatsudo.events.api.ClickableMenu;
import com.himatsudo.events.api.ShopItem;
import com.himatsudo.events.util.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

public class LegendaryMerchantShopMenu implements ClickableMenu {

    private static final int SLOT_CLOSE = 53;

    private final LegendaryMerchantEvent event;
    private final Player player;

    public LegendaryMerchantShopMenu(LegendaryMerchantEvent event, Player player) {
        this.event  = event;
        this.player = player;
    }

    public void open() {
        player.openInventory(build());
        event.getPlugin().getShopMenuManager().track(player, this);
    }

    @Override
    public void handleClick(int slot, Player clicker) {
        if (slot == SLOT_CLOSE) {
            Bukkit.getScheduler().runTaskLater(event.getPlugin(), () -> clicker.closeInventory(), 1L);
            return;
        }
        List<ShopItem> items = event.getShopRegistry().getItems();
        if (slot >= items.size()) return;
        purchase(clicker, items.get(slot));
    }

    private void purchase(Player buyer, ShopItem item) {
        if (!item.command().isEmpty()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    item.command().replace("{player}", buyer.getName()));
        }
        buyer.sendMessage(Component.text(
                item.displayName().replaceAll("&[0-9a-fk-or]", "") + " を購入しました！",
                NamedTextColor.GOLD));
        Bukkit.getScheduler().runTaskLater(event.getPlugin(), () ->
                new LegendaryMerchantShopMenu(event, buyer).open(), 1L);
    }

    private Inventory build() {
        Inventory inv = Bukkit.createInventory(null, 54,
                Gui.parse(event.getShopRegistry().getDisplayName()));
        for (int i = 0; i < 54; i++) inv.setItem(i, Gui.filler());

        List<ShopItem> items = event.getShopRegistry().getItems();
        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            inv.setItem(i, buildItemStack(items.get(i)));
        }
        inv.setItem(SLOT_CLOSE, Gui.item(Material.BARRIER, "&c&l閉じる"));
        return inv;
    }

    private org.bukkit.inventory.ItemStack buildItemStack(ShopItem item) {
        List<String> lore = new ArrayList<>(item.description());
        if (!item.costDisplay().isEmpty()) {
            lore.add("");
            lore.add("&6価格: " + item.costDisplay());
        }
        lore.add("");
        lore.add("&eクリックで購入");
        return Gui.item(item.material(), item.displayName(), lore, item.customModelData());
    }
}
