package com.bountysmp.configurableitems.api;

import com.bountysmp.configurableitems.gui.GuiManager;
import com.bountysmp.configurableitems.item.ItemFactory;
import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.storage.ItemRepository;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ConfigurableItemsService implements ConfigurableItemsAPI {
    private final ItemRepository repository;
    private final ItemFactory itemFactory;
    private final GuiManager guiManager;

    public ConfigurableItemsService(ItemRepository repository, ItemFactory itemFactory, GuiManager guiManager) {
        this.repository = repository;
        this.itemFactory = itemFactory;
        this.guiManager = guiManager;
    }

    @Override public Collection<CustomItemDefinition> definitions() {
        return repository.all().stream().map(CustomItemDefinition::copy).toList();
    }

    @Override public Optional<CustomItemDefinition> definition(String id) {
        CustomItemDefinition value = repository.get(id);
        return Optional.ofNullable(value == null ? null : value.copy());
    }

    @Override public void saveDefinition(CustomItemDefinition definition) throws IOException {
        repository.save(definition.copy());
    }

    @Override public void deleteDefinition(String id) {
        repository.delete(id);
    }

    @Override public ItemStack createItem(CustomItemDefinition definition, int amount) {
        return itemFactory.create(definition, amount);
    }

    @Override public Optional<String> itemId(ItemStack stack) {
        return itemFactory.itemId(stack);
    }

    @Override public void openDraftEditor(Player player, CustomItemDefinition definition, boolean lockPresentation,
                                          Consumer<CustomItemDefinition> onSave, Runnable onCancel) {
        guiManager.openDraftEditor(player, definition.copy(), lockPresentation, onSave, onCancel);
    }

    @Override public void openSectionEditor(Player player, CustomItemDefinition definition, EditorSection section,
                                             Consumer<CustomItemDefinition> onChange, Runnable onBack) {
        guiManager.openSectionEditor(player, definition, section, onChange, onBack);
    }

    @Override public void openTriggerActionEditor(Player player, CustomItemDefinition definition,
                                                   com.bountysmp.configurableitems.model.TriggerType trigger,
                                                   Consumer<CustomItemDefinition> onChange, Runnable onBack) {
        guiManager.openTriggerActionEditor(player, definition, trigger, onChange, onBack);
    }
}
