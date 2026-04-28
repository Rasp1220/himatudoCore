package com.himatsudo.events.api;

import org.bukkit.Material;

import java.util.List;

/**
 * Shared shop item record used by all event shop registries.
 *
 * {@code costDisplay}    — display-only price text (e.g. "1000コイン"). Empty = not shown.
 * {@code command}        — console command run on purchase; {player} → player name.
 * {@code customModelData}— applied to the icon ItemStack when > 0; 0 = not set.
 */
public record ShopItem(
        String id,
        Material material,
        String displayName,
        List<String> description,
        String costDisplay,
        String command,
        int customModelData
) {}
