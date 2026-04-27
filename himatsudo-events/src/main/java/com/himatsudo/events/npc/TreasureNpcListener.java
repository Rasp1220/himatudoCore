package com.himatsudo.events.npc;

import com.himatsudo.events.HimatsudoEvents;
import com.himatsudo.events.shop.TreasureShopMenu;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Citizens NPC への右クリックを検出して宝探し限定ショップを開く。
 * config.yml の treasure-hunt.shop-npc-id で対象NPC IDを指定する。
 * Citizens が存在しない場合はこのリスナーは登録されない。
 */
public class TreasureNpcListener implements Listener {

    private final HimatsudoEvents plugin;

    public TreasureNpcListener(HimatsudoEvents plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        int shopNpcId = plugin.getConfig().getInt("treasure-hunt.shop-npc-id", -1);
        if (shopNpcId < 0 || event.getNPC().getId() != shopNpcId) return;

        Player player   = event.getClicker();
        int    required = plugin.getConfig().getInt("treasure-hunt.required-count", 10);
        int    count    = plugin.getProgressManager().getCount(player.getUniqueId());

        if (!plugin.getProgressManager().hasUnlocked(player.getUniqueId())) {
            player.sendMessage(Component.text(
                    "まだ宝が足りません！(" + count + "/" + required + "個)",
                    NamedTextColor.RED));
            return;
        }

        new TreasureShopMenu(plugin, player).open();
    }
}
