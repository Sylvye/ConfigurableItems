package com.bountysmp.configurableitems.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;

public record VeinmineOptions(
    Integer limit,
    boolean drop,
    Filter filter,
    MatchMode matchMode,
    Mode mode,
    Material replacement,
    boolean useEnchants,
    boolean useDurability,
    boolean effect,
    boolean xp,
    List<String> errors
) {
    public enum Mode {
        DESTROY,
        REPLACE
    }

    public enum MatchMode {
        ALL,
        SAME_TYPE
    }

    public enum FilterKind {
        SAME_TYPE,
        MATERIAL,
        TAG,
        MULTI
    }

    public record FilterEntry(FilterKind kind, Material material, NamespacedKey tagKey, String raw) {
    }

    public record Filter(FilterKind kind, Material material, NamespacedKey tagKey, String raw, List<FilterEntry> entries) {
        static Filter sameType() {
            return new Filter(FilterKind.SAME_TYPE, null, null, "", List.of());
        }

        static Filter of(List<FilterEntry> entries) {
            if (entries.isEmpty()) {
                return sameType();
            }
            FilterEntry first = entries.getFirst();
            String raw = String.join(",", entries.stream().map(FilterEntry::raw).toList());
            FilterKind kind = entries.size() == 1 ? first.kind() : FilterKind.MULTI;
            return new Filter(kind, first.material(), first.tagKey(), raw, List.copyOf(entries));
        }
    }

    @FunctionalInterface
    public interface TagChecker {
        boolean exists(NamespacedKey key);
    }

    public int effectiveLimit(int fallback) {
        return limit == null ? fallback : limit;
    }

    public static VeinmineOptions parse(List<String> tokens) {
        return parse(tokens, bukkitTagChecker());
    }

    public static VeinmineOptions parse(List<String> tokens, TagChecker tagChecker) {
        List<String> errors = new ArrayList<>();
        int index = 1;
        Integer limit = null;
        if (tokens.size() > 1 && !isOption(tokens.get(1))) {
            limit = positiveInt(tokens.get(1));
            if (limit == null) {
                errors.add("VEINMINE limit must be a positive integer: " + tokens.get(1));
            }
            index = 2;
        }

        boolean drop = true;
        Filter filter = Filter.sameType();
        MatchMode matchMode = null;
        Mode mode = Mode.DESTROY;
        Material replacement = null;
        boolean useEnchants = true;
        boolean useDurability = true;
        boolean effect = true;
        boolean xp = true;

        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (!token.contains(":")) {
                errors.add("Unknown VEINMINE token: " + token);
                index++;
                continue;
            }
            String key = token.substring(0, token.indexOf(':')).toLowerCase(Locale.ROOT);
            String value = token.substring(token.indexOf(':') + 1);
            switch (key) {
                case "drop" -> drop = bool(value, "drop", errors, drop);
                case "filter" -> filter = filter(value, tagChecker, errors);
                case "match" -> matchMode = matchMode(value, errors, matchMode);
                case "mode" -> mode = mode(value, errors, mode);
                case "replace" -> replacement = material(value, "replace", errors);
                case "use-enchants" -> useEnchants = bool(value, "use-enchants", errors, useEnchants);
                case "use-durability" -> useDurability = bool(value, "use-durability", errors, useDurability);
                case "effect" -> effect = bool(value, "effect", errors, effect);
                case "xp" -> xp = bool(value, "xp", errors, xp);
                default -> errors.add("Unknown VEINMINE option: " + key);
            }
            index++;
        }

        if (mode == Mode.REPLACE && replacement == null) {
            errors.add("VEINMINE mode:replace requires replace:<material>");
        }
        if (matchMode == null) {
            matchMode = filter.kind() == FilterKind.SAME_TYPE ? MatchMode.SAME_TYPE : MatchMode.ALL;
        }
        return new VeinmineOptions(limit, drop, filter, matchMode, mode, replacement, useEnchants, useDurability, effect, xp, List.copyOf(errors));
    }

    public static boolean isVeinmineOption(String token) {
        return isOption(token);
    }

    static TagChecker bukkitTagChecker() {
        return key -> {
            try {
                return Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class) != null;
            } catch (Throwable ex) {
                return true;
            }
        };
    }

    private static boolean isOption(String token) {
        if (token == null) {
            return false;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.startsWith("drop:")
            || lower.startsWith("filter:")
            || lower.startsWith("match:")
            || lower.startsWith("mode:")
            || lower.startsWith("replace:")
            || lower.startsWith("use-enchants:")
            || lower.startsWith("use-durability:")
            || lower.startsWith("effect:")
            || lower.startsWith("xp:");
    }

    private static Integer positiveInt(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean bool(String raw, String key, List<String> errors, boolean fallback) {
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        errors.add("VEINMINE " + key + " must be true or false: " + raw);
        return fallback;
    }

    private static Mode mode(String raw, List<String> errors, Mode fallback) {
        try {
            return Mode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            errors.add("VEINMINE mode must be destroy or replace: " + raw);
            return fallback;
        }
    }

    private static MatchMode matchMode(String raw, List<String> errors, MatchMode fallback) {
        try {
            return MatchMode.valueOf(raw.replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            errors.add("VEINMINE match must be all or same-type: " + raw);
            return fallback;
        }
    }

    private static Material material(String raw, String key, List<String> errors) {
        if (raw.contains("{")) {
            errors.add("VEINMINE " + key + " does not support variables: " + raw);
            return null;
        }
        Material material = matchMaterial(raw);
        if (material == null || !isBlockMaterial(material)) {
                errors.add("Invalid VEINMINE " + key + " material: " + raw);
                return null;
        }
        return material;
    }

    private static Filter filter(String raw, TagChecker tagChecker, List<String> errors) {
        if (raw.contains("{")) {
            errors.add("VEINMINE filter does not support variables: " + raw);
            return Filter.sameType();
        }
        List<FilterEntry> entries = new ArrayList<>();
        for (String entry : raw.split(",", -1)) {
            String trimmed = entry.trim();
            if (trimmed.isBlank()) {
                errors.add("VEINMINE filter has an empty entry: " + raw);
                continue;
            }
            FilterEntry parsed = filterEntry(trimmed, tagChecker, errors);
            if (parsed != null) {
                entries.add(parsed);
            }
        }
        return Filter.of(entries);
    }

    private static FilterEntry filterEntry(String raw, TagChecker tagChecker, List<String> errors) {
        if (raw.startsWith("#")) {
            String tagName = raw.substring(1);
            NamespacedKey key = namespacedKey(tagName);
            if (key == null) {
                errors.add("Invalid VEINMINE block tag: " + raw);
                return null;
            }
            if (!tagChecker.exists(key)) {
                errors.add("Unknown VEINMINE block tag: " + raw);
            }
            return new FilterEntry(FilterKind.TAG, null, key, "#" + key);
        }
        Material material = material(raw, "filter", errors);
        return material == null ? null : new FilterEntry(FilterKind.MATERIAL, material, null, material.name());
    }

    private static Material matchMaterial(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            value = value.substring("minecraft:".length());
        }
        try {
            Material material = Material.valueOf(value.toUpperCase(Locale.ROOT));
            return isAirMaterial(material) ? null : material;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isBlockMaterial(Material material) {
        try {
            return material.isBlock() && !material.isAir();
        } catch (Throwable ex) {
            return !isAirMaterial(material);
        }
    }

    private static boolean isAirMaterial(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private static NamespacedKey namespacedKey(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return null;
        }
        if (!value.contains(":")) {
            value = "minecraft:" + value;
        }
        try {
            return NamespacedKey.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
