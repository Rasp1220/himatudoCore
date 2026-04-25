package com.himatsudo.npc.currency;

import com.himatsudo.npc.HimatsudoNpc;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * プレイヤーが所持する称号と有効な称号を YAML で管理する。
 *
 * titles.yml:
 *   <uuid>:
 *     owned:  [title_id, ...]
 *     active: title_id
 */
public class TitleManager {

    private final HimatsudoNpc plugin;
    private final File file;
    private YamlConfiguration data;

    public TitleManager(HimatsudoNpc plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "titles.yml");
        load();
    }

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------

    public void grantTitle(UUID uuid, String titleId) {
        List<String> owned = new ArrayList<>(getOwnedTitles(uuid));
        if (!owned.contains(titleId)) {
            owned.add(titleId);
            data.set(uuid + ".owned", owned);
            save();
        }
    }

    public boolean hasTitle(UUID uuid, String titleId) {
        return getOwnedTitles(uuid).contains(titleId);
    }

    public List<String> getOwnedTitles(UUID uuid) {
        return Collections.unmodifiableList(
                data.getStringList(uuid + ".owned"));
    }

    public String getActiveTitle(UUID uuid) {
        return data.getString(uuid + ".active", "");
    }

    public boolean setActiveTitle(UUID uuid, String titleId) {
        if (!hasTitle(uuid, titleId)) return false;
        data.set(uuid + ".active", titleId);
        save();
        return true;
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private void load() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[TitleManager] 保存に失敗しました。", e);
        }
    }
}
