package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.model.TriggerType;
import com.bountysmp.configurableitems.trigger.TriggerExecutor;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TriggerExecutorTest {
    @Test
    void allowsBlockVariablesOnlyForBlockTriggers() {
        assertTrue(TriggerExecutor.invalidVariable("say {BLOCK}", TriggerType.BLOCK_BREAK).isEmpty());
        assertTrue(TriggerExecutor.invalidVariable("say {BLOCK}", TriggerType.RIGHT_CLICK_BLOCK).isEmpty());
        assertTrue(TriggerExecutor.invalidVariable("say {BLOCK}", TriggerType.LEFT_CLICK_BLOCK).isEmpty());
        assertEquals("BLOCK", TriggerExecutor.invalidVariable("say {BLOCK}", TriggerType.RIGHT_CLICK).orElseThrow());
        assertEquals("BLOCK", TriggerExecutor.invalidVariable("say {BLOCK}", TriggerType.LEFT_CLICK).orElseThrow());
    }

    @Test
    void allowsTargetVariablesForHitTriggers() {
        assertTrue(TriggerExecutor.invalidVariable("effect give {TARGET} speed", TriggerType.HIT_PLAYER).isEmpty());
        assertEquals("TARGET", TriggerExecutor.invalidVariable("effect give {TARGET} speed", TriggerType.CONSUME).orElseThrow());
    }

    @Test
    void allowsUppercaseForVariablesOnlyInsideDeclaredScope() {
        List<CustomItemDefinition.TriggerCommandDef> commands = List.of(
            new CustomItemDefinition.TriggerCommandDef("FOR [red,blue] > COLOR"),
            new CustomItemDefinition.TriggerCommandDef("SEND_MESSAGE {COLOR}"),
            new CustomItemDefinition.TriggerCommandDef("END_FOR COLOR")
        );

        assertTrue(TriggerExecutor.invalidVariable(commands, TriggerType.RIGHT_CLICK).isEmpty());
        assertEquals("COLOR", TriggerExecutor.invalidVariable("SEND_MESSAGE {COLOR}", TriggerType.RIGHT_CLICK).orElseThrow());
    }

    @Test
    void allowsUppercaseNumericForVariablesOnlyInsideDeclaredScope() {
        List<CustomItemDefinition.TriggerCommandDef> commands = List.of(
            new CustomItemDefinition.TriggerCommandDef("FOR [X=0.5;1;3]"),
            new CustomItemDefinition.TriggerCommandDef("SEND_MESSAGE {X}"),
            new CustomItemDefinition.TriggerCommandDef("END_FOR X")
        );

        assertTrue(TriggerExecutor.invalidVariable(commands, TriggerType.RIGHT_CLICK).isEmpty());
    }
}
