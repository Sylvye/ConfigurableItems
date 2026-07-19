package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.util.TextUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TextUtilTest {
    @Test
    void translatesAmpersandCodes() {
        assertEquals("\u00a7aGreen", TextUtil.plainLegacy("&aGreen"));
    }
}
