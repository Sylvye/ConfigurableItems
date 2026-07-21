package com.bountysmp.configurableitems.action;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ActionEngineTest {
    @Test
    void comparesEntitiesByUuidForHitscanExclusion() {
        UUID sourceId = UUID.randomUUID();
        Player source = fake(Player.class, sourceId);
        Entity sameSource = fake(Entity.class, sourceId);
        Entity other = fake(Entity.class, UUID.randomUUID());

        assertTrue(ActionEngine.sameEntity(source, source));
        assertTrue(ActionEngine.sameEntity(sameSource, source));
        assertFalse(ActionEngine.sameEntity(other, source));
        assertFalse(ActionEngine.sameEntity(other, null));
    }

    @Test
    void veinmineIncludesObliqueNeighbors() {
        assertTrue(ActionEngine.isVeinmineNeighborOffset(1, 0, 0));
        assertTrue(ActionEngine.isVeinmineNeighborOffset(1, 1, 0));
        assertTrue(ActionEngine.isVeinmineNeighborOffset(1, 1, 1));

        assertFalse(ActionEngine.isVeinmineNeighborOffset(0, 0, 0));
        assertFalse(ActionEngine.isVeinmineNeighborOffset(2, 0, 0));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> T fake(Class<T> type, UUID uuid) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> uuid;
                case "equals" -> proxy == args[0];
                case "hashCode" -> uuid.hashCode();
                case "toString" -> type.getSimpleName() + "[" + uuid + "]";
                default -> null;
            }
        );
    }
}
