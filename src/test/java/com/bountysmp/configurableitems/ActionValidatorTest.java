package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.action.ActionValidator;
import com.bountysmp.configurableitems.model.TriggerType;
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
        assertTrue(ActionValidator.invalidKnownAction(List.of("VEINMINE 64 filter:OAK_LOG,#minecraft:logs use-durability:false"), TriggerType.BLOCK_BREAK).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of("VEINMINE 64 mode:replace"), TriggerType.BLOCK_BREAK).orElseThrow().contains("replace"));
        assertTrue(ActionValidator.invalidKnownAction(List.of("VEINMINE 64 drop:maybe"), TriggerType.BLOCK_BREAK).orElseThrow().contains("drop"));
        assertTrue(ActionValidator.invalidKnownAction(List.of("VEINMINE 64 match:maybe"), TriggerType.BLOCK_BREAK).orElseThrow().contains("match"));
    }

    @Test
    void rejectsInvalidProjectileTrailOptions() {
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "PROJECTILE_TRAIL particle:FLAME interval:1 duration:20",
            "PARTICLE CRIT 1",
            "END_PROJECTILE_TRAIL"
        ), TriggerType.LAUNCH_PROJECTILE).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "PROJECTILE_TRAIL particle:not_a_particle",
            "PARTICLE CRIT 1",
            "END_PROJECTILE_TRAIL"
        ), TriggerType.LAUNCH_PROJECTILE).orElseThrow().contains("particle"));
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "PROJECTILE_TRAIL interval:0",
            "PARTICLE CRIT 1",
            "END_PROJECTILE_TRAIL"
        ), TriggerType.LAUNCH_PROJECTILE).orElseThrow().contains("interval"));
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "LAUNCH_PROJECTILE ARROW",
            "PROJECTILE_TRAIL particle:FLAME",
            "END_PROJECTILE_TRAIL"
        ), TriggerType.RIGHT_CLICK).isEmpty());
    }

    @Test
    void validatesNewActionOptionsAndScopes() {
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "TIMER duration:10 interval:2",
            "SEND_MESSAGE {TICKS}",
            "END_TIMER"
        ), TriggerType.RIGHT_CLICK).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of("SEND_MESSAGE {TICKS}"), TriggerType.RIGHT_CLICK).orElseThrow().contains("TICKS"));
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "HITBOX shape:SPHERE size:3 targets:PLAYER,BLOCK",
            "SEND_MESSAGE target:SELF {TARGET_X} {BLOCK_X} {HIT_X}",
            "END_HITBOX"
        ), TriggerType.RIGHT_CLICK).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of("HITBOX shape:BAD size:3", "DAMAGE 1", "END_HITBOX"), TriggerType.RIGHT_CLICK).orElseThrow().contains("shape"));
        assertTrue(ActionValidator.invalidKnownAction(List.of("DAMAGE 5 type:true"), TriggerType.HIT_PLAYER).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of("DAMAGE 5 type:magic"), TriggerType.HIT_PLAYER).orElseThrow().contains("type"));
        assertTrue(ActionValidator.invalidKnownAction(List.of("PARTICLE FLAME 1 shape:DECAGON size:2 rotation:0,0,0 points:12"), TriggerType.RIGHT_CLICK).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of("PARTICLE FLAME 1 shape:BAD"), TriggerType.RIGHT_CLICK).orElseThrow().contains("shape"));
        assertTrue(ActionValidator.invalidKnownAction(List.of("SEND_MESSAGE {CRITICAL}"), TriggerType.HIT_PLAYER).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of("SEND_MESSAGE {CRITICAL}"), TriggerType.RIGHT_CLICK).orElseThrow().contains("CRITICAL"));
    }

    @Test
    void allowsScopedVariablesOnlyWhereProduced() {
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "AROUND 8 target:PLAYER SEND_MESSAGE target:TARGET &e{TARGET_X}"
        ), TriggerType.RIGHT_CLICK).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "AROUND 8 target:MOB DAMAGE 2"
        ), TriggerType.RIGHT_CLICK).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "NEAREST 8 target:ENTITY SEND_MESSAGE &e{TARGET_UUID}"
        ), TriggerType.RIGHT_CLICK).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "HITSCAN 24 target:ENTITY SEND_MESSAGE target:TARGET &e{TARGET_X},{HIT_X}"
        ), TriggerType.RIGHT_CLICK).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "HITSCAN 24 target:BLOCK minecraft:fill {HIT_X-1} {HIT_Y-1} {HIT_Z-1} {HIT_X+1} {HIT_Y+1} {HIT_Z+1} minecraft:cobweb replace #air"
        ), TriggerType.RIGHT_CLICK).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "SEND_MESSAGE &e{TARGET_X}"
        ), TriggerType.RIGHT_CLICK).orElseThrow().contains("TARGET_X"));
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "HITSCAN 24 target:BLOCK SET_BLOCK GLOWSTONE <+> SEND_MESSAGE &e{BLOCK_X},{HIT_TYPE}"
        ), TriggerType.RIGHT_CLICK).isEmpty());
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "SEND_MESSAGE &e{X},{Y},{Z},{WORLD}"
        ), TriggerType.RIGHT_CLICK).orElseThrow().contains("X"));
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "SEND_MESSAGE &e{X-1}"
        ), TriggerType.RIGHT_CLICK).orElseThrow().contains("X"));
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "FOR [1,2] > X",
            "SEND_MESSAGE {X}",
            "END_FOR X"
        ), TriggerType.RIGHT_CLICK).orElseThrow().contains("reserved"));
    }

    @Test
    void rejectsRequiredContextsAndUselessDelay() {
        assertTrue(ActionValidator.invalidKnownAction(List.of("AROUND 8 target:BLOCK DAMAGE 2"), TriggerType.RIGHT_CLICK).orElseThrow().contains("target must"));
        assertTrue(ActionValidator.invalidKnownAction(List.of("AROUND 8 target:NOPE DAMAGE 2"), TriggerType.RIGHT_CLICK).orElseThrow().contains("target must"));
        assertTrue(ActionValidator.invalidKnownAction(List.of("NEAREST 8 target:MOB"), TriggerType.RIGHT_CLICK).orElseThrow().contains("missing an action body"));
        assertTrue(ActionValidator.invalidKnownAction(List.of("SET_BLOCK STONE"), TriggerType.RIGHT_CLICK).orElseThrow().contains("block context"));
        assertTrue(ActionValidator.invalidKnownAction(List.of("PROJECTILE_TRAIL particle:FLAME", "END_PROJECTILE_TRAIL"), TriggerType.RIGHT_CLICK).orElseThrow().contains("projectile context"));
        assertTrue(ActionValidator.invalidKnownAction(List.of("DELAY_TICK 20"), TriggerType.RIGHT_CLICK).orElseThrow().contains("no following action"));
        assertTrue(ActionValidator.invalidKnownAction(List.of(
            "LOOP_START 2",
            "PARTICLE CRIT 1",
            "DELAY_TICK 20",
            "LOOP_END"
        ), TriggerType.RIGHT_CLICK).isEmpty());
    }
}
