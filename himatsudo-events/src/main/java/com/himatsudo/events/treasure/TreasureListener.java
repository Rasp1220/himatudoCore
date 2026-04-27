package com.himatsudo.events.treasure;

import com.himatsudo.events.HimatsudoEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

/** Interaction エンティティへの右クリックを検出して宝収集処理を行う。 */
public class TreasureListener implements Listener {

    private final HimatsudoEvents plugin;

    public TreasureListener(HimatsudoEvents plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;

        TreasureSpot spot = plugin.getTreasureManager().findByInteraction(interaction.getUniqueId());
        if (spot == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!player.hasPermission("hev.use")) return;

        TreasureProgressManager progress = plugin.getProgressManager();

        if (!progress.collect(player.getUniqueId(), spot.key())) {
            player.sendMessage(Component.text("この宝はすでに収集済みです！", NamedTextColor.YELLOW));
            return;
        }

        int count    = progress.getCount(player.getUniqueId());
        int required = plugin.getConfig().getInt("treasure-hunt.required-count", 10);

        player.sendMessage(Component.text(
                "宝を発見！ (" + count + "/" + required + ")",
                NamedTextColor.GOLD));

        if (count >= required) {
            player.sendMessage(Component.text(
                    "おめでとうございます！全ての宝を集めました！限定ショップが解放されました！",
                    NamedTextColor.AQUA));
        }
    }
}
