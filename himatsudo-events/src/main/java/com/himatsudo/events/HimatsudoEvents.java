package com.himatsudo.events;

import com.himatsudo.events.command.HevCommand;
import com.himatsudo.events.npc.TreasureNpcListener;
import com.himatsudo.events.shop.TreasureShopMenuManager;
import com.himatsudo.events.shop.TreasureShopRegistry;
import com.himatsudo.events.treasure.TreasureListener;
import com.himatsudo.events.treasure.TreasureManager;
import com.himatsudo.events.treasure.TreasureProgressManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class HimatsudoEvents extends JavaPlugin {

    private TreasureManager       treasureManager;
    private TreasureProgressManager progressManager;
    private TreasureShopRegistry  shopRegistry;
    private TreasureShopMenuManager shopMenuManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        treasureManager  = new TreasureManager(this);
        progressManager  = new TreasureProgressManager(this);
        shopRegistry     = new TreasureShopRegistry(this);
        shopMenuManager  = new TreasureShopMenuManager();

        getServer().getPluginManager().registerEvents(new TreasureListener(this), this);
        getServer().getPluginManager().registerEvents(shopMenuManager, this);

        if (getServer().getPluginManager().getPlugin("Citizens") != null) {
            getServer().getPluginManager().registerEvents(new TreasureNpcListener(this), this);
            getLogger().info("Citizens が検出されました。宝探しNPCリスナーを有効化しました。");
        }

        HevCommand cmd = new HevCommand(this);
        getCommand("hev").setExecutor(cmd);
        getCommand("hev").setTabCompleter(cmd);

        treasureManager.spawnAll();

        getLogger().info("HimatsudoEvents enabled.");
    }

    @Override
    public void onDisable() {
        if (treasureManager != null) {
            treasureManager.removeAll();
        }
        if (progressManager != null) {
            progressManager.save();
        }
        getLogger().info("HimatsudoEvents disabled.");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public TreasureManager        getTreasureManager()  { return treasureManager; }
    public TreasureProgressManager getProgressManager() { return progressManager; }
    public TreasureShopRegistry   getShopRegistry()     { return shopRegistry; }
    public TreasureShopMenuManager getShopMenuManager() { return shopMenuManager; }
}
