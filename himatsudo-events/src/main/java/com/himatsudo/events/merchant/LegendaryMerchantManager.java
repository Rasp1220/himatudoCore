package com.himatsudo.events.merchant;

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

    private final LegendaryMerchantEvent event;
    private NPC        npc;
    private BukkitTask despawnTask;
    private BukkitTask timeCheckTask;

    public LegendaryMerchantManager(LegendaryMerchantEvent event) {
        this.event = event;
        startTimeCheckTask();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Spawns the merchant at the given location. Returns false if already active. */
    public boolean spawn(Location location) {
        if (isActive()) return false;

        String rawName = event.getPlugin().getConfig()
                .getString("legendary-merchant.npc-name", "&6&l[伝説の商人]");
        npc = CitizensAPI.getNPCRegistry()
                .createNPC(EntityType.VILLAGER, rawName.replace('&', '§'));
        npc.spawn(location);

        long durationTicks = event.getPlugin().getConfig()
                .getLong("legendary-merchant.duration-seconds", 7200L) * 20L;
        despawnTask = Bukkit.getScheduler().runTaskLater(
                event.getPlugin(), () -> despawn(true), durationTicks);

        Bukkit.broadcast(Component.text(
                "伝説の商人が現れた！限定コスチューム素材を販売しています！", NamedTextColor.GOLD));
        return true;
    }

    /** Despawns and destroys the NPC. */
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

    public boolean isActive()  { return npc != null && npc.isSpawned(); }
    public int     getNpcId()  { return npc != null ? npc.getId() : -1; }

    public void shutdown() {
        if (timeCheckTask != null) { timeCheckTask.cancel(); timeCheckTask = null; }
        despawn(false);
    }

    /** True if the current server hour is within the configured spawn window. */
    public boolean isWithinTimeWindow() {
        int start   = event.getPlugin().getConfig().getInt("legendary-merchant.spawn-hour-start", 0);
        int end     = event.getPlugin().getConfig().getInt("legendary-merchant.spawn-hour-end", 24);
        int current = LocalTime.now().getHour();
        return start <= end
                ? current >= start && current < end
                : current >= start || current < end;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void startTimeCheckTask() {
        long interval = event.getPlugin().getConfig()
                .getLong("legendary-merchant.check-interval", 1200L);
        timeCheckTask = Bukkit.getScheduler().runTaskTimer(event.getPlugin(), () -> {
            if (isActive() && !isWithinTimeWindow()) despawn(true);
        }, interval, interval);
    }
}
