package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.util.TextUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TextUtilTest {
    @Test
    void translatesAmpersandCodes() {
        assertEquals("\u00a7aGreen", TextUtil.plainLegacy("&aGreen"));
    }

    @Test
    void legacyTextDefaultsToNonItalic() {
        assertEquals(TextDecoration.State.FALSE, TextUtil.legacy("&aGreen").decoration(TextDecoration.ITALIC));
    }

    @Test
    void explicitItalicCodeStillWorks() {
        assertEquals(TextDecoration.State.TRUE, TextUtil.legacy("&oItalic").decoration(TextDecoration.ITALIC));
    }
}
