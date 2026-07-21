package com.bountysmp.configurableitems.action;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ActionValidator {
    private ActionValidator() {
    }

    public static Optional<String> invalidKnownAction(List<String> lines) {
        ActionParser.ParseResult parsed = ActionParser.parse(lines);
        if (!parsed.ok()) {
            return parsed.errors().stream().findFirst();
        }
        return invalidSteps(parsed.steps());
    }

    private static Optional<String> invalidSteps(List<ActionStep> steps) {
        for (ActionStep step : steps) {
            Optional<String> invalid = invalidStep(step);
            if (invalid.isPresent()) {
                return invalid;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> invalidStep(ActionStep step) {
        if (step instanceof ActionStep.Simple simple) {
            return invalidSimple(simple.line());
        }
        if (step instanceof ActionStep.Loop loop) {
            return invalidSteps(loop.body());
        }
        if (step instanceof ActionStep.RandomBlock randomBlock) {
            return invalidSteps(randomBlock.body());
        }
        if (step instanceof ActionStep.ForBlock forBlock) {
            return invalidSteps(forBlock.body());
        }
        if (step instanceof ActionStep.ProjectileTrail projectileTrail) {
            ProjectileTrailOptions options = ProjectileTrailOptions.parse(tokens(projectileTrail.header()));
            if (!options.errors().isEmpty()) {
                return Optional.of(options.errors().getFirst());
            }
            return invalidSteps(projectileTrail.body());
        }
        return Optional.empty();
    }

    private static Optional<String> invalidSimple(String line) {
        List<String> tokens = tokens(line);
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        String action = tokens.getFirst().toUpperCase(Locale.ROOT);
        if (action.equals("HITSCAN")) {
            HitscanOptions options = HitscanOptions.parse(tokens);
            if (!options.errors().isEmpty()) {
                return Optional.of(options.errors().getFirst());
            }
            return invalidInline(options.body());
        }
        if (action.equals("VEINMINE")) {
            VeinmineOptions options = VeinmineOptions.parse(tokens);
            if (!options.errors().isEmpty()) {
                return Optional.of(options.errors().getFirst());
            }
        }
        if (action.equals("IF")) {
            String[] pieces = restAfter(line, 1).split("\\s+", 2);
            return pieces.length > 1 ? invalidInline(pieces[1]) : Optional.of("IF is missing an action body");
        }
        if (action.equals("AROUND") || action.equals("MOB_AROUND") || action.equals("NEAREST") || action.equals("MOB_NEAREST")) {
            return invalidInline(restAfter(line, 2));
        }
        return Optional.empty();
    }

    private static Optional<String> invalidInline(String body) {
        for (String part : ActionParser.splitInline(body)) {
            Optional<String> invalid = invalidSimple(part);
            if (invalid.isPresent()) {
                return invalid;
            }
        }
        return Optional.empty();
    }

    private static List<String> tokens(String line) {
        return List.of((line == null ? "" : line.trim()).split("\\s+")).stream().filter(value -> !value.isBlank()).toList();
    }

    private static String restAfter(String line, int tokenCount) {
        String value = line == null ? "" : line.trim();
        for (int i = 0; i < tokenCount; i++) {
            int space = value.indexOf(' ');
            if (space < 0) {
                return "";
            }
            value = value.substring(space + 1).trim();
        }
        return value;
    }
}
