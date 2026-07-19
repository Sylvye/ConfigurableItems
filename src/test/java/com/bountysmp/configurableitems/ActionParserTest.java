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
        assertFalse(ActionEngine.isKnownActionName("minecraft:give"));
    }

    @Test
    void splitsInlineChains() {
        assertEquals(List.of("DAMAGE 1", "SEND_MESSAGE &aHit"), ActionParser.splitInline("DAMAGE 1 <+> SEND_MESSAGE &aHit"));
        assertEquals(List.of("give a", "give b"), ActionParser.splitRandomInline("give a +++ give b"));
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
}
