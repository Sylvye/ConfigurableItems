package com.bountysmp.configurableitems.trigger;

import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TriggerListenerTest {
    @Test
    void handlesAirClickEvenWhenInteractBlockResultIsDenied() {
        assertTrue(TriggerListener.shouldHandleInteract(Action.RIGHT_CLICK_AIR, false, Event.Result.DENY));
        assertTrue(TriggerListener.shouldHandleInteract(Action.LEFT_CLICK_AIR, false, Event.Result.DENY));
    }

    @Test
    void skipsDeniedBlockClick() {
        assertFalse(TriggerListener.shouldHandleInteract(Action.RIGHT_CLICK_BLOCK, true, Event.Result.DENY));
        assertFalse(TriggerListener.shouldHandleInteract(Action.LEFT_CLICK_BLOCK, true, Event.Result.DENY));
    }

    @Test
    void handlesAllowedBlockClick() {
        assertTrue(TriggerListener.shouldHandleInteract(Action.RIGHT_CLICK_BLOCK, true, Event.Result.ALLOW));
        assertTrue(TriggerListener.shouldHandleInteract(Action.LEFT_CLICK_BLOCK, true, Event.Result.ALLOW));
    }
}
