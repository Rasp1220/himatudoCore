package com.himatsudo.events.merchant;

import com.himatsudo.events.HimatsudoEvents;
import com.himatsudo.events.util.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 伝説の商人ショップ GUI (54スロット)
 *
 * Layout:
 *   Slot  0-44 : shop items (max 45)
 *   Slot 45-52 : filler
 *   Slot 53    : close button
 */
public class LegendaryMerchantShopMenu {

    static final int SLOT_CLOSE = 53;

    private final HimatsudoEvents plugin;
    private final Player player;

    public LegendaryMerchantShopMenu(HimatsudoEvents plugin, Player player) {
        this.plugin  = plugin;
        this.player  = player;
    }

    public void open() {
        player.openInventory(build());
        plugin.getMerchantShopMenuManager().track(player, this);
    }

    public void handleClick(int slot, Player clicker) {
        if (slot == SLOT_CLOSE) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> clicker.closeInventory(), 1L);
            return;
        }
        List<LegendaryMerchantItem> items = plugin.getMerchantShopRegistry().getItems();
        if (slot >= items.size()) return;
        purchase(clicker, items.get(slot));
    }

    private void purchase(Player buyer, LegendaryMerchantItem item) {
        if (!item.command().isEmpty()) {
            String cmd = item.command().replace("{player}", buyer.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        String plain = item.displayName().replaceAll("&[0-9a-fk-or]", "");
        buyer.sendMessage(Component.text(plain + " を購入しました！", NamedTextColor.GOLD));

        Bukkit.getScheduler().runTaskLater(plugin, () ->
                new LegendaryMerchantShopMenu(plugin, buyer).open(), 1L);
    }

    private Inventory build() {
        Inventory inv = Bukkit.createInventory(null, 54,
                Gui.parse(plugin.getMerchantShopRegistry().getDisplayName()));

        for (int i = 0; i < 54; i++) inv.setItem(i, Gui.filler());

        List<LegendaryMerchantItem> items = plugin.getMerchantShopRegistry().getItems();
        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            inv.setItem(i, buildItemStack(items.get(i)));
        }

        inv.setItem(SLOT_CLOSE, Gui.item(Material.BARRIER, "&c&l閉じる"));
        return inv;
    }

    private org.bukkit.inventory.ItemStack buildItemStack(LegendaryMerchantItem item) {
        List<String> lore = new ArrayList<>(item.description());
        if (!item.costDisplay().isEmpty()) {
            lore.add("");
            lore.add("&6価格: " + item.costDisplay());
        }
        lore.add("");
        lore.add("&eクリックで購入");
        return Gui.item(item.material(), item.displayName(), lore);
    }
}
