package com.himatsudo.events;

import com.himatsudo.events.command.HevCommand;
import com.himatsudo.events.merchant.LegendaryMerchantListener;
import com.himatsudo.events.merchant.LegendaryMerchantManager;
import com.himatsudo.events.merchant.LegendaryMerchantShopMenuManager;
import com.himatsudo.events.merchant.LegendaryMerchantShopRegistry;
import com.himatsudo.events.treasure.TreasureListener;
import com.himatsudo.events.treasure.TreasureManager;
import com.himatsudo.events.treasure.TreasureNpcListener;
import com.himatsudo.events.treasure.TreasureProgressManager;
import com.himatsudo.events.treasure.TreasureShopMenuManager;
import com.himatsudo.events.treasure.TreasureShopRegistry;
import org.bukkit.plugin.java.JavaPlugin;

public final class HimatsudoEvents extends JavaPlugin {

    private TreasureManager              treasureManager;
    private TreasureProgressManager      progressManager;
    private TreasureShopRegistry         shopRegistry;
    private TreasureShopMenuManager      shopMenuManager;

    private LegendaryMerchantManager         merchantManager;
    private LegendaryMerchantShopRegistry    merchantShopRegistry;
    private LegendaryMerchantShopMenuManager merchantShopMenuManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        treasureManager         = new TreasureManager(this);
        progressManager         = new TreasureProgressManager(this);
        shopRegistry            = new TreasureShopRegistry(this);
        shopMenuManager         = new TreasureShopMenuManager();

        merchantShopRegistry    = new LegendaryMerchantShopRegistry(this);
        merchantShopMenuManager = new LegendaryMerchantShopMenuManager();

        getServer().getPluginManager().registerEvents(new TreasureListener(this), this);
        getServer().getPluginManager().registerEvents(shopMenuManager, this);
        getServer().getPluginManager().registerEvents(merchantShopMenuManager, this);

        if (getServer().getPluginManager().getPlugin("Citizens") != null) {
            merchantManager = new LegendaryMerchantManager(this);
            getServer().getPluginManager().registerEvents(new TreasureNpcListener(this), this);
            getServer().getPluginManager().registerEvents(new LegendaryMerchantListener(this), this);
            getLogger().info("Citizens が検出されました。伝説の商人・宝探しNPCリスナーを有効化しました。");
        } else {
            getLogger().warning("Citizens が見つかりません。NPC 機能は無効です。");
        }

        HevCommand cmd = new HevCommand(this);
        getCommand("hev").setExecutor(cmd);
        getCommand("hev").setTabCompleter(cmd);

        treasureManager.spawnAll();

        getLogger().info("HimatsudoEvents enabled.");
    }

    @Override
    public void onDisable() {
        if (merchantManager != null) {
            merchantManager.shutdown();
        }
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

    public TreasureManager              getTreasureManager()        { return treasureManager; }
    public TreasureProgressManager      getProgressManager()        { return progressManager; }
    public TreasureShopRegistry         getShopRegistry()           { return shopRegistry; }
    public TreasureShopMenuManager      getShopMenuManager()        { return shopMenuManager; }

    public LegendaryMerchantManager         getMerchantManager()        { return merchantManager; }
    public LegendaryMerchantShopRegistry    getMerchantShopRegistry()   { return merchantShopRegistry; }
    public LegendaryMerchantShopMenuManager getMerchantShopMenuManager(){ return merchantShopMenuManager; }
}
