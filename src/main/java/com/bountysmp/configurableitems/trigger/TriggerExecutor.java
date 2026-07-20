package com.bountysmp.configurableitems.trigger;

import com.bountysmp.configurableitems.action.ActionEngine;
import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.model.TriggerType;
import com.bountysmp.configurableitems.storage.ItemRepository;
import com.bountysmp.configurableitems.util.TextUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.plugin.Plugin;

public final class TriggerExecutor {
    private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z0-9_]+)}");
    private static final Set<String> BASE_VARIABLES = Set.of("SELF", "SELF_UUID", "WORLD", "X", "Y", "Z", "ITEM_ID", "ITEM_NAME");
    private static final Set<String> TARGET_VARIABLES = Set.of("TARGET", "TARGET_UUID", "ENTITY", "ENTITY_UUID");
    private static final Set<String> BLOCK_VARIABLES = Set.of("BLOCK");
    private static final Set<String> PROJECTILE_VARIABLES = Set.of("PROJECTILE");

    private final Plugin plugin;
    private final ItemRepository repository;
    private final ActionEngine actionEngine;
    private final Map<CooldownKey, Long> cooldowns = new HashMap<>();

    public TriggerExecutor(Plugin plugin, ItemRepository repository, ActionEngine actionEngine) {
        this.plugin = plugin;
        this.repository = repository;
        this.actionEngine = actionEngine;
    }

    public void fire(TriggerContext context) {
        CustomItemDefinition item = repository.get(context.itemId());
        if (item == null) {
            return;
        }
        List<CustomItemDefinition.TriggerCommandDef> commands = item.triggers().get(context.type());
        if (commands == null || commands.isEmpty()) {
            return;
        }
        List<String> renderedCommands = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < commands.size(); i++) {
            CustomItemDefinition.TriggerCommandDef command = commands.get(i);
            String raw = command.command();
            String upper = raw.trim().toUpperCase(Locale.ROOT);
            if (isBlockHeader(upper)) {
                CooldownCheck cooldown = checkCooldown(context, i, command, now);
                if (!cooldown.active()) {
                    Optional<String> rendered = render(raw, context);
                    if (rendered.isEmpty()) {
                        plugin.getLogger().warning("Skipped " + context.type() + " command for " + context.itemId() + " because a variable was missing: " + raw);
                        continue;
                    }
                    renderedCommands.add(rendered.get());
                    continue;
                }
                sendCooldownMessage(context, command, cooldown.remainingSeconds());
                i = skipBlock(commands, i);
                continue;
            }
            if (!isBlockTerminator(upper)) {
                CooldownCheck cooldown = checkCooldown(context, i, command, now);
                if (cooldown.active()) {
                    sendCooldownMessage(context, command, cooldown.remainingSeconds());
                    continue;
                }
            }
            Optional<String> rendered = render(raw, context);
            if (rendered.isEmpty()) {
                plugin.getLogger().warning("Skipped " + context.type() + " command for " + context.itemId() + " because a variable was missing: " + raw);
                continue;
            }
            renderedCommands.add(rendered.get());
        }
        actionEngine.execute(context, renderedCommands);
    }

    public static Set<String> allowedVariables(TriggerType type) {
        Set<String> allowed = new HashSet<>(BASE_VARIABLES);
        if (TriggerType.TARGET_TRIGGERS.contains(type)) {
            allowed.addAll(TARGET_VARIABLES);
        }
        if (TriggerType.BLOCK_TRIGGERS.contains(type)) {
            allowed.addAll(BLOCK_VARIABLES);
        }
        if (TriggerType.PROJECTILE_TRIGGERS.contains(type)) {
            allowed.addAll(PROJECTILE_VARIABLES);
        }
        return allowed;
    }

    public static Optional<String> invalidVariable(String command, TriggerType type) {
        return invalidVariable(command, type, Set.of());
    }

    public static Optional<String> invalidVariable(List<CustomItemDefinition.TriggerCommandDef> commands, TriggerType type) {
        Set<String> scopedVariables = new HashSet<>();
        List<String> forStack = new ArrayList<>();
        for (CustomItemDefinition.TriggerCommandDef command : commands) {
            String raw = command.command();
            parseForVariable(raw).ifPresent(variable -> {
                scopedVariables.add(variable);
                forStack.add(variable);
            });
            Optional<String> invalid = invalidVariable(raw, type, scopedVariables);
            if (invalid.isPresent()) {
                return invalid;
            }
            parseEndForVariable(raw).ifPresent(variable -> {
                for (int i = forStack.size() - 1; i >= 0; i--) {
                    if (forStack.get(i).equals(variable)) {
                        forStack.remove(i);
                        break;
                    }
                }
                if (!forStack.contains(variable)) {
                    scopedVariables.remove(variable);
                }
            });
        }
        return Optional.empty();
    }

    private static Optional<String> invalidVariable(String command, TriggerType type, Set<String> scopedVariables) {
        Set<String> allowed = allowedVariables(type);
        Matcher matcher = VARIABLE.matcher(command);
        while (matcher.find()) {
            String rawVariable = matcher.group(1);
            String variable = rawVariable.toUpperCase(Locale.ROOT);
            if (!rawVariable.equals(variable)) {
                continue;
            }
            if (!allowed.contains(variable) && !scopedVariables.contains(variable)) {
                return Optional.of(variable);
            }
        }
        return Optional.empty();
    }

    public static Optional<String> render(String command, TriggerContext context) {
        Matcher matcher = VARIABLE.matcher(command);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String variable = matcher.group(1).toUpperCase(Locale.ROOT);
            String value = context.variables().get(variable);
            if (value == null) {
                matcher.appendReplacement(output, Matcher.quoteReplacement("{" + matcher.group(1) + "}"));
                continue;
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(output);
        return Optional.of(output.toString());
    }

    private CooldownCheck checkCooldown(TriggerContext context, int commandIndex, CustomItemDefinition.TriggerCommandDef command, long now) {
        if (!command.cooldownEnabled()) {
            return CooldownCheck.ready();
        }
        CooldownKey key = new CooldownKey(context.self().getUniqueId(), context.itemId(), context.type(), commandIndex);
        Long until = cooldowns.get(key);
        if (until != null && until > now) {
            return new CooldownCheck(true, secondsRemaining(until, now));
        }
        if (until != null) {
            cooldowns.remove(key);
        }
        long durationMillis = (long) command.cooldownTicks() * 50L;
        long end = Long.MAX_VALUE - now < durationMillis ? Long.MAX_VALUE : now + durationMillis;
        cooldowns.put(key, end);
        return CooldownCheck.ready();
    }

    private void sendCooldownMessage(TriggerContext context, CustomItemDefinition.TriggerCommandDef command, long remainingSeconds) {
        if (command.cooldownMessage().isBlank()) {
            return;
        }
        String message = command.cooldownMessage().replace("{COOLDOWN}", String.valueOf(remainingSeconds));
        String rendered = render(message, context).orElse(message);
        context.self().sendMessage(TextUtil.legacy(rendered));
    }

    static long secondsRemaining(long until, long now) {
        return Math.max(1L, (until - now + 999L) / 1000L);
    }

    private int skipBlock(List<CustomItemDefinition.TriggerCommandDef> commands, int start) {
        int depth = 0;
        for (int i = start; i < commands.size(); i++) {
            String upper = commands.get(i).command().trim().toUpperCase(Locale.ROOT);
            if (isBlockHeader(upper)) {
                depth++;
            } else if (isBlockTerminator(upper)) {
                depth--;
                if (depth <= 0) {
                    return i;
                }
            }
        }
        return commands.size() - 1;
    }

    private static boolean isBlockHeader(String upper) {
        return upper.startsWith("LOOP_START") || upper.startsWith("RANDOM_RUN") || upper.startsWith("FOR ");
    }

    private static boolean isBlockTerminator(String upper) {
        return upper.equals("LOOP_END") || upper.equals("RANDOM_END") || upper.startsWith("END_FOR") || upper.startsWith("ENDFOR ");
    }

    private static Optional<String> parseForVariable(String raw) {
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (!upper.startsWith("FOR ")) {
            return Optional.empty();
        }
        int arrow = raw.indexOf('>');
        if (arrow < 0) {
            return Optional.empty();
        }
        String variable = raw.substring(arrow + 1).trim();
        return variable.isBlank() ? Optional.empty() : Optional.of(variable.toUpperCase(Locale.ROOT));
    }

    private static Optional<String> parseEndForVariable(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 2) {
            return Optional.empty();
        }
        String first = parts[0].toUpperCase(Locale.ROOT);
        if (!first.equals("END_FOR") && !first.equals("ENDFOR")) {
            return Optional.empty();
        }
        return Optional.of(parts[1].toUpperCase(Locale.ROOT));
    }

    private record CooldownKey(UUID playerId, String itemId, TriggerType type, int commandIndex) {
    }

    private record CooldownCheck(boolean active, long remainingSeconds) {
        private static CooldownCheck ready() {
            return new CooldownCheck(false, 0L);
        }
    }
}
