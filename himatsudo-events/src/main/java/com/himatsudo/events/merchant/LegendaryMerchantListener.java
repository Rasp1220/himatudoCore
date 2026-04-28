package com.himatsudo.events.merchant;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LegendaryMerchantListener implements Listener {

    private final LegendaryMerchantEvent event;

    public LegendaryMerchantListener(LegendaryMerchantEvent event) {
        this.event = event;
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent e) {
        LegendaryMerchantManager manager = event.getManager();
        if (manager == null || !manager.isActive()) return;
        if (e.getNPC().getId() != manager.getNpcId()) return;

        new LegendaryMerchantShopMenu(event, e.getClicker()).open();
    }
}
