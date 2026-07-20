package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.action.ActionFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ActionFormatterTest {
    @Test
    void normalizesActionNamesAndVariables() {
        assertEquals("DAMAGE {SELF}", ActionFormatter.normalizeLine("damage {self}"));
        assertEquals("HITSCAN 24 DAMAGE {TARGET} <+> PARTICLE flame 10", ActionFormatter.normalizeLine("hitscan 24 damage {Target} <+> particle flame 10"));
    }

    @Test
    void preservesConsoleCommandTextExceptVariables() {
        assertEquals("minecraft:give {SELF} minecraft:diamond 1", ActionFormatter.normalizeLine("minecraft:give {self} minecraft:diamond 1"));
    }

    @Test
    void normalizesForVariables() {
        assertEquals("FOR [red,blue] > COLOR", ActionFormatter.normalizeLine("for [red,blue] > color"));
        assertEquals("END_FOR COLOR", ActionFormatter.normalizeLine("endfor color"));
        assertEquals(List.of("FOR [red] > COLOR", "SEND_MESSAGE {COLOR}"), ActionFormatter.normalizeLines(List.of("for [red] > color", "send_message {color}")));
    }
}
