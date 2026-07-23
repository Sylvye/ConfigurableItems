package com.bountysmp.configurableitems.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class ActionCommandRows {
    private ActionCommandRows() {
    }

    static List<Row> rows(List<String> commands) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            int end = blockEnd(commands, i);
            boolean block = end > i;
            rows.add(new Row(i, end, block, nestedLineCount(i, end, block), summary(commands, i, end, block)));
            i = end;
        }
        return rows;
    }

    static int blockEnd(List<String> commands, int index) {
        if (index < 0 || index >= commands.size() || !isBlockHeader(firstToken(commands.get(index)))) {
            return index;
        }
        int depth = 0;
        for (int i = index; i < commands.size(); i++) {
            String token = firstToken(commands.get(i));
            if (isBlockHeader(token)) {
                depth++;
            } else if (isBlockTerminator(token)) {
                depth--;
                if (depth <= 0) {
                    return i;
                }
            }
        }
        return index;
    }

    static boolean isBlockHeader(String token) {
        String normalized = token.toUpperCase(Locale.ROOT);
        return normalized.equals("LOOP_START")
            || normalized.equals("RANDOM_RUN")
            || normalized.equals("FOR")
            || normalized.equals("PROJECTILE_TRAIL")
            || normalized.equals("HITBOX")
            || normalized.equals("TIMER");
    }

    private static boolean isBlockTerminator(String token) {
        String normalized = token.toUpperCase(Locale.ROOT);
        return normalized.equals("LOOP_END")
            || normalized.equals("RANDOM_END")
            || normalized.equals("END_FOR")
            || normalized.equals("ENDFOR")
            || normalized.equals("END_PROJECTILE_TRAIL")
            || normalized.equals("END_HITBOX")
            || normalized.equals("END_TIMER");
    }

    private static int nestedLineCount(int start, int end, boolean block) {
        return block ? Math.max(0, end - start - 1) : 0;
    }

    private static String summary(List<String> commands, int start, int end, boolean block) {
        String command = commands.get(start);
        if (!block) {
            return command;
        }
        return command + " (" + nestedLineCount(start, end, true) + " nested)";
    }

    private static String firstToken(String line) {
        return Arrays.stream((line == null ? "" : line.trim()).split("\\s+"))
            .filter(value -> !value.isBlank())
            .findFirst()
            .orElse("")
            .toUpperCase(Locale.ROOT);
    }

    record Row(int startIndex, int endIndex, boolean block, int nestedLineCount, String summary) {
    }
}
