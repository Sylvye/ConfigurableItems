package com.bountysmp.configurableitems.trigger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

public final class ProjectileTracker {
    private final Map<UUID, String> projectileItems = new HashMap<>();
    private final Map<UUID, String> suppressedLaunches = new HashMap<>();

    public void track(Projectile projectile, String itemId) {
        if (projectile != null && itemId != null && !itemId.isBlank()) {
            projectileItems.put(projectile.getUniqueId(), itemId);
        }
    }

    public String remove(Projectile projectile) {
        return projectile == null ? null : projectileItems.remove(projectile.getUniqueId());
    }

    public void suppressNextLaunch(Player player, String itemId) {
        if (player != null) {
            suppressedLaunches.put(player.getUniqueId(), itemId == null ? "" : itemId);
        }
    }

    public String consumeSuppressedLaunch(Player player) {
        return player == null ? null : suppressedLaunches.remove(player.getUniqueId());
    }
}
