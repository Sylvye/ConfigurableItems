package com.bountysmp.configurableitems.trigger;

import com.bountysmp.configurableitems.model.TriggerType;
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
import static org.junit.jupiter.api.Assertions.assertFalse;

final class TriggerContextTest {
    @Test
    void targetAndBlockDoNotOverwriteSelfCoordinates() {
        World world = fakeWorld("world");
        Player self = fake(Player.class, "self", UUID.randomUUID(), EntityType.PLAYER, new Location(world, 10, 64, 20));
        Entity target = fake(Entity.class, "zombie", UUID.randomUUID(), EntityType.ZOMBIE, new Location(world, 30, 65, 40));
        Block block = fakeBlock(world, Material.STONE, new Location(world, 50, 66, 60));

        TriggerContext context = new TriggerContext(TriggerType.RIGHT_CLICK_BLOCK, "item", "Item", self)
            .target(target)
            .block(block);

        assertFalse(context.variables().containsKey("X"));
        assertFalse(context.variables().containsKey("Y"));
        assertFalse(context.variables().containsKey("Z"));
        assertFalse(context.variables().containsKey("WORLD"));
        assertEquals("30", context.variables().get("TARGET_X"));
        assertEquals("50", context.variables().get("BLOCK_X"));
        assertEquals("10", context.variables().get("SELF_X"));
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
