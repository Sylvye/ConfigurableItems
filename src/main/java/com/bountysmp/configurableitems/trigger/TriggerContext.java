package com.bountysmp.configurableitems.trigger;

import com.bountysmp.configurableitems.model.TriggerType;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class TriggerContext {
    private final TriggerType type;
    private final String itemId;
    private final String itemName;
    private final Player self;
    private Entity target;
    private Block block;
    private Entity projectile;
    private final Map<String, String> variables = new LinkedHashMap<>();

    public TriggerContext(TriggerType type, String itemId, String itemName, Player self) {
        this.type = type;
        this.itemId = itemId;
        this.itemName = itemName;
        this.self = self;
        put("SELF", self.getName());
        put("SELF_UUID", self.getUniqueId().toString());
        put("SELF_WORLD", self.getWorld().getName());
        putLocation("SELF_", self.getLocation());
        put("ITEM_ID", itemId);
        put("ITEM_NAME", itemName);
    }

    public TriggerType type() {
        return type;
    }

    public String itemId() {
        return itemId;
    }

    public String itemName() {
        return itemName;
    }

    public Map<String, String> variables() {
        return variables;
    }

    public Player self() {
        return self;
    }

    public Entity target() {
        return target;
    }

    public Block block() {
        return block;
    }

    public Entity projectile() {
        return projectile;
    }

    public TriggerContext target(Entity entity) {
        if (entity != null) {
            this.target = entity;
            put("TARGET", entity instanceof Player player ? player.getName() : entity.getType().key().asString());
            put("TARGET_UUID", entity.getUniqueId().toString());
            put("ENTITY", entity.getType().key().asString());
            put("ENTITY_UUID", entity.getUniqueId().toString());
            put("TARGET_WORLD", entity.getWorld().getName());
            putLocation("TARGET_", entity.getLocation());
        }
        return this;
    }

    public TriggerContext block(Block block) {
        if (block != null) {
            this.block = block;
            put("BLOCK", block.getType().key().asString());
            put("BLOCK_WORLD", block.getWorld().getName());
            putLocation("BLOCK_", block.getLocation());
        }
        return this;
    }

    public TriggerContext projectile(Entity projectile) {
        if (projectile != null) {
            this.projectile = projectile;
            put("PROJECTILE", projectile.getType().key().asString());
            put("PROJECTILE_WORLD", projectile.getWorld().getName());
            putLocation("PROJECTILE_", projectile.getLocation());
        }
        return this;
    }

    private void putLocation(String prefix, Location location) {
        put(prefix + "X", String.valueOf(location.getBlockX()));
        put(prefix + "Y", String.valueOf(location.getBlockY()));
        put(prefix + "Z", String.valueOf(location.getBlockZ()));
    }

    private void put(String key, String value) {
        variables.put(key.toUpperCase(Locale.ROOT), value);
    }
}
