package com.himatsudo.npc.shop;

import com.himatsudo.npc.HimatsudoNpc;
import com.himatsudo.npc.currency.TitleManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * shops.yml からショップ定義を読み込む。
 * 新しいショップや商品は shops.yml を編集して /hnpc reload で反映できる。
 */
public class ShopRegistry {

    private final HimatsudoNpc plugin;
    private final TitleManager titleManager;
    private final Map<String, Shop> shops = new LinkedHashMap<>();

    public ShopRegistry(HimatsudoNpc plugin, TitleManager titleManager) {
        this.plugin       = plugin;
        this.titleManager = titleManager;
        load();
    }

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------

    public Shop getShop(String id) {
        return shops.get(id);
    }

    public Map<String, Shop> getAll() {
        return Collections.unmodifiableMap(shops);
    }

    public void load() {
        shops.clear();
        File file = new File(plugin.getDataFolder(), "shops.yml");
        if (!file.exists()) {
            plugin.saveResource("shops.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        for (String shopId : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(shopId);
            if (sec == null) continue;

            String displayName = sec.getString("display-name", shopId);
            List<ShopItem> items = new ArrayList<>();

            List<?> rawItems = sec.getList("items", List.of());
            for (Object entry : rawItems) {
                if (!(entry instanceof Map<?, ?> map)) continue;
                ShopItem item = parseItem(shopId, map);
                if (item != null) items.add(item);
            }
            shops.put(shopId, new Shop(shopId, displayName, items));
        }
        plugin.getLogger().info("[ShopRegistry] " + shops.size() + " 件のショップを読み込みました。");
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    private ShopItem parseItem(String shopId, Map<?, ?> map) {
        String itemId = str(map, "id", "");
        String matStr = str(map, "material", "STONE");
        String name   = str(map, "display-name", itemId);
        long   cost   = toLong(map, "cost", 0L);

        Material mat = Material.matchMaterial(matStr);
        if (mat == null) {
            plugin.getLogger().warning("[ShopRegistry] 不明なマテリアル: " + matStr
                    + " (shop=" + shopId + ", id=" + itemId + ")");
            mat = Material.STONE;
        }

        List<String> desc = new ArrayList<>();
        if (map.get("description") instanceof List<?> dl) {
            for (Object line : dl) if (line != null) desc.add(line.toString());
        }

        if (!(map.get("reward") instanceof Map<?, ?> rewardMap)) {
            plugin.getLogger().warning("[ShopRegistry] reward が未定義: shop=" + shopId + ", id=" + itemId);
            return null;
        }
        Reward reward = parseReward(shopId, itemId, rewardMap);
        if (reward == null) return null;

        return new ShopItem(itemId, mat, name, desc, cost, reward);
    }

    private Reward parseReward(String shopId, String itemId, Map<?, ?> map) {
        String type = str(map, "type", "");
        return switch (type) {
            case TitleReward.TYPE -> {
                String titleId   = str(map, "title-id", "");
                String titleName = str(map, "display-name", titleId);
                yield new TitleReward(titleId, titleName, titleManager);
            }
            case CommandReward.TYPE -> {
                String cmd  = str(map, "command", "");
                String dName = str(map, "display-name", cmd);
                yield new CommandReward(cmd, dName);
            }
            default -> {
                plugin.getLogger().warning("[ShopRegistry] 不明な reward type: \"" + type
                        + "\" (shop=" + shopId + ", id=" + itemId + ")");
                yield null;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }

    private static long toLong(Map<?, ?> m, String key, long def) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v != null) try { return Long.parseLong(v.toString()); }
                       catch (NumberFormatException ignored) {}
        return def;
    }
}
