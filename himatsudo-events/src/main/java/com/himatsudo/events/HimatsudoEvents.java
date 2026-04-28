package com.himatsudo.events;

import com.himatsudo.events.api.EventModule;
import com.himatsudo.events.api.ShopMenuManager;
import com.himatsudo.events.command.HevCommand;
import com.himatsudo.events.merchant.LegendaryMerchantEvent;
import com.himatsudo.events.treasure.TreasureHuntEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * HimatsudoEvents — periodic and limited-time event host.
 *
 * Adding a new event:
 *   1. Create com.himatsudo.events.<name>/ package
 *   2. Extend EventModule, implement getId() / onEnable() / onDisable()
 *   3. Call registerEvent(new MyEvent(this)) below
 */
public final class HimatsudoEvents extends JavaPlugin {

    private ShopMenuManager       shopMenuManager;
    private final List<EventModule> eventModules = new ArrayList<>();

    private TreasureHuntEvent      treasureHuntEvent;
    private LegendaryMerchantEvent legendaryMerchantEvent;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        shopMenuManager = new ShopMenuManager();
        getServer().getPluginManager().registerEvents(shopMenuManager, this);

        registerEvent(treasureHuntEvent      = new TreasureHuntEvent(this));
        registerEvent(legendaryMerchantEvent = new LegendaryMerchantEvent(this));

        HevCommand cmd = new HevCommand(this);
        getCommand("hev").setExecutor(cmd);
        getCommand("hev").setTabCompleter(cmd);

        getLogger().info("HimatsudoEvents enabled. " + eventModules.size() + " event(s) loaded.");
    }

    /**
     * Registers and enables one event module.
     * A module that throws during onEnable() is skipped without crashing the plugin.
     */
    public void registerEvent(EventModule module) {
        try {
            module.onEnable();
            eventModules.add(module);
            getLogger().info("[Events] " + module.getId() + " loaded.");
        } catch (Exception e) {
            getLogger().warning("[Events] Failed to load " + module.getId() + ": " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        for (int i = eventModules.size() - 1; i >= 0; i--) {
            try { eventModules.get(i).onDisable(); } catch (Exception ignored) {}
        }
        getLogger().info("HimatsudoEvents disabled.");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public ShopMenuManager        getShopMenuManager()        { return shopMenuManager; }
    public TreasureHuntEvent      getTreasureHuntEvent()      { return treasureHuntEvent; }
    public LegendaryMerchantEvent getLegendaryMerchantEvent() { return legendaryMerchantEvent; }
}
