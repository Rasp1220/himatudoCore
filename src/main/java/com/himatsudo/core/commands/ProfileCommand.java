package com.himatsudo.core.commands;

import com.himatsudo.core.HimatsudoCore;
import com.himatsudo.core.modules.ProfileModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /profile [player] — プレイヤープロフィールを GUI で表示する。
 *
 * 引数なし  → 自分のプロフィールを開く
 * [player] → 指定プレイヤーのプロフィールを開く (オンラインのみ)
 */
public class ProfileCommand implements CommandExecutor {

    private final HimatsudoCore plugin;

    public ProfileCommand(HimatsudoCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        ProfileModule profileModule = plugin.getProfileModule();
        if (profileModule == null) {
            viewer.sendMessage(Component.text("プロフィール機能は現在無効です。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            profileModule.openProfile(viewer, viewer);
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            viewer.sendMessage(Component.text(
                    "プレイヤー「" + args[0] + "」はオンラインではありません。",
                    NamedTextColor.RED));
            return true;
        }

        profileModule.openProfile(viewer, target);
        return true;
    }
}
