package com.bountysmp.configurableitems;

import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.storage.ItemRepository;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
