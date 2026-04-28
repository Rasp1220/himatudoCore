package com.himatsudo.events.merchant;

import com.himatsudo.events.HimatsudoEvents;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;

public class LegendaryMerchantManager {

    private final HimatsudoEvents plugin;
    private NPC npc;
    private BukkitTask despawnTask;
    private BukkitTask timeCheckTask;

    public LegendaryMerchantManager(HimatsudoEvents plugin) {
        this.plugin = plugin;
        startTimeCheckTask();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Spawns the merchant NPC at the given location.
     * @return false if already active
     */
    public boolean spawn(Location location) {
        if (isActive()) return false;

        String rawName = plugin.getConfig().getString(
                "legendary-merchant.npc-name", "&6&l[伝説の商人]");
        String coloredName = rawName.replace('&', '§');

        npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, coloredName);
        npc.spawn(location);

        long durationTicks = plugin.getConfig().getLong(
                "legendary-merchant.duration-seconds", 7200L) * 20L;
        despawnTask = Bukkit.getScheduler().runTaskLater(plugin, () -> despawn(true), durationTicks);

        Bukkit.broadcast(Component.text(
                "伝説の商人が現れた！限定コスチューム素材を販売しています！",
                NamedTextColor.GOLD));
        return true;
    }

    /**
     * Despawns and destroys the merchant NPC.
     * @param broadcast whether to announce the departure to all players
     */
    public void despawn(boolean broadcast) {
        if (despawnTask != null) { despawnTask.cancel(); despawnTask = null; }
        if (npc != null) {
            if (npc.isSpawned()) npc.despawn();
            npc.destroy();
            npc = null;
        }
        if (broadcast) {
            Bukkit.broadcast(Component.text(
                    "伝説の商人は去って行きました...", NamedTextColor.GRAY));
        }
    }

    public boolean isActive() {
        return npc != null && npc.isSpawned();
    }

    public int getNpcId() {
        return npc != null ? npc.getId() : -1;
    }

    public void shutdown() {
        if (timeCheckTask != null) { timeCheckTask.cancel(); timeCheckTask = null; }
        despawn(false);
    }

    // -------------------------------------------------------------------------
    // Time-window enforcement
    // -------------------------------------------------------------------------

    private void startTimeCheckTask() {
        long interval = plugin.getConfig().getLong("legendary-merchant.check-interval", 1200L);
        timeCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (isActive() && !isWithinTimeWindow()) {
                despawn(true);
            }
        }, interval, interval);
    }

    /**
     * Returns true if the current server hour is inside the configured spawn window.
     * Supports wrap-around (e.g. 22–02 spanning midnight).
     */
    public boolean isWithinTimeWindow() {
        int start   = plugin.getConfig().getInt("legendary-merchant.spawn-hour-start", 0);
        int end     = plugin.getConfig().getInt("legendary-merchant.spawn-hour-end", 24);
        int current = LocalTime.now().getHour();
        if (start <= end) {
            return current >= start && current < end;
        }
        return current >= start || current < end;
    }
}
