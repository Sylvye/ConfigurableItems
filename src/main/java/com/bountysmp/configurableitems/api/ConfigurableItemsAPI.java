package com.bountysmp.configurableitems.api;

import com.bountysmp.configurableitems.model.CustomItemDefinition;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Supported integration surface for plugins that use ConfigurableItems as their mechanics engine. */
public interface ConfigurableItemsAPI {
    Collection<CustomItemDefinition> definitions();

    Optional<CustomItemDefinition> definition(String id);

    void saveDefinition(CustomItemDefinition definition) throws IOException;

    void deleteDefinition(String id);

    ItemStack createItem(CustomItemDefinition definition, int amount);

    Optional<String> itemId(ItemStack stack);

    void openDraftEditor(Player player, CustomItemDefinition definition, boolean lockPresentation,
                         Consumer<CustomItemDefinition> onSave, Runnable onCancel);

    void openSectionEditor(Player player, CustomItemDefinition definition, EditorSection section,
                           Consumer<CustomItemDefinition> onChange, Runnable onBack);

    void openTriggerActionEditor(Player player, CustomItemDefinition definition,
                                 com.bountysmp.configurableitems.model.TriggerType trigger,
                                 Consumer<CustomItemDefinition> onChange, Runnable onBack);
}
