package com.himatsudo.npc.action;

import org.bukkit.entity.Player;

/**
 * NPC を右クリックしたときに実行される処理。
 * 新しい NPC 種別はこのインタフェースを実装し、
 * NpcActionFactory に登録するだけで追加できる。
 */
public interface NpcAction {
    /** この action の識別子 (npcs.yml の type フィールドと一致する)。 */
    String getType();

    /** プレイヤーが NPC を右クリックしたときに呼ばれる。 */
    void execute(Player player);
}
