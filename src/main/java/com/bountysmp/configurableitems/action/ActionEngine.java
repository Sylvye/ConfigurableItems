package com.bountysmp.configurableitems.action;

import com.bountysmp.configurableitems.storage.ItemRepository;
import com.bountysmp.configurableitems.trigger.TriggerContext;
import com.bountysmp.configurableitems.util.TextUtil;
import com.bountysmp.configurableitems.util.ValidationUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class ActionEngine {
    private static final Set<String> ACTIONS = Set.of(
        "DELAY_TICK", "IF", "AROUND", "MOB_AROUND", "NEAREST", "MOB_NEAREST", "HITSCAN",
        "DAMAGE", "HEAL", "SET_HEALTH", "KILL", "BURN", "INVULNERABILITY", "TELEPORT", "VELOCITY", "DASH",
        "SEND_MESSAGE", "ACTIONBAR", "PARTICLE", "PARTICLE_LINE", "SET_BLOCK", "SET_TEMP_BLOCK", "BREAK_BLOCK", "DROPITEM", "VEINMINE"
    );

    private final Plugin plugin;
    private final ItemRepository repository;
    private final ActionConfig config;
    private final Random random = new Random();
    private int delayedQueues;

    public ActionEngine(Plugin plugin, ItemRepository repository, ActionConfig config) {
        this.plugin = plugin;
        this.repository = repository;
        this.config = config;
    }

    public boolean isKnownAction(String line) {
        return ACTIONS.contains(actionName(line));
    }

    public static boolean isKnownActionName(String name) {
        return ACTIONS.contains(name.toUpperCase(Locale.ROOT));
    }

    public void execute(TriggerContext triggerContext, List<String> lines) {
        ActionParser.ParseResult parsed = ActionParser.parse(lines);
        for (String error : parsed.errors()) {
            warn(triggerContext, error);
        }
        runQueue(new ActionExecutionContext(triggerContext), new ArrayDeque<>(parsed.steps()));
    }

    private void runQueue(ActionExecutionContext context, Deque<ActionStep> queue) {
        while (!queue.isEmpty()) {
            if (!context.self().isOnline() || repository.get(context.triggerContext().itemId()) == null) {
                return;
            }
            ActionStep step = queue.removeFirst();
            if (step instanceof ActionStep.Simple simple) {
                String line = context.replaceVariables(simple.line()).trim();
                if (line.isBlank()) {
                    continue;
                }
                if (actionName(line).equals("DELAY_TICK")) {
                    int ticks = Math.max(0, intArg(tokens(line), 1, 0));
                    schedule(context, queue, ticks);
                    return;
                }
                runSimple(context, line);
            } else if (step instanceof ActionStep.Loop loop) {
                if (loop.times() > config.warnLoopIterations()) {
                    warn(context.triggerContext(), "LOOP_START iterations " + loop.times() + " exceeds warning threshold " + config.warnLoopIterations());
                }
                prepend(queue, expandLoop(loop));
            } else if (step instanceof ActionStep.RandomBlock randomBlock) {
                List<ActionStep> body = new ArrayList<>(randomBlock.body());
                Collections.shuffle(body, random);
                int selected = Math.min(randomBlock.selectionCount(), body.size());
                List<ActionStep> expanded = new ArrayList<>();
                for (int i = 0; i < selected; i++) {
                    ActionStep selectedStep = body.get(i);
                    if (selectedStep instanceof ActionStep.Simple simple) {
                        ActionParser.splitRandomInline(simple.line()).forEach(part -> expanded.add(new ActionStep.Simple(part)));
                    } else {
                        expanded.add(selectedStep);
                    }
                }
                prepend(queue, expanded);
            } else if (step instanceof ActionStep.ForBlock forBlock) {
                if (forBlock.values().size() > config.warnForIterations()) {
                    warn(context.triggerContext(), "FOR iterations " + forBlock.values().size() + " exceeds warning threshold " + config.warnForIterations());
                }
                List<ActionStep> expanded = new ArrayList<>();
                for (String value : forBlock.values()) {
                    expanded.add(new ActionStep.Simple("CI_SET_VAR " + forBlock.variable() + " " + value));
                    expanded.addAll(forBlock.body());
                }
                prepend(queue, expanded);
            }
        }
    }

    private List<ActionStep> expandLoop(ActionStep.Loop loop) {
        List<ActionStep> expanded = new ArrayList<>();
        for (int i = 0; i < loop.times(); i++) {
            if (i > 0 && loop.delayTicks() > 0) {
                expanded.add(new ActionStep.Simple("DELAY_TICK " + loop.delayTicks()));
            }
            expanded.addAll(loop.body());
        }
        return expanded;
    }

    private void prepend(Deque<ActionStep> queue, List<ActionStep> steps) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            queue.addFirst(steps.get(i));
        }
    }

    private void schedule(ActionExecutionContext context, Deque<ActionStep> queue, int ticks) {
        delayedQueues++;
        if (delayedQueues > config.warnDelayedQueues()) {
            warn(context.triggerContext(), "Delayed action queues " + delayedQueues + " exceeds warning threshold " + config.warnDelayedQueues());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            delayedQueues = Math.max(0, delayedQueues - 1);
            runQueue(context, queue);
        }, ticks);
    }

    private void runSimple(ActionExecutionContext context, String line) {
        line = context.replaceVariables(line).trim();
        String action = actionName(line);
        if (action.equals("CI_SET_VAR")) {
            String[] split = line.split("\\s+", 3);
            if (split.length >= 3) {
                context.putVariable(split[1], split[2]);
            }
            return;
        }
        if (!isKnownAction(line)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
            return;
        }
        List<String> tokens = tokens(line);
        if (ActionFormatter.isEntityActionName(action) && !context.hasLivingTarget()) {
            warn(context.triggerContext(), action + " requires a living target");
            return;
        }
        switch (action) {
            case "IF" -> runIf(context, line.substring(2).trim());
            case "AROUND" -> runSelector(context, tokens, true, false, line);
            case "MOB_AROUND" -> runSelector(context, tokens, false, false, line);
            case "NEAREST" -> runSelector(context, tokens, true, true, line);
            case "MOB_NEAREST" -> runSelector(context, tokens, false, true, line);
            case "HITSCAN" -> runHitscan(context, tokens, line);
            case "DAMAGE" -> context.livingTarget().damage(doubleArg(tokens, "amount", 1, 0.0), context.self());
            case "HEAL" -> heal(context, doubleArg(tokens, "amount", 1, -1.0));
            case "SET_HEALTH" -> setHealth(context, doubleArg(tokens, "amount", 1, 1.0));
            case "KILL" -> context.livingTarget().setHealth(0.0);
            case "BURN" -> context.livingTarget().setFireTicks(Math.max(0, intArg(tokens, 1, 0)) * 20);
            case "INVULNERABILITY" -> context.livingTarget().setNoDamageTicks(Math.max(0, intArg(tokens, 1, 0)));
            case "TELEPORT" -> teleport(context, tokens);
            case "VELOCITY" -> context.livingTarget().setVelocity(vector(tokens, 1));
            case "DASH" -> context.self().setVelocity(context.self().getLocation().getDirection().normalize().multiply(doubleArg(tokens, "strength", 1, 1.0)));
            case "SEND_MESSAGE" -> sendMessage(context, line.substring(action.length()).trim());
            case "ACTIONBAR" -> actionbar(context, line.substring(action.length()).trim());
            case "PARTICLE" -> particle(context, tokens);
            case "PARTICLE_LINE" -> particleLine(context, tokens);
            case "SET_BLOCK" -> setBlock(context, tokens);
            case "SET_TEMP_BLOCK" -> setTempBlock(context, tokens);
            case "BREAK_BLOCK" -> breakBlock(context, tokens);
            case "DROPITEM" -> dropItem(context, tokens);
            case "VEINMINE" -> veinmine(context, tokens);
            default -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }
    }

    private void runIf(ActionExecutionContext context, String input) {
        String[] pieces = input.split("\\s+", 2);
        if (pieces.length < 2) {
            warn(context.triggerContext(), "IF is missing an action body");
            return;
        }
        if (ConditionEvaluator.evaluate(pieces[0])) {
            ActionParser.splitInline(pieces[1]).forEach(part -> runSimple(context.copy(), part));
        }
    }

    private void runSelector(ActionExecutionContext context, List<String> tokens, boolean players, boolean nearestOnly, String line) {
        double radius = doubleArg(tokens, 1, 0.0);
        if (radius > config.warnRadius()) {
            warn(context.triggerContext(), actionName(line) + " radius " + radius + " exceeds warning threshold " + config.warnRadius());
        }
        String body = bodyAfter(tokens, 2);
        if (body.isBlank()) {
            warn(context.triggerContext(), actionName(line) + " is missing an action body");
            return;
        }
        Predicate<Entity> filter = entity -> entity != context.self()
            && (players ? entity instanceof Player : entity instanceof LivingEntity && !(entity instanceof Player));
        List<Entity> selected = context.self().getNearbyEntities(radius, radius, radius).stream()
            .filter(filter)
            .filter(entity -> entity.getLocation().distanceSquared(context.self().getLocation()) <= radius * radius)
            .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(context.self().getLocation())))
            .toList();
        if (selected.size() > config.warnSelectedEntities()) {
            warn(context.triggerContext(), actionName(line) + " selected " + selected.size() + " entities, threshold " + config.warnSelectedEntities());
        }
        if (nearestOnly && !selected.isEmpty()) {
            selected = List.of(selected.getFirst());
        }
        for (Entity entity : selected) {
            ActionExecutionContext scoped = context.copy();
            scoped.target(entity);
            ActionParser.splitInline(body).forEach(part -> runSimple(scoped, part));
        }
    }

    private void runHitscan(ActionExecutionContext context, List<String> tokens, String line) {
        int distance = Math.max(1, intArg(tokens, 1, 32));
        if (distance > config.warnHitscanDistance()) {
            warn(context.triggerContext(), "HITSCAN distance " + distance + " exceeds warning threshold " + config.warnHitscanDistance());
        }
        String body = bodyAfter(tokens, 2);
        if (body.isBlank()) {
            warn(context.triggerContext(), "HITSCAN is missing an action body");
            return;
        }
        ActionExecutionContext scoped = context.copy();
        Entity entity = rayEntity(context.self(), distance, context.target());
        if (entity != null) {
            scoped.target(entity);
        } else {
            Block block = context.self().getTargetBlockExact(distance);
            if (block != null) {
                scoped.block(block);
                scoped.clearTarget();
            } else {
                return;
            }
        }
        ActionParser.splitInline(body).forEach(part -> runSimple(scoped, part));
    }

    private Entity rayEntity(Player player, int distance, Entity excludedSource) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Entity best = null;
        double bestProjection = Double.MAX_VALUE;
        for (Entity entity : player.getNearbyEntities(distance, distance, distance)) {
            if (!(entity instanceof LivingEntity) || sameEntity(entity, player) || sameEntity(entity, excludedSource)) {
                continue;
            }
            Vector toEntity = entity.getLocation().add(0, entity.getHeight() / 2.0, 0).toVector().subtract(eye.toVector());
            double projection = toEntity.dot(direction);
            if (projection < 0 || projection > distance) {
                continue;
            }
            double perpendicular = toEntity.clone().subtract(direction.clone().multiply(projection)).length();
            if (perpendicular <= 1.2 && projection < bestProjection) {
                bestProjection = projection;
                best = entity;
            }
        }
        return best;
    }

    static boolean sameEntity(Entity first, Entity second) {
        return first != null && second != null && (first == second || first.equals(second) || first.getUniqueId().equals(second.getUniqueId()));
    }

    private void heal(ActionExecutionContext context, double amount) {
        LivingEntity target = context.livingTarget();
        double max = Optional.ofNullable(target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH))
            .map(attribute -> attribute.getValue())
            .orElse(target.getHealth());
        target.setHealth(Math.min(max, amount < 0 ? max : target.getHealth() + amount));
    }

    private void setHealth(ActionExecutionContext context, double amount) {
        LivingEntity target = context.livingTarget();
        double max = Optional.ofNullable(target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH))
            .map(attribute -> attribute.getValue())
            .orElse(amount);
        target.setHealth(Math.max(0.0, Math.min(max, amount)));
    }

    private void teleport(ActionExecutionContext context, List<String> tokens) {
        String targetArg = keyArg(tokens, "target");
        if (targetArg != null) {
            if (targetArg.equalsIgnoreCase("SELF")) {
                context.livingTarget().teleport(context.self());
            } else if (targetArg.equalsIgnoreCase("TARGET") && context.triggerContext().target() != null) {
                context.self().teleport(context.triggerContext().target());
            }
            return;
        }
        if (tokens.size() < 4) {
            warn(context.triggerContext(), "TELEPORT requires x y z [world] or target:<SELF|TARGET>");
            return;
        }
        World world = tokens.size() > 4 ? Bukkit.getWorld(tokens.get(4)) : context.location().getWorld();
        if (world == null) {
            warn(context.triggerContext(), "TELEPORT world not found: " + tokens.get(4));
            return;
        }
        context.livingTarget().teleport(new Location(world, doubleArg(tokens, 1, 0.0), doubleArg(tokens, 2, 0.0), doubleArg(tokens, 3, 0.0)));
    }

    private Vector vector(List<String> tokens, int start) {
        return new Vector(doubleArg(tokens, start, 0.0), doubleArg(tokens, start + 1, 0.0), doubleArg(tokens, start + 2, 0.0));
    }

    private void sendMessage(ActionExecutionContext context, String text) {
        Entity target = context.target();
        Player receiver = target instanceof Player player ? player : context.self();
        receiver.sendMessage(TextUtil.legacy(text));
    }

    private void actionbar(ActionExecutionContext context, String text) {
        Entity target = context.target();
        Player receiver = target instanceof Player player ? player : context.self();
        receiver.sendActionBar(TextUtil.legacy(text));
    }

    private void particle(ActionExecutionContext context, List<String> tokens) {
        if (tokens.size() < 3) {
            warn(context.triggerContext(), "PARTICLE requires type and count");
            return;
        }
        Particle particle = Registry.PARTICLE_TYPE.match(tokens.get(1));
        if (particle == null) {
            warn(context.triggerContext(), "Unknown particle: " + tokens.get(1));
            return;
        }
        int count = Math.max(1, intArg(tokens, 2, 1));
        double offset = doubleArg(tokens, 3, 0.1);
        double speed = doubleArg(tokens, 4, 0.0);
        context.location().getWorld().spawnParticle(particle, context.location(), count, offset, offset, offset, speed);
    }

    private void particleLine(ActionExecutionContext context, List<String> tokens) {
        if (tokens.size() < 4) {
            warn(context.triggerContext(), "PARTICLE_LINE requires type, distance, and points");
            return;
        }
        Particle particle = Registry.PARTICLE_TYPE.match(tokens.get(1));
        if (particle == null) {
            warn(context.triggerContext(), "Unknown particle: " + tokens.get(1));
            return;
        }
        double distance = Math.max(0.0, doubleArg(tokens, 2, 0.0));
        int points = Math.max(1, intArg(tokens, 3, 1));
        double offset = doubleArg(tokens, 4, 0.0);
        double speed = doubleArg(tokens, 5, 0.0);
        Location start = context.location().clone();
        if (context.target() == context.self()) {
            start = context.self().getEyeLocation();
        } else if (context.block() != null) {
            start.add(0.5, 0.5, 0.5);
        }
        Vector direction = context.self().getEyeLocation().getDirection();
        if (direction.lengthSquared() == 0.0) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize();
        double step = points == 1 ? 0.0 : distance / (points - 1);
        for (int i = 0; i < points; i++) {
            Location point = start.clone().add(direction.clone().multiply(step * i));
            point.getWorld().spawnParticle(particle, point, 1, offset, offset, offset, speed);
        }
    }

    private void setBlock(ActionExecutionContext context, List<String> tokens) {
        Block block = blockOrTarget(context);
        if (block == null || tokens.size() < 2) {
            warn(context.triggerContext(), "SET_BLOCK requires a block and material");
            return;
        }
        ValidationUtil.material(tokens.get(1)).filter(Material::isBlock).ifPresentOrElse(
            material -> block.setType(material),
            () -> warn(context.triggerContext(), "Invalid block material: " + tokens.get(1))
        );
    }

    private void setTempBlock(ActionExecutionContext context, List<String> tokens) {
        Block block = blockOrTarget(context);
        if (block == null || tokens.size() < 3) {
            warn(context.triggerContext(), "SET_TEMP_BLOCK requires material and ticks");
            return;
        }
        Material oldType = block.getType();
        ValidationUtil.material(tokens.get(1)).filter(Material::isBlock).ifPresentOrElse(material -> {
            block.setType(material);
            Bukkit.getScheduler().runTaskLater(plugin, () -> block.setType(oldType), Math.max(1, intArg(tokens, 2, 20)));
        }, () -> warn(context.triggerContext(), "Invalid block material: " + tokens.get(1)));
    }

    private void breakBlock(ActionExecutionContext context, List<String> tokens) {
        Block block = blockOrTarget(context);
        if (block == null) {
            warn(context.triggerContext(), "BREAK_BLOCK requires a block");
            return;
        }
        boolean drop = booleanArg(tokens, "drop", true);
        if (drop) {
            block.breakNaturally(context.self().getInventory().getItemInMainHand());
        } else {
            block.setType(Material.AIR);
        }
    }

    private void dropItem(ActionExecutionContext context, List<String> tokens) {
        if (tokens.size() < 2) {
            warn(context.triggerContext(), "DROPITEM requires material");
            return;
        }
        ValidationUtil.material(tokens.get(1)).ifPresentOrElse(material -> {
            int amount = Math.max(1, intArg(tokens, 2, 1));
            context.location().getWorld().dropItemNaturally(context.location(), new ItemStack(material, amount));
        }, () -> warn(context.triggerContext(), "Invalid item material: " + tokens.get(1)));
    }

    private void veinmine(ActionExecutionContext context, List<String> tokens) {
        Block start = blockOrTarget(context);
        if (start == null || start.getType().isAir()) {
            warn(context.triggerContext(), "VEINMINE requires a non-air block");
            return;
        }
        int limit = Math.max(1, intArg(tokens, 1, config.warnVeinmineBlocks()));
        if (limit > config.warnVeinmineBlocks()) {
            warn(context.triggerContext(), "VEINMINE limit " + limit + " exceeds warning threshold " + config.warnVeinmineBlocks());
        }
        boolean drop = booleanArg(tokens, "drop", true);
        Material type = start.getType();
        Set<String> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        int broken = 0;
        while (!queue.isEmpty() && broken < limit) {
            Block block = queue.removeFirst();
            String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (!visited.add(key) || block.getType() != type) {
                continue;
            }
            if (drop) {
                block.breakNaturally(context.self().getInventory().getItemInMainHand());
            } else {
                block.setType(Material.AIR);
            }
            broken++;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (Math.abs(x) + Math.abs(y) + Math.abs(z) == 1) {
                            queue.add(block.getRelative(x, y, z));
                        }
                    }
                }
            }
        }
    }

    private Block blockOrTarget(ActionExecutionContext context) {
        if (context.block() != null) {
            return context.block();
        }
        return context.self().getTargetBlockExact(8);
    }

    private void warn(TriggerContext context, String message) {
        plugin.getLogger().warning("[actions] item=" + context.itemId() + " trigger=" + context.type() + " player=" + context.self().getName() + " " + message);
    }

    private static String actionName(String line) {
        List<String> tokens = tokens(line);
        return tokens.isEmpty() ? "" : tokens.getFirst().toUpperCase(Locale.ROOT);
    }

    private static List<String> tokens(String line) {
        return List.of(line.trim().split("\\s+")).stream().filter(value -> !value.isBlank()).toList();
    }

    private static String bodyAfter(List<String> tokens, int start) {
        if (tokens.size() <= start) {
            return "";
        }
        return String.join(" ", tokens.subList(start, tokens.size()));
    }

    private static int intArg(List<String> tokens, int index, int fallback) {
        if (tokens.size() <= index) {
            return fallback;
        }
        try {
            return Integer.parseInt(tokens.get(index));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double doubleArg(List<String> tokens, int index, double fallback) {
        if (tokens.size() <= index) {
            return fallback;
        }
        try {
            return Double.parseDouble(tokens.get(index));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double doubleArg(List<String> tokens, String key, int positionalIndex, double fallback) {
        String value = keyArg(tokens, key);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return doubleArg(tokens, positionalIndex, fallback);
    }

    private static boolean booleanArg(List<String> tokens, String key, boolean fallback) {
        String value = keyArg(tokens, key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static String keyArg(List<String> tokens, String key) {
        String prefix = key.toLowerCase(Locale.ROOT) + ":";
        for (String token : tokens) {
            if (token.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return token.substring(token.indexOf(':') + 1);
            }
        }
        return null;
    }
}
