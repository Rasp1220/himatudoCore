package com.himatsudo.npc.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public final class Gui {

    private static final LegacyComponentSerializer AMP =
            LegacyComponentSerializer.legacyAmpersand();

    private Gui() {}

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(parse(name));
        if (lore.length > 0) {
            meta.lore(Arrays.stream(lore).map(Gui::parse).toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(parse(name));
        meta.lore(lore.stream().map(Gui::parse).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        return pane;
    }

    public static Component parse(String raw) {
        return AMP.deserialize(raw)
                .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
