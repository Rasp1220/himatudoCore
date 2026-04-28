package com.himatsudo.events.treasure;

import com.himatsudo.events.HimatsudoEvents;
import com.himatsudo.events.api.ShopItem;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TreasureShopRegistry {

    private final HimatsudoEvents plugin;
    private String displayName = "&6&l宝探し限定ショップ";
    private final List<ShopItem> items = new ArrayList<>();

    public TreasureShopRegistry(HimatsudoEvents plugin) {
        this.plugin = plugin;
        load();
    }

    public String        getDisplayName() { return displayName; }
    public List<ShopItem> getItems()      { return List.copyOf(items); }

    public void load() {
        items.clear();
        File file = new File(plugin.getDataFolder(), "treasure-shop.yml");
        if (!file.exists()) plugin.saveResource("treasure-shop.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        displayName = cfg.getString("display-name", "&6&l宝探し限定ショップ");

        for (Object entry : cfg.getList("items", List.of())) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            ShopItem item = parseItem(map);
            if (item != null) items.add(item);
        }
        plugin.getLogger().info("[TreasureShopRegistry] " + items.size() + " 件読み込み。");
    }

    private ShopItem parseItem(Map<?, ?> map) {
        String   id     = str(map, "id", "");
        String   matStr = str(map, "material", "STONE");
        String   name   = str(map, "display-name", id);
        Material mat    = Material.matchMaterial(matStr);

        if (mat == null) {
            plugin.getLogger().warning("[TreasureShopRegistry] 不明マテリアル: " + matStr);
            mat = Material.STONE;
        }

        List<String> desc = new ArrayList<>();
        if (map.get("description") instanceof List<?> dl) {
            for (Object line : dl) if (line != null) desc.add(line.toString());
        }

        String command = "";
        if (map.get("reward") instanceof Map<?, ?> rm) command = str(rm, "command", "");

        int customModelData = 0;
        if (map.get("custom-model-data") instanceof Number n) customModelData = n.intValue();

        return new ShopItem(id, mat, name, desc, "", command, customModelData);
    }

    private static String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }
}
