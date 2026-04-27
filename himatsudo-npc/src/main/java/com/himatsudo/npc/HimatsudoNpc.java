package com.himatsudo.npc;

import com.himatsudo.npc.action.NpcActionFactory;
import com.himatsudo.npc.action.ServerTransferAction;
import com.himatsudo.npc.action.ShopAction;
import com.himatsudo.npc.command.HnpcCommand;
import com.himatsudo.npc.currency.CurrencyManager;
import com.himatsudo.npc.currency.TitleManager;
import com.himatsudo.npc.npc.NpcListener;
import com.himatsudo.npc.npc.NpcManager;
import com.himatsudo.npc.shop.ShopMenuManager;
import com.himatsudo.npc.shop.ShopRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class HimatsudoNpc extends JavaPlugin {

    private CurrencyManager  currencyManager;
    private TitleManager     titleManager;
    private ShopRegistry     shopRegistry;
    private NpcActionFactory actionFactory;
    private NpcManager       npcManager;
    private ShopMenuManager  shopMenuManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        currencyManager  = new CurrencyManager(this);
        titleManager     = new TitleManager(this);
        shopRegistry     = new ShopRegistry(this, titleManager);
        actionFactory    = buildActionFactory();
        npcManager       = new NpcManager(this);
        shopMenuManager  = new ShopMenuManager();

        getServer().getPluginManager().registerEvents(new NpcListener(this), this);
        getServer().getPluginManager().registerEvents(shopMenuManager, this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        HnpcCommand cmd = new HnpcCommand(this);
        getCommand("hnpc").setExecutor(cmd);
        getCommand("hnpc").setTabCompleter(cmd);

        startCurrencyTask();

        getLogger().info("HimatsudoNpc enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
        getLogger().info("HimatsudoNpc disabled.");
    }

    // -------------------------------------------------------------------------
    // Factory setup — 新しい NPC タイプはここに追加する
    // -------------------------------------------------------------------------

    private NpcActionFactory buildActionFactory() {
        NpcActionFactory factory = new NpcActionFactory();
        factory.register(ServerTransferAction.TYPE, (plugin, section) ->
                new ServerTransferAction(plugin, section.getString("server", "")));
        factory.register(ShopAction.TYPE, (plugin, section) ->
                new ShopAction(plugin, section.getString("shop-id", "")));
        return factory;
    }

    // -------------------------------------------------------------------------
    // Currency accumulation task
    // -------------------------------------------------------------------------

    private void startCurrencyTask() {
        long interval = getConfig().getLong("currency.interval-ticks", 1200L);
        long reward   = getConfig().getLong("currency.points-per-interval", 1L);

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                currencyManager.add(player.getUniqueId(), reward);
            }
        }, interval, interval);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public CurrencyManager  getCurrencyManager()  { return currencyManager; }
    public TitleManager     getTitleManager()     { return titleManager; }
    public ShopRegistry     getShopRegistry()     { return shopRegistry; }
    public NpcActionFactory getActionFactory()    { return actionFactory; }
    public NpcManager       getNpcManager()       { return npcManager; }
    public ShopMenuManager  getShopMenuManager()  { return shopMenuManager; }
}
