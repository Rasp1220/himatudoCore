package com.himatsudo.events.shop;

import com.himatsudo.events.HimatsudoEvents;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * treasure-shop.yml からショップアイテム定義を読み込む。
 * /hev reload で再読み込み可能。
 */
public class TreasureShopRegistry {

    private final HimatsudoEvents plugin;
    private String displayName = "&6&l宝探し限定ショップ";
    private final List<TreasureShopItem> items = new ArrayList<>();

    public TreasureShopRegistry(HimatsudoEvents plugin) {
        this.plugin = plugin;
        load();
    }

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------

    public String getDisplayName() { return displayName; }

    public List<TreasureShopItem> getItems() { return List.copyOf(items); }

    public void load() {
        items.clear();
        File file = new File(plugin.getDataFolder(), "treasure-shop.yml");
        if (!file.exists()) {
            plugin.saveResource("treasure-shop.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        displayName = cfg.getString("display-name", "&6&l宝探し限定ショップ");

        List<?> rawItems = cfg.getList("items", List.of());
        for (Object entry : rawItems) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            TreasureShopItem item = parseItem(map);
            if (item != null) items.add(item);
        }
        plugin.getLogger().info("[TreasureShopRegistry] " + items.size() + " 件の限定アイテムを読み込みました。");
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    private TreasureShopItem parseItem(Map<?, ?> map) {
        String id     = str(map, "id", "");
        String matStr = str(map, "material", "STONE");
        String name   = str(map, "display-name", id);

        Material mat = Material.matchMaterial(matStr);
        if (mat == null) {
            plugin.getLogger().warning("[TreasureShopRegistry] 不明なマテリアル: " + matStr + " (id=" + id + ")");
            mat = Material.STONE;
        }

        List<String> desc = new ArrayList<>();
        if (map.get("description") instanceof List<?> dl) {
            for (Object line : dl) if (line != null) desc.add(line.toString());
        }

        String command = "";
        if (map.get("reward") instanceof Map<?, ?> rewardMap) {
            command = str(rewardMap, "command", "");
        }

        return new TreasureShopItem(id, mat, name, desc, command);
    }

    private static String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }
}
