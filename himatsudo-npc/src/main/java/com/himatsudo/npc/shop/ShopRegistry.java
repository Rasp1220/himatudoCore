package com.himatsudo.npc.shop;

import com.himatsudo.npc.currency.TitleManager;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全ショップの定義を管理する。
 * 新しいショップ・商品はこのクラスに追加する。
 */
public class ShopRegistry {

    private final Map<String, Shop> shops = new HashMap<>();

    public ShopRegistry(TitleManager titleManager) {
        registerDefaultShops(titleManager);
    }

    // -------------------------------------------------------------------------
    // ショップ定義 — ここに追加していく
    // -------------------------------------------------------------------------

    private void registerDefaultShops(TitleManager tm) {
        register(new Shop("titles", "&6&l称号ショップ", List.of(
                new ShopItem(
                        "title_beginner",
                        Material.WOODEN_SWORD,
                        "&7[ビギナー]",
                        List.of("&7プレイ開始の証", "", "&e費用: &f50 ポイント"),
                        50L,
                        new TitleReward("beginner", "&7[ビギナー]", tm)
                ),
                new ShopItem(
                        "title_active",
                        Material.DIAMOND,
                        "&a[アクティブ]",
                        List.of("&7精力的なプレイヤーの証", "", "&e費用: &f200 ポイント"),
                        200L,
                        new TitleReward("active", "&a[アクティブ]", tm)
                ),
                new ShopItem(
                        "title_veteran",
                        Material.NETHERITE_INGOT,
                        "&6[ベテラン]",
                        List.of("&7長年のプレイヤーの証", "", "&e費用: &f1000 ポイント"),
                        1000L,
                        new TitleReward("veteran", "&6[ベテラン]", tm)
                )
        )));
    }

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------

    public void register(Shop shop) {
        shops.put(shop.id(), shop);
    }

    public Shop getShop(String id) {
        return shops.get(id);
    }

    public Map<String, Shop> getAll() {
        return Map.copyOf(shops);
    }
}
