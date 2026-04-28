package com.himatsudo.events.api;

import com.himatsudo.events.HimatsudoEvents;

/**
 * Base class for all events managed by HimatsudoEvents.
 *
 * Adding a new event:
 *   1. Create a package under com.himatsudo.events.<event-name>/
 *   2. Extend this class, implement getId() / onEnable() / onDisable()
 *   3. Call plugin.registerEvent(new MyEvent(plugin)) in HimatsudoEvents.onEnable()
 */
public abstract class EventModule {

    protected final HimatsudoEvents plugin;

    protected EventModule(HimatsudoEvents plugin) {
        this.plugin = plugin;
    }

    public abstract String getId();

    public abstract void onEnable();

    public abstract void onDisable();

    public HimatsudoEvents getPlugin() { return plugin; }
}
