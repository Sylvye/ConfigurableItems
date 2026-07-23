package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.action.ActionConfig;
import com.bountysmp.configurableitems.action.ActionEngine;
import com.bountysmp.configurableitems.command.ConfigurableItemsCommand;
import com.bountysmp.configurableitems.gui.GuiManager;
import com.bountysmp.configurableitems.gui.InputManager;
import com.bountysmp.configurableitems.item.ItemFactory;
import com.bountysmp.configurableitems.restriction.RestrictionListener;
import com.bountysmp.configurableitems.storage.ItemRepository;
import com.bountysmp.configurableitems.trigger.TriggerExecutor;
import com.bountysmp.configurableitems.trigger.TriggerListener;
import com.bountysmp.configurableitems.trigger.ProjectileTracker;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigurableItemsPlugin extends JavaPlugin {
    private ItemRepository repository;
    private ItemFactory itemFactory;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        repository = new ItemRepository(this);
        repository.load();
        itemFactory = new ItemFactory(this);
        InputManager inputManager = new InputManager(this);
        ProjectileTracker projectileTracker = new ProjectileTracker();
        ActionEngine actionEngine = new ActionEngine(this, repository, ActionConfig.from(getConfig()), projectileTracker);
        TriggerExecutor triggerExecutor = new TriggerExecutor(this, repository, actionEngine);
        guiManager = new GuiManager(this, repository, itemFactory, inputManager);

        Bukkit.getPluginManager().registerEvents(inputManager, this);
        Bukkit.getPluginManager().registerEvents(guiManager, this);
        Bukkit.getPluginManager().registerEvents(new RestrictionListener(itemFactory, repository), this);
        Bukkit.getPluginManager().registerEvents(new TriggerListener(this, itemFactory, repository, triggerExecutor, projectileTracker), this);

        ConfigurableItemsCommand command = new ConfigurableItemsCommand(repository, itemFactory, guiManager);
        PluginCommand pluginCommand = getCommand("configurableitems");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        getLogger().info("Loaded " + repository.all().size() + " configurable items.");
    }
}
