package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.action.ActionEngine;
import com.bountysmp.configurableitems.action.ActionParser;
import com.bountysmp.configurableitems.action.ActionStep;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ActionParserTest {
    @Test
    void detectsKnownActions() {
        assertTrue(ActionEngine.isKnownActionName("DAMAGE"));
        assertTrue(ActionEngine.isKnownActionName("send_message"));
        assertTrue(ActionEngine.isKnownActionName("PARTICLE_LINE"));
        assertTrue(ActionEngine.isKnownActionName("PROJECTILE_TRAIL"));
        assertTrue(ActionEngine.isKnownActionName("HITBOX"));
        assertTrue(ActionEngine.isKnownActionName("TIMER"));
        assertTrue(ActionEngine.isKnownActionName("LAUNCH_PROJECTILE"));
        assertFalse(ActionEngine.isKnownActionName("minecraft:give"));
    }

    @Test
    void splitsInlineChains() {
        assertEquals(List.of("DAMAGE 1", "SEND_MESSAGE &aHit"), ActionParser.splitInline("DAMAGE 1 <+> SEND_MESSAGE &aHit"));
        assertEquals(List.of("give a", "give b"), ActionParser.splitRandomInline("give a +++ give b"));
    }

    @Test
    void keepsSelectorInlineChainsInsideNestedBody() {
        ActionParser.ParseResult result = ActionParser.parse(List.of("HITSCAN 24 DAMAGE 8 <+> PARTICLE CRIT 40"));

        assertTrue(result.ok());
        ActionStep.Simple simple = assertInstanceOf(ActionStep.Simple.class, result.steps().getFirst());
        assertEquals("HITSCAN 24 DAMAGE 8 <+> PARTICLE CRIT 40", simple.line());
        assertEquals(1, result.steps().size());
    }

    @Test
    void keepsSelectorOptionsAndInlineChainsInsideNestedBody() {
        ActionParser.ParseResult result = ActionParser.parse(List.of("AROUND 12 target:MOB DAMAGE 8 <+> PARTICLE CRIT 40"));

        assertTrue(result.ok());
        ActionStep.Simple simple = assertInstanceOf(ActionStep.Simple.class, result.steps().getFirst());
        assertEquals("AROUND 12 target:MOB DAMAGE 8 <+> PARTICLE CRIT 40", simple.line());
        assertEquals(1, result.steps().size());
    }

    @Test
    void parsesNestedBlocks() {
        ActionParser.ParseResult result = ActionParser.parse(List.of(
            "LOOP_START 2 5",
            "RANDOM_RUN selectionCount:1",
            "SEND_MESSAGE a",
            "SEND_MESSAGE b",
            "RANDOM_END",
            "LOOP_END"
        ));

        assertTrue(result.ok());
        ActionStep.Loop loop = assertInstanceOf(ActionStep.Loop.class, result.steps().getFirst());
        assertEquals(2, loop.times());
        assertEquals(5, loop.delayTicks());
        assertInstanceOf(ActionStep.RandomBlock.class, loop.body().getFirst());
    }

    @Test
    void parsesProjectileTrailBlock() {
        ActionParser.ParseResult result = ActionParser.parse(List.of(
            "PROJECTILE_TRAIL particle:FLAME points:3 interval:1 duration:100",
            "PARTICLE CRIT 2 0.05 0",
            "AROUND 1.5 DAMAGE 2",
            "END_PROJECTILE_TRAIL"
        ));

        assertTrue(result.ok());
        ActionStep.ProjectileTrail trail = assertInstanceOf(ActionStep.ProjectileTrail.class, result.steps().getFirst());
        assertEquals("PROJECTILE_TRAIL particle:FLAME points:3 interval:1 duration:100", trail.header());
        assertEquals(2, trail.body().size());
    }

    @Test
    void reportsMissingProjectileTrailTerminator() {
        ActionParser.ParseResult result = ActionParser.parse(List.of("PROJECTILE_TRAIL particle:FLAME", "PARTICLE CRIT 1"));

        assertFalse(result.ok());
        assertTrue(result.errors().getFirst().contains("END_PROJECTILE_TRAIL"));
    }

    @Test
    void parsesHitboxAndTimerBlocks() {
        ActionParser.ParseResult result = ActionParser.parse(List.of(
            "TIMER duration:20 interval:5",
            "HITBOX shape:SPHERE size:4 targets:PLAYER,BLOCK",
            "SEND_MESSAGE {TICKS} {HIT_X}",
            "END_HITBOX",
            "END_TIMER"
        ));

        assertTrue(result.ok());
        ActionStep.Timer timer = assertInstanceOf(ActionStep.Timer.class, result.steps().getFirst());
        assertEquals(1, timer.body().size());
        ActionStep.Hitbox hitbox = assertInstanceOf(ActionStep.Hitbox.class, timer.body().getFirst());
        assertEquals("HITBOX shape:SPHERE size:4 targets:PLAYER,BLOCK", hitbox.header());
    }

    @Test
    void reportsMissingHitboxTerminator() {
        ActionParser.ParseResult result = ActionParser.parse(List.of("HITBOX shape:CUBE size:2", "DAMAGE 1"));

        assertFalse(result.ok());
        assertTrue(result.errors().getFirst().contains("END_HITBOX"));
    }

    @Test
    void reportsMalformedBlocks() {
        ActionParser.ParseResult result = ActionParser.parse(List.of("LOOP_START 3", "SEND_MESSAGE test"));

        assertFalse(result.ok());
        assertTrue(result.errors().getFirst().contains("Missing block terminator"));
    }

    @Test
    void parsesForBlock() {
        ActionParser.ParseResult result = ActionParser.parse(List.of(
            "FOR [a,b,c] > for1",
            "SEND_MESSAGE {for1}",
            "END_FOR for1"
        ));

        assertTrue(result.ok());
        ActionStep.ForBlock block = assertInstanceOf(ActionStep.ForBlock.class, result.steps().getFirst());
        assertEquals(List.of("a", "b", "c"), block.values());
        assertEquals("for1", block.variable());
    }

    @Test
    void parsesNumericForBlock() {
        ActionParser.ParseResult result = ActionParser.parse(List.of(
            "FOR [step=0.5;1;4]",
            "SEND_MESSAGE {step}",
            "END_FOR step"
        ));

        assertTrue(result.ok());
        ActionStep.ForBlock block = assertInstanceOf(ActionStep.ForBlock.class, result.steps().getFirst());
        assertEquals(List.of("0.5", "1.5", "2.5", "3.5"), block.values());
        assertEquals("step", block.variable());
    }

    @Test
    void reportsMalformedNumericForBlock() {
        ActionParser.ParseResult result = ActionParser.parse(List.of(
            "FOR [step=bad;1;3]",
            "SEND_MESSAGE {step}",
            "END_FOR step"
        ));

        assertFalse(result.ok());
        assertTrue(result.errors().getFirst().contains("Malformed number"));
    }

    @Test
    void rejectsReservedForVariables() {
        ActionParser.ParseResult result = ActionParser.parse(List.of(
            "FOR [X=0;1;2]",
            "SEND_MESSAGE {X}",
            "END_FOR X"
        ));

        assertFalse(result.ok());
        assertTrue(result.errors().getFirst().contains("reserved"));
    }
}
