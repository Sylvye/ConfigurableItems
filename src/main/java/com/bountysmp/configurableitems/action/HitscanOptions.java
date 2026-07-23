package com.bountysmp.configurableitems.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Particle;

public record HitscanOptions(
    int distance,
    TargetMode targetMode,
    Integer maxHits,
    String particle,
    int points,
    double offset,
    String speed,
    double particleSpeed,
    String body,
    List<String> errors
) {
    private static final int DEFAULT_DISTANCE = 32;
    private static final int DEFAULT_POINTS = 24;

    public enum TargetMode {
        PLAYER,
        MOB,
        ENTITY,
        BLOCK
    }

    public boolean allHits() {
        return maxHits == null;
    }

    public static HitscanOptions parse(List<String> tokens) {
        List<String> errors = new ArrayList<>();
        int index = 1;
        int distance = DEFAULT_DISTANCE;
        if (tokens.size() > 1) {
            String rawDistance = tokens.get(1);
            if (isOption(rawDistance)) {
                errors.add("HITSCAN requires distance before options");
            } else {
                Integer parsed = positiveInt(rawDistance);
                if (parsed == null) {
                    errors.add("HITSCAN distance must be a positive integer: " + rawDistance);
                } else {
                    distance = parsed;
                }
                index = 2;
            }
        } else {
            errors.add("HITSCAN requires distance and action body");
        }

        TargetMode targetMode = TargetMode.ENTITY;
        Integer maxHits = 1;
        String particle = "";
        int points = DEFAULT_POINTS;
        double offset = 0.0;
        String speed = "instant";
        double particleSpeed = 0.0;
        String pendingSpeed = null;

        while (index < tokens.size() && isOption(tokens.get(index))) {
            String token = tokens.get(index);
            String key = token.substring(0, token.indexOf(':')).toLowerCase(Locale.ROOT);
            String value = token.substring(token.indexOf(':') + 1);
            switch (key) {
                case "target" -> {
                    try {
                        targetMode = TargetMode.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        errors.add("HITSCAN target must be PLAYER, MOB, ENTITY, or BLOCK: " + value);
                    }
                }
                case "max-hits" -> {
                    if (value.equalsIgnoreCase("all")) {
                        maxHits = null;
                    } else {
                        Integer parsed = positiveInt(value);
                        if (parsed == null) {
                            errors.add("HITSCAN max-hits must be a positive integer or all: " + value);
                        } else {
                            maxHits = parsed;
                        }
                    }
                }
                case "particle" -> {
                    particle = value.toUpperCase(Locale.ROOT);
                    if (!value.contains("{") && particleType(value) == null) {
                        errors.add("Unknown HITSCAN particle: " + value);
                    }
                }
                case "points" -> {
                    Integer parsed = positiveInt(value);
                    if (parsed == null) {
                        errors.add("HITSCAN points must be a positive integer: " + value);
                    } else {
                        points = parsed;
                    }
                }
                case "offset" -> {
                    Double parsed = doubleValue(value);
                    if (parsed == null || parsed < 0.0) {
                        errors.add("HITSCAN offset must be a non-negative number: " + value);
                    } else {
                        offset = parsed;
                    }
                }
                case "speed" -> {
                    pendingSpeed = value;
                }
                case "particle-speed" -> {
                    Double parsed = doubleValue(value);
                    if (parsed == null) {
                        errors.add("HITSCAN particle-speed must be a number: " + value);
                    } else {
                        particleSpeed = parsed;
                    }
                }
                default -> {
                    SpeedValues speeds = resolveSpeed(pendingSpeed, particle, speed, particleSpeed, errors);
                    return new HitscanOptions(distance, targetMode, maxHits, particle, points, offset, speeds.speed(), speeds.particleSpeed(), bodyAfter(tokens, index), List.copyOf(errors));
                }
            }
            index++;
        }

        SpeedValues speeds = resolveSpeed(pendingSpeed, particle, speed, particleSpeed, errors);
        speed = speeds.speed();
        particleSpeed = speeds.particleSpeed();
        String body = bodyAfter(tokens, index);
        if (body.isBlank()) {
            errors.add("HITSCAN is missing an action body");
        }
        return new HitscanOptions(distance, targetMode, maxHits, particle, points, offset, speed, particleSpeed, body, List.copyOf(errors));
    }

    public static boolean isHitscanOption(String token) {
        return isOption(token);
    }

    static Particle particleType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            value = value.substring("minecraft:".length());
        }
        try {
            return Particle.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isOption(String token) {
        if (token == null) {
            return false;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.startsWith("target:")
            || lower.startsWith("max-hits:")
            || lower.startsWith("particle:")
            || lower.startsWith("points:")
            || lower.startsWith("offset:")
            || lower.startsWith("speed:")
            || lower.startsWith("particle-speed:");
    }

    private static SpeedValues resolveSpeed(String pendingSpeed, String particle, String speed, double particleSpeed, List<String> errors) {
        if (pendingSpeed == null) {
            return new SpeedValues(speed, particleSpeed);
        }
        if (pendingSpeed.equalsIgnoreCase("instant")) {
            return new SpeedValues("instant", particleSpeed);
        }
        Double parsed = doubleValue(pendingSpeed);
        if (parsed == null) {
            errors.add("HITSCAN speed must be instant or a positive blocks-per-second number: " + pendingSpeed);
            return new SpeedValues(speed, particleSpeed);
        }
        if (!particle.isBlank() && parsed >= 0.0 && parsed < 1.0) {
            return new SpeedValues(speed, parsed);
        }
        if (parsed <= 0.0) {
            errors.add("HITSCAN speed must be instant or a positive blocks-per-second number: " + pendingSpeed);
            return new SpeedValues(speed, particleSpeed);
        }
        return new SpeedValues(String.valueOf(parsed), particleSpeed);
    }

    private record SpeedValues(String speed, double particleSpeed) {
    }

    private static Integer positiveInt(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double doubleValue(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String bodyAfter(List<String> tokens, int start) {
        if (tokens.size() <= start) {
            return "";
        }
        return String.join(" ", tokens.subList(start, tokens.size()));
    }
}
