package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.model.CustomItemDefinition;
import org.junit.jupiter.api.Test;

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
}
