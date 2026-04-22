package com.himatsudo.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Shared Adventure / Legacy formatting utilities.
 * All modules use the same serializer configuration, so helpers live here.
 */
public final class Fmt {

    private static final LegacyComponentSerializer AMP =
            LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECT =
            LegacyComponentSerializer.legacySection();

    private Fmt() {}

    /** Parses &-color codes into a Component. */
    public static Component parse(String raw) {
        return AMP.deserialize(raw);
    }

    /**
     * Parses &-color codes and returns the §-coded string.
     * Used as score-entry keys in the Scoreboard API.
     */
    public static String toSectionKey(String raw) {
        return SECT.serialize(AMP.deserialize(raw));
    }

    /**
     * Parses &-color codes and removes italic — suitable for item
     * display names and lore lines.
     */
    public static Component tooltip(String raw) {
        return AMP.deserialize(raw)
                .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
