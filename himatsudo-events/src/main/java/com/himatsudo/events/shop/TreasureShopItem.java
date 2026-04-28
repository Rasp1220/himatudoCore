package com.himatsudo.events.shop;

import org.bukkit.Material;

import java.util.List;

/** 宝探し限定ショップの商品1点。 */
public record TreasureShopItem(
        String id,
        Material material,
        String displayName,
        List<String> description,
        String command
) {}
