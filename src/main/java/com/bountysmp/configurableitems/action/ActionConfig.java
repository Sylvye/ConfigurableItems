package com.bountysmp.configurableitems.action;

import org.bukkit.configuration.file.FileConfiguration;

public record ActionConfig(
    int warnRadius,
    int warnSelectedEntities,
    int warnLoopIterations,
    int warnForIterations,
    int warnVeinmineBlocks,
    int warnHitscanDistance,
    int warnDelayedQueues
) {
    public static ActionConfig from(FileConfiguration config) {
        String root = "actions.warnings.";
        return new ActionConfig(
            config.getInt(root + "warn-radius", 32),
            config.getInt(root + "warn-selected-entities", 50),
            config.getInt(root + "warn-loop-iterations", 100),
            config.getInt(root + "warn-for-iterations", 200),
            config.getInt(root + "warn-veinmine-blocks", 128),
            config.getInt(root + "warn-hitscan-distance", 128),
            config.getInt(root + "warn-delayed-queues", 500)
        );
    }
}
