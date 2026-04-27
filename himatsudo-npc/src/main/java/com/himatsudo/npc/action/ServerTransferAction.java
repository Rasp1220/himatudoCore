package com.himatsudo.npc.action;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.himatsudo.npc.HimatsudoNpc;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/** 右クリックで Velocity の別サーバーへプレイヤーを転送する。 */
public class ServerTransferAction implements NpcAction {

    public static final String TYPE = "server_transfer";

    private final HimatsudoNpc plugin;
    private final String serverName;

    public ServerTransferAction(HimatsudoNpc plugin, String serverName) {
        this.plugin     = plugin;
        this.serverName = serverName;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void execute(Player player) {
        String msg = plugin.getConfig().getString(
                "server-transfer.connecting-message", "&7サーバーに接続中...");
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    public String getServerName() {
        return serverName;
    }
}
