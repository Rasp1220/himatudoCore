package com.himatsudo.events.treasure;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class TreasureNpcListener implements Listener {

    private final TreasureHuntEvent event;

    public TreasureNpcListener(TreasureHuntEvent event) {
        this.event = event;
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent e) {
        int shopNpcId = event.getPlugin().getConfig().getInt("treasure-hunt.shop-npc-id", -1);
        if (shopNpcId < 0 || e.getNPC().getId() != shopNpcId) return;

        Player player   = e.getClicker();
        int    required = event.getPlugin().getConfig().getInt("treasure-hunt.required-count", 10);
        int    count    = event.getProgressManager().getCount(player.getUniqueId());

        if (!event.getProgressManager().hasUnlocked(player.getUniqueId())) {
            player.sendMessage(Component.text(
                    "まだ宝が足りません！(" + count + "/" + required + "個)",
                    NamedTextColor.RED));
            return;
        }

        new TreasureShopMenu(event, player).open();
    }
}
