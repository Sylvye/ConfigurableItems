package com.bountysmp.configurableitems.util;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.Plugin;

public final class ValidationUtil {
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_-]{1,64}");

    private ValidationUtil() {
    }

    public static boolean validItemId(String id) {
        return id != null && ITEM_ID.matcher(id).matches();
    }

    public static Optional<NamespacedKey> key(String raw, Plugin plugin) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            String normalized = raw.toLowerCase(Locale.ROOT);
            if (!normalized.contains(":") && plugin == null) {
                normalized = "minecraft:" + normalized;
            }
            return Optional.ofNullable(NamespacedKey.fromString(normalized, plugin));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static Optional<Material> material(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            value = value.substring("minecraft:".length());
        }
        Material material = Material.matchMaterial(value);
        return material == null || material.isAir() ? Optional.empty() : Optional.of(material);
    }

    public static Optional<EnchantResult> enchantment(String raw) {
        NamespacedKey key = NamespacedKey.fromString(normalizeKey(raw));
        if (key != null) {
            Enchantment enchantment = Registry.ENCHANTMENT.get(key);
            if (enchantment != null) {
                return Optional.of(new EnchantResult(key.toString(), enchantment));
            }
        }
        Enchantment byName = Enchantment.getByName(raw.toUpperCase(Locale.ROOT));
        if (byName == null) {
            return Optional.empty();
        }
        return Optional.of(new EnchantResult(byName.getKey().toString(), byName));
    }

    public static Optional<AttributeResult> attribute(String raw) {
        NamespacedKey key = NamespacedKey.fromString(normalizeKey(raw));
        if (key != null) {
            Attribute attribute = Registry.ATTRIBUTE.get(key);
            if (attribute != null) {
                return Optional.of(new AttributeResult(key.toString(), attribute));
            }
        }
        try {
            Attribute attribute = Attribute.valueOf(raw.toUpperCase(Locale.ROOT));
            return Optional.of(new AttributeResult(attribute.getKey().toString(), attribute));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static Optional<PotionResult> potion(String raw) {
        NamespacedKey key = NamespacedKey.fromString(normalizeKey(raw));
        if (key != null) {
            PotionEffectType type = Registry.MOB_EFFECT.get(key);
            if (type != null) {
                return Optional.of(new PotionResult(key.toString(), type));
            }
        }
        PotionEffectType byName = PotionEffectType.getByName(raw.toUpperCase(Locale.ROOT));
        if (byName == null) {
            return Optional.empty();
        }
        return Optional.of(new PotionResult(byName.getKey().toString(), byName));
    }

    public static Optional<SoundResult> sound(String raw) {
        NamespacedKey key = NamespacedKey.fromString(normalizeKey(raw));
        if (key != null) {
            Sound sound = Registry.SOUNDS.get(key);
            if (sound != null) {
                return Optional.of(new SoundResult(key.toString(), sound));
            }
        }
        try {
            Sound sound = Sound.valueOf(raw.toUpperCase(Locale.ROOT));
            return Optional.of(new SoundResult(sound.getKey().toString(), sound));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static EquipmentSlotGroup slotGroup(String raw) {
        EquipmentSlotGroup group = EquipmentSlotGroup.getByName(raw == null ? "" : raw.toUpperCase(Locale.ROOT));
        return group == null ? EquipmentSlotGroup.ANY : group;
    }

    public static EquipmentSlot equipmentSlot(String raw) {
        try {
            return EquipmentSlot.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return EquipmentSlot.HEAD;
        }
    }

    public static String normalizeKey(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return value.contains(":") ? value : "minecraft:" + value;
    }

    public record EnchantResult(String key, Enchantment enchantment) {
    }

    public record AttributeResult(String key, Attribute attribute) {
    }

    public record PotionResult(String key, PotionEffectType type) {
    }

    public record SoundResult(String key, Sound sound) {
    }
}
