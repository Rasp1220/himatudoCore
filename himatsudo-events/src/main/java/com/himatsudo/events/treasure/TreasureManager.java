package com.himatsudo.events.treasure;

import com.himatsudo.events.HimatsudoEvents;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 宝エンティティ (ItemDisplay + Interaction) の生成・管理・永続化を担う。
 *
 * treasure-locations.yml に設置場所を保存する。
 * エンティティは非永続 (setPersistent(false)) かつスコアボードタグ付きで生成するため、
 * サーバー再起動時に重複が残っても spawnAll() の冒頭でクリーンアップされる。
 */
public class TreasureManager {

    private static final String TAG_DISPLAY     = "himatsudo_treasure_display";
    private static final String TAG_INTERACTION = "himatsudo_treasure_interaction";

    private final HimatsudoEvents plugin;
    private final Map<String, TreasureSpot> spots = new LinkedHashMap<>();
    private final File locFile;

    public TreasureManager(HimatsudoEvents plugin) {
        this.plugin  = plugin;
        this.locFile = new File(plugin.getDataFolder(), "treasure-locations.yml");
        startParticleTask();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void spawnAll() {
        spots.clear();
        cleanupStrayEntities();

        if (!locFile.exists()) {
            plugin.saveResource("treasure-locations.yml", false);
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(locFile);
        List<?> list = cfg.getList("locations", List.of());
        int count = 0;
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            Location loc = deserializeLocation(map);
            if (loc == null) continue;
            String key = TreasureSpot.keyOf(loc);
            TreasureSpot spot = spawnSpot(key, loc);
            if (spot != null) { spots.put(key, spot); count++; }
        }
        plugin.getLogger().info("[TreasureManager] " + count + " 個の宝を配置しました。");
    }

    public void removeAll() {
        for (TreasureSpot spot : spots.values()) {
            removeEntities(spot);
        }
        spots.clear();
    }

    // -------------------------------------------------------------------------
    // API
    // -------------------------------------------------------------------------

    public boolean placeTreasure(Location loc) {
        String key = TreasureSpot.keyOf(loc);
        if (spots.containsKey(key)) return false;

        TreasureSpot spot = spawnSpot(key, loc);
        if (spot == null) return false;

        spots.put(key, spot);
        saveLocations();
        return true;
    }

    public boolean removeTreasure(String key) {
        TreasureSpot spot = spots.remove(key);
        if (spot == null) return false;
        removeEntities(spot);
        saveLocations();
        return true;
    }

    /** 指定座標から2ブロック以内で最も近い宝を削除する。 */
    public boolean removeNearest(Location origin) {
        String nearest   = null;
        double nearestSq = 4.0;
        for (Map.Entry<String, TreasureSpot> e : spots.entrySet()) {
            Location spotLoc = e.getValue().location();
            if (!spotLoc.getWorld().equals(origin.getWorld())) continue;
            double dist = spotLoc.distanceSquared(origin);
            if (dist < nearestSq) {
                nearestSq = dist;
                nearest   = e.getKey();
            }
        }
        return nearest != null && removeTreasure(nearest);
    }

    public Map<String, TreasureSpot> getSpots() {
        return Collections.unmodifiableMap(spots);
    }

    /** Interaction エンティティの UUID から TreasureSpot を検索する。 */
    public TreasureSpot findByInteraction(UUID entityId) {
        for (TreasureSpot spot : spots.values()) {
            if (spot.interactionEntityId().equals(entityId)) return spot;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Spawn / Remove
    // -------------------------------------------------------------------------

    private TreasureSpot spawnSpot(String key, Location loc) {
        World world = loc.getWorld();
        if (world == null) return null;

        // 視覚エンティティ: ItemDisplay (NETHER_STAR、グロー付き)
        Location displayLoc = loc.clone().add(0.5, 0.1, 0.5);
        ItemDisplay display = world.spawn(displayLoc, ItemDisplay.class, e -> {
            e.setItemStack(new ItemStack(Material.NETHER_STAR));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            e.setGlowing(true);
            e.setPersistent(false);
            e.setCustomNameVisible(false);
            e.addScoreboardTag(TAG_DISPLAY);
            Transformation t = new Transformation(
                    new Vector3f(0f, 0.25f, 0f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(0.6f, 0.6f, 0.6f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            );
            e.setTransformation(t);
        });

        // インタラクションエンティティ: プレイヤーの右クリックを検出するヒットボックス
        Location interactionLoc = loc.clone().add(0.5, 0.2, 0.5);
        Interaction interaction = world.spawn(interactionLoc, Interaction.class, e -> {
            e.setInteractionWidth(0.6f);
            e.setInteractionHeight(0.6f);
            e.setResponsive(false);
            e.setPersistent(false);
            e.addScoreboardTag(TAG_INTERACTION);
        });

        return new TreasureSpot(key, loc, display.getUniqueId(), interaction.getUniqueId());
    }

    private void removeEntities(TreasureSpot spot) {
        World world = spot.location().getWorld();
        if (world == null) return;
        Entity display     = plugin.getServer().getEntity(spot.displayEntityId());
        Entity interaction = plugin.getServer().getEntity(spot.interactionEntityId());
        if (display != null)     display.remove();
        if (interaction != null) interaction.remove();
    }

    /** プラグインリロード等で残留したエンティティを除去する。 */
    private void cleanupStrayEntities() {
        for (World world : plugin.getServer().getWorlds()) {
            world.getEntitiesByClass(ItemDisplay.class).stream()
                    .filter(e -> e.getScoreboardTags().contains(TAG_DISPLAY))
                    .forEach(Entity::remove);
            world.getEntitiesByClass(Interaction.class).stream()
                    .filter(e -> e.getScoreboardTags().contains(TAG_INTERACTION))
                    .forEach(Entity::remove);
        }
    }

    // -------------------------------------------------------------------------
    // Particle task
    // -------------------------------------------------------------------------

    private void startParticleTask() {
        long interval = plugin.getConfig().getLong("treasure-hunt.particle-interval", 10L);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (TreasureSpot spot : spots.values()) {
                    World world = spot.location().getWorld();
                    if (world == null) continue;
                    Location center = spot.location().clone().add(0.5, 0.6, 0.5);
                    world.spawnParticle(Particle.END_ROD,   center, 3, 0.2, 0.25, 0.2, 0.01);
                    world.spawnParticle(Particle.ENCHANT,   center, 6, 0.3, 0.3,  0.3, 0.5);
                }
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private void saveLocations() {
        YamlConfiguration cfg  = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (TreasureSpot spot : spots.values()) {
            list.add(serializeLocation(spot.location()));
        }
        cfg.set("locations", list);
        try {
            cfg.save(locFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[TreasureManager] treasure-locations.yml の保存に失敗しました: " + e.getMessage());
        }
    }

    private Map<String, Object> serializeLocation(Location loc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", loc.getWorld().getName());
        map.put("x", loc.getBlockX());
        map.put("y", loc.getBlockY());
        map.put("z", loc.getBlockZ());
        return map;
    }

    private Location deserializeLocation(Map<?, ?> map) {
        try {
            String worldName = map.get("world").toString();
            int x = ((Number) map.get("x")).intValue();
            int y = ((Number) map.get("y")).intValue();
            int z = ((Number) map.get("z")).intValue();
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("[TreasureManager] ワールドが見つかりません: " + worldName);
                return null;
            }
            return new Location(world, x, y, z);
        } catch (Exception e) {
            plugin.getLogger().warning("[TreasureManager] 座標のパースに失敗しました: " + e.getMessage());
            return null;
        }
    }
}
