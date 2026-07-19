package com.bountysmp.configurableitems.action;

import com.bountysmp.configurableitems.trigger.TriggerContext;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class ActionExecutionContext {
    private final TriggerContext triggerContext;
    private Entity target;
    private Location location;
    private Block block;
    private final Map<String, String> variables = new HashMap<>();

    ActionExecutionContext(TriggerContext triggerContext) {
        this.triggerContext = triggerContext;
        this.target = triggerContext.target() == null ? triggerContext.self() : triggerContext.target();
        this.block = triggerContext.block();
        this.location = block == null ? target.getLocation() : block.getLocation();
        this.variables.putAll(triggerContext.variables());
    }

    ActionExecutionContext copy() {
        ActionExecutionContext copy = new ActionExecutionContext(triggerContext);
        copy.target = target;
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

    LivingEntity livingTarget() {
        return target instanceof LivingEntity living ? living : self();
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
        putEntityVariables(this.target);
    }

    void location(Location location) {
        this.location = location;
        this.block = location == null ? null : location.getBlock();
        if (location != null) {
            putLocationVariables(location);
        }
    }

    void block(Block block) {
        this.block = block;
        this.location = block == null ? null : block.getLocation();
        if (block != null) {
            variables.put("BLOCK", block.getType().key().asString());
            variables.put("WORLD", block.getWorld().getName());
            putLocationVariables(block.getLocation());
        }
    }

    void putVariable(String key, String value) {
        variables.put(key, value);
        variables.put(key.toUpperCase(), value);
    }

    String replaceVariables(String input) {
        String output = input;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }

    private void putEntityVariables(Entity entity) {
        variables.put("TARGET", entity instanceof Player player ? player.getName() : entity.getType().key().asString());
        variables.put("TARGET_UUID", entity.getUniqueId().toString());
        variables.put("ENTITY", entity.getType().key().asString());
        variables.put("ENTITY_UUID", entity.getUniqueId().toString());
        variables.put("WORLD", entity.getWorld().getName());
        putLocationVariables(entity.getLocation());
    }

    private void putLocationVariables(Location location) {
        variables.put("X", String.valueOf(location.getBlockX()));
        variables.put("Y", String.valueOf(location.getBlockY()));
        variables.put("Z", String.valueOf(location.getBlockZ()));
    }
}
