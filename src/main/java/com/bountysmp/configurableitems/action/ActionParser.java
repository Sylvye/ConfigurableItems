package com.bountysmp.configurableitems.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ActionParser {
    private ActionParser() {
    }

    public static ParseResult parse(List<String> lines) {
        Parser parser = new Parser(lines);
        List<ActionStep> steps = parser.parseUntil(null);
        return new ParseResult(steps, List.copyOf(parser.errors));
    }

    public static List<String> splitInline(String line) {
        return Arrays.stream(line.split("\\s*<\\+>\\s*"))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    public static List<String> splitRandomInline(String line) {
        return Arrays.stream(line.split("\\s*\\+\\+\\+\\s*"))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    public record ParseResult(List<ActionStep> steps, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    private static final class Parser {
        private final List<String> lines;
        private final List<String> errors = new ArrayList<>();
        private int index;

        private Parser(List<String> lines) {
            this.lines = lines;
        }

        private List<ActionStep> parseUntil(String terminator) {
            List<ActionStep> steps = new ArrayList<>();
            while (index < lines.size()) {
                String raw = lines.get(index).trim();
                String upper = raw.toUpperCase(Locale.ROOT);
                if (raw.isBlank()) {
                    index++;
                    continue;
                }
                if (terminator != null && matchesTerminator(upper, terminator)) {
                    index++;
                    return steps;
                }
                if (isTerminator(upper)) {
                    errors.add("Unmatched block terminator: " + raw);
                    index++;
                    continue;
                }
                if (upper.startsWith("LOOP_START")) {
                    steps.add(parseLoop(raw));
                    continue;
                }
                if (upper.startsWith("RANDOM_RUN")) {
                    steps.add(parseRandom(raw));
                    continue;
                }
                if (upper.startsWith("FOR ")) {
                    steps.add(parseFor(raw));
                    continue;
                }
                if (hasNestedInlineBody(upper)) {
                    steps.add(new ActionStep.Simple(raw));
                    index++;
                    continue;
                }
                for (String part : splitInline(raw)) {
                    steps.add(new ActionStep.Simple(part));
                }
                index++;
            }
            if (terminator != null) {
                errors.add("Missing block terminator: " + terminator);
            }
            return steps;
        }

        private ActionStep parseLoop(String raw) {
            index++;
            String[] parts = raw.split("\\s+");
            int times = parts.length > 1 ? intArg(parts[1], 1, raw) : 1;
            int delay = parts.length > 2 ? intArg(parts[2], 0, raw) : 0;
            return new ActionStep.Loop(Math.max(0, times), Math.max(0, delay), parseUntil("LOOP_END"));
        }

        private ActionStep parseRandom(String raw) {
            index++;
            int selectionCount = 1;
            for (String token : raw.split("\\s+")) {
                if (token.toLowerCase(Locale.ROOT).startsWith("selectioncount:")) {
                    selectionCount = intArg(token.substring(token.indexOf(':') + 1), 1, raw);
                }
            }
            return new ActionStep.RandomBlock(Math.max(0, selectionCount), parseUntil("RANDOM_END"));
        }

        private ActionStep parseFor(String raw) {
            index++;
            int start = raw.indexOf('[');
            int end = raw.indexOf(']');
            int arrow = raw.indexOf('>', end);
            if (start < 0 || end < start || arrow < 0) {
                errors.add("Malformed FOR block: " + raw);
                return new ActionStep.ForBlock(List.of(), "for", parseUntil("END_FOR"));
            }
            List<String> values = Arrays.stream(raw.substring(start + 1, end).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
            String variable = raw.substring(arrow + 1).trim();
            if (variable.isBlank()) {
                variable = "for";
                errors.add("Missing FOR variable: " + raw);
            }
            return new ActionStep.ForBlock(values, variable, parseUntil("END_FOR " + variable));
        }

        private int intArg(String raw, int fallback, String source) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ex) {
                errors.add("Malformed number '" + raw + "' in: " + source);
                return fallback;
            }
        }

        private boolean isTerminator(String upper) {
            return upper.equals("LOOP_END") || upper.equals("RANDOM_END") || upper.startsWith("END_FOR");
        }

        private boolean matchesTerminator(String upper, String terminator) {
            String expected = terminator.toUpperCase(Locale.ROOT);
            if (expected.startsWith("END_FOR ")) {
                return upper.equals(expected) || upper.equals("ENDFOR " + expected.substring("END_FOR ".length()));
            }
            return upper.equals(expected);
        }

        private boolean hasNestedInlineBody(String upper) {
            return upper.startsWith("IF ")
                || upper.startsWith("AROUND ")
                || upper.startsWith("MOB_AROUND ")
                || upper.startsWith("NEAREST ")
                || upper.startsWith("MOB_NEAREST ")
                || upper.startsWith("HITSCAN ");
        }
    }
}
