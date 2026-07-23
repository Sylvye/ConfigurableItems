package com.bountysmp.configurableitems.action;

import com.bountysmp.configurableitems.model.TriggerType;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActionValidator {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> BASE_VARIABLES = Set.of("SELF", "SELF_UUID", "SELF_WORLD", "SELF_X", "SELF_Y", "SELF_Z", "ITEM_ID", "ITEM_NAME");
    private static final Set<String> TARGET_VARIABLES = Set.of("TARGET", "TARGET_UUID", "ENTITY", "ENTITY_UUID", "TARGET_WORLD", "TARGET_X", "TARGET_Y", "TARGET_Z");
    private static final Set<String> BLOCK_VARIABLES = Set.of("BLOCK", "BLOCK_WORLD", "BLOCK_X", "BLOCK_Y", "BLOCK_Z");
    private static final Set<String> PROJECTILE_VARIABLES = Set.of("PROJECTILE", "PROJECTILE_WORLD", "PROJECTILE_X", "PROJECTILE_Y", "PROJECTILE_Z");
    private static final Set<String> HIT_VARIABLES = Set.of("HIT_TYPE", "HIT_WORLD", "HIT_X", "HIT_Y", "HIT_Z");
    private static final Set<String> BLOCK_ACTIONS = Set.of("SET_BLOCK", "SET_TEMP_BLOCK", "BREAK_BLOCK", "VEINMINE");
    private static final Set<String> RESERVED_FOR_VARIABLES = Set.of("X", "Y", "Z", "WORLD");
    private static final Set<TriggerType> DAMAGE_TRIGGERS = Set.of(
        TriggerType.HIT_ENTITY,
        TriggerType.HIT_PLAYER,
        TriggerType.HIT_BY_ENTITY,
        TriggerType.HIT_BY_PLAYER,
        TriggerType.HIT_GLOBAL,
        TriggerType.PROJECTILE_HIT_ENTITY,
        TriggerType.PROJECTILE_HIT_PLAYER
    );

    private ActionValidator() {
    }

    public static Optional<String> invalidKnownAction(List<String> lines) {
        return invalidKnownAction(lines, null);
    }

    public static Optional<String> invalidKnownAction(List<String> lines, TriggerType triggerType) {
        ActionParser.ParseResult parsed = ActionParser.parse(lines);
        if (!parsed.ok()) {
            return parsed.errors().stream().findFirst();
        }
        return invalidSteps(parsed.steps(), ContextScope.from(triggerType), false);
    }

    private static Optional<String> invalidSteps(List<ActionStep> steps, ContextScope context, boolean allowTrailingDelay) {
        ContextScope current = context;
        for (int i = 0; i < steps.size(); i++) {
            ActionStep step = steps.get(i);
            Optional<String> invalid = invalidStep(step, current, i == steps.size() - 1, allowTrailingDelay);
            if (invalid.isPresent()) {
                return invalid;
            }
            if (step instanceof ActionStep.Simple simple && actionName(simple.line()).equals("LAUNCH_PROJECTILE")) {
                current = current.withProjectile();
            }
        }
        return Optional.empty();
    }

    private static Optional<String> invalidStep(ActionStep step, ContextScope context, boolean last, boolean allowTrailingDelay) {
        if (step instanceof ActionStep.Simple simple) {
            return invalidSimple(simple.line(), context, last, allowTrailingDelay);
        }
        if (step instanceof ActionStep.Loop loop) {
            return invalidSteps(loop.body(), context, true);
        }
        if (step instanceof ActionStep.RandomBlock randomBlock) {
            return invalidSteps(randomBlock.body(), context, false);
        }
        if (step instanceof ActionStep.ForBlock forBlock) {
            if (RESERVED_FOR_VARIABLES.contains(forBlock.variable().toUpperCase(Locale.ROOT))) {
                return Optional.of("FOR variable is reserved: " + forBlock.variable());
            }
            return invalidSteps(forBlock.body(), context.withVariable(forBlock.variable()), false);
        }
        if (step instanceof ActionStep.ProjectileTrail projectileTrail) {
            if (!context.projectile()) {
                return Optional.of("PROJECTILE_TRAIL requires projectile context");
            }
            ProjectileTrailOptions options = ProjectileTrailOptions.parse(tokens(projectileTrail.header()));
            if (!options.errors().isEmpty()) {
                return Optional.of(options.errors().getFirst());
            }
            return invalidVariables(projectileTrail.header(), context).or(() -> invalidSteps(projectileTrail.body(), context.withProjectile(), false));
        }
        if (step instanceof ActionStep.Hitbox hitbox) {
            HitboxOptions options = HitboxOptions.parse(tokens(hitbox.header()));
            if (!options.errors().isEmpty()) {
                return Optional.of(options.errors().getFirst());
            }
            Optional<String> invalidAt = invalidAtContext(tokens(hitbox.header()), context, "HITBOX");
            if (invalidAt.isPresent()) {
                return invalidAt;
            }
            ContextScope hitContext = context.withTarget().withBlock().withHit();
            return invalidVariables(hitbox.header(), context).or(() -> invalidSteps(hitbox.body(), hitContext, false));
        }
        if (step instanceof ActionStep.Timer timer) {
            TimerOptions options = TimerOptions.parse(tokens(timer.header()));
            if (!options.errors().isEmpty()) {
                return Optional.of(options.errors().getFirst());
            }
            return invalidVariables(timer.header(), context).or(() -> invalidSteps(timer.body(), context.withVariable("TICKS"), false));
        }
        return Optional.empty();
    }

    private static Optional<String> invalidSimple(String line, ContextScope context, boolean last, boolean allowTrailingDelay) {
        List<String> tokens = tokens(line);
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        String action = tokens.getFirst().toUpperCase(Locale.ROOT);
        if (action.equals("DELAY_TICK") && last && !allowTrailingDelay) {
            return Optional.of("DELAY_TICK has no following action to delay");
        }
        if (action.equals("HITSCAN")) {
            HitscanOptions options = HitscanOptions.parse(tokens);
            if (!options.errors().isEmpty()) {
                return Optional.of(options.errors().getFirst());
            }
            ContextScope hitContext = switch (options.targetMode()) {
                case BLOCK -> context.withBlock().withHit();
                case PLAYER, MOB, ENTITY -> context.withTarget().withHit();
            };
            return invalidInline(options.body(), hitContext);
        }
        if (action.equals("IF")) {
            String[] pieces = restAfter(line, 1).split("\\s+", 2);
            if (pieces.length <= 1) {
                return Optional.of("IF is missing an action body");
            }
            return invalidVariables(pieces[0], context).or(() -> invalidInline(pieces[1], context));
        }
        if (isSelector(action)) {
            SelectorValidation selector = selector(tokens, action);
            if (selector.error().isPresent()) {
                return selector.error();
            }
            if (selector.body().isBlank()) {
                return Optional.of(selector.action() + " is missing an action body");
            }
            return invalidInline(selector.body(), context.withTarget());
        }
        Optional<String> invalidVariable = invalidVariables(line, context);
        if (invalidVariable.isPresent()) {
            return invalidVariable;
        }
        if (BLOCK_ACTIONS.contains(action) && !context.block()) {
            return Optional.of(action + " requires block context");
        }
        if (requiresTargetContext(tokens) && !context.target()) {
            return Optional.of(action + " target:TARGET requires target context");
        }
        Optional<String> invalidAt = invalidAtContext(tokens, context, action);
        if (invalidAt.isPresent()) {
            return invalidAt;
        }
        if (action.equals("VEINMINE")) {
            VeinmineOptions options = VeinmineOptions.parse(tokens);
            if (!options.errors().isEmpty()) {
                return Optional.of(options.errors().getFirst());
            }
        }
        if (action.equals("PARTICLE")) {
            ParticleShapeOptions options = ParticleShapeOptions.parse(tokens);
            if (!options.errors().isEmpty()) {
                return Optional.of(options.errors().getFirst());
            }
        }
        if (action.equals("DAMAGE")) {
            Optional<String> type = keyArg(tokens, "type");
            if (type.isPresent() && !type.get().equalsIgnoreCase("normal") && !type.get().equalsIgnoreCase("true")) {
                return Optional.of("DAMAGE type must be normal or true");
            }
        }
        if (action.equals("TELEPORT")) {
            Optional<String> safe = keyArg(tokens, "safe");
            if (safe.isPresent() && !safe.get().equalsIgnoreCase("true") && !safe.get().equalsIgnoreCase("false")) {
                return Optional.of("TELEPORT safe must be true or false");
            }
        }
        if (action.equals("LAUNCH_PROJECTILE")) {
            Optional<String> gravity = keyArg(tokens, "gravity");
            Optional<String> track = keyArg(tokens, "track");
            if (gravity.isPresent() && !booleanValue(gravity.get()) || track.isPresent() && !booleanValue(track.get())) {
                return Optional.of("LAUNCH_PROJECTILE gravity and track must be true or false");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> invalidInline(String body, ContextScope context) {
        List<String> parts = ActionParser.splitInline(body);
        for (int i = 0; i < parts.size(); i++) {
            Optional<String> invalid = invalidSimple(parts.get(i), context, i == parts.size() - 1, false);
            if (invalid.isPresent()) {
                return invalid;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> invalidVariables(String line, ContextScope context) {
        Matcher matcher = PLACEHOLDER.matcher(line);
        while (matcher.find()) {
            Matcher identifiers = IDENTIFIER.matcher(matcher.group(1));
            while (identifiers.find()) {
                String rawVariable = identifiers.group();
                String variable = rawVariable.toUpperCase(Locale.ROOT);
                if (!rawVariable.equals(variable) && !context.variables().contains(variable)) {
                    continue;
                }
                if (!context.allows(variable)) {
                    return Optional.of("Invalid variable {" + variable + "} for this context");
                }
            }
        }
        return Optional.empty();
    }

    private static boolean requiresTargetContext(List<String> tokens) {
        return keyArg(tokens, "target").map(value -> value.equalsIgnoreCase("TARGET")).orElse(false);
    }

    private static Optional<String> invalidAtContext(List<String> tokens, ContextScope context, String action) {
        Optional<String> at = keyArg(tokens, "at");
        if (at.isEmpty()) {
            return Optional.empty();
        }
        String value = at.get().toUpperCase(Locale.ROOT);
        if (value.equals("TARGET") && !context.target()) {
            return Optional.of(action + " at:TARGET requires target context");
        }
        if (value.equals("BLOCK") && !context.block()) {
            return Optional.of(action + " at:BLOCK requires block context");
        }
        if (value.equals("HIT") && !context.hit()) {
            return Optional.of(action + " at:HIT requires hit context");
        }
        if (value.equals("PROJECTILE") && !context.projectile()) {
            return Optional.of(action + " at:PROJECTILE requires projectile context");
        }
        return Optional.empty();
    }

    private static Optional<String> keyArg(List<String> tokens, String key) {
        String prefix = key.toLowerCase(Locale.ROOT) + ":";
        return tokens.stream()
            .filter(token -> token.toLowerCase(Locale.ROOT).startsWith(prefix))
            .map(token -> token.substring(token.indexOf(':') + 1))
            .findFirst();
    }

    private static boolean isSelector(String action) {
        return action.equals("AROUND") || action.equals("MOB_AROUND") || action.equals("NEAREST") || action.equals("MOB_NEAREST");
    }

    private static boolean isSelectorOption(String token) {
        return token != null && token.toLowerCase(Locale.ROOT).startsWith("target:");
    }

    private static boolean booleanValue(String raw) {
        return raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false");
    }

    private static SelectorValidation selector(List<String> tokens, String action) {
        String canonical = action.equals("MOB_AROUND") ? "AROUND" : action.equals("MOB_NEAREST") ? "NEAREST" : action;
        int index = 2;
        while (index < tokens.size() && isSelectorOption(tokens.get(index))) {
            String value = tokens.get(index).substring(tokens.get(index).indexOf(':') + 1);
            Optional<TargetKind> target = TargetKind.parse(value);
            if (target.isEmpty() || target.get() == TargetKind.BLOCK) {
                return new SelectorValidation(canonical, "", Optional.of(canonical + " target must be " + TargetKind.allowedValues(false) + ": " + value));
            }
            index++;
        }
        return new SelectorValidation(canonical, bodyAfter(tokens, index), Optional.empty());
    }

    private static List<String> tokens(String line) {
        return List.of((line == null ? "" : line.trim()).split("\\s+")).stream().filter(value -> !value.isBlank()).toList();
    }

    private static String actionName(String line) {
        List<String> tokens = tokens(line);
        return tokens.isEmpty() ? "" : tokens.getFirst().toUpperCase(Locale.ROOT);
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

    private static String bodyAfter(List<String> tokens, int start) {
        if (tokens.size() <= start) {
            return "";
        }
        return String.join(" ", tokens.subList(start, tokens.size()));
    }

    private record SelectorValidation(String action, String body, Optional<String> error) {
    }

    private record ContextScope(boolean target, boolean block, boolean projectile, boolean hit, Set<String> variables) {
        static ContextScope from(TriggerType type) {
            boolean hasTarget = type != null && TriggerType.TARGET_TRIGGERS.contains(type);
            boolean hasBlock = type != null && TriggerType.BLOCK_TRIGGERS.contains(type);
            boolean hasProjectile = type != null && TriggerType.PROJECTILE_TRIGGERS.contains(type);
            Set<String> variables = type != null && DAMAGE_TRIGGERS.contains(type) ? Set.of("CRITICAL") : Set.of();
            return new ContextScope(hasTarget, hasBlock, hasProjectile, false, variables);
        }

        ContextScope withTarget() {
            return new ContextScope(true, block, projectile, hit, variables);
        }

        ContextScope withBlock() {
            return new ContextScope(target, true, projectile, hit, variables);
        }

        ContextScope withProjectile() {
            return new ContextScope(target, block, true, hit, variables);
        }

        ContextScope withHit() {
            return new ContextScope(target, block, projectile, true, variables);
        }

        ContextScope withVariable(String variable) {
            Set<String> copy = new HashSet<>(variables);
            copy.add(variable.toUpperCase(Locale.ROOT));
            return new ContextScope(target, block, projectile, hit, Set.copyOf(copy));
        }

        boolean allows(String variable) {
            return BASE_VARIABLES.contains(variable)
                || variables.contains(variable)
                || target && TARGET_VARIABLES.contains(variable)
                || block && BLOCK_VARIABLES.contains(variable)
                || projectile && PROJECTILE_VARIABLES.contains(variable)
                || hit && HIT_VARIABLES.contains(variable);
        }
    }
}
