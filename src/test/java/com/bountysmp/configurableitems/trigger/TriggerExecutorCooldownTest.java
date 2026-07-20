package com.bountysmp.configurableitems.trigger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TriggerExecutorCooldownTest {
    @Test
    void roundsRemainingCooldownUpToSeconds() {
        assertEquals(5L, TriggerExecutor.secondsRemaining(5_000L, 0L));
        assertEquals(5L, TriggerExecutor.secondsRemaining(4_001L, 0L));
        assertEquals(1L, TriggerExecutor.secondsRemaining(1L, 0L));
    }
}
