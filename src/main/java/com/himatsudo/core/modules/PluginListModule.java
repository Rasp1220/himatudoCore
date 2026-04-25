package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * PluginListModule — /pl・/plugins をカスタム表示に置き換える。
 *
 * 一般プレイヤー: プラグイン一覧を非表示にし、サーバー名のみ表示。
 * 管理者 (himatsudo.admin): プラグイン名・バージョン・有効状態を整形して表示。
 */
public class PluginListModule implements Listener {

    private static final List<String> INTERCEPTED = List.of("pl", "plugins");

    private final HimatsudoCore plugin;

    public PluginListModule(HimatsudoCore plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[PluginListModule] /pl override active.");
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String label = extractLabel(event.getMessage());
        if (!INTERCEPTED.contains(label)) return;

        event.setCancelled(true);
        sendPluginList(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        String label = extractLabel(event.getCommand());
        if (!INTERCEPTED.contains(label)) return;

        event.setCancelled(true);
        sendPluginList(event.getSender());
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    private void sendPluginList(CommandSender sender) {
        boolean isAdmin = !(sender instanceof Player player)
                || player.isOp()
                || player.hasPermission("himatsudo.admin");

        if (!isAdmin) {
            sender.sendMessage(
                Component.text("このサーバーはカスタムプラグインで動作しています。", NamedTextColor.GRAY));
            return;
        }

        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        List<Plugin> sorted = Arrays.stream(plugins)
                .sorted(Comparator.comparing(p -> p.getName().toLowerCase()))
                .toList();

        sender.sendMessage(Component.text(
                "─── プラグイン一覧 (" + plugins.length + "個) ───", NamedTextColor.GOLD));

        for (Plugin p : sorted) {
            boolean enabled = p.isEnabled();
            String version  = p.getPluginMeta().getVersion();
            String authors  = String.join(", ", p.getPluginMeta().getAuthors());

            Component line = Component.text("  " + p.getName(), enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .append(Component.text(" v" + version, NamedTextColor.GRAY));

            if (!authors.isEmpty()) {
                line = line.append(Component.text(" by " + authors, NamedTextColor.DARK_GRAY));
            }

            sender.sendMessage(line);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String extractLabel(String message) {
        // メッセージは "/pl" や "pl arg" どちらの形式でも来る
        String stripped = message.startsWith("/") ? message.substring(1) : message;
        String[] parts = stripped.split(" ", 2);
        return parts[0].toLowerCase();
    }

    public void shutdown() {}
}
