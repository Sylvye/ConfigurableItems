package com.bountysmp.configurableitems.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActionParser {
    private static final Pattern NUMERIC_FOR = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*=\\s*([^;]+)\\s*;\\s*([^;]+)\\s*;\\s*([^;]+)\\s*$");
    private static final Set<String> RESERVED_FOR_VARIABLES = Set.of("X", "Y", "Z", "WORLD");

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
                if (upper.startsWith("PROJECTILE_TRAIL")) {
                    steps.add(parseProjectileTrail(raw));
                    continue;
                }
                if (upper.startsWith("HITBOX")) {
                    steps.add(parseHitbox(raw));
                    continue;
                }
                if (upper.startsWith("TIMER")) {
                    steps.add(parseTimer(raw));
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
            if (start < 0 || end < start) {
                errors.add("Malformed FOR block: " + raw);
                return new ActionStep.ForBlock(List.of(), "for", parseUntil("END_FOR"));
            }
            String spec = raw.substring(start + 1, end).trim();
            ForSpec parsed = parseForSpec(spec, arrow < 0 ? "" : raw.substring(arrow + 1).trim(), raw);
            return new ActionStep.ForBlock(parsed.values(), parsed.variable(), parseUntil("END_FOR " + parsed.variable()));
        }

        private ActionStep parseProjectileTrail(String raw) {
            index++;
            return new ActionStep.ProjectileTrail(raw, parseUntil("END_PROJECTILE_TRAIL"));
        }

        private ActionStep parseHitbox(String raw) {
            index++;
            return new ActionStep.Hitbox(raw, parseUntil("END_HITBOX"));
        }

        private ActionStep parseTimer(String raw) {
            index++;
            return new ActionStep.Timer(raw, parseUntil("END_TIMER"));
        }

        private ForSpec parseForSpec(String spec, String arrowVariable, String raw) {
            Matcher numeric = NUMERIC_FOR.matcher(spec);
            if (numeric.matches()) {
                String variable = numeric.group(1).trim();
                validateForVariable(variable, raw);
                List<String> values = numericValues(numeric.group(2), numeric.group(3), numeric.group(4), raw);
                if (!arrowVariable.isBlank() && !arrowVariable.equalsIgnoreCase(variable)) {
                    errors.add("FOR variable mismatch: " + raw);
                }
                return new ForSpec(values, variable);
            }
            if (arrowVariable.isBlank()) {
                errors.add("Malformed FOR block: " + raw);
                return new ForSpec(List.of(), "for");
            }
            List<String> values = Arrays.stream(spec.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
            String variable = arrowVariable;
            if (variable.isBlank()) {
                variable = "for";
                errors.add("Missing FOR variable: " + raw);
            }
            validateForVariable(variable, raw);
            return new ForSpec(values, variable);
        }

        private void validateForVariable(String variable, String raw) {
            if (RESERVED_FOR_VARIABLES.contains(variable.toUpperCase(Locale.ROOT))) {
                errors.add("FOR variable is reserved: " + variable + " in: " + raw);
            }
        }

        private List<String> numericValues(String rawStart, String rawStep, String rawCount, String source) {
            double start = doubleArg(rawStart, 0.0, source);
            double step = doubleArg(rawStep, 0.0, source);
            int count = intArg(rawCount, 0, source);
            if (count < 0) {
                errors.add("FOR count must be non-negative in: " + source);
                count = 0;
            }
            List<String> values = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                values.add(formatNumber(start + step * i));
            }
            return values;
        }

        private int intArg(String raw, int fallback, String source) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ex) {
                errors.add("Malformed number '" + raw + "' in: " + source);
                return fallback;
            }
        }

        private double doubleArg(String raw, double fallback, String source) {
            try {
                return Double.parseDouble(raw.trim());
            } catch (NumberFormatException ex) {
                errors.add("Malformed number '" + raw + "' in: " + source);
                return fallback;
            }
        }

        private String formatNumber(double value) {
            if (Double.isFinite(value) && value == Math.rint(value)) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }

        private boolean isTerminator(String upper) {
            return upper.equals("LOOP_END")
                || upper.equals("RANDOM_END")
                || upper.startsWith("END_FOR")
                || upper.equals("END_PROJECTILE_TRAIL")
                || upper.equals("END_HITBOX")
                || upper.equals("END_TIMER");
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

        private record ForSpec(List<String> values, String variable) {
        }
    }
}
