package com.himatsudo.events.api;

import org.bukkit.Material;

import java.util.List;

/**
 * Shared shop item record used by all event shop registries.
 *
 * {@code costDisplay} is display-only text (e.g. "1000コイン").
 * Actual currency deduction must be handled inside {@code command}.
 * Leave {@code costDisplay} empty when not applicable.
 */
public record ShopItem(
        String id,
        Material material,
        String displayName,
        List<String> description,
        String costDisplay,
        String command
) {}
