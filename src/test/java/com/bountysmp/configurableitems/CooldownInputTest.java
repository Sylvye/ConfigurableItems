package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.gui.CooldownInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CooldownInputTest {
    @Test
    void parsesEnabledCooldown() {
        CooldownInput.ParseResult result = CooldownInput.parse("100 &cThat ability is on cooldown.");

        assertTrue(result.valid());
        assertEquals(100, result.ticks());
        assertEquals("&cThat ability is on cooldown.", result.message());
    }

    @Test
    void clearDisablesCooldown() {
        CooldownInput.ParseResult result = CooldownInput.parse("clear");

        assertTrue(result.valid());
        assertEquals(0, result.ticks());
        assertEquals("", result.message());
    }

    @Test
    void zeroDisablesCooldown() {
        CooldownInput.ParseResult result = CooldownInput.parse("0 ignored message");

        assertTrue(result.valid());
        assertEquals(0, result.ticks());
        assertEquals("", result.message());
    }

    @Test
    void messageIsOptional() {
        CooldownInput.ParseResult result = CooldownInput.parse("20");

        assertTrue(result.valid());
        assertEquals(20, result.ticks());
        assertEquals("", result.message());
    }

    @Test
    void rejectsInvalidCooldowns() {
        assertFalse(CooldownInput.parse("abc &cWait").valid());
        assertFalse(CooldownInput.parse("-1 &cWait").valid());
    }
}
