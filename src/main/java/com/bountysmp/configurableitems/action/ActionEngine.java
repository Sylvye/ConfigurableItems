package com.bountysmp.configurableitems.action;

import com.bountysmp.configurableitems.storage.ItemRepository;
import com.bountysmp.configurableitems.trigger.TriggerContext;
import com.bountysmp.configurableitems.util.PlaceholderResolver;
import com.bountysmp.configurableitems.util.TextUtil;
import com.bountysmp.configurableitems.util.ValidationUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class ActionEngine {
    private static final Set<String> ACTIONS = Set.of(
        "DELAY_TICK", "IF", "AROUND", "MOB_AROUND", "NEAREST", "MOB_NEAREST", "HITSCAN",
        "DAMAGE", "HEAL", "SET_HEALTH", "KILL", "BURN", "INVULNERABILITY", "TELEPORT", "VELOCITY", "DASH",
        "SEND_MESSAGE", "ACTIONBAR", "PARTICLE", "PARTICLE_LINE", "PROJECTILE_TRAIL", "SET_BLOCK", "SET_TEMP_BLOCK", "BREAK_BLOCK", "DROPITEM", "VEINMINE"
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
                String line = simple.line().trim();
                if (line.isBlank()) {
                    continue;
                }
                if (actionName(line).equals("DELAY_TICK")) {
                    List<String> delayTokens = tokens(line);
                    Optional<String> renderedTicks = renderRequired(context, token(delayTokens, 1, "0"), line);
                    if (renderedTicks.isEmpty()) {
                        continue;
                    }
                    int ticks = Math.max(0, intValue(renderedTicks.get(), 0));
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
            } else if (step instanceof ActionStep.ProjectileTrail projectileTrail) {
                startProjectileTrail(context, projectileTrail);
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
            try {
                delayedQueues = Math.max(0, delayedQueues - 1);
                runQueue(context, queue);
            } catch (RuntimeException ex) {
                runtimeError(context.triggerContext(), "delayed queue", ex);
            }
        }, ticks);
    }

    private void runSimple(ActionExecutionContext context, String line) {
        try {
            runSimpleUnchecked(context, line);
        } catch (RuntimeException ex) {
            runtimeError(context.triggerContext(), line, ex);
        }
    }

    private void runSimpleUnchecked(ActionExecutionContext context, String rawLine) {
        Optional<String> prepared = prepareLine(context, rawLine);
        if (prepared.isEmpty()) {
            return;
        }
        String line = prepared.get().trim();
        if (line.isBlank()) {
            return;
        }
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
        if (ActionFormatter.isEntityActionName(action) && livingReceiver(context, tokens, action) == null) {
            return;
        }
        switch (action) {
            case "IF" -> runIf(context, line.substring(2).trim());
            case "AROUND" -> runSelector(context, tokens, true, false, line);
            case "MOB_AROUND" -> runSelector(context, tokens, false, false, line);
            case "NEAREST" -> runSelector(context, tokens, true, true, line);
            case "MOB_NEAREST" -> runSelector(context, tokens, false, true, line);
            case "HITSCAN" -> runHitscan(context, tokens, line);
            case "DAMAGE" -> livingReceiver(context, tokens, action).damage(doubleArg(tokens, "amount", firstPositionalIndex(tokens), 0.0), context.self());
            case "HEAL" -> heal(context, tokens);
            case "SET_HEALTH" -> setHealth(context, tokens);
            case "KILL" -> livingReceiver(context, tokens, action).setHealth(0.0);
            case "BURN" -> livingReceiver(context, tokens, action).setFireTicks(Math.max(0, intArg(tokens, firstPositionalIndex(tokens), 0)) * 20);
            case "INVULNERABILITY" -> livingReceiver(context, tokens, action).setNoDamageTicks(Math.max(0, intArg(tokens, firstPositionalIndex(tokens), 0)));
            case "TELEPORT" -> teleport(context, tokens);
            case "VELOCITY" -> livingReceiver(context, tokens, action).setVelocity(vector(tokens, firstPositionalIndex(tokens)));
            case "DASH" -> context.self().setVelocity(context.self().getLocation().getDirection().normalize().multiply(doubleArg(tokens, "strength", 1, 1.0)));
            case "SEND_MESSAGE" -> sendMessage(context, tokens, line.substring(action.length()).trim());
            case "ACTIONBAR" -> actionbar(context, tokens, line.substring(action.length()).trim());
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

    private Optional<String> prepareLine(ActionExecutionContext context, String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isBlank()) {
            return Optional.of("");
        }
        String action = actionName(line);
        if (action.equals("IF")) {
            String[] pieces = line.substring(Math.min(2, line.length())).trim().split("\\s+", 2);
            if (pieces.length < 2) {
                return Optional.of(line);
            }
            Optional<String> condition = renderRequired(context, pieces[0], line);
            return condition.map(value -> "IF " + value + " " + pieces[1]);
        }
        if (action.equals("AROUND") || action.equals("MOB_AROUND") || action.equals("NEAREST") || action.equals("MOB_NEAREST")) {
            List<String> rawTokens = tokens(line);
            if (rawTokens.size() < 2) {
                return Optional.of(line);
            }
            Optional<String> radius = renderRequired(context, rawTokens.get(1), line);
            return radius.map(value -> action + " " + value + " " + bodyAfter(rawTokens, 2));
        }
        if (action.equals("HITSCAN")) {
            return prepareHitscanLine(context, line);
        }
        return renderRequired(context, line, line);
    }

    private Optional<String> prepareHitscanLine(ActionExecutionContext context, String line) {
        List<String> rawTokens = tokens(line);
        if (rawTokens.size() < 2) {
            return Optional.of(line);
        }
        List<String> rendered = new ArrayList<>();
        rendered.add(rawTokens.getFirst());
        Optional<String> distance = renderRequired(context, rawTokens.get(1), line);
        if (distance.isEmpty()) {
            return Optional.empty();
        }
        rendered.add(distance.get());
        int index = 2;
        while (index < rawTokens.size() && HitscanOptions.isHitscanOption(rawTokens.get(index))) {
            Optional<String> option = renderRequired(context, rawTokens.get(index), line);
            if (option.isEmpty()) {
                return Optional.empty();
            }
            rendered.add(option.get());
            index++;
        }
        if (index < rawTokens.size()) {
            rendered.add(bodyAfter(rawTokens, index));
        }
        return Optional.of(String.join(" ", rendered));
    }

    private Optional<String> renderRequired(ActionExecutionContext context, String input, String line) {
        PlaceholderResolver.Result rendered = context.replaceVariables(input, false);
        if (rendered.ok()) {
            return Optional.of(rendered.output());
        }
        for (String error : rendered.errors()) {
            warn(context.triggerContext(), error + " in: " + line);
        }
        return Optional.empty();
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
        HitscanOptions options = HitscanOptions.parse(tokens);
        for (String error : options.errors()) {
            warn(context.triggerContext(), error);
        }
        if (options.distance() > config.warnHitscanDistance()) {
            warn(context.triggerContext(), "HITSCAN distance " + options.distance() + " exceeds warning threshold " + config.warnHitscanDistance());
        }
        if (options.body().isBlank()) {
            return;
        }
        Particle particle = null;
        if (!options.particle().isBlank()) {
            particle = HitscanOptions.particleType(options.particle());
            if (particle == null) {
                warn(context.triggerContext(), "Unknown HITSCAN particle: " + options.particle());
            }
        }
        RayTraceResult blockTrace = context.self().getWorld().rayTraceBlocks(
            context.self().getEyeLocation(),
            context.self().getEyeLocation().getDirection().normalize(),
            options.distance(),
            FluidCollisionMode.NEVER,
            true
        );
        RayHit blockHit = blockTrace == null || blockTrace.getHitBlock() == null
            ? null
            : new RayHit(null, blockTrace.getHitBlock(), blockTrace.getHitPosition().toLocation(context.self().getWorld()), blockTrace.getHitPosition().distance(context.self().getEyeLocation().toVector()), HitKind.BLOCK);
        List<RayHit> hits = options.targetMode() == HitscanOptions.TargetMode.BLOCK
            ? (blockHit == null ? List.of() : List.of(blockHit))
            : rayEntities(context.self(), options.distance(), blockHit == null ? options.distance() : blockHit.projection(), options.targetMode());
        int selectedCount = options.targetMode() == HitscanOptions.TargetMode.BLOCK
            ? Math.min(1, hits.size())
            : options.allHits() ? hits.size() : Math.min(options.maxHits(), hits.size());
        Location particleEnd = context.self().getEyeLocation().clone().add(context.self().getEyeLocation().getDirection().normalize().multiply(options.distance()));
        if (blockHit != null) {
            particleEnd = blockHit.location();
        }
        if (selectedCount > 0) {
            particleEnd = hits.get(selectedCount - 1).location();
            for (int i = 0; i < selectedCount; i++) {
                runHitscanBody(context, hits.get(i), options);
            }
        }
        if (particle != null) {
            drawParticleRay(context.triggerContext(), context.self().getEyeLocation(), particleEnd, particle, options.points(), options.offset(), options.particleSpeed(), options.speed());
        }
    }

    private void runHitscanBody(ActionExecutionContext context, RayHit hit, HitscanOptions options) {
        Runnable action = () -> {
            ActionExecutionContext scoped = context.copy();
            if (hit.kind() == HitKind.BLOCK) {
                scoped.block(hit.block());
                scoped.hitBlock(hit.block(), hit.location());
                scoped.clearTarget();
            } else {
                scoped.target(hit.entity());
                scoped.hitEntity(hit.entity(), hit.location());
            }
            ActionParser.splitInline(options.body()).forEach(part -> runSimple(scoped, part));
        };
        if (options.speed().equalsIgnoreCase("instant")) {
            action.run();
            return;
        }
        double blocksPerSecond = doubleValue(options.speed(), -1.0);
        if (blocksPerSecond <= 0.0) {
            action.run();
            return;
        }
        long delayTicks = Math.max(0L, Math.round((hit.projection() / blocksPerSecond) * 20.0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                action.run();
            } catch (RuntimeException ex) {
                runtimeError(context.triggerContext(), options.body(), ex);
            }
        }, delayTicks);
    }

    private List<RayHit> rayEntities(Player player, int distance, double maxProjection, HitscanOptions.TargetMode targetMode) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        List<RayHit> hits = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(distance, distance, distance)) {
            if (!matchesHitscanTarget(entity, targetMode) || sameEntity(entity, player)) {
                continue;
            }
            Location hitLocation = entity.getLocation().add(0, entity.getHeight() / 2.0, 0);
            Vector toEntity = hitLocation.toVector().subtract(eye.toVector());
            double projection = toEntity.dot(direction);
            if (projection < 0 || projection > distance || projection > maxProjection) {
                continue;
            }
            double perpendicular = toEntity.clone().subtract(direction.clone().multiply(projection)).length();
            if (perpendicular <= 1.2) {
                hits.add(new RayHit(entity, null, hitLocation, projection, HitKind.ENTITY));
            }
        }
        return hits.stream().sorted(Comparator.comparingDouble(RayHit::projection)).toList();
    }

    private boolean matchesHitscanTarget(Entity entity, HitscanOptions.TargetMode targetMode) {
        return switch (targetMode) {
            case PLAYER -> entity instanceof Player;
            case MOB -> entity instanceof LivingEntity && !(entity instanceof Player);
            case ENTITY -> true;
            case BLOCK -> false;
        };
    }

    private void drawParticleRay(TriggerContext context, Location start, Location end, Particle particle, int points, double offset, double speed, String raySpeed) {
        int safePoints = Math.max(1, points);
        Vector direction = end.toVector().subtract(start.toVector());
        double step = safePoints == 1 ? 0.0 : 1.0 / (safePoints - 1);
        double blocksPerSecond = raySpeed.equalsIgnoreCase("instant") ? -1.0 : doubleValue(raySpeed, -1.0);
        if (blocksPerSecond <= 0.0) {
            for (int i = 0; i < safePoints; i++) {
                Location point = start.clone().add(direction.clone().multiply(step * i));
                point.getWorld().spawnParticle(particle, point, 1, offset, offset, offset, speed);
            }
            return;
        }
        List<Long> delays = particleRayDelays(direction.length(), safePoints, blocksPerSecond);
        Map<Long, List<Location>> locationsByDelay = new HashMap<>();
        for (int i = 0; i < safePoints; i++) {
            Location point = start.clone().add(direction.clone().multiply(step * i));
            locationsByDelay.computeIfAbsent(delays.get(i), ignored -> new ArrayList<>()).add(point);
        }
        locationsByDelay.forEach((delay, locations) -> {
            Runnable spawn = () -> {
                for (Location location : locations) {
                    location.getWorld().spawnParticle(particle, location, 1, offset, offset, offset, speed);
                }
            };
            if (delay <= 0L) {
                spawn.run();
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        spawn.run();
                    } catch (RuntimeException ex) {
                        runtimeError(context, "HITSCAN particle", ex);
                    }
                }, delay);
            }
        });
    }

    static List<Long> particleRayDelays(double distance, int points, double blocksPerSecond) {
        int safePoints = Math.max(1, points);
        double safeSpeed = Math.max(0.000001, blocksPerSecond);
        double step = safePoints == 1 ? 0.0 : distance / (safePoints - 1);
        List<Long> delays = new ArrayList<>();
        for (int i = 0; i < safePoints; i++) {
            delays.add(Math.max(0L, Math.round(((step * i) / safeSpeed) * 20.0)));
        }
        return delays;
    }

    static boolean sameEntity(Entity first, Entity second) {
        return first != null && second != null && (first == second || first.equals(second) || first.getUniqueId().equals(second.getUniqueId()));
    }

    private enum HitKind {
        ENTITY,
        BLOCK
    }

    private record RayHit(Entity entity, Block block, Location location, double projection, HitKind kind) {
    }

    private void heal(ActionExecutionContext context, List<String> tokens) {
        LivingEntity target = livingReceiver(context, tokens, "HEAL");
        if (target == null) {
            return;
        }
        double amount = doubleArg(tokens, "amount", firstPositionalIndex(tokens), -1.0);
        double max = Optional.ofNullable(target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH))
            .map(attribute -> attribute.getValue())
            .orElse(target.getHealth());
        target.setHealth(Math.min(max, amount < 0 ? max : target.getHealth() + amount));
    }

    private void setHealth(ActionExecutionContext context, List<String> tokens) {
        LivingEntity target = livingReceiver(context, tokens, "SET_HEALTH");
        if (target == null) {
            return;
        }
        double amount = doubleArg(tokens, "amount", firstPositionalIndex(tokens), 1.0);
        double max = Optional.ofNullable(target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH))
            .map(attribute -> attribute.getValue())
            .orElse(amount);
        target.setHealth(Math.max(0.0, Math.min(max, amount)));
    }

    private void teleport(ActionExecutionContext context, List<String> tokens) {
        String targetArg = keyArg(tokens, "target");
        String toArg = keyArg(tokens, "to");
        if (targetArg != null && toArg == null && tokens.size() == 2) {
            warn(context.triggerContext(), "Legacy TELEPORT target:" + targetArg + " used; prefer TELEPORT target:<receiver> to:<destination>");
            LivingEntity legacyCurrent = livingReceiver(context, List.of("TELEPORT", "target:CURRENT"), "TELEPORT");
            if (legacyCurrent == null) {
                return;
            }
            if (targetArg.equalsIgnoreCase("SELF")) {
                legacyCurrent.teleport(context.self());
            } else if (targetArg.equalsIgnoreCase("TARGET") && context.triggerContext().target() != null) {
                context.self().teleport(context.triggerContext().target());
            }
            return;
        }
        LivingEntity receiver = livingReceiver(context, tokens, "TELEPORT");
        if (receiver == null) {
            return;
        }
        if (toArg != null) {
            Location destination = locationArg(context, toArg, "TELEPORT");
            if (destination != null) {
                receiver.teleport(destination);
            }
            return;
        }
        if (tokens.size() < 4) {
            warn(context.triggerContext(), "TELEPORT requires target:<receiver> x y z [world] or target:<receiver> to:<destination>");
            return;
        }
        int start = targetArg == null ? 1 : firstPositionalIndex(tokens);
        if (tokens.size() <= start + 2) {
            warn(context.triggerContext(), "TELEPORT requires x y z coordinates");
            return;
        }
        World world = tokens.size() > start + 3 ? Bukkit.getWorld(tokens.get(start + 3)) : context.location().getWorld();
        if (world == null) {
            warn(context.triggerContext(), "TELEPORT world not found: " + tokens.get(start + 3));
            return;
        }
        receiver.teleport(new Location(world, doubleArg(tokens, start, 0.0), doubleArg(tokens, start + 1, 0.0), doubleArg(tokens, start + 2, 0.0)));
    }

    private Vector vector(List<String> tokens, int start) {
        return new Vector(doubleArg(tokens, start, 0.0), doubleArg(tokens, start + 1, 0.0), doubleArg(tokens, start + 2, 0.0));
    }

    private LivingEntity livingReceiver(ActionExecutionContext context, List<String> tokens, String action) {
        Entity entity = entityReceiver(context, keyArg(tokens, "target"), action);
        if (entity instanceof LivingEntity living) {
            return living;
        }
        warn(context.triggerContext(), action + " requires a living target");
        return null;
    }

    private Player playerReceiver(ActionExecutionContext context, List<String> tokens, String action) {
        String targetArg = keyArg(tokens, "target");
        Entity entity = entityReceiver(context, targetArg, action);
        if (entity instanceof Player player) {
            return player;
        }
        if (targetArg == null) {
            return context.self();
        }
        warn(context.triggerContext(), action + " requires a player target");
        return null;
    }

    private Entity entityReceiver(ActionExecutionContext context, String targetArg, String action) {
        if (targetArg == null || targetArg.equalsIgnoreCase("CURRENT")) {
            return context.target() == null ? context.self() : context.target();
        }
        if (targetArg.equalsIgnoreCase("SELF")) {
            return context.self();
        }
        if (targetArg.equalsIgnoreCase("TARGET")) {
            Entity current = context.target();
            if (current != null && !sameEntity(current, context.self())) {
                return current;
            }
            if (context.triggerContext().target() != null) {
                return context.triggerContext().target();
            }
            warn(context.triggerContext(), action + " target:TARGET requires target context");
            return null;
        }
        warn(context.triggerContext(), action + " target must be SELF, TARGET, or CURRENT: " + targetArg);
        return null;
    }

    private Location locationArg(ActionExecutionContext context, String atArg, String action) {
        if (atArg == null || atArg.equalsIgnoreCase("CURRENT")) {
            return context.location();
        }
        if (atArg.equalsIgnoreCase("SELF")) {
            return context.self().getLocation();
        }
        if (atArg.equalsIgnoreCase("TARGET")) {
            Entity target = entityReceiver(context, "TARGET", action);
            return target == null ? null : target.getLocation();
        }
        if (atArg.equalsIgnoreCase("BLOCK")) {
            if (context.block() == null) {
                warn(context.triggerContext(), action + " at:BLOCK requires block context");
                return null;
            }
            return context.block().getLocation();
        }
        if (atArg.equalsIgnoreCase("HIT")) {
            return variableLocation(context, "HIT_", action + " at:HIT requires hit context");
        }
        if (atArg.equalsIgnoreCase("PROJECTILE")) {
            return variableLocation(context, "PROJECTILE_", action + " at:PROJECTILE requires projectile context");
        }
        warn(context.triggerContext(), action + " at must be SELF, TARGET, CURRENT, BLOCK, HIT, or PROJECTILE: " + atArg);
        return null;
    }

    private Location variableLocation(ActionExecutionContext context, String prefix, String missingMessage) {
        String worldName = context.variables().get(prefix + "WORLD");
        String x = context.variables().get(prefix + "X");
        String y = context.variables().get(prefix + "Y");
        String z = context.variables().get(prefix + "Z");
        if (worldName == null || x == null || y == null || z == null) {
            warn(context.triggerContext(), missingMessage);
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            warn(context.triggerContext(), "World not found: " + worldName);
            return null;
        }
        return new Location(world, doubleValue(x, 0.0), doubleValue(y, 0.0), doubleValue(z, 0.0));
    }

    private void sendMessage(ActionExecutionContext context, List<String> tokens, String text) {
        Player receiver = playerReceiver(context, tokens, "SEND_MESSAGE");
        if (receiver == null) {
            return;
        }
        receiver.sendMessage(TextUtil.legacy(stripKeyToken(text, "target")));
    }

    private void actionbar(ActionExecutionContext context, List<String> tokens, String text) {
        Player receiver = playerReceiver(context, tokens, "ACTIONBAR");
        if (receiver == null) {
            return;
        }
        receiver.sendActionBar(TextUtil.legacy(stripKeyToken(text, "target")));
    }

    private void particle(ActionExecutionContext context, List<String> tokens) {
        if (tokens.size() < 3) {
            warn(context.triggerContext(), "PARTICLE requires type and count");
            return;
        }
        int start = firstPositionalIndex(tokens);
        if (tokens.size() <= start + 1) {
            warn(context.triggerContext(), "PARTICLE requires type and count");
            return;
        }
        Particle particle = HitscanOptions.particleType(tokens.get(start));
        if (particle == null) {
            warn(context.triggerContext(), "Unknown particle: " + tokens.get(start));
            return;
        }
        int count = Math.max(1, intArg(tokens, start + 1, 1));
        double offset = doubleArg(tokens, start + 2, 0.1);
        double speed = doubleArg(tokens, start + 3, 0.0);
        Location location = locationArg(context, keyArg(tokens, "at"), "PARTICLE");
        if (location == null) {
            return;
        }
        location.getWorld().spawnParticle(particle, location, count, offset, offset, offset, speed);
    }

    private void particleLine(ActionExecutionContext context, List<String> tokens) {
        if (tokens.size() < 4) {
            warn(context.triggerContext(), "PARTICLE_LINE requires type, distance, and points");
            return;
        }
        int startIndex = firstPositionalIndex(tokens);
        if (tokens.size() <= startIndex + 2) {
            warn(context.triggerContext(), "PARTICLE_LINE requires type, distance, and points");
            return;
        }
        Particle particle = HitscanOptions.particleType(tokens.get(startIndex));
        if (particle == null) {
            warn(context.triggerContext(), "Unknown particle: " + tokens.get(startIndex));
            return;
        }
        double distance = Math.max(0.0, doubleArg(tokens, startIndex + 1, 0.0));
        int points = Math.max(1, intArg(tokens, startIndex + 2, 1));
        double offset = doubleArg(tokens, startIndex + 3, 0.0);
        double speed = doubleArg(tokens, startIndex + 4, 0.0);
        String at = keyArg(tokens, "at");
        Location start = locationArg(context, at, "PARTICLE_LINE");
        if (start == null) {
            return;
        }
        start = start.clone();
        if ((at == null && context.target() == context.self()) || "SELF".equalsIgnoreCase(at)) {
            start = context.self().getEyeLocation();
        } else if ((at == null && context.block() != null) || "BLOCK".equalsIgnoreCase(at) || "HIT".equalsIgnoreCase(at)) {
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

    private void startProjectileTrail(ActionExecutionContext context, ActionStep.ProjectileTrail trail) {
        Entity projectile = context.triggerContext().projectile();
        if (projectile == null) {
            warn(context.triggerContext(), "PROJECTILE_TRAIL requires a projectile context");
            return;
        }
        ProjectileTrailOptions options = ProjectileTrailOptions.parse(tokens(trail.header()));
        if (!options.errors().isEmpty()) {
            options.errors().forEach(error -> warn(context.triggerContext(), error));
            return;
        }
        Particle particle = options.particle().isBlank() ? null : ProjectileTrailOptions.particleType(options.particle());
        Location initial = projectile.getLocation().clone();
        new BukkitRunnable() {
            private int elapsed;
            private Location previous = initial;

            @Override
            public void run() {
                if (!context.self().isOnline()
                    || repository.get(context.triggerContext().itemId()) == null
                    || !projectile.isValid()
                    || projectile.isDead()
                    || projectile.isOnGround()
                    || elapsed >= options.duration()) {
                    cancel();
                    return;
                }
                Location current = projectile.getLocation().clone();
                if (particle != null) {
                    drawParticleTrail(previous, current, particle, options.count(), options.points(), options.offset(), options.speed());
                }
                ActionExecutionContext scoped = context.copy();
                scoped.location(current);
                runQueue(scoped, new ArrayDeque<>(trail.body()));
                previous = current;
                elapsed += options.interval();
            }
        }.runTaskTimer(plugin, options.interval(), options.interval());
    }

    private void drawParticleTrail(Location previous, Location current, Particle particle, int count, int points, double offset, double speed) {
        int safePoints = Math.max(1, points);
        Vector direction = current.toVector().subtract(previous.toVector());
        double step = safePoints == 1 ? 1.0 : 1.0 / (safePoints - 1);
        for (int i = 0; i < safePoints; i++) {
            Location point = previous.clone().add(direction.clone().multiply(step * i));
            point.getWorld().spawnParticle(particle, point, count, offset, offset, offset, speed);
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
        int start = firstPositionalIndex(tokens);
        if (tokens.size() <= start) {
            warn(context.triggerContext(), "DROPITEM requires material");
            return;
        }
        ValidationUtil.material(tokens.get(start)).ifPresentOrElse(material -> {
            int amount = Math.max(1, intArg(tokens, start + 1, 1));
            Location location = locationArg(context, keyArg(tokens, "at"), "DROPITEM");
            if (location == null) {
                return;
            }
            location.getWorld().dropItemNaturally(location, new ItemStack(material, amount));
        }, () -> warn(context.triggerContext(), "Invalid item material: " + tokens.get(start)));
    }

    private void veinmine(ActionExecutionContext context, List<String> tokens) {
        VeinmineOptions options = VeinmineOptions.parse(tokens);
        if (!options.errors().isEmpty()) {
            options.errors().forEach(error -> warn(context.triggerContext(), error));
            return;
        }
        Block start = blockOrTarget(context);
        if (start == null || start.getType().isAir()) {
            warn(context.triggerContext(), "VEINMINE requires a non-air block");
            return;
        }
        Map<NamespacedKey, Tag<Material>> tags = new HashMap<>();
        for (VeinmineOptions.FilterEntry entry : options.filter().entries()) {
            if (entry.kind() == VeinmineOptions.FilterKind.TAG) {
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, entry.tagKey(), Material.class);
                if (tag == null) {
                    warn(context.triggerContext(), "Unknown VEINMINE block tag: #" + entry.tagKey());
                    return;
                }
                tags.put(entry.tagKey(), tag);
            }
        }
        int limit = options.effectiveLimit(config.warnVeinmineBlocks());
        if (limit > config.warnVeinmineBlocks()) {
            warn(context.triggerContext(), "VEINMINE limit " + limit + " exceeds warning threshold " + config.warnVeinmineBlocks());
        }
        Material type = start.getType();
        Set<String> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        int broken = 0;
        while (!queue.isEmpty() && broken < limit) {
            Block block = queue.removeFirst();
            String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (!visited.add(key) || !matchesVeinmineFilter(block.getType(), type, options, tags)) {
                continue;
            }
            if (options.mode() == VeinmineOptions.Mode.REPLACE) {
                block.setType(options.replacement());
            } else {
                destroyVeinmineBlock(context, block, options);
            }
            broken++;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (isVeinmineNeighborOffset(x, y, z)) {
                            queue.add(block.getRelative(x, y, z));
                        }
                    }
                }
            }
        }
    }

    static boolean isVeinmineNeighborOffset(int x, int y, int z) {
        return x >= -1 && x <= 1
            && y >= -1 && y <= 1
            && z >= -1 && z <= 1
            && !(x == 0 && y == 0 && z == 0);
    }

    private boolean matchesVeinmineFilter(Material material, Material startType, VeinmineOptions options, Map<NamespacedKey, Tag<Material>> tags) {
        boolean filterMatch = switch (options.filter().kind()) {
            case SAME_TYPE -> material == startType;
            case MATERIAL -> material == options.filter().material();
            case TAG -> matchesVeinmineEntry(material, options.filter().entries().getFirst(), tags);
            case MULTI -> options.filter().entries().stream().anyMatch(entry -> matchesVeinmineEntry(material, entry, tags));
        };
        return options.matchMode() == VeinmineOptions.MatchMode.SAME_TYPE
            ? filterMatch && material == startType
            : filterMatch;
    }

    private boolean matchesVeinmineEntry(Material material, VeinmineOptions.FilterEntry entry, Map<NamespacedKey, Tag<Material>> tags) {
        return switch (entry.kind()) {
            case MATERIAL -> material == entry.material();
            case TAG -> Optional.ofNullable(tags.get(entry.tagKey())).map(tag -> tag.isTagged(material)).orElse(false);
            case SAME_TYPE, MULTI -> false;
        };
    }

    private void destroyVeinmineBlock(ActionExecutionContext context, Block block, VeinmineOptions options) {
        if (options.drop()) {
            if (options.useEnchants()) {
                block.breakNaturally(context.self().getInventory().getItemInMainHand(), options.effect(), options.xp());
            } else {
                block.breakNaturally(options.effect(), options.xp());
            }
        } else {
            if (options.effect()) {
                block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());
            }
            block.setType(Material.AIR);
        }
        if (options.useDurability()) {
            damageHeldTool(context.self());
        }
    }

    private void damageHeldTool(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        Material type = tool.getType();
        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR || type.getMaxDurability() <= 0) {
            return;
        }
        player.getInventory().setItemInMainHand(tool.damage(1, player));
    }

    private Block blockOrTarget(ActionExecutionContext context) {
        if (context.block() != null) {
            return context.block();
        }
        return null;
    }

    private void warn(TriggerContext context, String message) {
        String detail = "[actions] item=" + context.itemId() + " trigger=" + context.type() + " player=" + context.self().getName() + " " + message;
        if (context.self().isOp()) {
            context.self().sendMessage(Component.text("[ConfigurableItems] " + message, NamedTextColor.RED));
            return;
        }
        plugin.getLogger().warning(detail);
    }

    private void runtimeError(TriggerContext context, String line, RuntimeException ex) {
        String message = "Action failed: " + line + " (" + rootMessage(ex) + ")";
        String detail = "[actions] item=" + context.itemId() + " trigger=" + context.type() + " player=" + context.self().getName() + " " + message;
        if (context.self().isOp()) {
            context.self().sendMessage(Component.text("[ConfigurableItems] item=" + context.itemId() + " trigger=" + context.type() + " " + message, NamedTextColor.RED));
            return;
        }
        plugin.getLogger().warning(detail);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
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
        return intValue(tokens.get(index), fallback);
    }

    private static int intValue(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double doubleArg(List<String> tokens, int index, double fallback) {
        if (tokens.size() <= index) {
            return fallback;
        }
        return doubleValue(tokens.get(index), fallback);
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

    private static int firstPositionalIndex(List<String> tokens) {
        for (int i = 1; i < tokens.size(); i++) {
            if (!tokens.get(i).contains(":")) {
                return i;
            }
        }
        return tokens.size();
    }

    private static double doubleValue(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String stripKeyToken(String text, String key) {
        String prefix = key.toLowerCase(Locale.ROOT) + ":";
        return List.of((text == null ? "" : text).trim().split("\\s+")).stream()
            .filter(token -> !token.toLowerCase(Locale.ROOT).startsWith(prefix))
            .reduce((left, right) -> left + " " + right)
            .orElse("");
    }

    private static String token(List<String> tokens, int index, String fallback) {
        return tokens.size() > index ? tokens.get(index) : fallback;
    }
}
