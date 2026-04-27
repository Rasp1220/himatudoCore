package com.himatsudo.npc.npc;

import com.himatsudo.npc.HimatsudoNpc;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Citizens の右クリックイベントを受け取り NpcManager へ委譲する。 */
public class NpcListener implements Listener {

    private final HimatsudoNpc plugin;

    public NpcListener(HimatsudoNpc plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        if (!player.hasPermission("hnpc.use")) return;

        int npcId = event.getNPC().getId();
        plugin.getNpcManager().dispatch(npcId, player);
    }
}
