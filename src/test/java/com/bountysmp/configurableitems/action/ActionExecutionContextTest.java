package com.bountysmp.configurableitems.action;

import com.bountysmp.configurableitems.model.TriggerType;
import com.bountysmp.configurableitems.trigger.TriggerContext;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ActionExecutionContextTest {
    @Test
    void nestedLocationsExposeExactAndBlockCoordinates() {
        World world = fakeWorld("world");
        Player self = fake(Player.class, "self", UUID.randomUUID(), EntityType.PLAYER, new Location(world, 0.5, 64, 0.5));
        Entity target = fake(Entity.class, "zombie", UUID.randomUUID(), EntityType.ZOMBIE, new Location(world, 30.5, 65.75, -0.2));
        Entity projectile = fake(Entity.class, "arrow", UUID.randomUUID(), EntityType.ARROW, new Location(world, 10.9, 64.25, -0.2));
        Block block = fakeBlock(world, Material.STONE, new Location(world, 50, 66, 60));
        ActionExecutionContext context = new ActionExecutionContext(new TriggerContext(TriggerType.RIGHT_CLICK, "item", "Item", self));

        context.target(target);
        context.projectile(projectile);
        context.hitEntity(target, new Location(world, 10.9, 64.25, -0.2));
        context.hitBlock(block, new Location(world, 20.9, 70.25, -2.2));

        assertEquals("30.5", context.variables().get("TARGET_X"));
        assertEquals("-1", context.variables().get("TARGET_BLOCK_Z"));
        assertEquals("10.9", context.variables().get("PROJECTILE_X"));
        assertEquals("-1", context.variables().get("PROJECTILE_BLOCK_Z"));
        assertEquals("20.9", context.variables().get("HIT_X"));
        assertEquals("-3", context.variables().get("HIT_BLOCK_Z"));
        assertEquals("50", context.variables().get("BLOCK_X"));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> T fake(Class<T> type, String name, UUID uuid, EntityType entityType, Location location) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> name;
                case "getUniqueId" -> uuid;
                case "getType" -> entityType;
                case "getWorld" -> location.getWorld();
                case "getLocation" -> location.clone();
                case "equals" -> proxy == args[0];
                case "hashCode" -> uuid.hashCode();
                default -> null;
            }
        );
    }

    private static World fakeWorld(String name) {
        return (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[] {World.class},
            (proxy, method, args) -> method.getName().equals("getName") ? name : null
        );
    }

    private static Block fakeBlock(World world, Material material, Location location) {
        return (Block) Proxy.newProxyInstance(
            Block.class.getClassLoader(),
            new Class<?>[] {Block.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getType" -> material;
                case "getWorld" -> world;
                case "getLocation" -> location.clone();
                default -> null;
            }
        );
    }
}
