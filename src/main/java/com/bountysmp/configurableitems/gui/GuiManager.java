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
import java.util.Comparator;
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
import org.bukkit.Registry;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
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
        button(menu, 10, Material.CYAN_DYE, NamedTextColor.AQUA, "Material", item.material().name(), e -> openMaterialSelector(player, item, 0, ""));
        button(menu, 11, Material.NAME_TAG, NamedTextColor.AQUA, "Custom Name", item.customName(), e -> promptName(player, item));
        button(menu, 12, Material.WRITABLE_BOOK, NamedTextColor.AQUA, "Lore", item.lore().size() + " lines", e -> promptLore(player, item));
        button(menu, 13, Material.ENCHANTED_BOOK, NamedTextColor.LIGHT_PURPLE, "Enchantments", item.enchantments().size() + " entries", e -> openEnchantments(player, item));
        button(menu, 14, Material.IRON_CHESTPLATE, NamedTextColor.LIGHT_PURPLE, "Attributes", item.attributes().size() + " entries", e -> openAttributes(player, item));
        button(menu, 15, Material.COOKED_BEEF, NamedTextColor.LIGHT_PURPLE, "Food", status(item.food().enabled), e -> openFood(player, item));
        button(menu, 16, Material.IRON_PICKAXE, NamedTextColor.LIGHT_PURPLE, "Tool", status(item.tool().enabled), e -> openTool(player, item));
        button(menu, 19, Material.ELYTRA, NamedTextColor.LIGHT_PURPLE, "Equip", status(item.equip().enabled), e -> openEquip(player, item));
        button(menu, 20, Material.CHEST, NamedTextColor.LIGHT_PURPLE, "Extras", "Durability, stack, model, cooldown", e -> openExtras(player, item));
        button(menu, 21, Material.BARRIER, NamedTextColor.YELLOW, "Restrictions", restrictionCount(item) + " enabled", e -> openRestrictions(player, item));
        button(menu, 22, Material.COMMAND_BLOCK, NamedTextColor.YELLOW, "Triggers", triggerCount(item) + " commands", e -> openTriggers(player, item));
        button(menu, 45, Material.ARROW, NamedTextColor.GRAY, "Back", "", e -> openMain(player, 0));
        button(menu, 46, Material.RED_DYE, NamedTextColor.RED, "Delete YAML", "Deletes saved YAML", e -> {
            repository.delete(item.id());
            drafts.remove(player.getUniqueId());
            openMain(player, 0);
        });
        button(menu, 49, Material.PLAYER_HEAD, NamedTextColor.GREEN, "Give To Self", "", e -> player.getInventory().addItem(itemFactory.create(item)));
        button(menu, 53, Material.LIME_DYE, NamedTextColor.GREEN, "Save", "Write YAML and update cache", e -> save(player, item));
        player.openInventory(inv);
    }

    private void openEnchantments(Player player, CustomItemDefinition item) {
        Menu menu = new Menu("CI Enchants");
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 10, Material.LIME_DYE, NamedTextColor.GREEN, "Add Enchantment", "Choose from registry", e -> openEnchantmentSelector(player, item, 0, ""));
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
        button(menu, 10, Material.LIME_DYE, NamedTextColor.GREEN, "Add Attribute", "Choose from registry", e -> openAttributeSelector(player, item, 0, ""));
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
        button(menu, 16, Material.POTION, NamedTextColor.GREEN, "Add Effect", "Choose potion effect", e -> openPotionEffectSelector(player, item, item.food().consumedEffects, () -> openFood(player, item), 0, ""));
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
        button(menu, 11, Material.ARMOR_STAND, NamedTextColor.AQUA, "Slot", item.equip().slot.name(), e -> openEquipmentSlotSelector(player, item, 0, ""));
        button(menu, 12, Material.ELYTRA, item.equip().glider ? NamedTextColor.GREEN : NamedTextColor.RED, "Glider", status(item.equip().glider), e -> {
            item.equip().glider = !item.equip().glider;
            openEquip(player, item);
        });
        button(menu, 13, Material.NOTE_BLOCK, NamedTextColor.AQUA, "Equip Sound", item.equip().equipSound, e -> openSoundSelector(player, item, 0, ""));
        button(menu, 14, Material.DISPENSER, item.equip().dispensable ? NamedTextColor.GREEN : NamedTextColor.RED, "Dispensable", status(item.equip().dispensable), e -> toggleEquip(player, item, "dispensable"));
        button(menu, 15, Material.LEVER, item.equip().swappable ? NamedTextColor.GREEN : NamedTextColor.RED, "Swappable", status(item.equip().swappable), e -> toggleEquip(player, item, "swappable"));
        button(menu, 16, Material.SHIELD, item.equip().damageOnHurt ? NamedTextColor.GREEN : NamedTextColor.RED, "Damage On Hurt", status(item.equip().damageOnHurt), e -> toggleEquip(player, item, "damage"));
        button(menu, 21, Material.TOTEM_OF_UNDYING, item.equip().deathProtection ? NamedTextColor.GREEN : NamedTextColor.RED, "Death Protection", status(item.equip().deathProtection), e -> {
            item.equip().deathProtection = !item.equip().deathProtection;
            openEquip(player, item);
        });
        button(menu, 22, Material.POTION, NamedTextColor.GREEN, "Add Death Effect", "Choose potion effect", e -> openPotionEffectSelector(player, item, item.equip().deathEffects, () -> openEquip(player, item), 0, ""));
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
        button(menu, 16, Material.IRON_INGOT, NamedTextColor.AQUA, "Repair Item", String.valueOf(item.extras().repairItem), e -> openRepairItemSelector(player, item, 0, ""));
        button(menu, 21, Material.GLASS_BOTTLE, NamedTextColor.AQUA, "Use Remainder", String.valueOf(item.extras().useRemainder), e -> openUseRemainderSelector(player, item, 0, ""));
        button(menu, 22, Material.CLOCK, NamedTextColor.AQUA, "Use Cooldown", String.valueOf(item.extras().useCooldownSeconds), e -> input(player, "Enter cooldown seconds, or clear", raw -> {
            item.extras().useCooldownSeconds = raw.equalsIgnoreCase("clear") ? null : parseFloat(raw, item.extras().useCooldownSeconds == null ? 0f : item.extras().useCooldownSeconds);
            openExtras(player, item);
        }));
        button(menu, 23, Material.ITEM_FRAME, NamedTextColor.AQUA, "Item Model", String.valueOf(item.extras().itemModel), e -> openItemModelSelector(player, item, 0, ""));
        back(menu, player, item);
        player.openInventory(inv);
    }

    private void openRestrictions(Player player, CustomItemDefinition item) {
        Menu menu = new Menu("CI Restrictions");
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 10, Material.DROPPER, restrictionColor(item.restrictions().cancelDrop), "Cancel Item Drop", status(item.restrictions().cancelDrop), e -> {
            item.restrictions().cancelDrop = !item.restrictions().cancelDrop;
            openRestrictions(player, item);
        });
        button(menu, 11, Material.GRASS_BLOCK, restrictionColor(item.restrictions().cancelPlacement), "Cancel Item Placement", status(item.restrictions().cancelPlacement), e -> {
            item.restrictions().cancelPlacement = !item.restrictions().cancelPlacement;
            openRestrictions(player, item);
        });
        button(menu, 12, Material.IRON_AXE, restrictionColor(item.restrictions().cancelToolInteractions), "Cancel Tool Interactions", status(item.restrictions().cancelToolInteractions), e -> {
            item.restrictions().cancelToolInteractions = !item.restrictions().cancelToolInteractions;
            openRestrictions(player, item);
        });
        button(menu, 13, Material.COOKED_BEEF, restrictionColor(item.restrictions().cancelConsumption), "Cancel Consumption", status(item.restrictions().cancelConsumption), e -> {
            item.restrictions().cancelConsumption = !item.restrictions().cancelConsumption;
            openRestrictions(player, item);
        });
        button(menu, 14, Material.CRAFTING_TABLE, restrictionColor(item.restrictions().cancelCraft), "Cancel Craft", status(item.restrictions().cancelCraft), e -> {
            item.restrictions().cancelCraft = !item.restrictions().cancelCraft;
            openRestrictions(player, item);
        });
        button(menu, 15, Material.ANVIL, restrictionColor(item.restrictions().cancelEnchantAnvil), "Cancel Enchant / Anvil", status(item.restrictions().cancelEnchantAnvil), e -> {
            item.restrictions().cancelEnchantAnvil = !item.restrictions().cancelEnchantAnvil;
            openRestrictions(player, item);
        });
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
        button(menu, 12, Material.COMMAND_BLOCK, NamedTextColor.YELLOW, "Add Action", "Choose a CI action template", e -> openActionTemplateSelector(player, item, type, 0, ""));
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

    private void openActionTemplateSelector(Player player, CustomItemDefinition item, TriggerType type, int page, String filter) {
        openSelector(player, item, "Select Action", actionTemplateOptions(), page, filter,
            option -> {
                for (String line : option.key().split("\\n")) {
                    if (!line.isBlank()) {
                        item.commands(type).add(line);
                    }
                }
                openTriggerCommands(player, item, type);
            },
            () -> openTriggerCommands(player, item, type),
            null,
            () -> input(player, "Enter custom CI action", raw -> {
                item.commands(type).add(raw);
                openTriggerCommands(player, item, type);
            })
        );
    }

    private void openMaterialSelector(Player player, CustomItemDefinition item, int page, String filter) {
        openSelector(player, item, "Select Material", materialOptions(), page, filter,
            option -> {
                ValidationUtil.material(option.key()).ifPresent(item::material);
                openEditor(player, item);
            },
            () -> openEditor(player, item),
            null,
            null
        );
    }

    private void openEnchantmentSelector(Player player, CustomItemDefinition item, int page, String filter) {
        openSelector(player, item, "Select Enchant", enchantmentOptions(), page, filter,
            option -> input(player, "Enter level for " + option.key(), raw -> {
                item.enchantments().add(new CustomItemDefinition.EnchantDef(option.key(), parseInt(raw, 1)));
                openEnchantments(player, item);
            }),
            () -> openEnchantments(player, item),
            null,
            null
        );
    }

    private void openAttributeSelector(Player player, CustomItemDefinition item, int page, String filter) {
        openSelector(player, item, "Select Attribute", attributeOptions(), page, filter,
            option -> openAttributeOperationSelector(player, item, option.key()),
            () -> openAttributes(player, item),
            null,
            null
        );
    }

    private void openAttributeOperationSelector(Player player, CustomItemDefinition item, String attributeKey) {
        List<SelectorOption> options = Arrays.stream(AttributeModifier.Operation.values())
            .map(operation -> new SelectorOption(operation.name(), Material.COMPARATOR, operation.name(), ""))
            .toList();
        openSelector(player, item, "Attribute Operation", options, 0, "",
            option -> input(player, "Enter value for " + attributeKey, raw -> openAttributeSlotGroupSelector(player, item, attributeKey, AttributeModifier.Operation.valueOf(option.key()), parseFloat(raw, 0.0f))),
            () -> openAttributes(player, item),
            null,
            null
        );
    }

    private void openAttributeSlotGroupSelector(Player player, CustomItemDefinition item, String attributeKey, AttributeModifier.Operation operation, double value) {
        openSelector(player, item, "Attribute Slot", slotGroupOptions(), 0, "",
            option -> {
                item.attributes().add(new CustomItemDefinition.AttributeDef(attributeKey, operation, value, ValidationUtil.slotGroup(option.key())));
                openAttributes(player, item);
            },
            () -> openAttributes(player, item),
            null,
            null
        );
    }

    private void openPotionEffectSelector(Player player, CustomItemDefinition item, List<CustomItemDefinition.EffectDef> effects, Runnable reopen, int page, String filter) {
        openSelector(player, item, "Select Effect", potionOptions(), page, filter,
            option -> input(player, "Enter seconds amplifier chance for " + option.key() + ", e.g. 10 1 1.0", raw -> {
                String[] p = raw.split("\\s+");
                effects.add(new CustomItemDefinition.EffectDef(
                    option.key(),
                    p.length > 0 ? parseInt(p[0], 5) : 5,
                    p.length > 1 ? parseInt(p[1], 0) : 0,
                    p.length > 2 ? parseFloat(p[2], 1.0f) : 1.0f
                ));
                reopen.run();
            }),
            reopen,
            null,
            null
        );
    }

    private void openSoundSelector(Player player, CustomItemDefinition item, int page, String filter) {
        openSelector(player, item, "Select Sound", soundOptions(), page, filter,
            option -> {
                item.equip().equipSound = option.key();
                openEquip(player, item);
            },
            () -> openEquip(player, item),
            null,
            () -> input(player, "Enter custom sound id", raw -> {
                if (ValidationUtil.sound(raw).isPresent()) {
                    item.equip().equipSound = ValidationUtil.sound(raw).get().key();
                } else {
                    error(player, "Unknown sound.");
                }
                openEquip(player, item);
            })
        );
    }

    private void openEquipmentSlotSelector(Player player, CustomItemDefinition item, int page, String filter) {
        openSelector(player, item, "Select Equip Slot", equipmentSlotOptions(), page, filter,
            option -> {
                item.equip().slot = ValidationUtil.equipmentSlot(option.key());
                openEquip(player, item);
            },
            () -> openEquip(player, item),
            null,
            null
        );
    }

    private void openRepairItemSelector(Player player, CustomItemDefinition item, int page, String filter) {
        openSelector(player, item, "Select Repair Item", materialOptions(), page, filter,
            option -> {
                item.extras().repairItem = option.key();
                openExtras(player, item);
            },
            () -> openExtras(player, item),
            () -> {
                item.extras().repairItem = null;
                openExtras(player, item);
            },
            null
        );
    }

    private void openUseRemainderSelector(Player player, CustomItemDefinition item, int page, String filter) {
        openSelector(player, item, "Select Remainder", materialOptions(), page, filter,
            option -> {
                item.extras().useRemainder = option.key();
                openExtras(player, item);
            },
            () -> openExtras(player, item),
            () -> {
                item.extras().useRemainder = null;
                openExtras(player, item);
            },
            null
        );
    }

    private void openItemModelSelector(Player player, CustomItemDefinition item, int page, String filter) {
        openSelector(player, item, "Select Item Model", materialModelOptions(), page, filter,
            option -> {
                item.extras().itemModel = option.key();
                openExtras(player, item);
            },
            () -> openExtras(player, item),
            () -> {
                item.extras().itemModel = null;
                openExtras(player, item);
            },
            () -> input(player, "Enter custom item model id, or clear", raw -> {
                if (raw.equalsIgnoreCase("clear")) {
                    item.extras().itemModel = null;
                } else if (ValidationUtil.key(raw, plugin).isPresent()) {
                    item.extras().itemModel = ValidationUtil.normalizeKey(raw);
                } else {
                    error(player, "Invalid namespaced key.");
                }
                openExtras(player, item);
            })
        );
    }

    private void openSelector(Player player, CustomItemDefinition item, String title, List<SelectorOption> options, int page, String filter, Consumer<SelectorOption> select, Runnable back, Runnable clear, Runnable custom) {
        Menu menu = new Menu(title);
        Inventory inv = menu.inventory();
        frame(inv);
        String normalizedFilter = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        List<SelectorOption> filtered = options.stream()
            .filter(option -> normalizedFilter.isBlank() || option.key().toLowerCase(Locale.ROOT).contains(normalizedFilter) || option.name().toLowerCase(Locale.ROOT).contains(normalizedFilter))
            .toList();
        int pageSize = 28;
        int safePage = Math.max(0, Math.min(page, Math.max(0, (filtered.size() - 1) / pageSize)));
        int start = safePage * pageSize;
        int slot = 10;
        for (int i = start; i < filtered.size() && i < start + pageSize; i++) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            SelectorOption option = filtered.get(i);
            button(menu, slot++, option.icon(), NamedTextColor.AQUA, option.name(), option.key() + (option.lore().isBlank() ? "" : " | " + option.lore()), e -> select.accept(option));
        }
        if (safePage > 0) {
            button(menu, 45, Material.ARROW, NamedTextColor.GRAY, "Previous", "", e -> openSelector(player, item, title, options, safePage - 1, normalizedFilter, select, back, clear, custom));
        }
        button(menu, 46, Material.COMPASS, NamedTextColor.AQUA, "Search", normalizedFilter.isBlank() ? "No filter" : normalizedFilter, e -> input(player, "Enter search filter, or clear", raw -> openSelector(player, item, title, options, 0, raw.equalsIgnoreCase("clear") ? "" : raw, select, back, clear, custom)));
        if (clear != null) {
            button(menu, 47, Material.BARRIER, NamedTextColor.RED, "Clear", "", e -> clear.run());
        }
        if (custom != null) {
            button(menu, 48, Material.NAME_TAG, NamedTextColor.YELLOW, "Custom Key", "", e -> custom.run());
        }
        button(menu, 49, Material.ARROW, NamedTextColor.GRAY, "Back", "", e -> back.run());
        if (start + pageSize < filtered.size()) {
            button(menu, 53, Material.ARROW, NamedTextColor.GRAY, "Next", "", e -> openSelector(player, item, title, options, safePage + 1, normalizedFilter, select, back, clear, custom));
        }
        player.openInventory(inv);
    }

    private List<SelectorOption> materialOptions() {
        return Arrays.stream(Material.values())
            .filter(material -> material.isItem() && !material.isAir())
            .sorted(Comparator.comparing(material -> material.getKey().toString()))
            .map(material -> new SelectorOption(material.getKey().toString(), material, material.name(), ""))
            .toList();
    }

    private List<SelectorOption> actionTemplateOptions() {
        return List.of(
            new SelectorOption("DELAY_TICK 20", Material.CLOCK, "DELAY_TICK", "Flow"),
            new SelectorOption("IF 1=1 SEND_MESSAGE &aCondition passed", Material.COMPARATOR, "IF", "Flow"),
            new SelectorOption("LOOP_START 3 20\nSEND_MESSAGE &eLoop tick\nLOOP_END", Material.REPEATER, "LOOP_START / LOOP_END", "Flow"),
            new SelectorOption("RANDOM_RUN selectionCount:1\nSEND_MESSAGE &aOption one\nSEND_MESSAGE &bOption two\nRANDOM_END", Material.DROPPER, "RANDOM_RUN / RANDOM_END", "Flow"),
            new SelectorOption("FOR [one,two,three] > for1\nSEND_MESSAGE &e{for1}\nEND_FOR for1", Material.HOPPER, "FOR / END_FOR", "Flow"),
            new SelectorOption("AROUND 10 SEND_MESSAGE &eNearby player", Material.PLAYER_HEAD, "AROUND", "Selectors"),
            new SelectorOption("MOB_AROUND 10 DAMAGE 2", Material.ZOMBIE_HEAD, "MOB_AROUND", "Selectors"),
            new SelectorOption("NEAREST 10 SEND_MESSAGE &eNearest player", Material.COMPASS, "NEAREST", "Selectors"),
            new SelectorOption("MOB_NEAREST 10 BURN 3", Material.ROTTEN_FLESH, "MOB_NEAREST", "Selectors"),
            new SelectorOption("HITSCAN 32 DAMAGE 5", Material.SPYGLASS, "HITSCAN", "Selectors"),
            new SelectorOption("DAMAGE 5", Material.IRON_SWORD, "DAMAGE", "Entity"),
            new SelectorOption("HEAL 5", Material.GOLDEN_APPLE, "HEAL", "Entity"),
            new SelectorOption("SET_HEALTH 10", Material.RED_DYE, "SET_HEALTH", "Entity"),
            new SelectorOption("KILL", Material.WITHER_SKELETON_SKULL, "KILL", "Entity"),
            new SelectorOption("BURN 5", Material.FLINT_AND_STEEL, "BURN", "Entity"),
            new SelectorOption("INVULNERABILITY 40", Material.SHIELD, "INVULNERABILITY", "Entity"),
            new SelectorOption("TELEPORT {X} {Y} {Z}", Material.ENDER_PEARL, "TELEPORT", "Entity"),
            new SelectorOption("VELOCITY 0 1 0", Material.SLIME_BALL, "VELOCITY", "Entity"),
            new SelectorOption("DASH 1.5", Material.FEATHER, "DASH", "Entity"),
            new SelectorOption("SEND_MESSAGE &aHello", Material.PAPER, "SEND_MESSAGE", "Message"),
            new SelectorOption("ACTIONBAR &eReady", Material.OAK_SIGN, "ACTIONBAR", "Message"),
            new SelectorOption("PARTICLE FLAME 20 0.2 0.01", Material.BLAZE_POWDER, "PARTICLE", "Visual"),
            new SelectorOption("SET_BLOCK STONE", Material.STONE, "SET_BLOCK", "Block"),
            new SelectorOption("SET_TEMP_BLOCK GLOWSTONE 100", Material.GLOWSTONE, "SET_TEMP_BLOCK", "Block"),
            new SelectorOption("BREAK_BLOCK drop:true", Material.IRON_PICKAXE, "BREAK_BLOCK", "Block"),
            new SelectorOption("DROPITEM DIAMOND 1", Material.DIAMOND, "DROPITEM", "Item"),
            new SelectorOption("VEINMINE 64 drop:true", Material.DIAMOND_PICKAXE, "VEINMINE", "Block")
        );
    }

    private List<SelectorOption> materialModelOptions() {
        return Arrays.stream(Material.values())
            .filter(material -> material.isItem() && !material.isAir())
            .sorted(Comparator.comparing(material -> material.getKey().toString()))
            .map(material -> new SelectorOption(material.getKey().toString(), material, material.name(), "Vanilla model key"))
            .toList();
    }

    private List<SelectorOption> enchantmentOptions() {
        return Registry.ENCHANTMENT.stream()
            .sorted(Comparator.comparing(enchantment -> enchantment.getKey().toString()))
            .map(enchantment -> new SelectorOption(enchantment.getKey().toString(), Material.ENCHANTED_BOOK, enchantment.getKey().getKey(), ""))
            .toList();
    }

    private List<SelectorOption> attributeOptions() {
        return Registry.ATTRIBUTE.stream()
            .sorted(Comparator.comparing(attribute -> attribute.getKey().toString()))
            .map(attribute -> new SelectorOption(attribute.getKey().toString(), Material.IRON_SWORD, attribute.getKey().getKey(), ""))
            .toList();
    }

    private List<SelectorOption> potionOptions() {
        return Registry.MOB_EFFECT.stream()
            .sorted(Comparator.comparing(effect -> effect.getKey().toString()))
            .map(effect -> new SelectorOption(effect.getKey().toString(), Material.POTION, effect.getKey().getKey(), ""))
            .toList();
    }

    private List<SelectorOption> soundOptions() {
        return Registry.SOUNDS.stream()
            .sorted(Comparator.comparing(sound -> sound.getKey().toString()))
            .map(sound -> new SelectorOption(sound.getKey().toString(), Material.NOTE_BLOCK, sound.getKey().getKey(), ""))
            .toList();
    }

    private List<SelectorOption> equipmentSlotOptions() {
        return Arrays.stream(EquipmentSlot.values())
            .map(slot -> new SelectorOption(slot.name(), slotIcon(slot), slot.name(), ""))
            .toList();
    }

    private List<SelectorOption> slotGroupOptions() {
        return List.of(
            new SelectorOption("ANY", Material.COMPASS, "ANY", ""),
            new SelectorOption("MAINHAND", Material.IRON_SWORD, "MAINHAND", ""),
            new SelectorOption("OFFHAND", Material.SHIELD, "OFFHAND", ""),
            new SelectorOption("HAND", Material.STICK, "HAND", ""),
            new SelectorOption("HEAD", Material.IRON_HELMET, "HEAD", ""),
            new SelectorOption("CHEST", Material.IRON_CHESTPLATE, "CHEST", ""),
            new SelectorOption("LEGS", Material.IRON_LEGGINGS, "LEGS", ""),
            new SelectorOption("FEET", Material.IRON_BOOTS, "FEET", ""),
            new SelectorOption("ARMOR", Material.ARMOR_STAND, "ARMOR", ""),
            new SelectorOption("BODY", Material.ELYTRA, "BODY", ""),
            new SelectorOption("SADDLE", Material.SADDLE, "SADDLE", "")
        );
    }

    private Material slotIcon(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Material.IRON_HELMET;
            case CHEST -> Material.IRON_CHESTPLATE;
            case LEGS -> Material.IRON_LEGGINGS;
            case FEET -> Material.IRON_BOOTS;
            case HAND -> Material.IRON_SWORD;
            case OFF_HAND -> Material.SHIELD;
            case BODY -> Material.ELYTRA;
            case SADDLE -> Material.SADDLE;
        };
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
                error(player, "Invalid id. Use lowercase letters, numbers, _, or -.");
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

    private int restrictionCount(CustomItemDefinition item) {
        CustomItemDefinition.RestrictionsDef restrictions = item.restrictions();
        int count = 0;
        if (restrictions.cancelDrop) count++;
        if (restrictions.cancelPlacement) count++;
        if (restrictions.cancelToolInteractions) count++;
        if (restrictions.cancelConsumption) count++;
        if (restrictions.cancelCraft) count++;
        if (restrictions.cancelEnchantAnvil) count++;
        return count;
    }

    private static String status(boolean enabled) {
        return enabled ? "Enabled" : "Disabled";
    }

    private static String shown(boolean shown) {
        return shown ? "Shown" : "Hidden";
    }

    private static NamedTextColor restrictionColor(boolean enabled) {
        return enabled ? NamedTextColor.GREEN : NamedTextColor.RED;
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

    private record SelectorOption(String key, Material icon, String name, String lore) {
    }
}
