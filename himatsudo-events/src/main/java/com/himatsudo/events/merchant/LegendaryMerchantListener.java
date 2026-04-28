package com.himatsudo.events.merchant;

import com.himatsudo.events.HimatsudoEvents;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LegendaryMerchantListener implements Listener {

    private final HimatsudoEvents plugin;

    public LegendaryMerchantListener(HimatsudoEvents plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        LegendaryMerchantManager manager = plugin.getMerchantManager();
        if (!manager.isActive()) return;
        if (event.getNPC().getId() != manager.getNpcId()) return;

        Player player = event.getClicker();
        new LegendaryMerchantShopMenu(plugin, player).open();
    }
}
