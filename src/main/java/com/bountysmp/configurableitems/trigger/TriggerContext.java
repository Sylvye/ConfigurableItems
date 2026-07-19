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
    private final Map<String, String> variables = new LinkedHashMap<>();

    public TriggerContext(TriggerType type, String itemId, String itemName, Player self) {
        this.type = type;
        this.itemId = itemId;
        this.itemName = itemName;
        put("SELF", self.getName());
        put("SELF_UUID", self.getUniqueId().toString());
        put("WORLD", self.getWorld().getName());
        putLocation(self.getLocation());
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

    public TriggerContext target(Entity entity) {
        if (entity != null) {
            put("TARGET", entity instanceof Player player ? player.getName() : entity.getType().key().asString());
            put("TARGET_UUID", entity.getUniqueId().toString());
            put("ENTITY", entity.getType().key().asString());
            put("ENTITY_UUID", entity.getUniqueId().toString());
            putLocation(entity.getLocation());
        }
        return this;
    }

    public TriggerContext block(Block block) {
        if (block != null) {
            put("BLOCK", block.getType().key().asString());
            put("WORLD", block.getWorld().getName());
            putLocation(block.getLocation());
        }
        return this;
    }

    public TriggerContext projectile(Entity projectile) {
        if (projectile != null) {
            put("PROJECTILE", projectile.getType().key().asString());
        }
        return this;
    }

    private void putLocation(Location location) {
        put("X", String.valueOf(location.getBlockX()));
        put("Y", String.valueOf(location.getBlockY()));
        put("Z", String.valueOf(location.getBlockZ()));
    }

    private void put(String key, String value) {
        variables.put(key.toUpperCase(Locale.ROOT), value);
    }
}
