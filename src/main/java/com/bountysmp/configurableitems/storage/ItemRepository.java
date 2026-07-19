package com.bountysmp.configurableitems.storage;

import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.model.TriggerType;
import com.bountysmp.configurableitems.util.ValidationUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.Plugin;

public final class ItemRepository {
    private final Plugin plugin;
    private final File itemsDir;
    private final Map<String, CustomItemDefinition> items = new LinkedHashMap<>();

    public ItemRepository(Plugin plugin) {
        this.plugin = plugin;
        this.itemsDir = new File(plugin.getDataFolder(), "items");
    }

    public void load() {
        items.clear();
        if (!itemsDir.exists() && !itemsDir.mkdirs()) {
            plugin.getLogger().warning("Could not create items directory: " + itemsDir);
            return;
        }
        File[] files = itemsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                CustomItemDefinition item = read(file);
                items.put(item.id(), item);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Could not load " + file.getName() + ": " + ex.getMessage());
            }
        }
    }

    public Collection<CustomItemDefinition> all() {
        return items.values().stream().sorted(Comparator.comparing(CustomItemDefinition::id)).toList();
    }

    public CustomItemDefinition get(String id) {
        return items.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String id) {
        return items.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public void save(CustomItemDefinition item) throws IOException {
        if (!itemsDir.exists() && !itemsDir.mkdirs()) {
            throw new IOException("Could not create " + itemsDir);
        }
        item.id(item.id());
        YamlConfiguration yaml = new YamlConfiguration();
        write(yaml, item);
        yaml.save(new File(itemsDir, item.id() + ".yml"));
        items.put(item.id(), item.copy());
    }

    public void delete(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        items.remove(key);
        File file = new File(itemsDir, key + ".yml");
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete " + file.getName());
        }
    }

    private CustomItemDefinition read(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = yaml.getString("id", file.getName().replaceFirst("\\.yml$", ""));
        if (!ValidationUtil.validItemId(id)) {
            throw new IllegalArgumentException("Invalid item id " + id);
        }
        CustomItemDefinition item = new CustomItemDefinition(id);
        item.material(Material.matchMaterial(yaml.getString("material", "STICK")));
        if (item.material() == null || item.material().isAir()) {
            item.material(Material.STICK);
        }
        item.customName(yaml.getString("custom-name", "&b" + id));
        item.lore().addAll(yaml.getStringList("lore"));
        item.showEnchantments(yaml.getBoolean("show-enchantments", true));
        if (yaml.contains("glint-override")) {
            item.glintOverride(yaml.getBoolean("glint-override"));
        }
        item.showAttributes(yaml.getBoolean("show-attributes", true));
        readEnchantments(yaml.getMapList("enchantments"), item);
        readAttributes(yaml.getMapList("attributes"), item);
        readFood(yaml.getConfigurationSection("food"), item.food());
        readTool(yaml.getConfigurationSection("tool"), item.tool());
        readEquip(yaml.getConfigurationSection("equip"), item.equip());
        readExtras(yaml.getConfigurationSection("extras"), item.extras());
        readTriggers(yaml.getConfigurationSection("triggers"), item.triggers());
        return item;
    }

    private void write(YamlConfiguration yaml, CustomItemDefinition item) {
        yaml.set("id", item.id());
        yaml.set("material", item.material().name());
        yaml.set("custom-name", item.customName());
        yaml.set("lore", item.lore());
        yaml.set("show-enchantments", item.showEnchantments());
        yaml.set("glint-override", item.glintOverride());
        yaml.set("show-attributes", item.showAttributes());

        List<Map<String, Object>> enchants = new ArrayList<>();
        for (CustomItemDefinition.EnchantDef enchantment : item.enchantments()) {
            enchants.add(Map.of("id", enchantment.key(), "level", enchantment.level()));
        }
        yaml.set("enchantments", enchants);

        List<Map<String, Object>> attributes = new ArrayList<>();
        for (CustomItemDefinition.AttributeDef attribute : item.attributes()) {
            attributes.add(Map.of(
                "id", attribute.key(),
                "operation", attribute.operation().name(),
                "value", attribute.value(),
                "slot", attribute.slot().toString()
            ));
        }
        yaml.set("attributes", attributes);

        writeFood(yaml.createSection("food"), item.food());
        writeTool(yaml.createSection("tool"), item.tool());
        writeEquip(yaml.createSection("equip"), item.equip());
        writeExtras(yaml.createSection("extras"), item.extras());

        ConfigurationSection triggerSection = yaml.createSection("triggers");
        item.triggers().forEach((type, commands) -> {
            if (!commands.isEmpty()) {
                triggerSection.set(type.name(), commands);
            }
        });
    }

    private static void readEnchantments(List<Map<?, ?>> maps, CustomItemDefinition item) {
        for (Map<?, ?> map : maps) {
            String key = String.valueOf(map.get("id"));
            int level = number(map, "level", 1).intValue();
            item.enchantments().add(new CustomItemDefinition.EnchantDef(key, level));
        }
    }

    private static void readAttributes(List<Map<?, ?>> maps, CustomItemDefinition item) {
        for (Map<?, ?> map : maps) {
            String key = String.valueOf(map.get("id"));
            AttributeModifier.Operation op = parseOperation(String.valueOf(map.get("operation")));
            double value = number(map, "value", 0.0d).doubleValue();
            EquipmentSlotGroup slot = ValidationUtil.slotGroup(text(map, "slot", "ANY"));
            item.attributes().add(new CustomItemDefinition.AttributeDef(key, op, value, slot));
        }
    }

    private static AttributeModifier.Operation parseOperation(String raw) {
        try {
            return AttributeModifier.Operation.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return AttributeModifier.Operation.ADD_NUMBER;
        }
    }

    private static void readFood(ConfigurationSection section, CustomItemDefinition.FoodDef food) {
        if (section == null) {
            return;
        }
        food.enabled = section.getBoolean("enabled", false);
        food.nutrition = section.getInt("nutrition", 1);
        food.saturation = (float) section.getDouble("saturation", 0.2d);
        food.alwaysEat = section.getBoolean("always-eat", false);
        food.eatSeconds = (float) section.getDouble("eat-seconds", 1.6d);
        food.animation = section.getString("animation", "EAT");
        readEffects(section.getMapList("consumed-effects"), food.consumedEffects);
    }

    private static void writeFood(ConfigurationSection section, CustomItemDefinition.FoodDef food) {
        section.set("enabled", food.enabled);
        section.set("nutrition", food.nutrition);
        section.set("saturation", food.saturation);
        section.set("always-eat", food.alwaysEat);
        section.set("eat-seconds", food.eatSeconds);
        section.set("animation", food.animation);
        section.set("consumed-effects", writeEffects(food.consumedEffects));
    }

    private static void readTool(ConfigurationSection section, CustomItemDefinition.ToolDef tool) {
        if (section == null) {
            return;
        }
        tool.enabled = section.getBoolean("enabled", false);
        tool.defaultSpeed = (float) section.getDouble("default-speed", 1.0d);
    }

    private static void writeTool(ConfigurationSection section, CustomItemDefinition.ToolDef tool) {
        section.set("enabled", tool.enabled);
        section.set("default-speed", tool.defaultSpeed);
    }

    private static void readEquip(ConfigurationSection section, CustomItemDefinition.EquipDef equip) {
        if (section == null) {
            return;
        }
        equip.enabled = section.getBoolean("enabled", false);
        equip.slot = ValidationUtil.equipmentSlot(section.getString("slot", "HEAD"));
        equip.glider = section.getBoolean("glider", false);
        equip.equipSound = section.getString("equip-sound", equip.equipSound);
        equip.dispensable = section.getBoolean("dispensable", true);
        equip.swappable = section.getBoolean("swappable", true);
        equip.damageOnHurt = section.getBoolean("damage-on-hurt", true);
        readEffects(section.getMapList("death-effects"), equip.deathEffects);
    }

    private static void writeEquip(ConfigurationSection section, CustomItemDefinition.EquipDef equip) {
        section.set("enabled", equip.enabled);
        section.set("slot", equip.slot.name());
        section.set("glider", equip.glider);
        section.set("equip-sound", equip.equipSound);
        section.set("dispensable", equip.dispensable);
        section.set("swappable", equip.swappable);
        section.set("damage-on-hurt", equip.damageOnHurt);
        section.set("death-effects", writeEffects(equip.deathEffects));
    }

    private static void readExtras(ConfigurationSection section, CustomItemDefinition.ExtrasDef extras) {
        if (section == null) {
            return;
        }
        extras.unbreakable = section.getBoolean("unbreakable", false);
        extras.showUnbreakable = section.getBoolean("show-unbreakable", true);
        extras.maxDurability = section.contains("max-durability") ? section.getInt("max-durability") : null;
        extras.maxStackSize = section.contains("max-stack-size") ? section.getInt("max-stack-size") : null;
        extras.fireResistant = section.getBoolean("fire-resistant", false);
        extras.explosionResistant = section.getBoolean("explosion-resistant", false);
        extras.repairItem = section.getString("repair-item");
        extras.useRemainder = section.getString("use-remainder");
        extras.useCooldownSeconds = section.contains("use-cooldown-seconds") ? (float) section.getDouble("use-cooldown-seconds") : null;
        extras.itemModel = section.getString("item-model");
    }

    private static void writeExtras(ConfigurationSection section, CustomItemDefinition.ExtrasDef extras) {
        section.set("unbreakable", extras.unbreakable);
        section.set("show-unbreakable", extras.showUnbreakable);
        section.set("max-durability", extras.maxDurability);
        section.set("max-stack-size", extras.maxStackSize);
        section.set("fire-resistant", extras.fireResistant);
        section.set("explosion-resistant", extras.explosionResistant);
        section.set("repair-item", extras.repairItem);
        section.set("use-remainder", extras.useRemainder);
        section.set("use-cooldown-seconds", extras.useCooldownSeconds);
        section.set("item-model", extras.itemModel);
    }

    private static void readEffects(List<Map<?, ?>> maps, List<CustomItemDefinition.EffectDef> target) {
        for (Map<?, ?> map : maps) {
            String key = String.valueOf(map.get("id"));
            int seconds = number(map, "seconds", 5).intValue();
            int amplifier = number(map, "amplifier", 0).intValue();
            float chance = number(map, "chance", 1.0d).floatValue();
            target.add(new CustomItemDefinition.EffectDef(key, seconds, amplifier, chance));
        }
    }

    private static Number number(Map<?, ?> map, String key, Number fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number : fallback;
    }

    private static String text(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static List<Map<String, Object>> writeEffects(List<CustomItemDefinition.EffectDef> effects) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (CustomItemDefinition.EffectDef effect : effects) {
            maps.add(Map.of(
                "id", effect.key(),
                "seconds", effect.seconds(),
                "amplifier", effect.amplifier(),
                "chance", effect.chance()
            ));
        }
        return maps;
    }

    private static void readTriggers(ConfigurationSection section, Map<TriggerType, List<String>> triggers) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                TriggerType type = TriggerType.valueOf(key.toUpperCase(Locale.ROOT));
                triggers.put(type, new ArrayList<>(section.getStringList(key)));
            } catch (IllegalArgumentException ignored) {
                // Unknown triggers are ignored for V0.
            }
        }
    }
}
