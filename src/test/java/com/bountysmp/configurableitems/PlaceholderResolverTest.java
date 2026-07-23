package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.util.PlaceholderResolver;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlaceholderResolverTest {
    @Test
    void rendersPlainVariablesAndNumericExpressions() {
        PlaceholderResolver.Result result = PlaceholderResolver.render(
            "{SELF} {SELF_X-1} {BLOCK_Y+2} {HIT_Z*2} {(SELF_X+BLOCK_Y)/2} {-SELF_Z}",
            Map.of(
                "SELF", "Steve",
                "SELF_X", "10",
                "SELF_Z", "4",
                "BLOCK_Y", "65",
                "HIT_Z", "3"
            ),
            false
        );

        assertTrue(result.ok());
        assertEquals("Steve 9 67 6 37.5 -4", result.output());
    }

    @Test
    void leavesUnknownFutureContextWhenAllowed() {
        PlaceholderResolver.Result result = PlaceholderResolver.render(
            "fill {HIT_X-1} {SELF_Y}",
            Map.of("SELF_Y", "64"),
            true
        );

        assertTrue(result.ok());
        assertEquals("fill {HIT_X-1} 64", result.output());
    }

    @Test
    void reportsMalformedAndUnknownFinalPlaceholders() {
        PlaceholderResolver.Result malformed = PlaceholderResolver.render("{SELF_X+}", Map.of("SELF_X", "10"), false);
        PlaceholderResolver.Result unknown = PlaceholderResolver.render("{HIT_X-1}", Map.of(), false);

        assertFalse(malformed.ok());
        assertTrue(malformed.errors().getFirst().contains("Invalid expression"));
        assertFalse(unknown.ok());
        assertTrue(unknown.errors().getFirst().contains("Unknown variable"));
    }
}
