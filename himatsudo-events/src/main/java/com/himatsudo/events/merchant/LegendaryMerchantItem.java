package com.himatsudo.events.merchant;

import org.bukkit.Material;

import java.util.List;

public record LegendaryMerchantItem(
        String id,
        Material material,
        String displayName,
        List<String> description,
        String costDisplay,
        String command
) {}
