package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.action.ActionValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ActionValidatorTest {
    @Test
    void rejectsInvalidHitscanOptions() {
        assertTrue(ActionValidator.invalidKnownAction(List.of("HITSCAN 24 target:PLAYER max-hits:all DAMAGE 3")).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of("HITSCAN 24 particle:not_a_particle DAMAGE 3")).orElseThrow().contains("particle"));
    }

    @Test
    void rejectsInvalidVeinmineOptions() {
        assertTrue(ActionValidator.invalidKnownAction(List.of("VEINMINE 64 filter:OAK_LOG,#minecraft:logs use-durability:false")).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of("VEINMINE 64 mode:replace")).orElseThrow().contains("replace"));
        assertTrue(ActionValidator.invalidKnownAction(List.of("VEINMINE 64 drop:maybe")).orElseThrow().contains("drop"));
        assertTrue(ActionValidator.invalidKnownAction(List.of("VEINMINE 64 match:maybe")).orElseThrow().contains("match"));
    }

    @Test
    void rejectsInvalidProjectileTrailOptions() {
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "PROJECTILE_TRAIL particle:FLAME interval:1 duration:20",
            "PARTICLE CRIT 1",
            "END_PROJECTILE_TRAIL"
        )).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "PROJECTILE_TRAIL particle:not_a_particle",
            "PARTICLE CRIT 1",
            "END_PROJECTILE_TRAIL"
        )).orElseThrow().contains("particle"));
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "PROJECTILE_TRAIL interval:0",
            "PARTICLE CRIT 1",
            "END_PROJECTILE_TRAIL"
        )).orElseThrow().contains("interval"));
    }
}
