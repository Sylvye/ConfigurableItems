package com.bountysmp.configurableitems.gui;

public final class CooldownInput {
    private CooldownInput() {
    }

    public static ParseResult parse(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.equalsIgnoreCase("clear")) {
            return new ParseResult(true, 0, "");
        }
        String[] parts = trimmed.split("\\s+", 2);
        if (parts.length == 0 || parts[0].isBlank()) {
            return ParseResult.invalid();
        }
        int ticks;
        try {
            ticks = Integer.parseInt(parts[0]);
        } catch (NumberFormatException ex) {
            return ParseResult.invalid();
        }
        if (ticks < 0) {
            return ParseResult.invalid();
        }
        if (ticks == 0) {
            return new ParseResult(true, 0, "");
        }
        String message = parts.length < 2 ? "" : parts[1].trim();
        return new ParseResult(true, ticks, message);
    }

    public record ParseResult(boolean valid, int ticks, String message) {
        private static ParseResult invalid() {
            return new ParseResult(false, 0, "");
        }
    }
}
