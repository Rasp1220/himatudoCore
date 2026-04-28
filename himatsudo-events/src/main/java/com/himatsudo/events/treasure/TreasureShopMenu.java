package com.himatsudo.events.treasure;

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

public class TreasureShopMenu implements ClickableMenu {

    private static final int SLOT_PROGRESS = 49;
    private static final int SLOT_CLOSE    = 53;

    private final TreasureHuntEvent event;
    private final Player player;

    public TreasureShopMenu(TreasureHuntEvent event, Player player) {
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
        attemptClaim(clicker, items.get(slot));
    }

    private void attemptClaim(Player buyer, ShopItem item) {
        TreasureProgressManager pm       = event.getProgressManager();
        int                     required = event.getPlugin().getConfig().getInt("treasure-hunt.required-count", 10);

        if (!pm.hasUnlocked(buyer.getUniqueId())) {
            buyer.sendMessage(Component.text(
                    "まだ宝が足りません！(" + pm.getCount(buyer.getUniqueId()) + "/" + required + "個)",
                    NamedTextColor.RED));
            return;
        }
        if (pm.hasClaimed(buyer.getUniqueId(), item.id())) {
            buyer.sendMessage(Component.text("このアイテムはすでに受け取り済みです。", NamedTextColor.YELLOW));
            return;
        }

        pm.setClaimed(buyer.getUniqueId(), item.id());
        if (!item.command().isEmpty()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    item.command().replace("{player}", buyer.getName()));
        }
        buyer.sendMessage(Component.text(
                item.displayName().replaceAll("&[0-9a-fk-or]", "") + " を受け取りました！",
                NamedTextColor.GREEN));
        Bukkit.getScheduler().runTaskLater(event.getPlugin(), () ->
                new TreasureShopMenu(event, buyer).open(), 1L);
    }

    private Inventory build() {
        Inventory inv = Bukkit.createInventory(null, 54,
                Gui.parse(event.getShopRegistry().getDisplayName()));
        for (int i = 0; i < 54; i++) inv.setItem(i, Gui.filler());

        TreasureProgressManager pm       = event.getProgressManager();
        List<ShopItem>          items    = event.getShopRegistry().getItems();
        boolean                 unlocked = pm.hasUnlocked(player.getUniqueId());
        int required = event.getPlugin().getConfig().getInt("treasure-hunt.required-count", 10);
        int count    = pm.getCount(player.getUniqueId());

        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            ShopItem item    = items.get(i);
            boolean  claimed = pm.hasClaimed(player.getUniqueId(), item.id());
            inv.setItem(i, buildItemStack(item, unlocked, claimed, required));
        }

        inv.setItem(SLOT_PROGRESS, Gui.item(Material.NETHER_STAR, "&e宝探し進捗",
                "&f" + count + "/" + required + " 個収集",
                unlocked ? "&a解放済み！" : "&c残り " + (required - count) + " 個"));
        inv.setItem(SLOT_CLOSE, Gui.item(Material.BARRIER, "&c&l閉じる"));
        return inv;
    }

    private org.bukkit.inventory.ItemStack buildItemStack(ShopItem item,
                                                          boolean unlocked,
                                                          boolean claimed,
                                                          int required) {
        List<String> lore = new ArrayList<>(item.description());
        lore.add("");
        if (claimed) {
            lore.add("&a受け取り済み");
        } else if (!unlocked) {
            lore.add("&c宝を " + required + " 個集めると解放");
        } else {
            lore.add("&e右クリックで受け取る");
        }
        return Gui.item(item.material(),
                (claimed || !unlocked ? "&7" : "") + item.displayName(), lore,
                item.customModelData());
    }
}
