package com.bountysmp.configurableitems.action;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public enum TargetKind {
    PLAYER,
    MOB,
    ENTITY,
    BLOCK;

    public static Optional<TargetKind> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TargetKind.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public boolean matchesEntity(Entity entity) {
        return switch (this) {
            case PLAYER -> entity instanceof Player;
            case MOB -> entity instanceof LivingEntity && !(entity instanceof Player);
            case ENTITY -> true;
            case BLOCK -> false;
        };
    }

    public static String allowedValues(boolean includeBlock) {
        return includeBlock ? "PLAYER, MOB, ENTITY, or BLOCK" : "PLAYER, MOB, or ENTITY";
    }
}
