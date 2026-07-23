package com.bountysmp.configurableitems.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record TimerOptions(int duration, int interval, List<String> errors) {
    public static TimerOptions parse(List<String> tokens) {
        int duration = -1;
        int interval = 1;
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            int colon = token.indexOf(':');
            if (colon < 1) {
                if (duration < 0) {
                    duration = positiveInt("duration", token, duration, errors);
                } else {
                    errors.add("Unknown TIMER token: " + token);
                }
                continue;
            }
            String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1);
            switch (key) {
                case "duration" -> duration = positiveInt("duration", value, duration, errors);
                case "interval" -> interval = positiveInt("interval", value, interval, errors);
                default -> errors.add("Unknown TIMER option: " + key);
            }
        }
        if (duration < 0) {
            errors.add("TIMER requires duration:<ticks>");
            duration = 1;
        }
        return new TimerOptions(duration, interval, List.copyOf(errors));
    }

    public static boolean isTimerOption(String token) {
        int colon = token == null ? -1 : token.indexOf(':');
        if (colon < 1) {
            return false;
        }
        String key = token.substring(0, colon).toLowerCase(Locale.ROOT);
        return key.equals("duration") || key.equals("interval");
    }

    private static int positiveInt(String key, String raw, int fallback, List<String> errors) {
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                errors.add("TIMER " + key + " must be positive: " + raw);
                return fallback;
            }
            return value;
        } catch (NumberFormatException ex) {
            errors.add("TIMER " + key + " must be an integer: " + raw);
            return fallback;
        }
    }
}
