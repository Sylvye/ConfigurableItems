package com.bountysmp.configurableitems.model;

import java.util.EnumSet;
import java.util.Set;

public enum TriggerType {
    RIGHT_CLICK,
    LEFT_CLICK,
    ALL_CLICK,
    CONSUME,
    BLOCK_BREAK,
    BLOCK_PLACE,
    CLICK_ENTITY,
    CLICK_PLAYER,
    HIT_ENTITY,
    HIT_PLAYER,
    HIT_BY_ENTITY,
    HIT_BY_PLAYER,
    HIT_GLOBAL,
    KILL_ENTITY,
    KILL_PLAYER,
    LAUNCH_PROJECTILE,
    PROJECTILE_HIT_BLOCK,
    PROJECTILE_HIT_ENTITY,
    PROJECTILE_HIT_PLAYER,
    DROP_SELF,
    SELECT_SELF,
    DESELECT_SELF,
    EQUIP_SELF,
    UNEQUIP_SELF,
    ITEM_BREAK,
    DEATH;

    public static final Set<TriggerType> TARGET_TRIGGERS = EnumSet.of(
        CLICK_ENTITY,
        CLICK_PLAYER,
        HIT_ENTITY,
        HIT_PLAYER,
        HIT_BY_ENTITY,
        HIT_BY_PLAYER,
        HIT_GLOBAL,
        KILL_ENTITY,
        KILL_PLAYER,
        PROJECTILE_HIT_ENTITY,
        PROJECTILE_HIT_PLAYER
    );

    public static final Set<TriggerType> BLOCK_TRIGGERS = EnumSet.of(
        BLOCK_BREAK,
        BLOCK_PLACE,
        PROJECTILE_HIT_BLOCK
    );

    public static final Set<TriggerType> PROJECTILE_TRIGGERS = EnumSet.of(
        LAUNCH_PROJECTILE,
        PROJECTILE_HIT_BLOCK,
        PROJECTILE_HIT_ENTITY,
        PROJECTILE_HIT_PLAYER
    );
}
