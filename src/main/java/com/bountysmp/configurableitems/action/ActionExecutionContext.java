package com.bountysmp.configurableitems.action;

import com.bountysmp.configurableitems.trigger.TriggerContext;
import com.bountysmp.configurableitems.util.PlaceholderResolver;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

final class ActionExecutionContext {
    private final TriggerContext triggerContext;
    private Entity target;
    private Entity projectile;
    private Location location;
    private Block block;
    private final Map<String, String> variables = new HashMap<>();

    ActionExecutionContext(TriggerContext triggerContext) {
        this.triggerContext = triggerContext;
        this.target = triggerContext.target() == null ? triggerContext.self() : triggerContext.target();
        this.projectile = triggerContext.projectile();
        this.block = triggerContext.block();
        this.location = block == null ? target.getLocation() : block.getLocation();
        this.variables.putAll(triggerContext.variables());
    }

    ActionExecutionContext copy() {
        ActionExecutionContext copy = new ActionExecutionContext(triggerContext);
        copy.target = target;
        copy.projectile = projectile;
        copy.location = location == null ? null : location.clone();
        copy.block = block;
        copy.variables.clear();
        copy.variables.putAll(variables);
        return copy;
    }

    TriggerContext triggerContext() {
        return triggerContext;
    }

    Player self() {
        return triggerContext.self();
    }

    Entity target() {
        return target;
    }

    Entity projectile() {
        return projectile;
    }

    Location location() {
        if (location != null) {
            return location;
        }
        return target == null ? self().getLocation() : target.getLocation();
    }

    Block block() {
        return block;
    }

    Map<String, String> variables() {
        return variables;
    }

    void target(Entity target) {
        this.target = target == null ? self() : target;
        this.location = this.target.getLocation();
        this.block = null;
        putTargetVariables(this.target);
    }

    void clearTarget() {
        this.target = null;
    }

    void projectile(Entity projectile) {
        this.projectile = projectile;
        if (projectile != null) {
            variables.put("PROJECTILE", projectile.getType().key().asString());
            variables.put("PROJECTILE_WORLD", projectile.getWorld().getName());
            putLocationVariables("PROJECTILE_", projectile.getLocation());
        }
    }

    void location(Location location) {
        this.location = location;
        this.block = location == null ? null : location.getBlock();
        if (location != null) {
            putProjectileLocationVariables(location);
        }
    }

    void block(Block block) {
        this.block = block;
        this.location = block == null ? null : block.getLocation();
        if (block != null) {
            variables.put("BLOCK", block.getType().key().asString());
            variables.put("BLOCK_WORLD", block.getWorld().getName());
            putLocationVariables("BLOCK_", block.getLocation());
        }
    }

    void hitEntity(Entity entity, Location location) {
        variables.put("HIT_TYPE", "ENTITY");
        putHitLocationVariables(location == null ? entity.getLocation() : location);
        putTargetVariables(entity);
    }

    void hitBlock(Block block, Location location) {
        variables.put("HIT_TYPE", "BLOCK");
        putHitLocationVariables(location == null ? block.getLocation() : location);
        variables.put("BLOCK", block.getType().key().asString());
        variables.put("BLOCK_WORLD", block.getWorld().getName());
        putLocationVariables("BLOCK_", block.getLocation());
    }

    void putVariable(String key, String value) {
        variables.put(key, value);
        variables.put(key.toUpperCase(), value);
    }

    PlaceholderResolver.Result replaceVariables(String input, boolean allowUnresolved) {
        return PlaceholderResolver.render(input, variables, allowUnresolved);
    }

    private void putTargetVariables(Entity entity) {
        variables.put("TARGET", entity instanceof Player player ? player.getName() : entity.getType().key().asString());
        variables.put("TARGET_UUID", entity.getUniqueId().toString());
        variables.put("ENTITY", entity.getType().key().asString());
        variables.put("ENTITY_UUID", entity.getUniqueId().toString());
        variables.put("TARGET_WORLD", entity.getWorld().getName());
        putLocationVariables("TARGET_", entity.getLocation());
    }

    private void putProjectileLocationVariables(Location location) {
        variables.put("PROJECTILE_WORLD", location.getWorld().getName());
        putLocationVariables("PROJECTILE_", location);
    }

    private void putHitLocationVariables(Location location) {
        variables.put("HIT_WORLD", location.getWorld().getName());
        putLocationVariables("HIT_", location);
    }

    private void putLocationVariables(String prefix, Location location) {
        variables.put(prefix + "X", String.valueOf(location.getBlockX()));
        variables.put(prefix + "Y", String.valueOf(location.getBlockY()));
        variables.put(prefix + "Z", String.valueOf(location.getBlockZ()));
    }
}
