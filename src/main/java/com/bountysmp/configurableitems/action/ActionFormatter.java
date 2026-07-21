package com.bountysmp.configurableitems.action;

import com.bountysmp.configurableitems.model.CustomItemDefinition;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActionFormatter {
    private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z0-9_]+)}");
    private static final Pattern FOR_HEADER = Pattern.compile("(?i)^FOR\\s+\\[([^]]*)]\\s*>\\s*([A-Za-z0-9_]+)\\s*$");
    private static final Pattern NUMERIC_FOR_HEADER = Pattern.compile("(?i)^FOR\\s+\\[\\s*([A-Za-z0-9_]+)\\s*=\\s*([^;]+)\\s*;\\s*([^;]+)\\s*;\\s*([^;]+)\\s*]\\s*$");
    private static final Pattern END_FOR = Pattern.compile("(?i)^END_?FOR\\s+([A-Za-z0-9_]+)\\s*$");

    public static final Set<String> ACTION_NAMES = Set.of(
        "DELAY_TICK", "IF", "LOOP_START", "LOOP_END", "RANDOM_RUN", "RANDOM_END", "FOR", "END_FOR",
        "AROUND", "MOB_AROUND", "NEAREST", "MOB_NEAREST", "HITSCAN",
        "DAMAGE", "HEAL", "SET_HEALTH", "KILL", "BURN", "INVULNERABILITY", "TELEPORT", "VELOCITY", "DASH",
        "SEND_MESSAGE", "ACTIONBAR", "PARTICLE", "PARTICLE_LINE", "PROJECTILE_TRAIL", "END_PROJECTILE_TRAIL",
        "SET_BLOCK", "SET_TEMP_BLOCK", "BREAK_BLOCK", "DROPITEM", "VEINMINE"
    );

    public static final Set<String> ENTITY_ACTION_NAMES = Set.of(
        "DAMAGE", "HEAL", "SET_HEALTH", "KILL", "BURN", "INVULNERABILITY", "TELEPORT", "VELOCITY"
    );

    private ActionFormatter() {
    }

    public static boolean isKnownActionName(String name) {
        return name != null && ACTION_NAMES.contains(name.toUpperCase(Locale.ROOT));
    }

    public static boolean isEntityActionName(String name) {
        return name != null && ENTITY_ACTION_NAMES.contains(name.toUpperCase(Locale.ROOT));
    }

    public static String normalizeLine(String raw) {
        if (raw == null) {
            return "";
        }
        String line = normalizeVariables(raw.trim());
        if (line.isBlank()) {
            return "";
        }
        if (line.contains("+++")) {
            return joinNormalized(line, "\\s*\\+\\+\\+\\s*", " +++ ");
        }
        Matcher forMatcher = FOR_HEADER.matcher(line);
        if (forMatcher.matches()) {
            return "FOR [" + forMatcher.group(1).trim() + "] > " + forMatcher.group(2).toUpperCase(Locale.ROOT);
        }
        Matcher numericForMatcher = NUMERIC_FOR_HEADER.matcher(line);
        if (numericForMatcher.matches()) {
            return "FOR [" + numericForMatcher.group(1).toUpperCase(Locale.ROOT)
                + "=" + numericForMatcher.group(2).trim()
                + ";" + numericForMatcher.group(3).trim()
                + ";" + numericForMatcher.group(4).trim()
                + "]";
        }
        Matcher endForMatcher = END_FOR.matcher(line);
        if (endForMatcher.matches()) {
            return "END_FOR " + endForMatcher.group(1).toUpperCase(Locale.ROOT);
        }
        String[] pieces = line.split("\\s+", 2);
        String first = pieces[0].toUpperCase(Locale.ROOT);
        if (!ACTION_NAMES.contains(first)) {
            return line;
        }
        String rest = pieces.length > 1 ? pieces[1].trim() : "";
        if (first.equals("IF")) {
            String[] ifParts = rest.split("\\s+", 2);
            if (ifParts.length < 2) {
                return rest.isBlank() ? first : first + " " + rest;
            }
            return first + " " + ifParts[0] + " " + normalizeInline(ifParts[1]);
        }
        if (first.equals("HITSCAN")) {
            return normalizeHitscan(first, rest);
        }
        if (first.equals("VEINMINE")) {
            return normalizeVeinmine(first, rest);
        }
        if (first.equals("PROJECTILE_TRAIL")) {
            return normalizeProjectileTrail(first, rest);
        }
        if (Set.of("AROUND", "MOB_AROUND", "NEAREST", "MOB_NEAREST").contains(first)) {
            String[] selectorParts = rest.split("\\s+", 2);
            if (selectorParts.length < 2) {
                return rest.isBlank() ? first : first + " " + rest;
            }
            return first + " " + selectorParts[0] + " " + normalizeInline(selectorParts[1]);
        }
        if (line.contains("<+>")) {
            return joinNormalized(line, "\\s*<\\+>\\s*", " <+> ");
        }
        return rest.isBlank() ? first : first + " " + rest;
    }

    public static CustomItemDefinition.TriggerCommandDef normalize(CustomItemDefinition.TriggerCommandDef command) {
        return command.withCommand(normalizeLine(command.command()));
    }

    public static List<String> normalizeLines(List<String> lines) {
        return lines.stream().map(ActionFormatter::normalizeLine).filter(line -> !line.isBlank()).toList();
    }

    public static String normalizeVariables(String raw) {
        Matcher matcher = VARIABLE.matcher(raw == null ? "" : raw);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement("{" + matcher.group(1).toUpperCase(Locale.ROOT) + "}"));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String normalizeInline(String raw) {
        return raw.contains("<+>") ? joinNormalized(raw, "\\s*<\\+>\\s*", " <+> ") : normalizeLine(raw);
    }

    private static String normalizeHitscan(String first, String rest) {
        if (rest.isBlank()) {
            return first;
        }
        String[] tokens = rest.split("\\s+");
        int index = 0;
        StringBuilder header = new StringBuilder(first);
        if (index < tokens.length) {
            header.append(' ').append(tokens[index]);
            index++;
        }
        while (index < tokens.length && HitscanOptions.isHitscanOption(tokens[index])) {
            String token = tokens[index];
            String key = token.substring(0, token.indexOf(':')).toLowerCase(Locale.ROOT);
            String value = token.substring(token.indexOf(':') + 1);
            if (key.equals("target") || key.equals("particle")) {
                value = value.toUpperCase(Locale.ROOT);
            } else if (key.equals("max-hits") && value.equalsIgnoreCase("all")) {
                value = "all";
            }
            header.append(' ').append(key).append(':').append(value);
            index++;
        }
        String body = index < tokens.length ? String.join(" ", List.of(tokens).subList(index, tokens.length)) : "";
        return body.isBlank() ? header.toString() : header + " " + normalizeInline(body);
    }

    private static String normalizeVeinmine(String first, String rest) {
        if (rest.isBlank()) {
            return first;
        }
        String[] tokens = rest.split("\\s+");
        int index = 0;
        StringBuilder output = new StringBuilder(first);
        if (index < tokens.length && !VeinmineOptions.isVeinmineOption(tokens[index])) {
            output.append(' ').append(tokens[index]);
            index++;
        }
        while (index < tokens.length) {
            String token = tokens[index];
            if (!VeinmineOptions.isVeinmineOption(token)) {
                output.append(' ').append(token);
                index++;
                continue;
            }
            String key = token.substring(0, token.indexOf(':')).toLowerCase(Locale.ROOT);
            String value = token.substring(token.indexOf(':') + 1);
            switch (key) {
                case "drop", "use-enchants", "use-durability", "effect", "xp", "mode", "match" -> value = value.toLowerCase(Locale.ROOT);
                case "filter" -> value = normalizeFilterValue(value);
                case "replace" -> value = normalizeMaterialValue(value);
                default -> {}
            }
            output.append(' ').append(key).append(':').append(value);
            index++;
        }
        return output.toString();
    }

    private static String normalizeProjectileTrail(String first, String rest) {
        if (rest.isBlank()) {
            return first;
        }
        String[] tokens = rest.split("\\s+");
        StringBuilder output = new StringBuilder(first);
        for (String token : tokens) {
            if (!ProjectileTrailOptions.isProjectileTrailOption(token)) {
                output.append(' ').append(token);
                continue;
            }
            String key = token.substring(0, token.indexOf(':')).toLowerCase(Locale.ROOT);
            String value = token.substring(token.indexOf(':') + 1);
            if (key.equals("particle")) {
                value = value.toUpperCase(Locale.ROOT);
            }
            output.append(' ').append(key).append(':').append(value);
        }
        return output.toString();
    }

    private static String normalizeFilterValue(String value) {
        return String.join(",", Pattern.compile("\\s*,\\s*").splitAsStream(value)
            .filter(entry -> !entry.isBlank())
            .map(entry -> entry.startsWith("#") ? "#" + entry.substring(1).toLowerCase(Locale.ROOT) : normalizeMaterialValue(entry))
            .toList());
    }

    private static String normalizeMaterialValue(String value) {
        return value.contains(":") ? value.toLowerCase(Locale.ROOT) : value.toUpperCase(Locale.ROOT);
    }

    private static String joinNormalized(String raw, String regex, String separator) {
        return Pattern.compile(regex).splitAsStream(raw)
            .map(ActionFormatter::normalizeLine)
            .filter(value -> !value.isBlank())
            .reduce((left, right) -> left + separator + right)
            .orElse("");
    }
}
