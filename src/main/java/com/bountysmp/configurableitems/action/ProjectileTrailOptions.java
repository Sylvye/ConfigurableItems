package com.bountysmp.configurableitems.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Particle;

public final class ProjectileTrailOptions {
    private final String particle;
    private final int count;
    private final int points;
    private final int interval;
    private final int duration;
    private final double offset;
    private final double speed;
    private final List<String> errors;

    private ProjectileTrailOptions(String particle, int count, int points, int interval, int duration, double offset, double speed, List<String> errors) {
        this.particle = particle;
        this.count = count;
        this.points = points;
        this.interval = interval;
        this.duration = duration;
        this.offset = offset;
        this.speed = speed;
        this.errors = List.copyOf(errors);
    }

    public static ProjectileTrailOptions parse(List<String> tokens) {
        String particle = "";
        int count = 1;
        int points = 1;
        int interval = 1;
        int duration = 100;
        double offset = 0.0;
        double speed = 0.0;
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            int colon = token.indexOf(':');
            if (colon < 0) {
                errors.add("Unknown PROJECTILE_TRAIL token: " + token);
                continue;
            }
            String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1);
            switch (key) {
                case "particle" -> {
                    particle = value.toUpperCase(Locale.ROOT);
                    if (particleType(value) == null) {
                        errors.add("Unknown PROJECTILE_TRAIL particle: " + value);
                    }
                }
                case "count" -> count = positiveInt(key, value, count, errors);
                case "points" -> points = positiveInt(key, value, points, errors);
                case "interval" -> interval = positiveInt(key, value, interval, errors);
                case "duration" -> duration = positiveInt(key, value, duration, errors);
                case "offset" -> offset = doubleArg(key, value, offset, errors);
                case "speed" -> speed = doubleArg(key, value, speed, errors);
                default -> errors.add("Unknown PROJECTILE_TRAIL option: " + key);
            }
        }
        return new ProjectileTrailOptions(particle, count, points, interval, duration, offset, speed, errors);
    }

    public static boolean isProjectileTrailOption(String token) {
        int colon = token.indexOf(':');
        if (colon < 1) {
            return false;
        }
        return switch (token.substring(0, colon).toLowerCase(Locale.ROOT)) {
            case "particle", "count", "points", "interval", "duration", "offset", "speed" -> true;
            default -> false;
        };
    }

    public static Particle particleType(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String particle() {
        return particle;
    }

    public int count() {
        return count;
    }

    public int points() {
        return points;
    }

    public int interval() {
        return interval;
    }

    public int duration() {
        return duration;
    }

    public double offset() {
        return offset;
    }

    public double speed() {
        return speed;
    }

    public List<String> errors() {
        return errors;
    }

    private static int positiveInt(String key, String raw, int fallback, List<String> errors) {
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed <= 0) {
                errors.add("PROJECTILE_TRAIL " + key + " must be positive: " + raw);
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            errors.add("PROJECTILE_TRAIL " + key + " must be a positive integer: " + raw);
            return fallback;
        }
    }

    private static double doubleArg(String key, String raw, double fallback, List<String> errors) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            errors.add("PROJECTILE_TRAIL " + key + " must be a number: " + raw);
            return fallback;
        }
    }
}
