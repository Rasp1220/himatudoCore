package com.himatsudo.npc.shop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * コンソールコマンドを実行する報酬。
 * shops.yml の reward.command に設定したコマンドが実行される。
 * {player} はプレイヤー名に自動置換される。
 */
public record CommandReward(String command, String displayName) implements Reward {

    public static final String TYPE = "command";

    @Override
    public void grant(Player player) {
        String cmd = command.replace("{player}", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    @Override
    public String describe() {
        return displayName;
    }
}
