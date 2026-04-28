package com.himatsudo.events.merchant;

import com.himatsudo.events.HimatsudoEvents;
import com.himatsudo.events.api.EventModule;

/**
 * Legendary merchant event module.
 *
 * The merchant NPC is spawned manually by an admin at their position.
 * Citizens is required for NPC functionality; the shop GUI works without it
 * (admin can force-open via command for testing).
 */
public class LegendaryMerchantEvent extends EventModule {

    private LegendaryMerchantManager     manager;
    private LegendaryMerchantShopRegistry shopRegistry;

    public LegendaryMerchantEvent(HimatsudoEvents plugin) {
        super(plugin);
    }

    @Override
    public String getId() { return "legendary-merchant"; }

    @Override
    public void onEnable() {
        shopRegistry = new LegendaryMerchantShopRegistry(plugin);

        if (plugin.getServer().getPluginManager().getPlugin("Citizens") != null) {
            manager = new LegendaryMerchantManager(this);
            plugin.getServer().getPluginManager()
                  .registerEvents(new LegendaryMerchantListener(this), plugin);
            plugin.getLogger().info("Citizens 検出。伝説の商人NPC機能を有効化しました。");
        } else {
            plugin.getLogger().warning("Citizens が見つかりません。伝説の商人のNPC機能は無効です。");
        }
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    public LegendaryMerchantManager      getManager()      { return manager; }
    public LegendaryMerchantShopRegistry getShopRegistry() { return shopRegistry; }
}
