package com.bountysmp.configurableitems.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ActionCommandRowsTest {
    @Test
    void collapsesMatchedBlockAsSingleRow() {
        List<ActionCommandRows.Row> rows = ActionCommandRows.rows(List.of(
            "SEND_MESSAGE &aBefore",
            "LOOP_START 3 20",
            "PARTICLE CRIT 15",
            "SEND_MESSAGE &ePulse",
            "LOOP_END",
            "SEND_MESSAGE &aAfter"
        ));

        assertEquals(3, rows.size());
        assertFalse(rows.get(0).block());
        assertTrue(rows.get(1).block());
        assertEquals(1, rows.get(1).startIndex());
        assertEquals(4, rows.get(1).endIndex());
        assertEquals(2, rows.get(1).nestedLineCount());
        assertEquals("LOOP_START 3 20 (2 nested)", rows.get(1).summary());
        assertFalse(rows.get(2).block());
    }

    @Test
    void keepsNestedBlocksInsideOuterRow() {
        List<ActionCommandRows.Row> rows = ActionCommandRows.rows(List.of(
            "RANDOM_RUN selectionCount:1",
            "LOOP_START 2 0",
            "DAMAGE 1",
            "LOOP_END",
            "SEND_MESSAGE &aOption",
            "RANDOM_END"
        ));

        assertEquals(1, rows.size());
        assertTrue(rows.getFirst().block());
        assertEquals(0, rows.getFirst().startIndex());
        assertEquals(5, rows.getFirst().endIndex());
        assertEquals(4, rows.getFirst().nestedLineCount());
    }

    @Test
    void treatsUnmatchedBlockHeaderAsSingleSafeRow() {
        List<ActionCommandRows.Row> rows = ActionCommandRows.rows(List.of(
            "FOR [a,b] > VALUE",
            "SEND_MESSAGE {VALUE}",
            "DAMAGE 1"
        ));

        assertEquals(3, rows.size());
        assertFalse(rows.getFirst().block());
        assertEquals(0, rows.getFirst().startIndex());
        assertEquals(0, rows.getFirst().endIndex());
        assertEquals("FOR [a,b] > VALUE", rows.getFirst().summary());
    }
}
