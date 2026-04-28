package com.himatsudo.events.treasure;

import com.himatsudo.events.HimatsudoEvents;
import com.himatsudo.events.api.EventModule;

/**
 * Treasure-hunt event module.
 *
 * Encapsulates all treasure-hunt state so HimatsudoEvents.java stays thin.
 * Citizens is a soft dependency: the NPC shop listener is registered only when present.
 */
public class TreasureHuntEvent extends EventModule {

    private TreasureManager         treasureManager;
    private TreasureProgressManager progressManager;
    private TreasureShopRegistry    shopRegistry;

    public TreasureHuntEvent(HimatsudoEvents plugin) {
        super(plugin);
    }

    @Override
    public String getId() { return "treasure-hunt"; }

    @Override
    public void onEnable() {
        treasureManager = new TreasureManager(plugin);
        progressManager = new TreasureProgressManager(plugin);
        shopRegistry    = new TreasureShopRegistry(plugin);

        plugin.getServer().getPluginManager()
              .registerEvents(new TreasureListener(this), plugin);

        if (plugin.getServer().getPluginManager().getPlugin("Citizens") != null) {
            plugin.getServer().getPluginManager()
                  .registerEvents(new TreasureNpcListener(this), plugin);
        }

        treasureManager.spawnAll();
    }

    @Override
    public void onDisable() {
        if (treasureManager != null) treasureManager.removeAll();
        if (progressManager != null) progressManager.save();
    }

    public TreasureManager         getTreasureManager()  { return treasureManager; }
    public TreasureProgressManager getProgressManager()  { return progressManager; }
    public TreasureShopRegistry    getShopRegistry()     { return shopRegistry; }
}
