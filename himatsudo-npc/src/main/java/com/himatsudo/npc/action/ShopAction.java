package com.himatsudo.npc.action;

import com.himatsudo.npc.HimatsudoNpc;
import com.himatsudo.npc.shop.Shop;
import com.himatsudo.npc.shop.ShopMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** 右クリックでショップ GUI を開く。 */
public class ShopAction implements NpcAction {

    public static final String TYPE = "shop";

    private final HimatsudoNpc plugin;
    private final String shopId;

    public ShopAction(HimatsudoNpc plugin, String shopId) {
        this.plugin = plugin;
        this.shopId = shopId;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void execute(Player player) {
        Shop shop = plugin.getShopRegistry().getShop(shopId);
        if (shop == null) {
            player.sendMessage(Component.text("ショップが見つかりません: " + shopId, NamedTextColor.RED));
            return;
        }
        new ShopMenu(plugin, shop, player).open();
    }

    public String getShopId() {
        return shopId;
    }
}
