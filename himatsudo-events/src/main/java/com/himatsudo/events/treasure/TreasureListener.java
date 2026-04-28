package com.himatsudo.events.treasure;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

public class TreasureListener implements Listener {

    private final TreasureHuntEvent event;

    public TreasureListener(TreasureHuntEvent event) {
        this.event = event;
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction interaction)) return;

        TreasureSpot spot = event.getTreasureManager().findByInteraction(interaction.getUniqueId());
        if (spot == null) return;

        e.setCancelled(true);
        Player player = e.getPlayer();
        if (!player.hasPermission("hev.use")) return;

        TreasureProgressManager progress = event.getProgressManager();
        if (!progress.collect(player.getUniqueId(), spot.key())) {
            player.sendMessage(Component.text("この宝はすでに収集済みです！", NamedTextColor.YELLOW));
            return;
        }

        int count    = progress.getCount(player.getUniqueId());
        int required = event.getPlugin().getConfig().getInt("treasure-hunt.required-count", 10);

        player.sendMessage(Component.text("宝を発見！ (" + count + "/" + required + ")", NamedTextColor.GOLD));

        if (count >= required) {
            player.sendMessage(Component.text(
                    "おめでとうございます！全ての宝を集めました！限定ショップが解放されました！",
                    NamedTextColor.AQUA));
        }
    }
}
