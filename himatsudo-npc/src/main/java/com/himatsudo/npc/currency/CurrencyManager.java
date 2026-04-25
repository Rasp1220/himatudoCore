package com.himatsudo.npc.currency;

import com.himatsudo.npc.HimatsudoNpc;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/** プレイヤーごとのポイント残高を YAML で管理する。 */
public class CurrencyManager {

    private final HimatsudoNpc plugin;
    private final File file;
    private YamlConfiguration data;

    public CurrencyManager(HimatsudoNpc plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "currency.yml");
        load();
    }

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------

    public long getBalance(UUID uuid) {
        return data.getLong(uuid.toString(), 0L);
    }

    public void add(UUID uuid, long amount) {
        data.set(uuid.toString(), getBalance(uuid) + amount);
        save();
    }

    public boolean deduct(UUID uuid, long amount) {
        long balance = getBalance(uuid);
        if (balance < amount) return false;
        data.set(uuid.toString(), balance - amount);
        save();
        return true;
    }

    public void set(UUID uuid, long amount) {
        data.set(uuid.toString(), Math.max(0, amount));
        save();
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
            plugin.getLogger().log(Level.WARNING, "[CurrencyManager] 保存に失敗しました。", e);
        }
    }
}
