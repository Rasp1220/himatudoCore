package com.himatsudo.npc.shop;

import org.bukkit.entity.Player;

/** 購入時に付与される報酬。新しい報酬タイプはこのインタフェースを実装して追加する。 */
public interface Reward {
    /** プレイヤーに報酬を付与する。 */
    void grant(Player player);
    /** ショップ表示用の説明文 (色コード付き)。 */
    String describe();
}
