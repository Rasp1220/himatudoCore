package com.himatsudo.core.commands;

import com.himatsudo.core.HimatsudoCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * MenuCommand — handles /menu (opens the Nexus Menu GUI).
 *
 * Permission: himatsudo.menu (defaults to true for all players)
 */
public class MenuCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "himatsudo.menu";

    private final HimatsudoCore plugin;

    public MenuCommand(HimatsudoCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ実行できます。", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }

        if (plugin.getMenuModule() == null) {
            player.sendMessage(Component.text("メニューモジュールが読み込まれていません。", NamedTextColor.RED));
            return true;
        }

        plugin.getMenuModule().openMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        return List.of(); // No sub-commands
    }
}
