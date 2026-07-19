package com.bountysmp.configurableitems.util;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class TextUtil {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private TextUtil() {
    }

    public static Component legacy(String input) {
        return nonItalic(LEGACY.deserialize(input == null || input.isBlank() ? "" : input));
    }

    public static List<Component> legacyLines(List<String> input) {
        List<Component> lines = new ArrayList<>();
        for (String line : input) {
            lines.add(legacy(line));
        }
        return lines;
    }

    public static String plainLegacy(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    public static ItemStack button(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(nonItalic(name));
            meta.lore(lore.stream().map(TextUtil::nonItalic).toList());
            meta.addItemFlags(ItemFlag.values());
        });
        return stack;
    }

    public static ItemStack button(Material material, NamedTextColor color, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(nonItalic(Component.text(line, NamedTextColor.GRAY)));
        }
        return button(material, nonItalic(Component.text(name, color)), lines);
    }

    public static void name(ItemStack stack, NamedTextColor color, String name) {
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(nonItalic(Component.text(name, color)));
        stack.setItemMeta(meta);
    }

    public static Component nonItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
