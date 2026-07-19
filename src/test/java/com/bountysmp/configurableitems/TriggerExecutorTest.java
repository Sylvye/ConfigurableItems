package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.model.TriggerType;
import com.bountysmp.configurableitems.trigger.TriggerExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TriggerExecutorTest {
    @Test
    void allowsBlockVariablesOnlyForBlockTriggers() {
        assertTrue(TriggerExecutor.invalidVariable("say {BLOCK}", TriggerType.BLOCK_BREAK).isEmpty());
        assertEquals("BLOCK", TriggerExecutor.invalidVariable("say {BLOCK}", TriggerType.RIGHT_CLICK).orElseThrow());
    }

    @Test
    void allowsTargetVariablesForHitTriggers() {
        assertTrue(TriggerExecutor.invalidVariable("effect give {TARGET} speed", TriggerType.HIT_PLAYER).isEmpty());
        assertEquals("TARGET", TriggerExecutor.invalidVariable("effect give {TARGET} speed", TriggerType.CONSUME).orElseThrow());
    }
}
