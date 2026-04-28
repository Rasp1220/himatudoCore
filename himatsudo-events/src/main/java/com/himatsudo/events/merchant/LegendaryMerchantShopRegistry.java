package com.himatsudo.events.merchant;

import com.himatsudo.events.HimatsudoEvents;
import com.himatsudo.events.api.ShopItem;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LegendaryMerchantShopRegistry {

    private final HimatsudoEvents plugin;
    private String displayName = "&6&l伝説の商人";
    private final List<ShopItem> items = new ArrayList<>();

    public LegendaryMerchantShopRegistry(HimatsudoEvents plugin) {
        this.plugin = plugin;
        load();
    }

    public String        getDisplayName() { return displayName; }
    public List<ShopItem> getItems()      { return List.copyOf(items); }

    public void load() {
        items.clear();
        File file = new File(plugin.getDataFolder(), "legendary-merchant.yml");
        if (!file.exists()) plugin.saveResource("legendary-merchant.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        displayName = cfg.getString("display-name", "&6&l伝説の商人");

        for (Object entry : cfg.getList("items", List.of())) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            ShopItem item = parseItem(map);
            if (item != null) items.add(item);
        }
        plugin.getLogger().info("[LegendaryMerchantShopRegistry] " + items.size() + " 件読み込み。");
    }

    private ShopItem parseItem(Map<?, ?> map) {
        String   id          = str(map, "id", "");
        String   matStr      = str(map, "material", "STONE");
        String   name        = str(map, "display-name", id);
        String   costDisplay = str(map, "cost-display", "");
        Material mat         = Material.matchMaterial(matStr);

        if (mat == null) {
            plugin.getLogger().warning("[LegendaryMerchantShopRegistry] 不明マテリアル: " + matStr);
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

        return new ShopItem(id, mat, name, desc, costDisplay, command, customModelData);
    }

    private static String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }
}
