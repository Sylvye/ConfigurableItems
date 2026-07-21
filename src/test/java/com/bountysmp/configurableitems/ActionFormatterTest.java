package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.action.ActionFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ActionFormatterTest {
    @Test
    void normalizesActionNamesAndVariables() {
        assertEquals("DAMAGE {SELF}", ActionFormatter.normalizeLine("damage {self}"));
        assertEquals("HITSCAN 24 DAMAGE {TARGET} <+> PARTICLE flame 10", ActionFormatter.normalizeLine("hitscan 24 damage {Target} <+> particle flame 10"));
        assertEquals("HITSCAN 24 target:PLAYER max-hits:all particle:FLAME points:8 DAMAGE 3", ActionFormatter.normalizeLine("hitscan 24 target:player max-hits:ALL particle:flame points:8 damage 3"));
        assertEquals("VEINMINE 64 filter:#minecraft:logs mode:replace replace:STONE use-durability:false", ActionFormatter.normalizeLine("veinmine 64 filter:#Minecraft:Logs mode:REPLACE replace:stone use-durability:FALSE"));
        assertEquals("VEINMINE 64 filter:OAK_LOG,#minecraft:leaves,minecraft:diamond_ore", ActionFormatter.normalizeLine("veinmine 64 filter:oak_log,#Minecraft:Leaves,minecraft:diamond_ore"));
        assertEquals("VEINMINE 64 filter:#minecraft:logs match:same-type", ActionFormatter.normalizeLine("veinmine 64 filter:#Minecraft:Logs match:SAME-TYPE"));
        assertEquals("PROJECTILE_TRAIL particle:FLAME count:2 points:3 interval:1 duration:100 offset:0 speed:0", ActionFormatter.normalizeLine("projectile_trail particle:flame count:2 points:3 interval:1 duration:100 offset:0 speed:0"));
        assertEquals("END_PROJECTILE_TRAIL", ActionFormatter.normalizeLine("end_projectile_trail"));
    }

    @Test
    void preservesConsoleCommandTextExceptVariables() {
        assertEquals("minecraft:give {SELF} minecraft:diamond 1", ActionFormatter.normalizeLine("minecraft:give {self} minecraft:diamond 1"));
    }

    @Test
    void normalizesForVariables() {
        assertEquals("FOR [red,blue] > COLOR", ActionFormatter.normalizeLine("for [red,blue] > color"));
        assertEquals("FOR [X=0.5;1;10]", ActionFormatter.normalizeLine("for [x = 0.5 ; 1 ; 10]"));
        assertEquals("END_FOR COLOR", ActionFormatter.normalizeLine("endfor color"));
        assertEquals(List.of("FOR [red] > COLOR", "SEND_MESSAGE {COLOR}"), ActionFormatter.normalizeLines(List.of("for [red] > color", "send_message {color}")));
    }
}
