package com.himatsudo.npc.shop;

import com.himatsudo.npc.HimatsudoNpc;
import com.himatsudo.npc.currency.CurrencyManager;
import com.himatsudo.npc.currency.TitleManager;
import com.himatsudo.npc.util.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * ショップの在庫 GUI (54スロット)。
 *
 * レイアウト:
 *   スロット 0-44  : 商品 (最大45品)
 *   スロット 45-48 : フィラー
 *   スロット 49    : 所持ポイント表示
 *   スロット 50-52 : フィラー
 *   スロット 53    : 閉じるボタン
 */
public class ShopMenu {

    static final int SLOT_BALANCE = 49;
    static final int SLOT_CLOSE   = 53;

    private final HimatsudoNpc plugin;
    private final Shop shop;
    private final Player player;
    private Inventory inventory;

    public ShopMenu(HimatsudoNpc plugin, Shop shop, Player player) {
        this.plugin = plugin;
        this.shop   = shop;
        this.player = player;
    }

    public void open() {
        inventory = build();
        player.openInventory(inventory);
        plugin.getShopMenuManager().track(player, this);
    }

    public void refresh() {
        // 残高変動後にメニューを再構築
        open();
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    public void handleClick(int slot, Player clicker) {
        if (slot == SLOT_CLOSE) {
            Bukkit.getScheduler().runTaskLater(plugin, clicker::closeInventory, 1L);
            return;
        }

        List<ShopItem> items = shop.items();
        if (slot >= items.size()) return;

        ShopItem item = items.get(slot);
        attemptPurchase(clicker, item);
    }

    // -------------------------------------------------------------------------
    // Purchase logic
    // -------------------------------------------------------------------------

    private void attemptPurchase(Player buyer, ShopItem item) {
        CurrencyManager currency = plugin.getCurrencyManager();
        TitleManager    titles   = plugin.getTitleManager();

        // 称号系: 既に所持しているか確認
        if (item.reward() instanceof TitleReward tr) {
            if (titles.hasTitle(buyer.getUniqueId(), tr.titleId())) {
                buyer.sendMessage(Component.text("既にこの称号を所持しています。", NamedTextColor.YELLOW));
                return;
            }
        }

        long balance = currency.getBalance(buyer.getUniqueId());
        if (balance < item.cost()) {
            buyer.sendMessage(Component.text(
                    "ポイントが不足しています。(必要: " + item.cost() + "  所持: " + balance + ")",
                    NamedTextColor.RED));
            return;
        }

        currency.deduct(buyer.getUniqueId(), item.cost());
        item.reward().grant(buyer);
        buyer.sendMessage(Component.text(
                item.displayName().replaceAll("&[0-9a-fk-or]", "") + " を購入しました！",
                NamedTextColor.GREEN));

        // メニューを更新して残高を反映
        Bukkit.getScheduler().runTaskLater(plugin, this::refresh, 1L);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    private Inventory build() {
        Inventory inv = Bukkit.createInventory(null, 54, Gui.parse(shop.displayName()));

        // フィラー
        for (int i = 0; i < 54; i++) inv.setItem(i, Gui.filler());

        // 商品
        List<ShopItem> items = shop.items();
        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            inv.setItem(i, buildItemStack(items.get(i)));
        }

        // 残高
        long balance = plugin.getCurrencyManager().getBalance(player.getUniqueId());
        inv.setItem(SLOT_BALANCE, Gui.item(
                Material.GOLD_INGOT,
                "&e所持ポイント",
                "&f" + balance + " &7ポイント"));

        // 閉じる
        inv.setItem(SLOT_CLOSE, Gui.item(Material.BARRIER, "&c&l閉じる"));

        return inv;
    }

    private org.bukkit.inventory.ItemStack buildItemStack(ShopItem item) {
        TitleManager titles = plugin.getTitleManager();
        boolean owned = (item.reward() instanceof TitleReward tr)
                && titles.hasTitle(player.getUniqueId(), tr.titleId());

        List<String> lore = new ArrayList<>(item.description());
        lore.add("");
        if (owned) {
            lore.add("&a✔ 所持済み");
        } else {
            lore.add("&e費用&8: &f" + item.cost() + " ポイント");
            lore.add("&7クリックで購入");
        }

        return Gui.item(item.material(), (owned ? "&7" : "") + item.displayName(), lore);
    }
}
