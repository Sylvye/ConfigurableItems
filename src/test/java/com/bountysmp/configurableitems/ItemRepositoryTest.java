package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.model.TriggerType;
import com.bountysmp.configurableitems.storage.ItemRepository;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemRepositoryTest {
    @TempDir
    File tempDir;

    @Test
    void savesRestrictionsToYaml() throws Exception {
        Plugin plugin = fakePlugin(tempDir);
        ItemRepository repository = new ItemRepository(plugin);
        CustomItemDefinition item = new CustomItemDefinition("restricted_item");
        item.restrictions().cancelDrop = true;
        item.restrictions().cancelPlacement = true;
        item.restrictions().cancelToolInteractions = true;
        item.restrictions().cancelConsumption = true;
        item.restrictions().cancelCraft = true;
        item.restrictions().cancelEnchantAnvil = true;
        item.equip().deathProtection = true;

        repository.save(item);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(tempDir, "items/restricted_item.yml"));

        assertTrue(yaml.getBoolean("restrictions.cancel-drop"));
        assertTrue(yaml.getBoolean("restrictions.cancel-placement"));
        assertTrue(yaml.getBoolean("restrictions.cancel-tool-interactions"));
        assertTrue(yaml.getBoolean("restrictions.cancel-consumption"));
        assertTrue(yaml.getBoolean("restrictions.cancel-craft"));
        assertTrue(yaml.getBoolean("restrictions.cancel-enchant-anvil"));
        assertTrue(yaml.getBoolean("equip.death-protection"));
    }

    @Test
    void readsOldTriggerStringLists() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("triggers.RIGHT_CLICK", List.of("SEND_MESSAGE &aReady"));
        Map<TriggerType, List<CustomItemDefinition.TriggerCommandDef>> triggers = new EnumMap<>(TriggerType.class);
        Method readTriggers = ItemRepository.class.getDeclaredMethod("readTriggers", ConfigurationSection.class, Map.class);
        readTriggers.setAccessible(true);

        readTriggers.invoke(null, yaml.getConfigurationSection("triggers"), triggers);

        assertEquals("SEND_MESSAGE &aReady", triggers.get(TriggerType.RIGHT_CLICK).getFirst().command());
        assertFalse(triggers.get(TriggerType.RIGHT_CLICK).getFirst().cooldownEnabled());
    }

    @Test
    void savesTriggerCooldownsOnlyWhenEnabled() throws Exception {
        ItemRepository repository = new ItemRepository(fakePlugin(tempDir));
        CustomItemDefinition item = new CustomItemDefinition("cooldown_item");
        item.commands(TriggerType.RIGHT_CLICK).add(new CustomItemDefinition.TriggerCommandDef("SEND_MESSAGE &aReady"));
        item.commands(TriggerType.RIGHT_CLICK).add(new CustomItemDefinition.TriggerCommandDef("DASH 1.5", 100, "&cCooling down"));

        repository.save(item);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(tempDir, "items/cooldown_item.yml"));
        List<?> commands = yaml.getList("triggers.RIGHT_CLICK");

        assertEquals("SEND_MESSAGE &aReady", commands.getFirst());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> second = (java.util.Map<String, Object>) commands.get(1);
        assertEquals("DASH 1.5", second.get("command"));
        assertEquals(100, second.get("cooldown-ticks"));
        assertEquals("&cCooling down", second.get("cooldown-message"));
    }

    @Test
    void restrictionsDefaultToFalse() {
        CustomItemDefinition item = new CustomItemDefinition("default_item");

        assertFalse(item.restrictions().cancelDrop);
        assertFalse(item.restrictions().cancelPlacement);
        assertFalse(item.restrictions().cancelToolInteractions);
        assertFalse(item.restrictions().cancelConsumption);
        assertFalse(item.restrictions().cancelCraft);
        assertFalse(item.restrictions().cancelEnchantAnvil);
    }

    private static Plugin fakePlugin(File dataFolder) {
        return (Plugin) Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getDataFolder" -> dataFolder;
                case "getLogger" -> Logger.getLogger("ConfigurableItemsTest");
                case "getName" -> "ConfigurableItemsTest";
                case "isEnabled", "isNaggable" -> false;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "ConfigurableItemsTest";
                default -> null;
            }
        );
    }
}
