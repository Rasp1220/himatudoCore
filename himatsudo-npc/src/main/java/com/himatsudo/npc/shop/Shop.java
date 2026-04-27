package com.himatsudo.npc.shop;

import java.util.List;

/** ショップ1店舗分の定義。 */
public record Shop(String id, String displayName, List<ShopItem> items) {}
