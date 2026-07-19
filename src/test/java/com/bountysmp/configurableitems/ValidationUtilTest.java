package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.util.ValidationUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ValidationUtilTest {
    @Test
    void validatesItemIds() {
        assertTrue(ValidationUtil.validItemId("flame_sword"));
        assertTrue(ValidationUtil.validItemId("flame-sword_1"));
        assertFalse(ValidationUtil.validItemId("Flame Sword"));
        assertFalse(ValidationUtil.validItemId("../bad"));
        assertFalse(ValidationUtil.validItemId("folder/flame_sword"));
    }

    @Test
    void resolvesPureValues() {
        assertTrue(ValidationUtil.key("minecraft:diamond_sword", null).isPresent());
        assertTrue(ValidationUtil.key("diamond_sword", null).isPresent());
    }
}
