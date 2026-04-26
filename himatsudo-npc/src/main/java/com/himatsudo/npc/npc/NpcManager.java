package com.himatsudo.npc.npc;

import com.himatsudo.npc.HimatsudoNpc;
import com.himatsudo.npc.action.NpcAction;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Citizens NPC ID と NpcAction のマッピングを管理する。
 * データは npcs.yml に永続化される。
 *
 * npcs.yml:
 *   assignments:
 *     42:                       # Citizens NPC ID
 *       type: server_transfer
 *       server: bedwars
 *     17:
 *       type: shop
 *       shop-id: titles
 */
public class NpcManager {

    private final HimatsudoNpc plugin;
    private final File file;
    private YamlConfiguration data;

    /** NPC ID → action */
    private final Map<Integer, NpcAction> assignments = new HashMap<>();

    public NpcManager(HimatsudoNpc plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "npcs.yml");
        load();
    }

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------

    public void dispatch(int npcId, Player player) {
        NpcAction action = assignments.get(npcId);
        if (action != null) action.execute(player);
    }

    public void assign(int npcId, NpcAction action) {
        assignments.put(npcId, action);
        saveAction(npcId, action);
    }

    public void unassign(int npcId) {
        assignments.remove(npcId);
        data.set("assignments." + npcId, null);
        save();
    }

    public Map<Integer, NpcAction> getAssignments() {
        return Collections.unmodifiableMap(assignments);
    }

    public boolean isAssigned(int npcId) {
        return assignments.containsKey(npcId);
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public void load() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            plugin.saveResource("npcs.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(file);
        assignments.clear();

        ConfigurationSection section = data.getConfigurationSection("assignments");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                int npcId = Integer.parseInt(key);
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) continue;

                NpcAction action = plugin.getActionFactory().create(plugin, entry);
                if (action == null) {
                    plugin.getLogger().warning("[NpcManager] 不明な type: " + entry.getString("type")
                            + " (NPC ID: " + npcId + ")");
                    continue;
                }
                assignments.put(npcId, action);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[NpcManager] 無効な NPC ID: " + key);
            }
        }
        plugin.getLogger().info("[NpcManager] " + assignments.size() + " 件の NPC を読み込みました。");
    }

    private void saveAction(int npcId, NpcAction action) {
        String base = "assignments." + npcId;
        data.set(base + ".type", action.getType());

        if (action instanceof com.himatsudo.npc.action.ServerTransferAction sta) {
            data.set(base + ".server", sta.getServerName());
        } else if (action instanceof com.himatsudo.npc.action.ShopAction sa) {
            data.set(base + ".shop-id", sa.getShopId());
        }
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[NpcManager] 保存に失敗しました。", e);
        }
    }
}
