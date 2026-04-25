package com.himatsudo.npc.shop;

import com.himatsudo.npc.currency.TitleManager;
import org.bukkit.entity.Player;

/** 称号を付与する報酬。 */
public record TitleReward(String titleId, String displayName, TitleManager titleManager)
        implements Reward {

    @Override
    public void grant(Player player) {
        titleManager.grantTitle(player.getUniqueId(), titleId);
    }

    @Override
    public String describe() {
        return "&7称号: " + displayName;
    }
}
