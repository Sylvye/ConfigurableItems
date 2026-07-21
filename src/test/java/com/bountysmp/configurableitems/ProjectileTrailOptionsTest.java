package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.action.ProjectileTrailOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProjectileTrailOptionsTest {
    @Test
    void parsesDefaults() {
        ProjectileTrailOptions options = ProjectileTrailOptions.parse(List.of("PROJECTILE_TRAIL"));

        assertTrue(options.errors().isEmpty());
        assertEquals("", options.particle());
        assertEquals(1, options.count());
        assertEquals(1, options.points());
        assertEquals(1, options.interval());
        assertEquals(100, options.duration());
        assertEquals(0.0, options.offset());
        assertEquals(0.0, options.speed());
    }

    @Test
    void parsesConfiguredTrail() {
        ProjectileTrailOptions options = ProjectileTrailOptions.parse(List.of(
            "PROJECTILE_TRAIL", "particle:flame", "count:2", "points:3", "interval:2", "duration:40", "offset:0.1", "speed:0.05"
        ));

        assertTrue(options.errors().isEmpty());
        assertEquals("FLAME", options.particle());
        assertEquals(2, options.count());
        assertEquals(3, options.points());
        assertEquals(2, options.interval());
        assertEquals(40, options.duration());
        assertEquals(0.1, options.offset());
        assertEquals(0.05, options.speed());
    }

    @Test
    void rejectsInvalidOptions() {
        ProjectileTrailOptions options = ProjectileTrailOptions.parse(List.of(
            "PROJECTILE_TRAIL", "particle:not_a_particle", "count:0", "points:nope", "interval:-1", "duration:bad", "offset:bad", "extra:true"
        ));

        assertEquals(7, options.errors().size());
    }
}
