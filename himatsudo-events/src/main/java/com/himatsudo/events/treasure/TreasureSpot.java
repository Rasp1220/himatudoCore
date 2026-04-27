package com.himatsudo.events.treasure;

import org.bukkit.Location;

import java.util.UUID;

/** 宝の設置情報を保持するレコード。key は場所を一意に識別する文字列。 */
public record TreasureSpot(
        String key,
        Location location,
        UUID displayEntityId,
        UUID interactionEntityId
) {
    public static String keyOf(Location loc) {
        return loc.getWorld().getName()
                + "," + loc.getBlockX()
                + "," + loc.getBlockY()
                + "," + loc.getBlockZ();
    }
}
