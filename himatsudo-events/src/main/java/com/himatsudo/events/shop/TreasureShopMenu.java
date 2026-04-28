package com.himatsudo.events.shop;

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
 * 宝探し限定ショップの GUI (54スロット)。
 *
 * レイアウト:
 *   スロット 0-44 : 限定アイテム (最大45品)
 *   スロット 45-48: フィラー
 *   スロット 49   : 進捗表示
 *   スロット 50-52: フィラー
 *   スロット 53   : 閉じるボタン
 */
public class TreasureShopMenu {

    static final int SLOT_PROGRESS = 49;
    static final int SLOT_CLOSE    = 53;

    private final HimatsudoEvents plugin;
    private final Player player;

    public TreasureShopMenu(HimatsudoEvents plugin, Player player) {
        this.plugin  = plugin;
        this.player  = player;
    }

    public void open() {
        Inventory inventory = build();
        player.openInventory(inventory);
        plugin.getShopMenuManager().track(player, this);
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    public void handleClick(int slot, Player clicker) {
        if (slot == SLOT_CLOSE) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> clicker.closeInventory(), 1L);
            return;
        }
        List<TreasureShopItem> items = plugin.getShopRegistry().getItems();
        if (slot >= items.size()) return;
        attemptClaim(clicker, items.get(slot));
    }

    // -------------------------------------------------------------------------
    // Claim logic
    // -------------------------------------------------------------------------

    private void attemptClaim(Player buyer, TreasureShopItem item) {
        if (!plugin.getProgressManager().hasUnlocked(buyer.getUniqueId())) {
            int required = plugin.getConfig().getInt("treasure-hunt.required-count", 10);
            int count    = plugin.getProgressManager().getCount(buyer.getUniqueId());
            buyer.sendMessage(Component.text(
                    "まだ宝が足りません！(" + count + "/" + required + "個)",
                    NamedTextColor.RED));
            return;
        }

        if (plugin.getProgressManager().hasClaimed(buyer.getUniqueId(), item.id())) {
            buyer.sendMessage(Component.text("このアイテムはすでに受け取り済みです。", NamedTextColor.YELLOW));
            return;
        }

        plugin.getProgressManager().setClaimed(buyer.getUniqueId(), item.id());

        if (!item.command().isEmpty()) {
            String cmd = item.command().replace("{player}", buyer.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        buyer.sendMessage(Component.text(
                item.displayName().replaceAll("&[0-9a-fk-or]", "") + " を受け取りました！",
                NamedTextColor.GREEN));

        Bukkit.getScheduler().runTaskLater(plugin, () ->
                new TreasureShopMenu(plugin, buyer).open(), 1L);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    private Inventory build() {
        Inventory inv = Bukkit.createInventory(null, 54,
                Gui.parse(plugin.getShopRegistry().getDisplayName()));

        for (int i = 0; i < 54; i++) inv.setItem(i, Gui.filler());

        List<TreasureShopItem> items = plugin.getShopRegistry().getItems();
        boolean unlocked = plugin.getProgressManager().hasUnlocked(player.getUniqueId());

        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            TreasureShopItem item    = items.get(i);
            boolean          claimed = plugin.getProgressManager().hasClaimed(player.getUniqueId(), item.id());
            inv.setItem(i, buildItemStack(item, unlocked, claimed));
        }

        int required = plugin.getConfig().getInt("treasure-hunt.required-count", 10);
        int count    = plugin.getProgressManager().getCount(player.getUniqueId());
        inv.setItem(SLOT_PROGRESS, Gui.item(
                Material.NETHER_STAR,
                "&e宝探し進捗",
                "&f" + count + "/" + required + " 個収集",
                unlocked ? "&a解放済み！" : "&c残り " + (required - count) + " 個"));

        inv.setItem(SLOT_CLOSE, Gui.item(Material.BARRIER, "&c&l閉じる"));
        return inv;
    }

    private org.bukkit.inventory.ItemStack buildItemStack(TreasureShopItem item,
                                                          boolean unlocked,
                                                          boolean claimed) {
        List<String> lore = new ArrayList<>(item.description());
        lore.add("");
        if (claimed) {
            lore.add("&a受け取り済み");
        } else if (!unlocked) {
            int required = plugin.getConfig().getInt("treasure-hunt.required-count", 10);
            lore.add("&c宝を " + required + " 個集めると解放");
        } else {
            lore.add("&e右クリックで受け取る");
        }

        String prefix = (claimed || !unlocked) ? "&7" : "";
        return Gui.item(item.material(), prefix + item.displayName(), lore);
    }
}
