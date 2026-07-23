package com.bountysmp.configurableitems.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record ParticleShapeOptions(Shape shape, double size, double rotX, double rotY, double rotZ, int points, List<String> errors) {
    public enum Shape {
        POINT(0),
        CIRCLE(0),
        LINE(0),
        TRIANGLE(3),
        SQUARE(4),
        PENTAGON(5),
        HEXAGON(6),
        SEPTAGON(7),
        OCTAGON(8),
        NONAGON(9),
        DECAGON(10);

        private final int sides;

        Shape(int sides) {
            this.sides = sides;
        }

        int sides() {
            return sides;
        }
    }

    public static ParticleShapeOptions parse(List<String> tokens) {
        Shape shape = Shape.POINT;
        double size = 1.0;
        double rotX = 0.0;
        double rotY = 0.0;
        double rotZ = 0.0;
        int points = 24;
        List<String> errors = new ArrayList<>();
        for (String token : tokens) {
            int colon = token.indexOf(':');
            if (colon < 1) {
                continue;
            }
            String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1);
            switch (key) {
                case "shape" -> {
                    try {
                        shape = Shape.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        errors.add("PARTICLE shape is invalid: " + value);
                    }
                }
                case "size" -> {
                    Double parsed = doubleValue(value);
                    if (parsed == null || parsed < 0.0) {
                        errors.add("PARTICLE size must be non-negative: " + value);
                    } else {
                        size = parsed;
                    }
                }
                case "rotation" -> {
                    String[] parts = value.split(",");
                    if (parts.length != 3) {
                        errors.add("PARTICLE rotation must be x,y,z: " + value);
                    } else {
                        Double x = doubleValue(parts[0]);
                        Double y = doubleValue(parts[1]);
                        Double z = doubleValue(parts[2]);
                        if (x == null || y == null || z == null) {
                            errors.add("PARTICLE rotation must be numeric: " + value);
                        } else {
                            rotX = x;
                            rotY = y;
                            rotZ = z;
                        }
                    }
                }
                case "points" -> {
                    try {
                        int parsed = Integer.parseInt(value);
                        if (parsed <= 0) {
                            errors.add("PARTICLE points must be positive: " + value);
                        } else {
                            points = parsed;
                        }
                    } catch (NumberFormatException ex) {
                        errors.add("PARTICLE points must be an integer: " + value);
                    }
                }
                default -> {}
            }
        }
        return new ParticleShapeOptions(shape, size, rotX, rotY, rotZ, points, List.copyOf(errors));
    }

    public static boolean isParticleShapeOption(String token) {
        int colon = token == null ? -1 : token.indexOf(':');
        if (colon < 1) {
            return false;
        }
        return switch (token.substring(0, colon).toLowerCase(Locale.ROOT)) {
            case "shape", "size", "rotation", "points" -> true;
            default -> false;
        };
    }

    private static Double doubleValue(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
