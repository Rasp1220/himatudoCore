package com.himatsudo.core.commands;

import com.himatsudo.core.HimatsudoCore;
import com.himatsudo.core.modules.BoardModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /board — toggles the sidebar scoreboard for the executing player. */
public class BoardCommand implements CommandExecutor {

    private final HimatsudoCore plugin;

    public BoardCommand(HimatsudoCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        BoardModule board = plugin.getBoardModule();
        if (board == null) {
            player.sendMessage(Component.text("スコアボード機能は現在無効です。", NamedTextColor.RED));
            return true;
        }

        boolean nowVisible = board.toggleBoard(player);
        player.sendMessage(nowVisible
                ? Component.text("スコアボードを表示しました。", NamedTextColor.GREEN)
                : Component.text("スコアボードを非表示にしました。", NamedTextColor.YELLOW));
        return true;
    }
}
