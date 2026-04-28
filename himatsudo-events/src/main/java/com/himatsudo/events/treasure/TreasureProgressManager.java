package com.himatsudo.events.treasure;

import com.himatsudo.events.HimatsudoEvents;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * プレイヤーの宝収集進捗とショップアイテム受け取り状態を管理する。
 * treasure-progress.yml に永続化する。
 */
public class TreasureProgressManager {

    private final HimatsudoEvents plugin;
    private final File progressFile;

    /** プレイヤーUUID → 収集済み宝のロケーションキーセット */
    private final Map<UUID, Set<String>> collected = new HashMap<>();
    /** プレイヤーUUID → 受け取り済みショップアイテムIDセット */
    private final Map<UUID, Set<String>> claimed   = new HashMap<>();

    public TreasureProgressManager(HimatsudoEvents plugin) {
        this.plugin       = plugin;
        this.progressFile = new File(plugin.getDataFolder(), "treasure-progress.yml");
        load();
    }

    // -------------------------------------------------------------------------
    // Collection
    // -------------------------------------------------------------------------

    /**
     * 宝を収集する。
     * @return 新規収集なら true、既に収集済みなら false
     */
    public boolean collect(UUID player, String treasureKey) {
        boolean added = collected.computeIfAbsent(player, k -> new HashSet<>()).add(treasureKey);
        if (added) save();
        return added;
    }

    public int getCount(UUID player) {
        return collected.getOrDefault(player, Set.of()).size();
    }

    public boolean hasUnlocked(UUID player) {
        int required = plugin.getConfig().getInt("treasure-hunt.required-count", 10);
        return getCount(player) >= required;
    }

    public void resetProgress(UUID player) {
        collected.remove(player);
        claimed.remove(player);
        save();
    }

    // -------------------------------------------------------------------------
    // Shop claiming
    // -------------------------------------------------------------------------

    public boolean hasClaimed(UUID player, String itemId) {
        return claimed.getOrDefault(player, Set.of()).contains(itemId);
    }

    public void setClaimed(UUID player, String itemId) {
        claimed.computeIfAbsent(player, k -> new HashSet<>()).add(itemId);
        save();
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private void load() {
        if (!progressFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(progressFile);

        if (cfg.isConfigurationSection("collected")) {
            for (String uuidStr : cfg.getConfigurationSection("collected").getKeys(false)) {
                tryParseUuid(uuidStr).ifPresent(uuid ->
                        collected.put(uuid, new HashSet<>(cfg.getStringList("collected." + uuidStr))));
            }
        }
        if (cfg.isConfigurationSection("claimed")) {
            for (String uuidStr : cfg.getConfigurationSection("claimed").getKeys(false)) {
                tryParseUuid(uuidStr).ifPresent(uuid ->
                        claimed.put(uuid, new HashSet<>(cfg.getStringList("claimed." + uuidStr))));
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        collected.forEach((uuid, keys)  -> cfg.set("collected." + uuid, new ArrayList<>(keys)));
        claimed.forEach((uuid, items)   -> cfg.set("claimed."   + uuid, new ArrayList<>(items)));
        try {
            cfg.save(progressFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[TreasureProgressManager] treasure-progress.yml の保存に失敗しました: " + e.getMessage());
        }
    }

    private Optional<UUID> tryParseUuid(String s) {
        try { return Optional.of(UUID.fromString(s)); }
        catch (IllegalArgumentException e) { return Optional.empty(); }
    }
}
