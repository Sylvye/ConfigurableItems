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
        assertEquals("HITSCAN 24 target:ENTITY particle:FLAME particle-speed:0.02 DAMAGE 3", ActionFormatter.normalizeLine("hitscan 24 target:entity particle:flame speed:0.02 damage 3"));
        assertEquals("AROUND 10 target:PLAYER SEND_MESSAGE &aReady", ActionFormatter.normalizeLine("around 10 send_message &aReady"));
        assertEquals("AROUND 10 target:MOB DAMAGE 3", ActionFormatter.normalizeLine("mob_around 10 damage 3"));
        assertEquals("NEAREST 12 target:PLAYER ACTIONBAR &cMarked", ActionFormatter.normalizeLine("nearest 12 actionbar &cMarked"));
        assertEquals("NEAREST 12 target:MOB BURN 4", ActionFormatter.normalizeLine("mob_nearest 12 burn 4"));
        assertEquals("AROUND 8 target:ENTITY DAMAGE 2", ActionFormatter.normalizeLine("around 8 target:entity damage 2"));
        assertEquals("VEINMINE 64 filter:#minecraft:logs mode:replace replace:STONE use-durability:false", ActionFormatter.normalizeLine("veinmine 64 filter:#Minecraft:Logs mode:REPLACE replace:stone use-durability:FALSE"));
        assertEquals("VEINMINE 64 filter:OAK_LOG,#minecraft:leaves,minecraft:diamond_ore", ActionFormatter.normalizeLine("veinmine 64 filter:oak_log,#Minecraft:Leaves,minecraft:diamond_ore"));
        assertEquals("VEINMINE 64 filter:#minecraft:logs match:same-type", ActionFormatter.normalizeLine("veinmine 64 filter:#Minecraft:Logs match:SAME-TYPE"));
        assertEquals("PROJECTILE_TRAIL particle:FLAME count:2 points:3 interval:1 duration:100 offset:0 speed:0", ActionFormatter.normalizeLine("projectile_trail particle:flame count:2 points:3 interval:1 duration:100 offset:0 speed:0"));
        assertEquals("HITBOX shape:CUBE size:3 at:SELF targets:PLAYER,BLOCK edge-particles:true", ActionFormatter.normalizeLine("hitbox shape:cube size:3 at:self targets:player,block edge-particles:TRUE"));
        assertEquals("TIMER duration:20 interval:5", ActionFormatter.normalizeLine("timer duration:20 interval:5"));
        assertEquals("PARTICLE FLAME 1 shape:HEXAGON size:2 rotation:0,90,0 points:12", ActionFormatter.normalizeLine("particle FLAME 1 shape:hexagon size:2 rotation:0,90,0 points:12"));
        assertEquals("DAMAGE 5 type:true target:TARGET", ActionFormatter.normalizeLine("damage 5 type:TRUE target:target"));
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
