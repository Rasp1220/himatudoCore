package com.himatsudo.core.modules;

import com.himatsudo.core.HimatsudoCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * TextModule — floating Text Display entity manager (⑥ Float Text unit).
 *
 * Text Display エンティティ (Minecraft 1.19.4+) を使い、アーマースタンドを
 * 一切使用せずに空中に文字を表示・管理します。
 *
 * データは plugins/HimatsudoCore/texts.yml に独立して保存されます。
 * サーバー再起動後も同じ座標・内容でテキストを自動復元します。
 *
 * 設定キー (config.yml の text: セクション):
 *   text.enabled — 機能 ON/OFF
 */
public class TextModule implements Listener {

    private static final String DATA_FILE = "texts.yml";

    /** PersistentDataContainer キー — このプラグインが生成したエンティティを識別する */
    private final NamespacedKey PDC_KEY;

    private final HimatsudoCore plugin;
    private final boolean enabled;

    /** id -> 稼働中の TextDisplay エンティティ */
    private final Map<String, TextDisplay> activeDisplays = new HashMap<>();

    private File dataFile;
    private YamlConfiguration data;

    public TextModule(HimatsudoCore plugin) {
        this.plugin  = plugin;
        this.PDC_KEY = new NamespacedKey(plugin, "text_id");
        this.enabled = plugin.getConfig().getBoolean("text.enabled", true);

        if (enabled) {
            loadDataFile();
            // ワールドが完全に準備されてからエンティティをスポーンさせるため 1tick 遅延
            plugin.getServer().getScheduler().runTask(plugin, this::spawnAll);
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[TextModule] Float Text manager active.");
        } else {
            plugin.getLogger().info("[TextModule] Disabled via config.");
        }
    }

    // -------------------------------------------------------------------------
    // データ永続化
    // -------------------------------------------------------------------------

    private void loadDataFile() {
        dataFile = new File(plugin.getDataFolder(), DATA_FILE);
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[TextModule] texts.yml を作成できません。", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveDataFile() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[TextModule] texts.yml を保存できません。", e);
        }
    }

    // -------------------------------------------------------------------------
    // エンティティライフサイクル
    // -------------------------------------------------------------------------

    /**
     * 起動時: 前セッションの残留エンティティを削除してから YAML でリストア。
     * 前回クラッシュした場合でも PDC タグで識別してクリーンアップする。
     */
    private void spawnAll() {
        // PDC タグ付きエンティティの残留クリーンアップ
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e instanceof TextDisplay td
                        && td.getPersistentDataContainer().has(PDC_KEY, PersistentDataType.STRING)) {
                    td.remove();
                }
            }
        }

        // YAML からリストア
        ConfigurationSection section = data.getConfigurationSection("texts");
        if (section == null) return;

        int count = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) continue;
            try {
                TextDisplay display = spawnFromSection(id, entry);
                if (display != null) {
                    activeDisplays.put(id, display);
                    count++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[TextModule] '" + id + "' のリストアに失敗しました。", e);
            }
        }
        plugin.getLogger().info("[TextModule] " + count + " 個のテキストを復元しました。");
    }

    /**
     * チャンクロード時: 追跡していない PDC タグ付きエンティティを除去する。
     * クラッシュ後に永続化されたままになった残留エンティティをクリーンアップ。
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity e : event.getChunk().getEntities()) {
            if (e instanceof TextDisplay td) {
                String tagId = td.getPersistentDataContainer()
                        .get(PDC_KEY, PersistentDataType.STRING);
                if (tagId != null && !activeDisplays.containsKey(tagId)) {
                    td.remove();
                }
            }
        }
    }

    public void shutdown() {
        for (TextDisplay td : activeDisplays.values()) {
            if (td.isValid()) td.remove();
        }
        activeDisplays.clear();
    }

    // -------------------------------------------------------------------------
    // 公開 CRUD API
    // -------------------------------------------------------------------------

    /**
     * 新しいテキストを設置して texts.yml に保存する。
     *
     * @return false の場合は ID が既に存在する
     */
    public boolean create(String id, Location loc, String rawText,
                          Display.Billboard billboard, float scale,
                          boolean hasBackground, Color bgColor) {
        if (activeDisplays.containsKey(id)) return false;

        TextDisplay entity = spawnEntity(loc, id, rawText, billboard, scale, hasBackground, bgColor);
        activeDisplays.put(id, entity);
        saveSection(id, loc, rawText, billboard, scale, hasBackground, bgColor);
        saveDataFile();
        return true;
    }

    /** テキスト内容を更新する */
    public boolean editText(String id, String rawText) {
        TextDisplay td = activeDisplays.get(id);
        if (td == null) return false;
        td.text(parseText(rawText));
        data.set("texts." + id + ".raw-text", rawText);
        saveDataFile();
        return true;
    }

    /** スケールを更新する */
    public boolean editScale(String id, float scale) {
        TextDisplay td = activeDisplays.get(id);
        if (td == null) return false;
        applyScale(td, scale);
        data.set("texts." + id + ".scale", (double) scale);
        saveDataFile();
        return true;
    }

    /** ビルボードモードを更新する */
    public boolean editBillboard(String id, Display.Billboard billboard) {
        TextDisplay td = activeDisplays.get(id);
        if (td == null) return false;
        td.setBillboard(billboard);
        data.set("texts." + id + ".billboard", billboard.name());
        saveDataFile();
        return true;
    }

    /** 背景色・表示を更新する */
    public boolean editBackground(String id, boolean hasBackground, Color bgColor) {
        TextDisplay td = activeDisplays.get(id);
        if (td == null) return false;
        applyBackground(td, hasBackground, bgColor);
        data.set("texts." + id + ".background", hasBackground);
        data.set("texts." + id + ".background-color",
                hasBackground ? colorToArgbInt(bgColor) : 0);
        saveDataFile();
        return true;
    }

    /** テキストシャドウを更新する */
    public boolean editShadow(String id, boolean shadow) {
        TextDisplay td = activeDisplays.get(id);
        if (td == null) return false;
        td.setShadowed(shadow);
        data.set("texts." + id + ".shadow", shadow);
        saveDataFile();
        return true;
    }

    /** テキスト整列を更新する */
    public boolean editAlign(String id, TextDisplay.TextAlignment alignment) {
        TextDisplay td = activeDisplays.get(id);
        if (td == null) return false;
        td.setAlignment(alignment);
        data.set("texts." + id + ".align", alignment.name());
        saveDataFile();
        return true;
    }

    /**
     * テキストを相対移動する。
     *
     * @param dx/dy/dz ブロック単位の移動量
     */
    public boolean move(String id, double dx, double dy, double dz) {
        TextDisplay td = activeDisplays.get(id);
        if (td == null) return false;
        Location newLoc = td.getLocation().add(dx, dy, dz);
        td.teleport(newLoc);
        data.set("texts." + id + ".x", newLoc.getX());
        data.set("texts." + id + ".y", newLoc.getY());
        data.set("texts." + id + ".z", newLoc.getZ());
        saveDataFile();
        return true;
    }

    /** テキストを削除する */
    public boolean delete(String id) {
        TextDisplay td = activeDisplays.remove(id);
        if (td == null) return false;
        if (td.isValid()) td.remove();
        data.set("texts." + id, null);
        saveDataFile();
        return true;
    }

    public boolean exists(String id)               { return activeDisplays.containsKey(id); }
    public TextDisplay getDisplay(String id)        { return activeDisplays.get(id); }
    public List<String> getIds() {
        return Collections.unmodifiableList(new ArrayList<>(activeDisplays.keySet()));
    }

    // -------------------------------------------------------------------------
    // 内部ヘルパー
    // -------------------------------------------------------------------------

    private TextDisplay spawnFromSection(String id, ConfigurationSection s) {
        String worldName = s.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning(
                    "[TextModule] ワールド '" + worldName + "' が見つかりません (id: " + id + ")");
            return null;
        }

        Location loc = new Location(world,
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));

        String rawText    = s.getString("raw-text", id);
        float  scale      = (float) s.getDouble("scale", 1.0);
        boolean hasBg     = s.getBoolean("background", true);
        int     bgArgb    = s.getInt("background-color", 0x40000000);
        String  billStr   = s.getString("billboard", "CENTER");
        boolean shadow    = s.getBoolean("shadow", false);
        String  alignStr  = s.getString("align", "CENTER");

        Display.Billboard billboard = parseBillboard(billStr);
        Color bgColor = argbIntToColor(bgArgb);

        TextDisplay td = spawnEntity(loc, id, rawText, billboard, scale, hasBg, bgColor);

        td.setShadowed(shadow);
        try {
            td.setAlignment(TextDisplay.TextAlignment.valueOf(alignStr.toUpperCase()));
        } catch (IllegalArgumentException ignored) {}

        return td;
    }

    private TextDisplay spawnEntity(Location loc, String id, String rawText,
                                     Display.Billboard billboard, float scale,
                                     boolean hasBackground, Color bgColor) {
        return loc.getWorld().spawn(loc, TextDisplay.class, entity -> {
            entity.getPersistentDataContainer().set(PDC_KEY, PersistentDataType.STRING, id);
            entity.setPersistent(true);   // チャンクアンロード時も保持
            entity.setInvulnerable(true);
            entity.text(parseText(rawText));
            entity.setBillboard(billboard);
            entity.setLineWidth(200);     // 長いテキストの折り返し幅
            applyScale(entity, scale);
            applyBackground(entity, hasBackground, bgColor);
        });
    }

    private void saveSection(String id, Location loc, String rawText,
                              Display.Billboard billboard, float scale,
                              boolean hasBackground, Color bgColor) {
        String base = "texts." + id;
        data.set(base + ".world",            loc.getWorld().getName());
        data.set(base + ".x",                loc.getX());
        data.set(base + ".y",                loc.getY());
        data.set(base + ".z",                loc.getZ());
        data.set(base + ".raw-text",         rawText);
        data.set(base + ".billboard",        billboard.name());
        data.set(base + ".scale",            (double) scale);
        data.set(base + ".background",       hasBackground);
        data.set(base + ".background-color", hasBackground ? colorToArgbInt(bgColor) : 0);
        data.set(base + ".shadow",           false);
        data.set(base + ".align",            "CENTER");
    }

    private void applyScale(TextDisplay entity, float scale) {
        Transformation t = entity.getTransformation();
        entity.setTransformation(new Transformation(
                t.getTranslation(),
                t.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                t.getRightRotation()
        ));
    }

    private void applyBackground(TextDisplay entity, boolean hasBackground, Color bgColor) {
        entity.setDefaultBackground(false);
        if (hasBackground) {
            entity.setBackgroundColor(bgColor);
        } else {
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // 完全透明
        }
    }

    // -------------------------------------------------------------------------
    // テキスト解析
    // -------------------------------------------------------------------------

    /**
     * &-カラーコード・&#RRGGBB HEX・\n 改行をすべて解析して Component に変換する。
     *
     * @param raw ユーザーが入力した生文字列
     */
    public Component parseText(String raw) {
        // \n をリテラルから実際の改行に変換
        raw = raw.replace("\\n", "\n");
        // &#RRGGBB → &x&R&R&G&G&B&B (Bukkit legacy hex 形式に変換)
        raw = raw.replaceAll(
                "&#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])",
                "&x&$1&$2&$3&$4&$5&$6"
        );
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    // -------------------------------------------------------------------------
    // 色変換ユーティリティ
    // -------------------------------------------------------------------------

    private int colorToArgbInt(Color color) {
        return ((color.getAlpha() & 0xFF) << 24)
             | ((color.getRed()   & 0xFF) << 16)
             | ((color.getGreen() & 0xFF) << 8)
             |  (color.getBlue()  & 0xFF);
    }

    private Color argbIntToColor(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8)  & 0xFF;
        int b =  argb        & 0xFF;
        return Color.fromARGB(a, r, g, b);
    }

    private Display.Billboard parseBillboard(String value) {
        try {
            return Display.Billboard.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Display.Billboard.CENTER;
        }
    }
}
