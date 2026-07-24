package com.bountysmp.configurableitems.action;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import org.bukkit.util.Vector;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void targetKindMatchesConfiguredEntityGroups() {
        Player player = fake(Player.class, UUID.randomUUID());
        Entity nonPlayer = fake(Entity.class, UUID.randomUUID());
        LivingEntity mob = fake(LivingEntity.class, UUID.randomUUID());

        assertTrue(TargetKind.PLAYER.matchesEntity(player));
        assertFalse(TargetKind.PLAYER.matchesEntity(nonPlayer));

        assertFalse(TargetKind.MOB.matchesEntity(player));
        assertFalse(TargetKind.MOB.matchesEntity(nonPlayer));
        assertTrue(TargetKind.MOB.matchesEntity(mob));

        assertTrue(TargetKind.ENTITY.matchesEntity(player));
        assertTrue(TargetKind.ENTITY.matchesEntity(nonPlayer));

        assertFalse(TargetKind.BLOCK.matchesEntity(player));
        assertFalse(TargetKind.BLOCK.matchesEntity(nonPlayer));
    }

    @Test
    void computesParticleRayDelaysFromRaySpeed() {
        assertEquals(List.of(0L, 20L, 40L), ActionEngine.particleRayDelays(2.0, 3, 1.0));
        assertEquals(List.of(0L, 10L, 20L), ActionEngine.particleRayDelays(4.0, 3, 4.0));
        assertEquals(List.of(0L), ActionEngine.particleRayDelays(10.0, 1, 1.0));
    }

    @Test
    void computesImpulseVectors() {
        assertEquals(new Vector(2, 0, 0), ActionEngine.impulseVector(new Vector(0, 0, 0), new Vector(5, 0, 0), 2, 10, true));
        assertEquals(new Vector(-1, 0, 0), ActionEngine.impulseVector(new Vector(0, 0, 0), new Vector(5, 0, 0), -2, 10, false));
    }

    @Test
    void explodeWorldNameIgnoresKeyValueOptions() {
        assertEquals(null, ActionEngine.explodeWorldName(List.of("EXPLODE", "2", "1", "2", "3", "fire:false", "break-blocks:true"), 1));
        assertEquals("world_nether", ActionEngine.explodeWorldName(List.of("EXPLODE", "2", "1", "2", "3", "world_nether", "fire:false"), 1));
        assertEquals("world_the_end", ActionEngine.explodeWorldName(List.of("EXPLODE", "2", "1", "2", "3", "world:world_the_end", "fire:false"), 1));
    }

    @Test
    void teleportWorldNameIgnoresKeyValueOptions() {
        assertEquals(null, ActionEngine.teleportWorldName(List.of("TELEPORT", "target:CURRENT", "1", "2", "3", "safe:false"), 2));
        assertEquals("world", ActionEngine.teleportWorldName(List.of("TELEPORT", "target:CURRENT", "1", "2", "3", "world", "safe:false"), 2));
        assertEquals("world_nether", ActionEngine.teleportWorldName(List.of("TELEPORT", "target:CURRENT", "1", "2", "3", "world:world_nether", "safe:false"), 2));
    }

    @Test
    void evaluatesHitboxShapes() {
        assertTrue(ActionEngine.hitboxIncludes(HitboxOptions.Shape.SPHERE, 2, new Vector(), new Vector(1, 1, 0), new Vector(0, 0, 1)));
        assertFalse(ActionEngine.hitboxIncludes(HitboxOptions.Shape.CUBE, 2, new Vector(), new Vector(3, 0, 0), new Vector(0, 0, 1)));
        assertTrue(ActionEngine.hitboxIncludes(HitboxOptions.Shape.CONE, 4, new Vector(), new Vector(0.5, 0, 2), new Vector(0, 0, 1)));
    }

    @Test
    void generatesParticleShapeOffsets() {
        assertEquals(6, ActionEngine.particleShapeOffsets(new ParticleShapeOptions(ParticleShapeOptions.Shape.HEXAGON, 1, 0, 0, 0, 6, List.of())).size());
        assertEquals(3, ActionEngine.particleShapeOffsets(new ParticleShapeOptions(ParticleShapeOptions.Shape.LINE, 2, 0, 0, 0, 3, List.of())).size());
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
