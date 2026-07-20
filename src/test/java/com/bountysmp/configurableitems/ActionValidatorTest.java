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
}
