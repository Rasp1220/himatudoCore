package com.himatsudo.core;

import com.himatsudo.core.commands.MainCommand;
import com.himatsudo.core.commands.MenuCommand;
import com.himatsudo.core.modules.AfkModule;
import com.himatsudo.core.modules.AnnounceModule;
import com.himatsudo.core.modules.BoardModule;
import com.himatsudo.core.modules.ChatModule;
import com.himatsudo.core.modules.DiscordModule;
import com.himatsudo.core.modules.JoinMessageModule;
import com.himatsudo.core.modules.MenuModule;
import com.himatsudo.core.modules.TabModule;
import com.himatsudo.core.modules.TextModule;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * HimatsudoCore - Modular core plugin for Himatsudo server.
 *
 * Each module is loaded independently so that a failure in one module
 * does not prevent other modules from functioning.
 */
public final class HimatsudoCore extends JavaPlugin {

    private DiscordModule     discordModule;
    private AnnounceModule    announceModule;
    private BoardModule       boardModule;
    private MenuModule        menuModule;
    private AfkModule         afkModule;
    private TextModule        textModule;
    private JoinMessageModule joinMessageModule;
    private ChatModule        chatModule;
    private TabModule         tabModule;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        loadModules();
        registerCommands();

        getLogger().info("HimatsudoCore enabled successfully.");
    }

    @Override
    public void onDisable() {
        unloadModules();
        getLogger().info("HimatsudoCore disabled.");
    }

    // -------------------------------------------------------------------------
    // Module lifecycle
    // -------------------------------------------------------------------------

    private void loadModules() {
        discordModule  = loadModule("DiscordModule",  () -> new DiscordModule(this));
        announceModule = loadModule("AnnounceModule", () -> new AnnounceModule(this));
        boardModule    = loadModule("BoardModule",    () -> new BoardModule(this));
        menuModule     = loadModule("MenuModule",     () -> new MenuModule(this));
        // AfkModule は BoardModule の後にロード (nametag 同期のため)
        afkModule         = loadModule("AfkModule",         () -> new AfkModule(this));
        textModule        = loadModule("TextModule",        () -> new TextModule(this));
        joinMessageModule = loadModule("JoinMessageModule", () -> new JoinMessageModule(this));
        chatModule        = loadModule("ChatModule",        () -> new ChatModule(this));
        // TabModule は ChatModule・AfkModule の後にロード (両方に依存するため)
        tabModule         = loadModule("TabModule",         () -> new TabModule(this));
    }

    /**
     * Generic loader that catches any exception thrown during module
     * initialisation so one bad module cannot kill the whole plugin.
     */
    private <T> T loadModule(String name, ModuleFactory<T> factory) {
        try {
            T module = factory.create();
            getLogger().info("[" + name + "] loaded.");
            return module;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "[" + name + "] failed to load — other modules will continue running.", e);
            return null;
        }
    }

    private void unloadModules() {
        if (tabModule         != null) tabModule.shutdown();
        if (afkModule         != null) afkModule.shutdown();
        if (textModule        != null) textModule.shutdown();
        if (joinMessageModule != null) joinMessageModule.shutdown();
        if (chatModule        != null) chatModule.shutdown();
        if (announceModule    != null) announceModule.shutdown();
        if (boardModule       != null) boardModule.shutdown();
        if (discordModule     != null) discordModule.shutdown();
        if (menuModule        != null) menuModule.shutdown();
    }

    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------

    private void registerCommands() {
        getCommand("hc").setExecutor(new MainCommand(this));
        getCommand("menu").setExecutor(new MenuCommand(this));
    }

    // -------------------------------------------------------------------------
    // Public accessors (for cross-module use if needed)
    // -------------------------------------------------------------------------

    public DiscordModule     getDiscordModule()     { return discordModule; }
    public AnnounceModule    getAnnounceModule()    { return announceModule; }
    public BoardModule       getBoardModule()       { return boardModule; }
    public MenuModule        getMenuModule()        { return menuModule; }
    public AfkModule         getAfkModule()         { return afkModule; }
    public TextModule        getTextModule()        { return textModule; }
    public JoinMessageModule getJoinMessageModule() { return joinMessageModule; }
    public ChatModule        getChatModule()        { return chatModule; }
    public TabModule         getTabModule()         { return tabModule; }

    // -------------------------------------------------------------------------
    // Internal helper
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface ModuleFactory<T> {
        T create() throws Exception;
    }
}
