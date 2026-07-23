package com.bountysmp.configurableitems.action;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Particle;

public record HitboxOptions(
    Shape shape,
    double size,
    String at,
    EnumSet<TargetKind> targets,
    int maxPlayers,
    int maxEntities,
    int maxBlocks,
    String particle,
    boolean edgeParticles,
    int points,
    List<String> errors
) {
    public enum Shape {
        SPHERE,
        CUBE,
        CONE
    }

    public static HitboxOptions parse(List<String> tokens) {
        Shape shape = Shape.SPHERE;
        double size = 1.0;
        String at = "CURRENT";
        EnumSet<TargetKind> targets = EnumSet.of(TargetKind.ENTITY);
        int maxPlayers = 50;
        int maxEntities = 50;
        int maxBlocks = 64;
        String particle = "";
        boolean edgeParticles = false;
        int points = 24;
        List<String> errors = new ArrayList<>();

        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            int colon = token.indexOf(':');
            if (colon < 1) {
                errors.add("Unknown HITBOX token: " + token);
                continue;
            }
            String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1);
            switch (key) {
                case "shape" -> {
                    try {
                        shape = Shape.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        errors.add("HITBOX shape must be SPHERE, CUBE, or CONE: " + value);
                    }
                }
                case "size" -> {
                    Double parsed = doubleValue(value);
                    if (parsed == null || parsed <= 0.0) {
                        errors.add("HITBOX size must be positive: " + value);
                    } else {
                        size = parsed;
                    }
                }
                case "at" -> at = value.toUpperCase(Locale.ROOT);
                case "targets" -> targets = parseTargets(value, errors);
                case "max-players" -> maxPlayers = nonNegativeInt("max-players", value, maxPlayers, errors);
                case "max-entities" -> maxEntities = nonNegativeInt("max-entities", value, maxEntities, errors);
                case "max-blocks" -> maxBlocks = nonNegativeInt("max-blocks", value, maxBlocks, errors);
                case "particle" -> {
                    particle = value.toUpperCase(Locale.ROOT);
                    if (!value.contains("{") && particleType(value) == null) {
                        errors.add("Unknown HITBOX particle: " + value);
                    }
                }
                case "edge-particles" -> edgeParticles = bool("edge-particles", value, edgeParticles, errors);
                case "points" -> points = positiveInt("points", value, points, errors);
                default -> errors.add("Unknown HITBOX option: " + key);
            }
        }
        return new HitboxOptions(shape, size, at, targets, maxPlayers, maxEntities, maxBlocks, particle, edgeParticles, points, List.copyOf(errors));
    }

    public static boolean isHitboxOption(String token) {
        int colon = token == null ? -1 : token.indexOf(':');
        if (colon < 1) {
            return false;
        }
        return switch (token.substring(0, colon).toLowerCase(Locale.ROOT)) {
            case "shape", "size", "at", "targets", "max-players", "max-entities", "max-blocks", "particle", "edge-particles", "points" -> true;
            default -> false;
        };
    }

    static Particle particleType(String name) {
        return HitscanOptions.particleType(name);
    }

    private static EnumSet<TargetKind> parseTargets(String raw, List<String> errors) {
        EnumSet<TargetKind> parsed = EnumSet.noneOf(TargetKind.class);
        for (String value : raw.split(",")) {
            Optional<TargetKind> target = TargetKind.parse(value);
            if (target.isEmpty()) {
                errors.add("HITBOX targets must contain PLAYER, MOB, ENTITY, or BLOCK: " + value);
            } else {
                parsed.add(target.get());
            }
        }
        if (parsed.isEmpty()) {
            errors.add("HITBOX targets cannot be empty");
            parsed.add(TargetKind.ENTITY);
        }
        return parsed;
    }

    private static int positiveInt(String key, String raw, int fallback, List<String> errors) {
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                errors.add("HITBOX " + key + " must be positive: " + raw);
                return fallback;
            }
            return value;
        } catch (NumberFormatException ex) {
            errors.add("HITBOX " + key + " must be an integer: " + raw);
            return fallback;
        }
    }

    private static int nonNegativeInt(String key, String raw, int fallback, List<String> errors) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                errors.add("HITBOX " + key + " must be non-negative: " + raw);
                return fallback;
            }
            return value;
        } catch (NumberFormatException ex) {
            errors.add("HITBOX " + key + " must be an integer: " + raw);
            return fallback;
        }
    }

    private static boolean bool(String key, String raw, boolean fallback, List<String> errors) {
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        errors.add("HITBOX " + key + " must be true or false: " + raw);
        return fallback;
    }

    private static Double doubleValue(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
