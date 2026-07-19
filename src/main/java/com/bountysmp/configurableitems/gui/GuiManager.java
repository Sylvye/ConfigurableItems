package com.bountysmp.configurableitems.gui;

import com.bountysmp.configurableitems.item.ItemFactory;
import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.model.TriggerType;
import com.bountysmp.configurableitems.storage.ItemRepository;
import com.bountysmp.configurableitems.trigger.TriggerExecutor;
import com.bountysmp.configurableitems.util.TextUtil;
import com.bountysmp.configurableitems.util.ValidationUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

public final class GuiManager implements Listener {
    private static final int SIZE = 54;

    private final Plugin plugin;
    private final ItemRepository repository;
    private final ItemFactory itemFactory;
    private final InputManager inputManager;
    private final Map<UUID, CustomItemDefinition> drafts = new HashMap<>();

    public GuiManager(Plugin plugin, ItemRepository repository, ItemFactory itemFactory, InputManager inputManager) {
        this.plugin = plugin;
        this.repository = repository;
        this.itemFactory = itemFactory;
        this.inputManager = inputManager;
    }

    public void openMain(Player player, int page) {
        Menu menu = new Menu("ConfigurableItems");
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 4, Material.NETHER_STAR, NamedTextColor.AQUA, "ConfigurableItems", "Admin item editor");
        button(menu, 49, Material.LIME_DYE, NamedTextColor.GREEN, "Create Item", "Add a new YAML-backed item", e -> promptCreate(player));

        List<CustomItemDefinition> items = new ArrayList<>(repository.all());
        int start = Math.max(0, page * 28);
        int slot = 10;
        for (int i = start; i < items.size() && slot < 44; i++) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            CustomItemDefinition item = items.get(i);
            ItemStack preview = itemFactory.create(item);
            TextUtil.name(preview, NamedTextColor.AQUA, item.id());
            int targetSlot = slot++;
            menu.action(targetSlot, e -> openEditor(player, item.copy()));
            inv.setItem(targetSlot, preview);
        }
        if (page > 0) {
            button(menu, 45, Material.ARROW, NamedTextColor.GRAY, "Previous", "", e -> openMain(player, page - 1));
        }
        if (start + 28 < items.size()) {
            button(menu, 53, Material.ARROW, NamedTextColor.GRAY, "Next", "", e -> openMain(player, page + 1));
        }
        player.openInventory(inv);
    }

    public void openEditor(Player player, CustomItemDefinition item) {
        drafts.put(player.getUniqueId(), item);
        Menu menu = new Menu("CI: " + item.id());
        Inventory inv = menu.inventory();
        frame(inv);
        inv.setItem(4, itemFactory.create(item));
        button(menu, 10, Material.CYAN_DYE, NamedTextColor.AQUA, "Material", item.material().name(), e -> promptMaterial(player, item));
        button(menu, 11, Material.NAME_TAG, NamedTextColor.AQUA, "Custom Name", item.customName(), e -> promptName(player, item));
        button(menu, 12, Material.WRITABLE_BOOK, NamedTextColor.AQUA, "Lore", item.lore().size() + " lines", e -> promptLore(player, item));
        button(menu, 13, Material.ENCHANTED_BOOK, NamedTextColor.LIGHT_PURPLE, "Enchantments", item.enchantments().size() + " entries", e -> openEnchantments(player, item));
        button(menu, 14, Material.IRON_CHESTPLATE, NamedTextColor.LIGHT_PURPLE, "Attributes", item.attributes().size() + " entries", e -> openAttributes(player, item));
        button(menu, 15, Material.COOKED_BEEF, NamedTextColor.LIGHT_PURPLE, "Food", status(item.food().enabled), e -> openFood(player, item));
        button(menu, 16, Material.IRON_PICKAXE, NamedTextColor.LIGHT_PURPLE, "Tool", status(item.tool().enabled), e -> openTool(player, item));
        button(menu, 19, Material.ELYTRA, NamedTextColor.LIGHT_PURPLE, "Equip", status(item.equip().enabled), e -> openEquip(player, item));
        button(menu, 20, Material.CHEST, NamedTextColor.LIGHT_PURPLE, "Extras", "Durability, stack, model, cooldown", e -> openExtras(player, item));
        button(menu, 21, Material.COMMAND_BLOCK, NamedTextColor.YELLOW, "Triggers", triggerCount(item) + " commands", e -> openTriggers(player, item));
        button(menu, 23, Material.PLAYER_HEAD, NamedTextColor.GREEN, "Give To Self", "", e -> player.getInventory().addItem(itemFactory.create(item)));
        button(menu, 24, Material.LIME_DYE, NamedTextColor.GREEN, "Save", "Write YAML and update cache", e -> save(player, item));
        button(menu, 25, Material.ARROW, NamedTextColor.GRAY, "Back", "", e -> openMain(player, 0));
        button(menu, 40, Material.RED_DYE, NamedTextColor.RED, "Delete", "Deletes saved YAML", e -> {
            repository.delete(item.id());
            drafts.remove(player.getUniqueId());
            openMain(player, 0);
        });
        player.openInventory(inv);
    }

    private void openEnchantments(Player player, CustomItemDefinition item) {
        Menu menu = new Menu("CI Enchants");
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 10, Material.LIME_DYE, NamedTextColor.GREEN, "Add Enchantment", "<id> <level>", e -> input(player, "Enter enchantment and level, e.g. sharpness 5", raw -> {
            String[] parts = raw.split("\\s+");
            if (parts.length < 2 || ValidationUtil.enchantment(parts[0]).isEmpty()) {
                error(player, "Invalid enchantment.");
            } else {
                item.enchantments().add(new CustomItemDefinition.EnchantDef(ValidationUtil.enchantment(parts[0]).get().key(), parseInt(parts[1], 1)));
            }
            openEnchantments(player, item);
        }));
        button(menu, 11, Material.BOOK, item.showEnchantments() ? NamedTextColor.GREEN : NamedTextColor.RED, "Tooltip", shown(item.showEnchantments()), e -> {
            item.showEnchantments(!item.showEnchantments());
            openEnchantments(player, item);
        });
        button(menu, 12, Material.GLOW_INK_SAC, NamedTextColor.AQUA, "Glint Override", String.valueOf(item.glintOverride()), e -> {
            item.glintOverride(item.glintOverride() == null ? Boolean.TRUE : item.glintOverride() ? Boolean.FALSE : null);
            openEnchantments(player, item);
        });
        int slot = 19;
        for (CustomItemDefinition.EnchantDef enchant : item.enchantments()) {
            int index = item.enchantments().indexOf(enchant);
            button(menu, slot++, Material.ENCHANTED_BOOK, NamedTextColor.RED, enchant.key(), "Level " + enchant.level(), e -> {
                item.enchantments().remove(index);
                openEnchantments(player, item);
            });
        }
        back(menu, player, item);
        player.openInventory(inv);
    }

    private void openAttributes(Player player, CustomItemDefinition item) {
        Menu menu = new Menu("CI Attributes");
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 10, Material.LIME_DYE, NamedTextColor.GREEN, "Add Attribute", "<id> <operation> <value> <slot>", e -> input(player, "Example: attack_damage ADD_NUMBER 4 HAND", raw -> {
            String[] p = raw.split("\\s+");
            if (p.length < 4 || ValidationUtil.attribute(p[0]).isEmpty()) {
                error(player, "Invalid attribute.");
            } else {
                try {
                    AttributeModifier.Operation op = AttributeModifier.Operation.valueOf(p[1].toUpperCase(Locale.ROOT));
                    item.attributes().add(new CustomItemDefinition.AttributeDef(ValidationUtil.attribute(p[0]).get().key(), op, Double.parseDouble(p[2]), ValidationUtil.slotGroup(p[3])));
                } catch (Exception ex) {
                    error(player, "Invalid operation or value.");
                }
            }
            openAttributes(player, item);
        }));
        button(menu, 11, Material.BOOK, item.showAttributes() ? NamedTextColor.GREEN : NamedTextColor.RED, "Tooltip", shown(item.showAttributes()), e -> {
            item.showAttributes(!item.showAttributes());
            openAttributes(player, item);
        });
        int slot = 19;
        for (CustomItemDefinition.AttributeDef attribute : item.attributes()) {
            int index = item.attributes().indexOf(attribute);
            button(menu, slot++, Material.IRON_SWORD, NamedTextColor.RED, attribute.key(), attribute.operation() + " " + attribute.value() + " " + attribute.slot(), e -> {
                item.attributes().remove(index);
                openAttributes(player, item);
            });
        }
        back(menu, player, item);
        player.openInventory(inv);
    }

    private void openFood(Player player, CustomItemDefinition item) {
        Menu menu = new Menu("CI Food");
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 10, Material.COOKED_BEEF, item.food().enabled ? NamedTextColor.GREEN : NamedTextColor.RED, "Enabled", status(item.food().enabled), e -> toggleFood(player, item));
        button(menu, 11, Material.APPLE, NamedTextColor.AQUA, "Nutrition", String.valueOf(item.food().nutrition), e -> input(player, "Enter nutrition integer", raw -> {
            item.food().nutrition = parseInt(raw, item.food().nutrition);
            openFood(player, item);
        }));
        button(menu, 12, Material.HONEY_BOTTLE, NamedTextColor.AQUA, "Saturation", String.valueOf(item.food().saturation), e -> input(player, "Enter saturation number", raw -> {
            item.food().saturation = parseFloat(raw, item.food().saturation);
            openFood(player, item);
        }));
        button(menu, 13, Material.GOLDEN_APPLE, item.food().alwaysEat ? NamedTextColor.GREEN : NamedTextColor.RED, "Always Eat", status(item.food().alwaysEat), e -> {
            item.food().alwaysEat = !item.food().alwaysEat;
            openFood(player, item);
        });
        button(menu, 14, Material.CLOCK, NamedTextColor.AQUA, "Eat Seconds", String.valueOf(item.food().eatSeconds), e -> input(player, "Enter consume seconds", raw -> {
            item.food().eatSeconds = parseFloat(raw, item.food().eatSeconds);
            openFood(player, item);
        }));
        button(menu, 15, Material.BOWL, NamedTextColor.AQUA, "Animation", item.food().animation, e -> {
            item.food().animation = next(item.food().animation, "EAT", "DRINK", "BLOCK", "BOW", "SPEAR", "CROSSBOW", "SPYGLASS", "HORN", "BRUSH");
            openFood(player, item);
        });
        button(menu, 16, Material.POTION, NamedTextColor.GREEN, "Add Effect", "<effect> <seconds> <amplifier> [chance]", e -> promptEffect(player, item, item.food().consumedEffects, () -> openFood(player, item)));
        listEffects(menu, item.food().consumedEffects, () -> openFood(player, item));
        back(menu, player, item);
        player.openInventory(inv);
    }

    private void openTool(Player player, CustomItemDefinition item) {
        Menu menu = new Menu("CI Tool");
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 10, Material.IRON_PICKAXE, item.tool().enabled ? NamedTextColor.GREEN : NamedTextColor.RED, "Enabled", status(item.tool().enabled), e -> {
            item.tool().enabled = !item.tool().enabled;
            openTool(player, item);
        });
        button(menu, 11, Material.GOLDEN_PICKAXE, NamedTextColor.AQUA, "Default Speed", String.valueOf(item.tool().defaultSpeed), e -> input(player, "Enter default mining speed", raw -> {
            item.tool().defaultSpeed = parseFloat(raw, item.tool().defaultSpeed);
            openTool(player, item);
        }));
        back(menu, player, item);
        player.openInventory(inv);
    }

    private void openEquip(Player player, CustomItemDefinition item) {
        Menu menu = new Menu("CI Equip");
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 10, Material.IRON_CHESTPLATE, item.equip().enabled ? NamedTextColor.GREEN : NamedTextColor.RED, "Enabled", status(item.equip().enabled), e -> {
            item.equip().enabled = !item.equip().enabled;
            openEquip(player, item);
        });
        button(menu, 11, Material.ARMOR_STAND, NamedTextColor.AQUA, "Slot", item.equip().slot.name(), e -> {
            item.equip().slot = nextSlot(item.equip().slot);
            openEquip(player, item);
        });
        button(menu, 12, Material.ELYTRA, item.equip().glider ? NamedTextColor.GREEN : NamedTextColor.RED, "Glider", status(item.equip().glider), e -> {
            item.equip().glider = !item.equip().glider;
            openEquip(player, item);
        });
        button(menu, 13, Material.NOTE_BLOCK, NamedTextColor.AQUA, "Equip Sound", item.equip().equipSound, e -> input(player, "Enter sound id, e.g. minecraft:item.armor.equip_generic", raw -> {
            if (ValidationUtil.sound(raw).isPresent()) {
                item.equip().equipSound = ValidationUtil.sound(raw).get().key();
            } else {
                error(player, "Unknown sound.");
            }
            openEquip(player, item);
        }));
        button(menu, 14, Material.DISPENSER, item.equip().dispensable ? NamedTextColor.GREEN : NamedTextColor.RED, "Dispensable", status(item.equip().dispensable), e -> toggleEquip(player, item, "dispensable"));
        button(menu, 15, Material.LEVER, item.equip().swappable ? NamedTextColor.GREEN : NamedTextColor.RED, "Swappable", status(item.equip().swappable), e -> toggleEquip(player, item, "swappable"));
        button(menu, 16, Material.SHIELD, item.equip().damageOnHurt ? NamedTextColor.GREEN : NamedTextColor.RED, "Damage On Hurt", status(item.equip().damageOnHurt), e -> toggleEquip(player, item, "damage"));
        button(menu, 22, Material.TOTEM_OF_UNDYING, NamedTextColor.GREEN, "Add Death Effect", "<effect> <seconds> <amplifier> [chance]", e -> promptEffect(player, item, item.equip().deathEffects, () -> openEquip(player, item)));
        listEffects(menu, item.equip().deathEffects, () -> openEquip(player, item));
        back(menu, player, item);
        player.openInventory(inv);
    }

    private void openExtras(Player player, CustomItemDefinition item) {
        Menu menu = new Menu("CI Extras");
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 10, Material.ANVIL, item.extras().unbreakable ? NamedTextColor.GREEN : NamedTextColor.RED, "Unbreakable", status(item.extras().unbreakable), e -> {
            item.extras().unbreakable = !item.extras().unbreakable;
            openExtras(player, item);
        });
        button(menu, 11, Material.BOOK, item.extras().showUnbreakable ? NamedTextColor.GREEN : NamedTextColor.RED, "Unbreakable Tooltip", shown(item.extras().showUnbreakable), e -> {
            item.extras().showUnbreakable = !item.extras().showUnbreakable;
            openExtras(player, item);
        });
        numberButton(menu, player, item, 12, Material.DIAMOND_PICKAXE, "Max Durability", item.extras().maxDurability, raw -> item.extras().maxDurability = raw);
        numberButton(menu, player, item, 13, Material.BUNDLE, "Max Stack Size", item.extras().maxStackSize, raw -> item.extras().maxStackSize = raw);
        button(menu, 14, Material.BLAZE_POWDER, item.extras().fireResistant ? NamedTextColor.GREEN : NamedTextColor.RED, "Fire Resistant", status(item.extras().fireResistant), e -> {
            item.extras().fireResistant = !item.extras().fireResistant;
            if (item.extras().fireResistant) {
                item.extras().explosionResistant = false;
            }
            openExtras(player, item);
        });
        button(menu, 15, Material.TNT, item.extras().explosionResistant ? NamedTextColor.GREEN : NamedTextColor.RED, "Explosion Resistant", status(item.extras().explosionResistant), e -> {
            item.extras().explosionResistant = !item.extras().explosionResistant;
            if (item.extras().explosionResistant) {
                item.extras().fireResistant = false;
            }
            openExtras(player, item);
        });
        materialText(menu, player, item, 16, Material.IRON_INGOT, "Repair Item", item.extras().repairItem, raw -> item.extras().repairItem = raw);
        materialText(menu, player, item, 21, Material.GLASS_BOTTLE, "Use Remainder", item.extras().useRemainder, raw -> item.extras().useRemainder = raw);
        button(menu, 22, Material.CLOCK, NamedTextColor.AQUA, "Use Cooldown", String.valueOf(item.extras().useCooldownSeconds), e -> input(player, "Enter cooldown seconds, or clear", raw -> {
            item.extras().useCooldownSeconds = raw.equalsIgnoreCase("clear") ? null : parseFloat(raw, item.extras().useCooldownSeconds == null ? 0f : item.extras().useCooldownSeconds);
            openExtras(player, item);
        }));
        button(menu, 23, Material.ITEM_FRAME, NamedTextColor.AQUA, "Item Model", String.valueOf(item.extras().itemModel), e -> input(player, "Enter item model id, or clear", raw -> {
            if (raw.equalsIgnoreCase("clear")) {
                item.extras().itemModel = null;
            } else if (ValidationUtil.key(raw, plugin).isPresent()) {
                item.extras().itemModel = ValidationUtil.normalizeKey(raw);
            } else {
                error(player, "Invalid namespaced key.");
            }
            openExtras(player, item);
        }));
        back(menu, player, item);
        player.openInventory(inv);
    }

    private void openTriggers(Player player, CustomItemDefinition item) {
        Menu menu = new Menu("CI Triggers");
        Inventory inv = menu.inventory();
        frame(inv);
        int slot = 10;
        for (TriggerType type : TriggerType.values()) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            int count = item.commands(type).size();
            button(menu, slot++, Material.COMMAND_BLOCK, count == 0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN, type.name(), count + " commands", e -> openTriggerCommands(player, item, type));
        }
        back(menu, player, item);
        player.openInventory(inv);
    }

    private void openTriggerCommands(Player player, CustomItemDefinition item, TriggerType type) {
        Menu menu = new Menu("CI " + type.name());
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 10, Material.LIME_DYE, NamedTextColor.GREEN, "Add Command", "Console command without slash", e -> input(player, "Enter console command. Variables: " + TriggerExecutor.allowedVariables(type), raw -> {
            String command = raw.startsWith("/") ? raw.substring(1) : raw;
            TriggerExecutor.invalidVariable(command, type).ifPresentOrElse(
                invalid -> error(player, "Invalid variable for this trigger: {" + invalid + "}"),
                () -> item.commands(type).add(command)
            );
            openTriggerCommands(player, item, type);
        }));
        button(menu, 11, Material.PAPER, NamedTextColor.AQUA, "Variables", String.join(", ", TriggerExecutor.allowedVariables(type)));
        int slot = 19;
        List<String> commands = item.commands(type);
        for (int i = 0; i < commands.size() && slot < 44; i++) {
            int index = i;
            button(menu, slot++, Material.REDSTONE, NamedTextColor.RED, commands.get(i), "Click to remove", e -> {
                commands.remove(index);
                openTriggerCommands(player, item, type);
            });
        }
        button(menu, 49, Material.ARROW, NamedTextColor.GRAY, "Back", "", e -> openTriggers(player, item));
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player && event.getRawSlot() >= 0 && event.getRawSlot() < event.getInventory().getSize()) {
            Consumer<InventoryClickEvent> action = menu.actions.get(event.getRawSlot());
            if (action != null) {
                action.accept(event);
            }
        }
    }

    private void promptCreate(Player player) {
        input(player, "Enter new item id, e.g. flame_sword", raw -> {
            String id = raw.toLowerCase(Locale.ROOT);
            if (!ValidationUtil.validItemId(id)) {
                error(player, "Invalid id. Use lowercase letters, numbers, _, -, or /.");
                openMain(player, 0);
                return;
            }
            if (repository.exists(id)) {
                error(player, "That item already exists.");
                openMain(player, 0);
                return;
            }
            openEditor(player, new CustomItemDefinition(id));
        });
    }

    private void promptMaterial(Player player, CustomItemDefinition item) {
        input(player, "Enter material id, e.g. diamond_sword", raw -> {
            ValidationUtil.material(raw).ifPresentOrElse(item::material, () -> error(player, "Invalid material."));
            openEditor(player, item);
        });
    }

    private void promptName(Player player, CustomItemDefinition item) {
        input(player, "Enter custom name with & color codes", raw -> {
            item.customName(raw);
            openEditor(player, item);
        });
    }

    private void promptLore(Player player, CustomItemDefinition item) {
        input(player, "Enter lore lines separated with |, or clear", raw -> {
            item.lore().clear();
            if (!raw.equalsIgnoreCase("clear")) {
                item.lore().addAll(Arrays.stream(raw.split("\\|")).map(String::trim).filter(s -> !s.isBlank()).toList());
            }
            openEditor(player, item);
        });
    }

    private void save(Player player, CustomItemDefinition item) {
        for (Map.Entry<TriggerType, List<String>> entry : item.triggers().entrySet()) {
            for (String command : entry.getValue()) {
                if (TriggerExecutor.invalidVariable(command, entry.getKey()).isPresent()) {
                    error(player, "Invalid trigger variable in " + entry.getKey());
                    return;
                }
            }
        }
        try {
            repository.save(item);
            player.sendMessage(Component.text("Saved " + item.id(), NamedTextColor.GREEN));
            openEditor(player, item);
        } catch (IOException ex) {
            player.sendMessage(Component.text("Save failed: " + ex.getMessage(), NamedTextColor.RED));
        }
    }

    private void promptEffect(Player player, CustomItemDefinition item, List<CustomItemDefinition.EffectDef> effects, Runnable reopen) {
        input(player, "Example: speed 10 1 1.0", raw -> {
            String[] p = raw.split("\\s+");
            if (p.length < 3 || ValidationUtil.potion(p[0]).isEmpty()) {
                error(player, "Invalid effect.");
            } else {
                effects.add(new CustomItemDefinition.EffectDef(ValidationUtil.potion(p[0]).get().key(), parseInt(p[1], 5), parseInt(p[2], 0), p.length > 3 ? parseFloat(p[3], 1.0f) : 1.0f));
            }
            reopen.run();
        });
    }

    private void listEffects(Menu menu, List<CustomItemDefinition.EffectDef> effects, Runnable reopen) {
        int slot = 28;
        for (int i = 0; i < effects.size() && slot < 35; i++) {
            int index = i;
            CustomItemDefinition.EffectDef effect = effects.get(i);
            button(menu, slot++, Material.POTION, NamedTextColor.RED, effect.key(), effect.seconds() + "s amp " + effect.amplifier(), e -> {
                effects.remove(index);
                reopen.run();
            });
        }
    }

    private void input(Player player, String prompt, Consumer<String> action) {
        inputManager.prompt(player, prompt, action);
    }

    private void back(Menu menu, Player player, CustomItemDefinition item) {
        button(menu, 49, Material.ARROW, NamedTextColor.GRAY, "Back", "", e -> openEditor(player, item));
    }

    private void button(Menu menu, int slot, Material material, NamedTextColor color, String name, String lore) {
        button(menu, slot, material, color, name, lore, e -> {});
    }

    private void button(Menu menu, int slot, Material material, NamedTextColor color, String name, String lore, Consumer<InventoryClickEvent> action) {
        menu.inventory().setItem(slot, TextUtil.button(material, color, name, lore == null ? "" : lore));
        menu.action(slot, action);
    }

    private void numberButton(Menu menu, Player player, CustomItemDefinition item, int slot, Material material, String name, Integer value, Consumer<Integer> setter) {
        button(menu, slot, material, NamedTextColor.AQUA, name, String.valueOf(value), e -> input(player, "Enter " + name + " integer, or clear", raw -> {
            setter.accept(raw.equalsIgnoreCase("clear") ? null : parseInt(raw, value == null ? 0 : value));
            openExtras(player, item);
        }));
    }

    private void materialText(Menu menu, Player player, CustomItemDefinition item, int slot, Material material, String name, String value, Consumer<String> setter) {
        button(menu, slot, material, NamedTextColor.AQUA, name, String.valueOf(value), e -> input(player, "Enter material id for " + name + ", or clear", raw -> {
            if (raw.equalsIgnoreCase("clear")) {
                setter.accept(null);
            } else if (ValidationUtil.material(raw).isPresent()) {
                setter.accept(ValidationUtil.material(raw).get().name());
            } else {
                error(player, "Invalid material.");
            }
            openExtras(player, item);
        }));
    }

    private static void frame(Inventory inv) {
        ItemStack glass = TextUtil.button(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            if (i < 9 || i >= inv.getSize() - 9 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, glass);
            }
        }
    }

    private void error(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    private int triggerCount(CustomItemDefinition item) {
        return item.triggers().values().stream().mapToInt(List::size).sum();
    }

    private static String status(boolean enabled) {
        return enabled ? "Enabled" : "Disabled";
    }

    private static String shown(boolean shown) {
        return shown ? "Shown" : "Hidden";
    }

    private void toggleFood(Player player, CustomItemDefinition item) {
        item.food().enabled = !item.food().enabled;
        openFood(player, item);
    }

    private void toggleEquip(Player player, CustomItemDefinition item, String field) {
        switch (field) {
            case "dispensable" -> item.equip().dispensable = !item.equip().dispensable;
            case "swappable" -> item.equip().swappable = !item.equip().swappable;
            case "damage" -> item.equip().damageOnHurt = !item.equip().damageOnHurt;
            default -> {}
        }
        openEquip(player, item);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String next(String current, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private static EquipmentSlot nextSlot(EquipmentSlot slot) {
        EquipmentSlot[] values = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.BODY, EquipmentSlot.SADDLE};
        for (int i = 0; i < values.length; i++) {
            if (values[i] == slot) {
                return values[(i + 1) % values.length];
            }
        }
        return EquipmentSlot.HEAD;
    }

    private final class Menu implements InventoryHolder {
        private final Inventory inventory;
        private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

        private Menu(String title) {
            this.inventory = Bukkit.createInventory(this, SIZE, title);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        Inventory inventory() {
            return inventory;
        }

        void action(int slot, Consumer<InventoryClickEvent> action) {
            actions.put(slot, action);
        }
    }
}
