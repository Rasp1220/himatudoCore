package com.himatsudo.npc.action;

import com.himatsudo.npc.HimatsudoNpc;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * NpcAction の生成ファクトリ。
 * 新しい action タイプを追加する場合は register() を呼ぶだけでよい。
 */
public class NpcActionFactory {

    private final Map<String, BiFunction<HimatsudoNpc, ConfigurationSection, NpcAction>> creators
            = new HashMap<>();

    /** action タイプを登録する。 */
    public void register(String type,
                         BiFunction<HimatsudoNpc, ConfigurationSection, NpcAction> creator) {
        creators.put(type, creator);
    }

    /**
     * ConfigurationSection から NpcAction を生成する。
     * type が未登録の場合は null を返す。
     */
    public NpcAction create(HimatsudoNpc plugin, ConfigurationSection section) {
        String type = section.getString("type", "");
        BiFunction<HimatsudoNpc, ConfigurationSection, NpcAction> creator = creators.get(type);
        if (creator == null) return null;
        return creator.apply(plugin, section);
    }
}
