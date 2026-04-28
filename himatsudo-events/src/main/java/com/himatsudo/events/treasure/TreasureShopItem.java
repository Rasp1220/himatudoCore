package com.himatsudo.events.treasure;

import org.bukkit.Material;

import java.util.List;

public record TreasureShopItem(
        String id,
        Material material,
        String displayName,
        List<String> description,
        String command
) {}
