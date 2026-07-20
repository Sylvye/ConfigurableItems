package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.action.HitscanOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HitscanOptionsTest {
    @Test
    void parsesLegacyHitscan() {
        HitscanOptions options = HitscanOptions.parse(List.of("HITSCAN", "32", "DAMAGE", "5"));

        assertTrue(options.errors().isEmpty());
        assertEquals(32, options.distance());
        assertEquals(HitscanOptions.TargetMode.ANY, options.targetMode());
        assertEquals(1, options.maxHits());
        assertFalse(options.allHits());
        assertEquals("DAMAGE 5", options.body());
    }

    @Test
    void parsesTargetModeAndAllHits() {
        HitscanOptions options = HitscanOptions.parse(List.of(
            "HITSCAN", "48", "target:PLAYER", "max-hits:all", "ACTIONBAR", "&cMarked"
        ));

        assertTrue(options.errors().isEmpty());
        assertEquals(HitscanOptions.TargetMode.PLAYER, options.targetMode());
        assertTrue(options.allHits());
        assertNull(options.maxHits());
        assertEquals("ACTIONBAR &cMarked", options.body());
    }

    @Test
    void parsesParticleTrailOptions() {
        HitscanOptions options = HitscanOptions.parse(List.of(
            "HITSCAN", "16", "target:MOB", "particle:flame", "points:12", "offset:0.1", "speed:0.02", "DAMAGE", "3"
        ));

        assertTrue(options.errors().isEmpty());
        assertEquals(HitscanOptions.TargetMode.MOB, options.targetMode());
        assertEquals("FLAME", options.particle());
        assertEquals(12, options.points());
        assertEquals(0.1, options.offset());
        assertEquals(0.02, options.speed());
        assertEquals("DAMAGE 3", options.body());
    }

    @Test
    void reportsInvalidNumbersAndParticle() {
        HitscanOptions options = HitscanOptions.parse(List.of(
            "HITSCAN", "0", "target:NOPE", "max-hits:0", "particle:not_a_particle", "points:-1", "DAMAGE", "3"
        ));

        assertEquals(5, options.errors().size());
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("distance")));
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("target")));
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("max-hits")));
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("particle")));
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("points")));
    }
}
