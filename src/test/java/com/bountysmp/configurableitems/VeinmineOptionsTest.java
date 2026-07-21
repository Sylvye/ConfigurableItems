package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.action.VeinmineOptions;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VeinmineOptionsTest {
    @Test
    void parsesLegacySyntaxWithNewDefaults() {
        VeinmineOptions options = VeinmineOptions.parse(List.of("VEINMINE", "64", "drop:true"));

        assertTrue(options.errors().isEmpty());
        assertEquals(64, options.limit());
        assertTrue(options.drop());
        assertEquals(VeinmineOptions.FilterKind.SAME_TYPE, options.filter().kind());
        assertEquals(VeinmineOptions.MatchMode.SAME_TYPE, options.matchMode());
        assertEquals(VeinmineOptions.Mode.DESTROY, options.mode());
        assertTrue(options.useEnchants());
        assertTrue(options.useDurability());
        assertTrue(options.effect());
        assertTrue(options.xp());
    }

    @Test
    void parsesMaterialFilterAndBooleans() {
        VeinmineOptions options = VeinmineOptions.parse(List.of(
            "VEINMINE", "32", "filter:oak_log", "drop:false", "use-enchants:false", "use-durability:false", "effect:false", "xp:false"
        ));

        assertTrue(options.errors().isEmpty());
        assertEquals(VeinmineOptions.FilterKind.MATERIAL, options.filter().kind());
        assertEquals(VeinmineOptions.MatchMode.ALL, options.matchMode());
        assertEquals(Material.OAK_LOG, options.filter().material());
        assertFalse(options.drop());
        assertFalse(options.useEnchants());
        assertFalse(options.useDurability());
        assertFalse(options.effect());
        assertFalse(options.xp());
    }

    @Test
    void parsesTagFilter() {
        VeinmineOptions options = VeinmineOptions.parse(List.of("VEINMINE", "128", "filter:#minecraft:logs"), key -> true);

        assertTrue(options.errors().isEmpty());
        assertEquals(VeinmineOptions.FilterKind.TAG, options.filter().kind());
        assertEquals("minecraft:logs", options.filter().tagKey().toString());
        assertEquals("#minecraft:logs", options.filter().raw());
    }

    @Test
    void parsesMultipleFilterEntries() {
        VeinmineOptions options = VeinmineOptions.parse(List.of(
            "VEINMINE", "128", "filter:oak_log,#minecraft:leaves,minecraft:diamond_ore"
        ), key -> true);

        assertTrue(options.errors().isEmpty());
        assertEquals(VeinmineOptions.FilterKind.MULTI, options.filter().kind());
        assertEquals(VeinmineOptions.MatchMode.ALL, options.matchMode());
        assertEquals("OAK_LOG,#minecraft:leaves,DIAMOND_ORE", options.filter().raw());
        assertEquals(3, options.filter().entries().size());
        assertEquals(Material.OAK_LOG, options.filter().entries().get(0).material());
        assertEquals("minecraft:leaves", options.filter().entries().get(1).tagKey().toString());
        assertEquals(Material.DIAMOND_ORE, options.filter().entries().get(2).material());
    }

    @Test
    void parsesReplaceMode() {
        VeinmineOptions options = VeinmineOptions.parse(List.of(
            "VEINMINE", "64", "filter:minecraft:diamond_ore", "mode:replace", "replace:stone"
        ));

        assertTrue(options.errors().isEmpty());
        assertEquals(VeinmineOptions.Mode.REPLACE, options.mode());
        assertEquals(Material.STONE, options.replacement());
    }

    @Test
    void parsesSameTypeMatchModeWithinFilter() {
        VeinmineOptions options = VeinmineOptions.parse(List.of(
            "VEINMINE", "128", "filter:#minecraft:logs,#minecraft:leaves", "match:same-type"
        ), key -> true);

        assertTrue(options.errors().isEmpty());
        assertEquals(VeinmineOptions.MatchMode.SAME_TYPE, options.matchMode());
        assertEquals(VeinmineOptions.FilterKind.MULTI, options.filter().kind());
    }

    @Test
    void reportsInvalidInputs() {
        VeinmineOptions options = VeinmineOptions.parse(List.of(
            "VEINMINE", "0", "filter:#minecraft:nope,", "match:nope", "mode:nope", "replace:not_a_block", "drop:maybe"
        ), key -> false);

        assertEquals(7, options.errors().size());
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("limit")));
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("tag")));
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("empty")));
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("match")));
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("mode")));
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("replace")));
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("drop")));
    }

    @Test
    void requiresReplacementForReplaceMode() {
        VeinmineOptions options = VeinmineOptions.parse(List.of("VEINMINE", "64", "mode:replace"));

        assertNull(options.replacement());
        assertTrue(options.errors().stream().anyMatch(error -> error.contains("replace:<material>")));
    }
}
