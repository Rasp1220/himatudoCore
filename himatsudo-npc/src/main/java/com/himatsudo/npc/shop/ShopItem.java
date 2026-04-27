package com.himatsudo.npc.shop;

import org.bukkit.Material;

import java.util.List;

/**
 * ショップに並ぶ商品1点。
 * 新しい商品は ShopRegistry に追加するだけでよい。
 */
public record ShopItem(
        String id,
        Material material,
        String displayName,
        List<String> description,
        long cost,
        Reward reward
) {}
