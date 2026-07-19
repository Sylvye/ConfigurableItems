package com.bountysmp.configurableitems.trigger;

import com.bountysmp.configurableitems.action.ActionEngine;
import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.model.TriggerType;
import com.bountysmp.configurableitems.storage.ItemRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
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
        List<String> commands = item.triggers().get(context.type());
        if (commands == null || commands.isEmpty()) {
            return;
        }
        List<String> renderedCommands = new ArrayList<>();
        for (String command : commands) {
            Optional<String> rendered = render(command, context);
            if (rendered.isEmpty()) {
                plugin.getLogger().warning("Skipped " + context.type() + " command for " + context.itemId() + " because a variable was missing: " + command);
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
        Set<String> allowed = allowedVariables(type);
        Matcher matcher = VARIABLE.matcher(command);
        while (matcher.find()) {
            String rawVariable = matcher.group(1);
            String variable = rawVariable.toUpperCase(Locale.ROOT);
            if (!rawVariable.equals(variable)) {
                continue;
            }
            if (!allowed.contains(variable)) {
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
}
