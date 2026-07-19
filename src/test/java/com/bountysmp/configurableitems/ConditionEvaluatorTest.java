package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.action.ConditionEvaluator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConditionEvaluatorTest {
    @Test
    void evaluatesNumericComparisons() {
        assertTrue(ConditionEvaluator.evaluate("10>=5"));
        assertTrue(ConditionEvaluator.evaluate("3<4"));
        assertFalse(ConditionEvaluator.evaluate("2>9"));
    }

    @Test
    void evaluatesStringComparisonsAndLogic() {
        assertTrue(ConditionEvaluator.evaluate("PIG=PIG&&5>2"));
        assertTrue(ConditionEvaluator.evaluate("PIG=COW||5>2"));
        assertFalse(ConditionEvaluator.evaluate("PIG=COW&&5>2"));
    }
}
