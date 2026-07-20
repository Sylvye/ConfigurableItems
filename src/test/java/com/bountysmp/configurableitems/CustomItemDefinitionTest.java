package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.model.TriggerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CustomItemDefinitionTest {
    @Test
    void copyPreservesRestrictionsAndDeathProtection() {
        CustomItemDefinition item = new CustomItemDefinition("test_item");
        item.restrictions().cancelDrop = true;
        item.restrictions().cancelCraft = true;
        item.equip().deathProtection = true;

        CustomItemDefinition copy = item.copy();

        assertTrue(copy.restrictions().cancelDrop);
        assertTrue(copy.restrictions().cancelCraft);
        assertTrue(copy.equip().deathProtection);
    }

    @Test
    void copyPreservesTriggerCooldowns() {
        CustomItemDefinition item = new CustomItemDefinition("test_item");
        item.commands(TriggerType.RIGHT_CLICK).add(new CustomItemDefinition.TriggerCommandDef("DASH 1.5", 100, "&cWait"));

        CustomItemDefinition copy = item.copy();
        CustomItemDefinition.TriggerCommandDef command = copy.commands(TriggerType.RIGHT_CLICK).getFirst();

        assertEquals("DASH 1.5", command.command());
        assertEquals(100, command.cooldownTicks());
        assertEquals("&cWait", command.cooldownMessage());
    }
}
