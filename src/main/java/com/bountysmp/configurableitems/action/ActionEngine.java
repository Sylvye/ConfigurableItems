package com.bountysmp.configurableitems.action;

import com.bountysmp.configurableitems.storage.ItemRepository;
import com.bountysmp.configurableitems.trigger.TriggerContext;
import com.bountysmp.configurableitems.trigger.ProjectileTracker;
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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class ActionEngine {
    private static final Set<String> ACTIONS = Set.of(
        "DELAY_TICK", "IF", "AROUND", "MOB_AROUND", "NEAREST", "MOB_NEAREST", "HITSCAN",
        "HITBOX", "TIMER", "LAUNCH_PROJECTILE",
        "DAMAGE", "HEAL", "SET_HEALTH", "KILL", "BURN", "INVULNERABILITY", "TELEPORT", "VELOCITY", "DASH",
        "DAMAGE_ITEM", "TAKE_ITEM", "REPAIR_ITEM", "IMPULSE", "EXPLODE",
        "SEND_MESSAGE", "ACTIONBAR", "PARTICLE", "PARTICLE_LINE", "PROJECTILE_TRAIL", "SET_BLOCK", "SET_TEMP_BLOCK", "BREAK_BLOCK", "DROPITEM", "VEINMINE"
    );

    private final Plugin plugin;
    private final ItemRepository repository;
    private final ActionConfig config;
    private final ProjectileTracker projectileTracker;
    private final Random random = new Random();
    private int delayedQueues;

    public ActionEngine(Plugin plugin, ItemRepository repository, ActionConfig config) {
        this(plugin, repository, config, new ProjectileTracker());
    }

    public ActionEngine(Plugin plugin, ItemRepository repository, ActionConfig config, ProjectileTracker projectileTracker) {
        this.plugin = plugin;
        this.repository = repository;
        this.config = config;
        this.projectileTracker = projectileTracker;
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
            } else if (step instanceof ActionStep.Hitbox hitbox) {
                runHitbox(context, hitbox);
            } else if (step instanceof ActionStep.Timer timer) {
                startTimer(context, timer);
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
            case "AROUND", "MOB_AROUND" -> runSelector(context, tokens, false, line);
            case "NEAREST", "MOB_NEAREST" -> runSelector(context, tokens, true, line);
            case "HITSCAN" -> runHitscan(context, tokens, line);
            case "LAUNCH_PROJECTILE" -> launchProjectile(context, tokens);
            case "DAMAGE" -> damage(context, tokens);
            case "HEAL" -> heal(context, tokens);
            case "SET_HEALTH" -> setHealth(context, tokens);
            case "KILL" -> livingReceiver(context, tokens, action).setHealth(0.0);
            case "BURN" -> livingReceiver(context, tokens, action).setFireTicks(Math.max(0, intArg(tokens, firstPositionalIndex(tokens), 0)) * 20);
            case "INVULNERABILITY" -> livingReceiver(context, tokens, action).setNoDamageTicks(Math.max(0, intArg(tokens, firstPositionalIndex(tokens), 0)));
            case "TELEPORT" -> teleport(context, tokens);
            case "VELOCITY" -> livingReceiver(context, tokens, action).setVelocity(vector(tokens, firstPositionalIndex(tokens)));
            case "DASH" -> context.self().setVelocity(context.self().getLocation().getDirection().normalize().multiply(doubleArg(tokens, "strength", 1, 1.0)));
            case "DAMAGE_ITEM" -> damageItem(context, tokens);
            case "TAKE_ITEM" -> takeItem(context, tokens);
            case "REPAIR_ITEM" -> repairItem(context, tokens);
            case "IMPULSE" -> impulse(context, tokens);
            case "EXPLODE" -> explode(context, tokens);
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
        if (isSelector(action)) {
            List<String> rawTokens = tokens(line);
            if (rawTokens.size() < 2) {
                return Optional.of(line);
            }
            int bodyIndex = selectorBodyIndex(rawTokens);
            Optional<String> radius = renderRequired(context, rawTokens.get(1), line);
            if (radius.isEmpty()) {
                return Optional.empty();
            }
            List<String> rendered = new ArrayList<>();
            rendered.add(rawTokens.getFirst());
            rendered.add(radius.get());
            for (int i = 2; i < bodyIndex; i++) {
                Optional<String> option = renderRequired(context, rawTokens.get(i), line);
                if (option.isEmpty()) {
                    return Optional.empty();
                }
                rendered.add(option.get());
            }
            if (bodyIndex < rawTokens.size()) {
                rendered.add(bodyAfter(rawTokens, bodyIndex));
            }
            return Optional.of(String.join(" ", rendered));
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

    private void runSelector(ActionExecutionContext context, List<String> tokens, boolean nearestOnly, String line) {
        SelectorSpec spec = SelectorSpec.parse(tokens, actionName(line));
        for (String error : spec.errors()) {
            warn(context.triggerContext(), error);
        }
        if (!spec.errors().isEmpty()) {
            return;
        }
        double radius = doubleArg(tokens, 1, 0.0);
        if (radius > config.warnRadius()) {
            warn(context.triggerContext(), spec.action() + " target:" + spec.target().name() + " radius " + radius + " exceeds warning threshold " + config.warnRadius());
        }
        String body = spec.body();
        if (body.isBlank()) {
            warn(context.triggerContext(), spec.action() + " is missing an action body");
            return;
        }
        List<Entity> selected = context.self().getNearbyEntities(radius, radius, radius).stream()
            .filter(entity -> !sameEntity(entity, context.self()) && spec.target().matchesEntity(entity))
            .filter(entity -> entity.getLocation().distanceSquared(context.self().getLocation()) <= radius * radius)
            .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(context.self().getLocation())))
            .toList();
        if (selected.size() > config.warnSelectedEntities()) {
            warn(context.triggerContext(), spec.action() + " target:" + spec.target().name() + " selected " + selected.size() + " entities, threshold " + config.warnSelectedEntities());
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
        List<RayHit> hits = options.targetMode() == TargetKind.BLOCK
            ? (blockHit == null ? List.of() : List.of(blockHit))
            : rayEntities(context.self(), options.distance(), blockHit == null ? options.distance() : blockHit.projection(), options.targetMode());
        int selectedCount = options.targetMode() == TargetKind.BLOCK
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

    private List<RayHit> rayEntities(Player player, int distance, double maxProjection, TargetKind targetMode) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        List<RayHit> hits = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(distance, distance, distance)) {
            if (!targetMode.matchesEntity(entity) || sameEntity(entity, player)) {
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

    private void launchProjectile(ActionExecutionContext context, List<String> tokens) {
        int start = firstPositionalIndex(tokens);
        if (tokens.size() <= start) {
            warn(context.triggerContext(), "LAUNCH_PROJECTILE requires projectile type");
            return;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(tokens.get(start).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            warn(context.triggerContext(), "Invalid projectile type: " + tokens.get(start));
            return;
        }
        Location startLocation = context.self().getEyeLocation().clone();
        boolean track = booleanArg(tokens, "track", true);
        projectileTracker.suppressNextLaunch(context.self(), track ? context.triggerContext().itemId() : "");
        Entity spawned = startLocation.getWorld().spawnEntity(startLocation, type);
        if (!(spawned instanceof Projectile projectile)) {
            spawned.remove();
            warn(context.triggerContext(), "LAUNCH_PROJECTILE type is not a projectile: " + tokens.get(start));
            return;
        }
        double speed = doubleArg(tokens, "speed", start + 1, 1.5);
        projectile.setShooter(context.self());
        projectile.setVelocity(startLocation.getDirection().normalize().multiply(speed));
        projectile.setGravity(booleanArg(tokens, "gravity", true));
        context.projectile(projectile);
        if (track) {
            projectileTracker.track(projectile, context.triggerContext().itemId());
        }
    }

    private void damage(ActionExecutionContext context, List<String> tokens) {
        LivingEntity target = livingReceiver(context, tokens, "DAMAGE");
        if (target == null) {
            return;
        }
        double amount = doubleArg(tokens, "amount", firstPositionalIndex(tokens), 0.0);
        String type = Optional.ofNullable(keyArg(tokens, "type")).orElse("normal");
        if (type.equalsIgnoreCase("true")) {
            target.setHealth(Math.max(0.0, target.getHealth() - Math.max(0.0, amount)));
            return;
        }
        if (!type.equalsIgnoreCase("normal")) {
            warn(context.triggerContext(), "DAMAGE type must be normal or true: " + type);
            return;
        }
        target.damage(amount, context.self());
    }

    private void damageItem(ActionExecutionContext context, List<String> tokens) {
        Player player = playerReceiver(context, tokens, "DAMAGE_ITEM");
        if (player == null) {
            return;
        }
        int start = firstPositionalIndex(tokens);
        int amount = Math.max(1, intArg(tokens, start, 1));
        damageInventoryItem(context, player, keyArg(tokens, "item"), amount);
    }

    private void takeItem(ActionExecutionContext context, List<String> tokens) {
        Player player = playerReceiver(context, tokens, "TAKE_ITEM");
        if (player == null) {
            return;
        }
        int start = firstPositionalIndex(tokens);
        if (tokens.size() <= start) {
            warn(context.triggerContext(), "TAKE_ITEM requires SELF or material");
            return;
        }
        String item = tokens.get(start);
        int amount = Math.max(1, intArg(tokens, start + 1, 1));
        if (item.equalsIgnoreCase("SELF")) {
            ItemStack stack = preferredHeldItem(player);
            if (stack == null) {
                warn(context.triggerContext(), "TAKE_ITEM SELF found no held item");
                return;
            }
            stack.setAmount(Math.max(0, stack.getAmount() - amount));
            return;
        }
        ValidationUtil.material(item).ifPresentOrElse(
            material -> player.getInventory().removeItem(new ItemStack(material, amount)),
            () -> warn(context.triggerContext(), "Invalid item material: " + item)
        );
    }

    private void repairItem(ActionExecutionContext context, List<String> tokens) {
        Player player = playerReceiver(context, tokens, "REPAIR_ITEM");
        if (player == null) {
            return;
        }
        int start = firstPositionalIndex(tokens);
        String amountToken = token(tokens, start, "full");
        String itemArg = keyArg(tokens, "item");
        if (itemArg == null && tokens.size() > start + 1) {
            itemArg = tokens.get(start + 1);
        }
        ItemStack stack = itemStackArg(context, player, itemArg, "REPAIR_ITEM");
        if (stack == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        if (amountToken.equalsIgnoreCase("full")) {
            damageable.setDamage(0);
        } else {
            damageable.setDamage(Math.max(0, damageable.getDamage() - Math.max(1, intValue(amountToken, 1))));
        }
        stack.setItemMeta(meta);
    }

    private void damageInventoryItem(ActionExecutionContext context, Player player, String rawItem, int amount) {
        if (rawItem == null || rawItem.isBlank() || rawItem.equalsIgnoreCase("SELF")) {
            ItemStack stack = preferredHeldItem(player);
            if (stack == null) {
                warn(context.triggerContext(), "DAMAGE_ITEM SELF found no held item");
                return;
            }
            if (stack == player.getInventory().getItemInOffHand()) {
                player.getInventory().setItemInOffHand(stack.damage(amount, player));
            } else {
                player.getInventory().setItemInMainHand(stack.damage(amount, player));
            }
            return;
        }
        Optional<Material> material = ValidationUtil.material(rawItem);
        if (material.isEmpty()) {
            warn(context.triggerContext(), "Invalid item material: " + rawItem);
            return;
        }
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != null && stack.getType() == material.get()) {
                player.getInventory().setItem(i, stack.damage(amount, player));
                return;
            }
        }
    }

    private ItemStack itemStackArg(ActionExecutionContext context, Player player, String rawItem, String action) {
        if (rawItem == null || rawItem.isBlank() || rawItem.equalsIgnoreCase("SELF")) {
            ItemStack stack = preferredHeldItem(player);
            if (stack == null) {
                warn(context.triggerContext(), action + " SELF found no held item");
            }
            return stack;
        }
        Optional<Material> material = ValidationUtil.material(rawItem);
        if (material.isEmpty()) {
            return null;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material.get()) {
                return stack;
            }
        }
        return null;
    }

    private ItemStack preferredHeldItem(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && !main.getType().isAir()) {
            return main;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand != null && !offhand.getType().isAir() ? offhand : null;
    }

    private void impulse(ActionExecutionContext context, List<String> tokens) {
        int start = firstPositionalIndex(tokens);
        if (tokens.size() <= start + 2) {
            warn(context.triggerContext(), "IMPULSE requires x y z");
            return;
        }
        Location origin = new Location(
            context.location().getWorld(),
            doubleArg(tokens, start, 0.0),
            doubleArg(tokens, start + 1, 0.0),
            doubleArg(tokens, start + 2, 0.0)
        );
        double power = doubleArg(tokens, "power", start + 3, 1.0);
        double radius = Math.max(0.000001, doubleArg(tokens, "radius", start + 4, 8.0));
        boolean normalize = booleanArg(tokens, "normalize", true);
        for (Entity entity : impulseTargets(context, Optional.ofNullable(keyArg(tokens, "targets")).orElse("CURRENT"), radius)) {
            Vector velocity = impulseVector(origin.toVector(), entity.getLocation().toVector(), power, radius, normalize);
            if (velocity.lengthSquared() > 0.0) {
                entity.setVelocity(velocity);
            }
        }
    }

    private List<Entity> impulseTargets(ActionExecutionContext context, String rawTargets, double radius) {
        List<Entity> selected = new ArrayList<>();
        for (String raw : rawTargets.split(",")) {
            switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "SELF" -> selected.add(context.self());
                case "TARGET", "CURRENT" -> {
                    if (context.target() != null) {
                        selected.add(context.target());
                    }
                }
                case "PLAYERS" -> selected.addAll(context.self().getNearbyEntities(radius, radius, radius).stream().filter(Player.class::isInstance).toList());
                case "MOBS" -> selected.addAll(context.self().getNearbyEntities(radius, radius, radius).stream().filter(entity -> entity instanceof LivingEntity && !(entity instanceof Player)).toList());
                case "ENTITIES" -> selected.addAll(context.self().getNearbyEntities(radius, radius, radius));
                default -> {}
            }
        }
        return selected.stream().distinct().toList();
    }

    static Vector impulseVector(Vector origin, Vector target, double power, double radius, boolean normalize) {
        Vector direction = target.clone().subtract(origin);
        double distance = direction.length();
        if (distance <= 0.000001) {
            return new Vector();
        }
        double strength = normalize ? power : power * Math.max(0.0, 1.0 - distance / Math.max(0.000001, radius));
        return direction.normalize().multiply(strength);
    }

    private void explode(ActionExecutionContext context, List<String> tokens) {
        int start = firstPositionalIndex(tokens);
        if (tokens.size() <= start + 3) {
            warn(context.triggerContext(), "EXPLODE requires power x y z");
            return;
        }
        World world = tokens.size() > start + 4 ? Bukkit.getWorld(tokens.get(start + 4)) : context.location().getWorld();
        if (world == null) {
            warn(context.triggerContext(), "EXPLODE world not found: " + tokens.get(start + 4));
            return;
        }
        Location location = new Location(world, doubleArg(tokens, start + 1, 0.0), doubleArg(tokens, start + 2, 0.0), doubleArg(tokens, start + 3, 0.0));
        world.createExplosion(location, (float) doubleArg(tokens, start, 1.0), booleanArg(tokens, "fire", false), booleanArg(tokens, "break-blocks", true), context.self());
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
                teleportReceiver(context, receiver, destination, tokens);
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
        teleportReceiver(context, receiver, new Location(world, doubleArg(tokens, start, 0.0), doubleArg(tokens, start + 1, 0.0), doubleArg(tokens, start + 2, 0.0)), tokens);
    }

    private void teleportReceiver(ActionExecutionContext context, LivingEntity receiver, Location destination, List<String> tokens) {
        if (!booleanArg(tokens, "safe", false)) {
            receiver.teleport(destination);
            return;
        }
        Location safe = safeTeleportLocation(destination);
        if (safe == null) {
            warn(context.triggerContext(), "TELEPORT safe:true found no safe destination near " + destination.getBlockX() + "," + destination.getBlockY() + "," + destination.getBlockZ());
            return;
        }
        receiver.teleport(safe);
    }

    static Location safeTeleportLocation(Location destination) {
        World world = destination.getWorld();
        if (world == null) {
            return null;
        }
        int baseX = destination.getBlockX();
        int baseY = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 2, destination.getBlockY()));
        int baseZ = destination.getBlockZ();
        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        int y = baseY + dy;
                        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) {
                            continue;
                        }
                        Block feet = world.getBlockAt(baseX + dx, y, baseZ + dz);
                        Block head = world.getBlockAt(baseX + dx, y + 1, baseZ + dz);
                        Block floor = world.getBlockAt(baseX + dx, y - 1, baseZ + dz);
                        if ((feet.isPassable() || feet.getType().isAir())
                            && (head.isPassable() || head.getType().isAir())
                            && floor.getType().isSolid()) {
                            return new Location(world, baseX + dx + 0.5, y, baseZ + dz + 0.5, destination.getYaw(), destination.getPitch());
                        }
                    }
                }
            }
        }
        return null;
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
        ParticleShapeOptions shape = ParticleShapeOptions.parse(tokens);
        for (String error : shape.errors()) {
            warn(context.triggerContext(), error);
        }
        if (shape.shape() != ParticleShapeOptions.Shape.POINT) {
            for (Vector vector : particleShapeOffsets(shape)) {
                Location point = location.clone().add(vector);
                point.getWorld().spawnParticle(particle, point, count, offset, offset, offset, speed);
            }
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

    static List<Vector> particleShapeOffsets(ParticleShapeOptions options) {
        double size = Math.max(0.0, options.size());
        int points = Math.max(1, options.points());
        List<Vector> vectors = new ArrayList<>();
        switch (options.shape()) {
            case POINT -> vectors.add(new Vector());
            case LINE -> {
                double step = points == 1 ? 0.0 : size / (points - 1);
                for (int i = 0; i < points; i++) {
                    vectors.add(new Vector(-size / 2.0 + step * i, 0, 0));
                }
            }
            case CIRCLE -> {
                for (int i = 0; i < points; i++) {
                    double angle = (Math.PI * 2.0 * i) / points;
                    vectors.add(new Vector(Math.cos(angle) * size, 0, Math.sin(angle) * size));
                }
            }
            case TRIANGLE, SQUARE, PENTAGON, HEXAGON, SEPTAGON, OCTAGON, NONAGON, DECAGON -> {
                int sides = options.shape().sides();
                int perSide = Math.max(1, points / sides);
                List<Vector> corners = new ArrayList<>();
                for (int i = 0; i < sides; i++) {
                    double angle = (Math.PI * 2.0 * i) / sides;
                    corners.add(new Vector(Math.cos(angle) * size, 0, Math.sin(angle) * size));
                }
                for (int i = 0; i < sides; i++) {
                    Vector a = corners.get(i);
                    Vector b = corners.get((i + 1) % sides);
                    for (int p = 0; p < perSide; p++) {
                        double t = perSide == 1 ? 0.0 : (double) p / perSide;
                        vectors.add(a.clone().multiply(1.0 - t).add(b.clone().multiply(t)));
                    }
                }
            }
        }
        return vectors.stream().map(vector -> rotate(vector, options.rotX(), options.rotY(), options.rotZ())).toList();
    }

    private static Vector rotate(Vector input, double rotX, double rotY, double rotZ) {
        double xRad = Math.toRadians(rotX);
        double yRad = Math.toRadians(rotY);
        double zRad = Math.toRadians(rotZ);
        double x = input.getX();
        double y = input.getY();
        double z = input.getZ();
        double cos = Math.cos(xRad);
        double sin = Math.sin(xRad);
        double y1 = y * cos - z * sin;
        double z1 = y * sin + z * cos;
        y = y1;
        z = z1;
        cos = Math.cos(yRad);
        sin = Math.sin(yRad);
        double x1 = x * cos + z * sin;
        z1 = -x * sin + z * cos;
        x = x1;
        z = z1;
        cos = Math.cos(zRad);
        sin = Math.sin(zRad);
        x1 = x * cos - y * sin;
        y1 = x * sin + y * cos;
        return new Vector(x1, y1, z);
    }

    private void startProjectileTrail(ActionExecutionContext context, ActionStep.ProjectileTrail trail) {
        Entity projectile = context.projectile();
        if (projectile == null) {
            warn(context.triggerContext(), "PROJECTILE_TRAIL requires a projectile context");
            return;
        }
        Optional<String> renderedHeader = renderRequired(context, trail.header(), trail.header());
        if (renderedHeader.isEmpty()) {
            return;
        }
        ProjectileTrailOptions options = ProjectileTrailOptions.parse(tokens(renderedHeader.get()));
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

    private void startTimer(ActionExecutionContext context, ActionStep.Timer timer) {
        Optional<String> renderedHeader = renderRequired(context, timer.header(), timer.header());
        if (renderedHeader.isEmpty()) {
            return;
        }
        TimerOptions options = TimerOptions.parse(tokens(renderedHeader.get()));
        if (!options.errors().isEmpty()) {
            options.errors().forEach(error -> warn(context.triggerContext(), error));
            return;
        }
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!context.self().isOnline() || repository.get(context.triggerContext().itemId()) == null || ticks > options.duration()) {
                    cancel();
                    return;
                }
                ActionExecutionContext scoped = context.copy();
                scoped.putVariable("TICKS", String.valueOf(ticks));
                runQueue(scoped, new ArrayDeque<>(timer.body()));
                ticks += options.interval();
            }
        }.runTaskTimer(plugin, 0L, options.interval());
    }

    private void runHitbox(ActionExecutionContext context, ActionStep.Hitbox hitbox) {
        Optional<String> renderedHeader = renderRequired(context, hitbox.header(), hitbox.header());
        if (renderedHeader.isEmpty()) {
            return;
        }
        HitboxOptions options = HitboxOptions.parse(tokens(renderedHeader.get()));
        if (!options.errors().isEmpty()) {
            options.errors().forEach(error -> warn(context.triggerContext(), error));
            return;
        }
        Location center = locationArg(context, options.at(), "HITBOX");
        if (center == null) {
            return;
        }
        Location hitboxCenter = center.clone();
        List<Entity> nearby = hitboxCenter.getWorld().getNearbyEntities(hitboxCenter, options.size(), options.size(), options.size()).stream()
            .filter(entity -> !sameEntity(entity, context.self()))
            .filter(entity -> hitboxIncludes(options.shape(), options.size(), hitboxCenter.toVector(), entity.getLocation().toVector(), context.self().getEyeLocation().getDirection()))
            .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(hitboxCenter)))
            .toList();
        runHitboxEntities(context, hitbox, options, hitboxCenter, nearby);
        runHitboxBlocks(context, hitbox, options, hitboxCenter);
        drawHitboxEdges(context, options, hitboxCenter);
    }

    private void runHitboxEntities(ActionExecutionContext context, ActionStep.Hitbox hitbox, HitboxOptions options, Location center, List<Entity> nearby) {
        int players = 0;
        int entities = 0;
        for (Entity entity : nearby) {
            boolean player = entity instanceof Player;
            boolean entityAllowed = options.targets().contains(TargetKind.ENTITY) || options.targets().contains(TargetKind.MOB) && TargetKind.MOB.matchesEntity(entity);
            if (player && !options.targets().contains(TargetKind.PLAYER)) {
                continue;
            }
            if (!player && !entityAllowed) {
                continue;
            }
            if (player && players++ >= options.maxPlayers()) {
                continue;
            }
            if (!player && entities++ >= options.maxEntities()) {
                continue;
            }
            ActionExecutionContext scoped = context.copy();
            scoped.target(entity);
            scoped.hitEntity(entity, entity.getLocation());
            runQueue(scoped, new ArrayDeque<>(hitbox.body()));
        }
    }

    private void runHitboxBlocks(ActionExecutionContext context, ActionStep.Hitbox hitbox, HitboxOptions options, Location center) {
        if (!options.targets().contains(TargetKind.BLOCK) || options.maxBlocks() <= 0) {
            return;
        }
        int radius = (int) Math.ceil(options.size());
        List<Block> blocks = new ArrayList<>();
        Vector direction = context.self().getEyeLocation().getDirection();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getWorld().getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    if (hitboxIncludes(options.shape(), options.size(), center.toVector(), block.getLocation().add(0.5, 0.5, 0.5).toVector(), direction)) {
                        blocks.add(block);
                    }
                }
            }
        }
        blocks.stream()
            .sorted(Comparator.comparingDouble(block -> block.getLocation().distanceSquared(center)))
            .limit(options.maxBlocks())
            .forEach(block -> {
                ActionExecutionContext scoped = context.copy();
                scoped.block(block);
                scoped.hitBlock(block, block.getLocation().add(0.5, 0.5, 0.5));
                scoped.clearTarget();
                runQueue(scoped, new ArrayDeque<>(hitbox.body()));
            });
    }

    private void drawHitboxEdges(ActionExecutionContext context, HitboxOptions options, Location center) {
        if (!options.edgeParticles() || options.particle().isBlank()) {
            return;
        }
        Particle particle = HitboxOptions.particleType(options.particle());
        if (particle == null) {
            warn(context.triggerContext(), "Unknown HITBOX particle: " + options.particle());
            return;
        }
        for (Vector offset : hitboxEdgeOffsets(options)) {
            Location point = center.clone().add(offset);
            point.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0);
        }
    }

    static boolean hitboxIncludes(HitboxOptions.Shape shape, double size, Vector center, Vector point, Vector direction) {
        Vector relative = point.clone().subtract(center);
        return switch (shape) {
            case SPHERE -> relative.lengthSquared() <= size * size;
            case CUBE -> Math.abs(relative.getX()) <= size && Math.abs(relative.getY()) <= size && Math.abs(relative.getZ()) <= size;
            case CONE -> {
                Vector dir = direction.lengthSquared() == 0.0 ? new Vector(0, 0, 1) : direction.clone().normalize();
                double projection = relative.dot(dir);
                if (projection < 0 || projection > size) {
                    yield false;
                }
                double allowedRadius = (projection / size) * size;
                double perpendicular = relative.clone().subtract(dir.multiply(projection)).length();
                yield perpendicular <= allowedRadius;
            }
        };
    }

    static List<Vector> hitboxEdgeOffsets(HitboxOptions options) {
        return switch (options.shape()) {
            case SPHERE -> particleShapeOffsets(new ParticleShapeOptions(ParticleShapeOptions.Shape.CIRCLE, options.size(), 0, 0, 0, options.points(), List.of()));
            case CUBE -> cubeEdgeOffsets(options.size(), options.points());
            case CONE -> coneEdgeOffsets(options.size(), options.points());
        };
    }

    private static List<Vector> cubeEdgeOffsets(double size, int points) {
        List<Vector> output = new ArrayList<>();
        double[] signs = {-size, size};
        int perEdge = Math.max(2, points / 12);
        for (double y : signs) {
            for (double z : signs) {
                addLine(output, new Vector(-size, y, z), new Vector(size, y, z), perEdge);
            }
        }
        for (double x : signs) {
            for (double z : signs) {
                addLine(output, new Vector(x, -size, z), new Vector(x, size, z), perEdge);
            }
        }
        for (double x : signs) {
            for (double y : signs) {
                addLine(output, new Vector(x, y, -size), new Vector(x, y, size), perEdge);
            }
        }
        return output;
    }

    private static List<Vector> coneEdgeOffsets(double size, int points) {
        List<Vector> output = new ArrayList<>();
        int circlePoints = Math.max(8, points);
        for (Vector base : particleShapeOffsets(new ParticleShapeOptions(ParticleShapeOptions.Shape.CIRCLE, size, 0, 0, 0, circlePoints, List.of()))) {
            base.setZ(base.getZ() + size);
            output.add(base);
        }
        Vector tip = new Vector();
        int perSide = Math.max(2, points / 8);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            addLine(output, tip, new Vector(Math.cos(angle) * size, 0, Math.sin(angle) * size + size), perSide);
        }
        return output;
    }

    private static void addLine(List<Vector> output, Vector a, Vector b, int points) {
        for (int i = 0; i < points; i++) {
            double t = points == 1 ? 0.0 : (double) i / (points - 1);
            output.add(a.clone().multiply(1.0 - t).add(b.clone().multiply(t)));
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

    private static boolean isSelector(String action) {
        return action.equals("AROUND") || action.equals("MOB_AROUND") || action.equals("NEAREST") || action.equals("MOB_NEAREST");
    }

    private static boolean isSelectorOption(String token) {
        return token != null && token.toLowerCase(Locale.ROOT).startsWith("target:");
    }

    private static int selectorBodyIndex(List<String> tokens) {
        int index = 2;
        while (index < tokens.size() && isSelectorOption(tokens.get(index))) {
            index++;
        }
        return index;
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

    private record SelectorSpec(String action, TargetKind target, String body, List<String> errors) {
        static SelectorSpec parse(List<String> tokens, String rawAction) {
            String upper = rawAction.toUpperCase(Locale.ROOT);
            String action = upper.equals("MOB_AROUND") ? "AROUND" : upper.equals("MOB_NEAREST") ? "NEAREST" : upper;
            TargetKind target = upper.startsWith("MOB_") ? TargetKind.MOB : TargetKind.PLAYER;
            List<String> errors = new ArrayList<>();
            int index = 2;
            while (index < tokens.size() && isSelectorOption(tokens.get(index))) {
                String value = tokens.get(index).substring(tokens.get(index).indexOf(':') + 1);
                Optional<TargetKind> parsed = TargetKind.parse(value);
                if (parsed.isEmpty() || parsed.get() == TargetKind.BLOCK) {
                    errors.add(action + " target must be " + TargetKind.allowedValues(false) + ": " + value);
                } else {
                    target = parsed.get();
                }
                index++;
            }
            return new SelectorSpec(action, target, bodyAfter(tokens, index), List.copyOf(errors));
        }
    }
}
