package com.bountysmp.configurableitems.gui;

import com.bountysmp.configurableitems.action.ActionFormatter;
import com.bountysmp.configurableitems.action.ActionParser;
import com.bountysmp.configurableitems.action.ActionValidator;
import com.bountysmp.configurableitems.action.HitscanOptions;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        }, () -> openFood(player, item)));
        button(menu, 12, Material.HONEY_BOTTLE, NamedTextColor.AQUA, "Saturation", String.valueOf(item.food().saturation), e -> input(player, "Enter saturation number", raw -> {
            item.food().saturation = parseFloat(raw, item.food().saturation);
            openFood(player, item);
        }, () -> openFood(player, item)));
        button(menu, 13, Material.GOLDEN_APPLE, item.food().alwaysEat ? NamedTextColor.GREEN : NamedTextColor.RED, "Always Eat", status(item.food().alwaysEat), e -> {
            item.food().alwaysEat = !item.food().alwaysEat;
            openFood(player, item);
        });
        button(menu, 14, Material.CLOCK, NamedTextColor.AQUA, "Eat Seconds", String.valueOf(item.food().eatSeconds), e -> input(player, "Enter consume seconds", raw -> {
            item.food().eatSeconds = parseFloat(raw, item.food().eatSeconds);
            openFood(player, item);
        }, () -> openFood(player, item)));
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
        }, () -> openTool(player, item)));
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
        }, () -> openExtras(player, item)));
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
            String command = ActionFormatter.normalizeLine(raw.startsWith("/") ? raw.substring(1) : raw);
            item.commands(type).add(new CustomItemDefinition.TriggerCommandDef(command));
            openTriggerCommands(player, item, type);
        }, () -> openTriggerCommands(player, item, type)));
        button(menu, 11, Material.PAPER, NamedTextColor.AQUA, "Variables", String.join(", ", TriggerExecutor.allowedVariables(type)));
        button(menu, 12, Material.COMMAND_BLOCK, NamedTextColor.YELLOW, "Add Action", "Choose a CI action", e -> openActionSelector(player, item, type, -1, 0, ""));
        int slot = 19;
        List<CustomItemDefinition.TriggerCommandDef> commands = item.commands(type);
        for (int i = 0; i < commands.size() && slot < 44; i++) {
            int index = i;
            CustomItemDefinition.TriggerCommandDef command = commands.get(i);
            button(menu, slot++, Material.REDSTONE, command.cooldownEnabled() ? NamedTextColor.GOLD : NamedTextColor.RED, command.command(), cooldownLore(command), e -> {
                if (e.isRightClick()) {
                    promptCooldown(player, item, type, index);
                    return;
                }
                if (e.isShiftClick()) {
                    removeCommandRange(commands, index);
                    openTriggerCommands(player, item, type);
                    return;
                }
                openExistingActionEditor(player, item, type, index);
            });
        }
        button(menu, 49, Material.ARROW, NamedTextColor.GRAY, "Back", "", e -> openTriggers(player, item));
        player.openInventory(inv);
    }

    private void openExistingActionEditor(Player player, CustomItemDefinition item, TriggerType type, int index) {
        List<CustomItemDefinition.TriggerCommandDef> commands = item.commands(type);
        if (index < 0 || index >= commands.size()) {
            openTriggerCommands(player, item, type);
            return;
        }
        String command = commands.get(index).command();
        String actionName = firstToken(command);
        if (!ActionFormatter.isKnownActionName(actionName)) {
            input(player, "Edit console command", raw -> {
                String normalized = ActionFormatter.normalizeLine(raw.startsWith("/") ? raw.substring(1) : raw);
                commands.set(index, commands.get(index).withCommand(normalized));
                openTriggerCommands(player, item, type);
            }, () -> openTriggerCommands(player, item, type));
            return;
        }
        ActionSpec spec = actionSpec(actionName);
        if (spec == null) {
            input(player, "Edit action line", raw -> {
                commands.set(index, commands.get(index).withCommand(ActionFormatter.normalizeLine(raw)));
                openTriggerCommands(player, item, type);
            }, () -> openTriggerCommands(player, item, type));
            return;
        }
        ActionDraft draft = draftFromExisting(spec, commands, index);
        openActionEditor(player, item, type, index, draft, () -> openTriggerCommands(player, item, type));
    }

    private void removeCommandRange(List<CustomItemDefinition.TriggerCommandDef> commands, int index) {
        int end = blockEnd(commands, index);
        for (int i = end; i >= index; i--) {
            commands.remove(i);
        }
    }

    private void openActionSelector(Player player, CustomItemDefinition item, TriggerType type, int editIndex, int page, String filter) {
        openSelector(player, item, "Select Action", actionCatalogOptions(), page, filter,
            option -> {
                ActionSpec spec = actionSpec(option.key());
                if (spec == null) {
                    openTriggerCommands(player, item, type);
                    return;
                }
                openActionEditor(player, item, type, editIndex, ActionDraft.create(spec), () -> openActionSelector(player, item, type, editIndex, page, filter));
            },
            () -> openTriggerCommands(player, item, type),
            null,
            () -> input(player, "Enter custom CI action", raw -> {
                String normalized = ActionFormatter.normalizeLine(raw);
                if (editIndex >= 0) {
                    List<CustomItemDefinition.TriggerCommandDef> commands = item.commands(type);
                    commands.set(editIndex, commands.get(editIndex).withCommand(normalized));
                } else {
                    item.commands(type).add(new CustomItemDefinition.TriggerCommandDef(normalized));
                }
                openTriggerCommands(player, item, type);
            }, () -> openActionSelector(player, item, type, editIndex, page, filter))
        );
    }

    private void openActionEditor(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft draft, Runnable cancel) {
        Menu menu = new Menu("Action " + draft.spec.name());
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 4, draft.spec.icon(), NamedTextColor.AQUA, draft.spec.name(), preview(draft));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int paramIndex = 0;
        for (ActionParam param : draft.spec.params()) {
            if (paramIndex >= slots.length) {
                break;
            }
            int slot = slots[paramIndex++];
            button(menu, slot, param.icon(), NamedTextColor.AQUA, param.label(), draft.value(param.key()), e -> editActionParam(player, item, type, editIndex, draft, param, cancel));
        }
        if (draft.spec.block()) {
            button(menu, 31, Material.WRITABLE_BOOK, NamedTextColor.YELLOW, "Body Lines", draft.body().size() + " lines", e -> openActionBodyEditor(player, item, type, editIndex, draft, cancel));
        } else if (draft.spec.hasBody()) {
            button(menu, 31, Material.WRITABLE_BOOK, NamedTextColor.YELLOW, "Nested Actions", draft.body().isEmpty() ? "Empty" : String.join(" <+> ", draft.body()), e -> openActionBodyEditor(player, item, type, editIndex, draft, cancel));
        }
        button(menu, 45, Material.BARRIER, NamedTextColor.RED, "Cancel", "", e -> cancel.run());
        button(menu, 49, Material.ARROW, NamedTextColor.GRAY, "Back", "", e -> openActionSelector(player, item, type, editIndex, 0, ""));
        button(menu, 53, Material.LIME_DYE, NamedTextColor.GREEN, editIndex >= 0 ? "Save Action" : "Add Action", preview(draft), e -> saveActionDraft(player, item, type, editIndex, draft, cancel));
        player.openInventory(inv);
    }

    private void editActionParam(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft draft, ActionParam param, Runnable cancel) {
        if (param.kind() == ParamKind.BOOLEAN) {
            draft.put(param.key(), String.valueOf(!Boolean.parseBoolean(draft.value(param.key()))));
            openActionEditor(player, item, type, editIndex, draft, cancel);
            return;
        }
        if (param.kind() == ParamKind.TARGET_MODE) {
            draft.put(param.key(), next(draft.value(param.key()), "COORDS", "SELF", "TARGET"));
            openActionEditor(player, item, type, editIndex, draft, cancel);
            return;
        }
        if (param.kind() == ParamKind.HITSCAN_TARGET) {
            draft.put(param.key(), next(draft.value(param.key()), "ANY", "PLAYER", "MOB"));
            openActionEditor(player, item, type, editIndex, draft, cancel);
            return;
        }
        input(player, param.prompt(), raw -> {
            String value = param.kind() == ParamKind.VARIABLE ? raw.toUpperCase(Locale.ROOT) : ActionFormatter.normalizeVariables(raw.trim());
            draft.put(param.key(), value);
            openActionEditor(player, item, type, editIndex, draft, cancel);
        }, () -> openActionEditor(player, item, type, editIndex, draft, cancel));
    }

    private void openActionBodyEditor(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft draft, Runnable cancel) {
        openActionBodyEditor(player, item, type, editIndex, draft, cancel, () -> openActionEditor(player, item, type, editIndex, draft, cancel));
    }

    private void openActionBodyEditor(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft draft, Runnable cancel, Runnable backToAction) {
        Menu menu = new Menu("Action Body");
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 10, Material.LIME_DYE, NamedTextColor.GREEN, "Add Nested Action", "Choose a CI action", e -> openNestedActionSelector(player, item, type, editIndex, draft, cancel, backToAction, 0, ""));
        button(menu, 11, Material.NAME_TAG, NamedTextColor.YELLOW, "Add Raw Line", "", e -> input(player, "Enter nested action or command", raw -> {
            draft.body().add(ActionFormatter.normalizeLine(raw));
            openActionBodyEditor(player, item, type, editIndex, draft, cancel, backToAction);
        }, () -> openActionBodyEditor(player, item, type, editIndex, draft, cancel, backToAction)));
        int slot = 19;
        for (int i = 0; i < draft.body().size() && slot < 44; i++) {
            int bodyIndex = i;
            button(menu, slot++, Material.REDSTONE, NamedTextColor.AQUA, draft.body().get(i), "Left edit | Shift remove", e -> {
                if (e.isShiftClick()) {
                    draft.body().remove(bodyIndex);
                    openActionBodyEditor(player, item, type, editIndex, draft, cancel, backToAction);
                    return;
                }
                openNestedExistingActionEditor(player, item, type, editIndex, draft, bodyIndex, cancel, backToAction);
            });
        }
        button(menu, 49, Material.ARROW, NamedTextColor.GRAY, "Back", "", e -> backToAction.run());
        player.openInventory(inv);
    }

    private void openNestedActionSelector(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft parent, Runnable cancel, Runnable parentBackToAction, int page, String filter) {
        openSelector(player, item, "Nested Action", actionCatalogOptions(false), page, filter,
            option -> {
                ActionSpec spec = actionSpec(option.key());
                if (spec != null) {
                    int bodyIndex = parent.body().size();
                    parent.body().add(ActionFormatter.normalizeLine(ActionDraft.create(spec).format().getFirst()));
                    openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, ActionDraft.create(spec), cancel, parentBackToAction);
                    return;
                }
                openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction);
            },
            () -> openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction),
            null,
            () -> input(player, "Enter nested action or command", raw -> {
                parent.body().add(ActionFormatter.normalizeLine(raw));
                openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction);
            }, () -> openNestedActionSelector(player, item, type, editIndex, parent, cancel, parentBackToAction, page, filter))
        );
    }

    private void openNestedExistingActionEditor(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft parent, int bodyIndex, Runnable cancel, Runnable parentBackToAction) {
        if (bodyIndex < 0 || bodyIndex >= parent.body().size()) {
            openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction);
            return;
        }
        String line = parent.body().get(bodyIndex);
        ActionSpec spec = actionSpec(firstToken(line));
        if (spec == null || spec.block()) {
            input(player, "Edit nested command", raw -> {
                parent.body().set(bodyIndex, ActionFormatter.normalizeLine(raw));
                openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction);
            }, () -> openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction));
            return;
        }
        openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, draftFromLine(spec, line), cancel, parentBackToAction);
    }

    private void openNestedActionEditor(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft parent, int bodyIndex, ActionDraft nested, Runnable cancel, Runnable parentBackToAction) {
        Menu menu = new Menu("Nested " + nested.spec.name());
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 4, nested.spec.icon(), NamedTextColor.AQUA, nested.spec.name(), preview(nested));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int paramIndex = 0;
        for (ActionParam param : nested.spec.params()) {
            if (paramIndex >= slots.length) {
                break;
            }
            int slot = slots[paramIndex++];
            button(menu, slot, param.icon(), NamedTextColor.AQUA, param.label(), nested.value(param.key()), e -> editNestedActionParam(player, item, type, editIndex, parent, bodyIndex, nested, param, cancel, parentBackToAction));
        }
        if (nested.spec.hasBody()) {
            Runnable backToNested = () -> openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction);
            button(menu, 31, Material.WRITABLE_BOOK, NamedTextColor.YELLOW, "Nested Actions", nested.body().isEmpty() ? "Empty" : String.join(" <+> ", nested.body()), e -> openActionBodyEditor(player, item, type, editIndex, nested, backToNested, backToNested));
        }
        button(menu, 45, Material.BARRIER, NamedTextColor.RED, "Cancel", "", e -> openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction));
        button(menu, 49, Material.ARROW, NamedTextColor.GRAY, "Back", "", e -> openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction));
        button(menu, 53, Material.LIME_DYE, NamedTextColor.GREEN, "Save Nested", preview(nested), e -> saveNestedActionDraft(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction));
        player.openInventory(inv);
    }

    private void editNestedActionParam(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft parent, int bodyIndex, ActionDraft nested, ActionParam param, Runnable cancel, Runnable parentBackToAction) {
        if (param.kind() == ParamKind.BOOLEAN) {
            nested.put(param.key(), String.valueOf(!Boolean.parseBoolean(nested.value(param.key()))));
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction);
            return;
        }
        if (param.kind() == ParamKind.TARGET_MODE) {
            nested.put(param.key(), next(nested.value(param.key()), "COORDS", "SELF", "TARGET"));
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction);
            return;
        }
        if (param.kind() == ParamKind.HITSCAN_TARGET) {
            nested.put(param.key(), next(nested.value(param.key()), "ANY", "PLAYER", "MOB"));
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction);
            return;
        }
        input(player, param.prompt(), raw -> {
            String value = param.kind() == ParamKind.VARIABLE ? raw.toUpperCase(Locale.ROOT) : ActionFormatter.normalizeVariables(raw.trim());
            nested.put(param.key(), value);
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction);
        }, () -> openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction));
    }

    private void saveNestedActionDraft(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft parent, int bodyIndex, ActionDraft nested, Runnable cancel, Runnable parentBackToAction) {
        List<String> lines = ActionFormatter.normalizeLines(nested.format());
        if (lines.isEmpty()) {
            error(player, "Action is empty.");
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction);
            return;
        }
        parent.body().set(bodyIndex, lines.getFirst());
        openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction);
    }

    private void saveActionDraft(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft draft, Runnable cancel) {
        List<String> lines = ActionFormatter.normalizeLines(draft.format());
        if (lines.isEmpty()) {
            error(player, "Action is empty.");
            openActionEditor(player, item, type, editIndex, draft, cancel);
            return;
        }
        List<CustomItemDefinition.TriggerCommandDef> commands = item.commands(type);
        if (editIndex >= 0 && editIndex < commands.size()) {
            int end = blockEnd(commands, editIndex);
            CustomItemDefinition.TriggerCommandDef first = commands.get(editIndex).withCommand(lines.getFirst());
            for (int i = end; i >= editIndex; i--) {
                commands.remove(i);
            }
            commands.add(editIndex, first);
            for (int i = 1; i < lines.size(); i++) {
                commands.add(editIndex + i, new CustomItemDefinition.TriggerCommandDef(lines.get(i)));
            }
        } else {
            for (String line : lines) {
                commands.add(new CustomItemDefinition.TriggerCommandDef(line));
            }
        }
        openTriggerCommands(player, item, type);
    }

    private ActionDraft draftFromExisting(ActionSpec spec, List<CustomItemDefinition.TriggerCommandDef> commands, int index) {
        int end = blockEnd(commands, index);
        List<String> lines = commands.subList(index, end + 1).stream().map(CustomItemDefinition.TriggerCommandDef::command).toList();
        ActionDraft draft = ActionDraft.create(spec);
        String line = lines.getFirst();
        List<String> tokens = tokens(line);
        switch (spec.name()) {
            case "DELAY_TICK" -> draft.put("ticks", token(tokens, 1, draft.value("ticks")));
            case "IF" -> {
                String rest = restAfter(line, 1);
                String[] pieces = rest.split("\\s+", 2);
                draft.put("condition", pieces.length > 0 && !pieces[0].isBlank() ? pieces[0] : draft.value("condition"));
                draft.body().clear();
                if (pieces.length > 1) {
                    draft.body().addAll(ActionParser.splitInline(pieces[1]));
                }
            }
            case "LOOP_START" -> {
                draft.put("times", token(tokens, 1, draft.value("times")));
                draft.put("delay", token(tokens, 2, draft.value("delay")));
                replaceBody(draft, lines, 1, lines.size() - 1);
            }
            case "RANDOM_RUN" -> {
                for (String token : tokens) {
                    if (token.toLowerCase(Locale.ROOT).startsWith("selectioncount:")) {
                        draft.put("selectionCount", token.substring(token.indexOf(':') + 1));
                    }
                }
                replaceBody(draft, lines, 1, lines.size() - 1);
            }
            case "FOR" -> {
                Matcher matcher = Pattern.compile("(?i)^FOR\\s+\\[([^]]*)]\\s*>\\s*([A-Za-z0-9_]+)").matcher(line);
                if (matcher.find()) {
                    draft.put("values", matcher.group(1).trim());
                    draft.put("variable", matcher.group(2).toUpperCase(Locale.ROOT));
                }
                replaceBody(draft, lines, 1, lines.size() - 1);
            }
            case "AROUND", "MOB_AROUND", "NEAREST", "MOB_NEAREST" -> {
                draft.put("radius", token(tokens, 1, draft.value("radius")));
                draft.body().clear();
                draft.body().addAll(ActionParser.splitInline(restAfter(line, 2)));
            }
            case "HITSCAN" -> {
                HitscanOptions options = HitscanOptions.parse(tokens);
                draft.put("distance", String.valueOf(options.distance()));
                draft.put("target", options.targetMode().name());
                draft.put("maxHits", options.allHits() ? "all" : String.valueOf(options.maxHits()));
                draft.put("particle", options.particle());
                draft.put("points", String.valueOf(options.points()));
                draft.put("offset", String.valueOf(options.offset()));
                draft.put("speed", String.valueOf(options.speed()));
                draft.body().clear();
                draft.body().addAll(ActionParser.splitInline(options.body()));
            }
            case "DAMAGE", "SET_HEALTH" -> draft.put("amount", tokenOrKey(tokens, "amount", 1, draft.value("amount")));
            case "HEAL" -> draft.put("amount", tokenOrKey(tokens, "amount", 1, draft.value("amount")));
            case "BURN" -> draft.put("seconds", token(tokens, 1, draft.value("seconds")));
            case "INVULNERABILITY" -> draft.put("ticks", token(tokens, 1, draft.value("ticks")));
            case "TELEPORT" -> parseTeleport(draft, tokens);
            case "VELOCITY" -> {
                draft.put("x", token(tokens, 1, draft.value("x")));
                draft.put("y", token(tokens, 2, draft.value("y")));
                draft.put("z", token(tokens, 3, draft.value("z")));
            }
            case "DASH" -> draft.put("strength", tokenOrKey(tokens, "strength", 1, draft.value("strength")));
            case "SEND_MESSAGE", "ACTIONBAR" -> draft.put("text", restAfter(line, 1));
            case "PARTICLE" -> {
                draft.put("particle", token(tokens, 1, draft.value("particle")).toUpperCase(Locale.ROOT));
                draft.put("count", token(tokens, 2, draft.value("count")));
                draft.put("offset", token(tokens, 3, draft.value("offset")));
                draft.put("speed", token(tokens, 4, draft.value("speed")));
            }
            case "PARTICLE_LINE" -> {
                draft.put("particle", token(tokens, 1, draft.value("particle")).toUpperCase(Locale.ROOT));
                draft.put("distance", token(tokens, 2, draft.value("distance")));
                draft.put("points", token(tokens, 3, draft.value("points")));
                draft.put("offset", token(tokens, 4, draft.value("offset")));
                draft.put("speed", token(tokens, 5, draft.value("speed")));
            }
            case "SET_BLOCK" -> draft.put("material", token(tokens, 1, draft.value("material")).toUpperCase(Locale.ROOT));
            case "SET_TEMP_BLOCK" -> {
                draft.put("material", token(tokens, 1, draft.value("material")).toUpperCase(Locale.ROOT));
                draft.put("ticks", token(tokens, 2, draft.value("ticks")));
            }
            case "BREAK_BLOCK" -> draft.put("drop", keyArg(tokens, "drop", draft.value("drop")));
            case "DROPITEM" -> {
                draft.put("material", token(tokens, 1, draft.value("material")).toUpperCase(Locale.ROOT));
                draft.put("amount", token(tokens, 2, draft.value("amount")));
            }
            case "VEINMINE" -> {
                draft.put("limit", token(tokens, 1, draft.value("limit")));
                draft.put("drop", keyArg(tokens, "drop", draft.value("drop")));
            }
            default -> {}
        }
        return draft;
    }

    private ActionDraft draftFromLine(ActionSpec spec, String line) {
        return draftFromExisting(spec, List.of(new CustomItemDefinition.TriggerCommandDef(line)), 0);
    }

    private void replaceBody(ActionDraft draft, List<String> lines, int startInclusive, int endExclusive) {
        draft.body().clear();
        for (int i = startInclusive; i < endExclusive; i++) {
            String line = lines.get(i).trim();
            if (!line.isBlank()) {
                draft.body().add(ActionFormatter.normalizeLine(line));
            }
        }
    }

    private void parseTeleport(ActionDraft draft, List<String> tokens) {
        String target = keyArg(tokens, "target", null);
        if (target != null) {
            draft.put("mode", target.toUpperCase(Locale.ROOT));
            return;
        }
        draft.put("mode", "COORDS");
        draft.put("x", token(tokens, 1, draft.value("x")));
        draft.put("y", token(tokens, 2, draft.value("y")));
        draft.put("z", token(tokens, 3, draft.value("z")));
        draft.put("world", token(tokens, 4, draft.value("world")));
    }

    private String preview(ActionDraft draft) {
        String value = String.join(" | ", draft.format());
        return value.length() > 140 ? value.substring(0, 137) + "..." : value;
    }

    private int blockEnd(List<CustomItemDefinition.TriggerCommandDef> commands, int index) {
        if (index < 0 || index >= commands.size()) {
            return index;
        }
        String first = firstToken(commands.get(index).command()).toUpperCase(Locale.ROOT);
        if (!first.equals("LOOP_START") && !first.equals("RANDOM_RUN") && !first.equals("FOR")) {
            return index;
        }
        int depth = 0;
        for (int i = index; i < commands.size(); i++) {
            String token = firstToken(commands.get(i).command()).toUpperCase(Locale.ROOT);
            if (token.equals("LOOP_START") || token.equals("RANDOM_RUN") || token.equals("FOR")) {
                depth++;
            } else if (token.equals("LOOP_END") || token.equals("RANDOM_END") || token.equals("END_FOR") || token.equals("ENDFOR")) {
                depth--;
                if (depth <= 0) {
                    return i;
                }
            }
        }
        return index;
    }

    private static String firstToken(String line) {
        List<String> tokens = tokens(line);
        return tokens.isEmpty() ? "" : tokens.getFirst();
    }

    private static List<String> tokens(String line) {
        return Arrays.stream((line == null ? "" : line.trim()).split("\\s+")).filter(value -> !value.isBlank()).toList();
    }

    private static String token(List<String> tokens, int index, String fallback) {
        return tokens.size() > index ? tokens.get(index) : fallback;
    }

    private static String tokenOrKey(List<String> tokens, String key, int index, String fallback) {
        String keyed = keyArg(tokens, key, null);
        return keyed == null ? token(tokens, index, fallback) : keyed;
    }

    private static String keyArg(List<String> tokens, String key, String fallback) {
        String prefix = key.toLowerCase(Locale.ROOT) + ":";
        for (String token : tokens) {
            if (token.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return token.substring(token.indexOf(':') + 1);
            }
        }
        return fallback;
    }

    private static String restAfter(String line, int tokenCount) {
        String value = line == null ? "" : line.trim();
        for (int i = 0; i < tokenCount; i++) {
            int space = value.indexOf(' ');
            if (space < 0) {
                return "";
            }
            value = value.substring(space + 1).trim();
        }
        return value;
    }

    private void promptCooldown(Player player, CustomItemDefinition item, TriggerType type, int index) {
        input(player, "Enter cooldown as [TICKS] [MESSAGE], or clear", raw -> {
            CooldownInput.ParseResult result = CooldownInput.parse(raw);
            if (!result.valid()) {
                error(player, "Invalid cooldown. Use: [TICKS] [optional message]");
                openTriggerCommands(player, item, type);
                return;
            }
            List<CustomItemDefinition.TriggerCommandDef> commands = item.commands(type);
            if (index >= 0 && index < commands.size()) {
                commands.set(index, commands.get(index).withCooldown(result.ticks(), result.message()));
            }
            openTriggerCommands(player, item, type);
        }, () -> openTriggerCommands(player, item, type));
    }

    private String cooldownLore(CustomItemDefinition.TriggerCommandDef command) {
        if (!command.cooldownEnabled()) {
            return "Left edit | Right cooldown: Disabled | Shift remove";
        }
        return "Left edit | Right cooldown: " + command.cooldownTicks() + " ticks | Shift remove";
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
            }, () -> openEnchantmentSelector(player, item, page, filter)),
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
            option -> input(player, "Enter value for " + attributeKey, raw -> openAttributeSlotGroupSelector(player, item, attributeKey, AttributeModifier.Operation.valueOf(option.key()), parseFloat(raw, 0.0f)), () -> openAttributeOperationSelector(player, item, attributeKey)),
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
            }, () -> openPotionEffectSelector(player, item, effects, reopen, page, filter)),
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
            }, () -> openSoundSelector(player, item, page, filter))
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
            }, () -> openItemModelSelector(player, item, page, filter))
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
        button(menu, 46, Material.COMPASS, NamedTextColor.AQUA, "Search", normalizedFilter.isBlank() ? "No filter" : normalizedFilter, e -> input(player, "Enter search filter, or clear", raw -> openSelector(player, item, title, options, 0, raw.equalsIgnoreCase("clear") ? "" : raw, select, back, clear, custom), () -> openSelector(player, item, title, options, safePage, normalizedFilter, select, back, clear, custom)));
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

    private List<SelectorOption> actionCatalogOptions() {
        return actionCatalogOptions(true);
    }

    private List<SelectorOption> actionCatalogOptions(boolean includeBlocks) {
        return actionSpecs().stream()
            .filter(spec -> includeBlocks || !spec.block())
            .map(spec -> new SelectorOption(spec.name(), spec.icon(), spec.name(), spec.group()))
            .toList();
    }

    private ActionSpec actionSpec(String name) {
        String normalized = firstToken(name).toUpperCase(Locale.ROOT);
        return actionSpecs().stream().filter(spec -> spec.name().equals(normalized)).findFirst().orElse(null);
    }

    private List<ActionSpec> actionSpecs() {
        return List.of(
            spec("DELAY_TICK", Material.CLOCK, "Flow", false, false, p("ticks", "Ticks", Material.CLOCK, "Enter delay ticks", "20", ParamKind.INTEGER)),
            spec("IF", Material.COMPARATOR, "Flow", false, true, p("condition", "Condition", Material.COMPARATOR, "Enter condition, e.g. {X}>0", "1=1", ParamKind.TEXT)),
            spec("LOOP_START", Material.REPEATER, "Flow", true, true, p("times", "Times", Material.REPEATER, "Enter loop count", "3", ParamKind.INTEGER), p("delay", "Delay Ticks", Material.CLOCK, "Enter delay ticks between loops", "20", ParamKind.INTEGER)),
            spec("RANDOM_RUN", Material.DROPPER, "Flow", true, true, p("selectionCount", "Selection Count", Material.DROPPER, "Enter number of choices to run", "1", ParamKind.INTEGER)),
            spec("FOR", Material.HOPPER, "Flow", true, true, p("values", "Values", Material.HOPPER, "Enter comma-separated values", "one,two,three", ParamKind.TEXT), p("variable", "Variable", Material.NAME_TAG, "Enter variable name", "VALUE", ParamKind.VARIABLE)),
            spec("AROUND", Material.PLAYER_HEAD, "Selectors", false, true, p("radius", "Radius", Material.PLAYER_HEAD, "Enter radius", "10", ParamKind.DOUBLE)),
            spec("MOB_AROUND", Material.ZOMBIE_HEAD, "Selectors", false, true, p("radius", "Radius", Material.ZOMBIE_HEAD, "Enter radius", "10", ParamKind.DOUBLE)),
            spec("NEAREST", Material.COMPASS, "Selectors", false, true, p("radius", "Radius", Material.COMPASS, "Enter radius", "10", ParamKind.DOUBLE)),
            spec("MOB_NEAREST", Material.ROTTEN_FLESH, "Selectors", false, true, p("radius", "Radius", Material.ROTTEN_FLESH, "Enter radius", "10", ParamKind.DOUBLE)),
            spec("HITSCAN", Material.SPYGLASS, "Selectors", false, true,
                p("distance", "Distance", Material.SPYGLASS, "Enter distance", "32", ParamKind.INTEGER),
                p("target", "Target", Material.PLAYER_HEAD, "Toggle target mode", "ANY", ParamKind.HITSCAN_TARGET),
                p("maxHits", "Max Hits", Material.CROSSBOW, "Enter positive integer or all", "1", ParamKind.TEXT),
                p("particle", "Particle", Material.BLAZE_POWDER, "Enter particle, or clear", "", ParamKind.TEXT),
                p("points", "Points", Material.REPEATER, "Enter trail points", "24", ParamKind.INTEGER),
                p("offset", "Offset", Material.SUGAR, "Enter particle offset", "0", ParamKind.DOUBLE),
                p("speed", "Speed", Material.FEATHER, "Enter particle speed", "0", ParamKind.DOUBLE)),
            spec("DAMAGE", Material.IRON_SWORD, "Entity", false, false, p("amount", "Amount", Material.RED_DYE, "Enter damage amount", "5", ParamKind.DOUBLE)),
            spec("HEAL", Material.GOLDEN_APPLE, "Entity", false, false, p("amount", "Amount", Material.GOLDEN_APPLE, "Enter heal amount, or leave blank for full heal", "5", ParamKind.DOUBLE)),
            spec("SET_HEALTH", Material.RED_DYE, "Entity", false, false, p("amount", "Amount", Material.RED_DYE, "Enter health amount", "10", ParamKind.DOUBLE)),
            spec("KILL", Material.WITHER_SKELETON_SKULL, "Entity", false, false),
            spec("BURN", Material.FLINT_AND_STEEL, "Entity", false, false, p("seconds", "Seconds", Material.FLINT_AND_STEEL, "Enter burn seconds", "5", ParamKind.INTEGER)),
            spec("INVULNERABILITY", Material.SHIELD, "Entity", false, false, p("ticks", "Ticks", Material.SHIELD, "Enter invulnerability ticks", "40", ParamKind.INTEGER)),
            spec("TELEPORT", Material.ENDER_PEARL, "Entity", false, false, p("mode", "Mode", Material.COMPASS, "Toggle target mode", "COORDS", ParamKind.TARGET_MODE), p("x", "X", Material.MAP, "Enter X", "{X}", ParamKind.TEXT), p("y", "Y", Material.MAP, "Enter Y", "{Y}", ParamKind.TEXT), p("z", "Z", Material.MAP, "Enter Z", "{Z}", ParamKind.TEXT), p("world", "World", Material.GRASS_BLOCK, "Enter world, or clear", "", ParamKind.TEXT)),
            spec("VELOCITY", Material.SLIME_BALL, "Entity", false, false, p("x", "X", Material.SLIME_BALL, "Enter X velocity", "0", ParamKind.DOUBLE), p("y", "Y", Material.SLIME_BALL, "Enter Y velocity", "1", ParamKind.DOUBLE), p("z", "Z", Material.SLIME_BALL, "Enter Z velocity", "0", ParamKind.DOUBLE)),
            spec("DASH", Material.FEATHER, "Entity", false, false, p("strength", "Strength", Material.FEATHER, "Enter dash strength", "1.5", ParamKind.DOUBLE)),
            spec("SEND_MESSAGE", Material.PAPER, "Message", false, false, p("text", "Text", Material.PAPER, "Enter message text", "&aHello", ParamKind.TEXT)),
            spec("ACTIONBAR", Material.OAK_SIGN, "Message", false, false, p("text", "Text", Material.OAK_SIGN, "Enter actionbar text", "&eReady", ParamKind.TEXT)),
            spec("PARTICLE", Material.BLAZE_POWDER, "Visual", false, false, p("particle", "Particle", Material.BLAZE_POWDER, "Enter particle type", "FLAME", ParamKind.TEXT), p("count", "Count", Material.GUNPOWDER, "Enter count", "20", ParamKind.INTEGER), p("offset", "Offset", Material.SUGAR, "Enter offset", "0.2", ParamKind.DOUBLE), p("speed", "Speed", Material.FEATHER, "Enter speed", "0.01", ParamKind.DOUBLE)),
            spec("PARTICLE_LINE", Material.BLAZE_ROD, "Visual", false, false, p("particle", "Particle", Material.BLAZE_POWDER, "Enter particle type", "FLAME", ParamKind.TEXT), p("distance", "Distance", Material.SPYGLASS, "Enter distance", "8", ParamKind.DOUBLE), p("points", "Points", Material.REPEATER, "Enter points", "24", ParamKind.INTEGER), p("offset", "Offset", Material.SUGAR, "Enter offset", "0", ParamKind.DOUBLE), p("speed", "Speed", Material.FEATHER, "Enter speed", "0", ParamKind.DOUBLE)),
            spec("SET_BLOCK", Material.STONE, "Block", false, false, p("material", "Material", Material.STONE, "Enter block material", "STONE", ParamKind.TEXT)),
            spec("SET_TEMP_BLOCK", Material.GLOWSTONE, "Block", false, false, p("material", "Material", Material.GLOWSTONE, "Enter block material", "GLOWSTONE", ParamKind.TEXT), p("ticks", "Ticks", Material.CLOCK, "Enter restore ticks", "100", ParamKind.INTEGER)),
            spec("BREAK_BLOCK", Material.IRON_PICKAXE, "Block", false, false, p("drop", "Drop Items", Material.CHEST, "Toggle drops", "true", ParamKind.BOOLEAN)),
            spec("DROPITEM", Material.DIAMOND, "Item", false, false, p("material", "Material", Material.DIAMOND, "Enter item material", "DIAMOND", ParamKind.TEXT), p("amount", "Amount", Material.CHEST, "Enter amount", "1", ParamKind.INTEGER)),
            spec("VEINMINE", Material.DIAMOND_PICKAXE, "Block", false, false, p("limit", "Limit", Material.DIAMOND_PICKAXE, "Enter block limit", "64", ParamKind.INTEGER), p("drop", "Drop Items", Material.CHEST, "Toggle drops", "true", ParamKind.BOOLEAN))
        );
    }

    private ActionSpec spec(String name, Material icon, String group, boolean block, boolean hasBody, ActionParam... params) {
        return new ActionSpec(name, icon, group, List.of(params), block, hasBody);
    }

    private ActionParam p(String key, String label, Material icon, String prompt, String value, ParamKind kind) {
        return new ActionParam(key, label, icon, prompt, value, kind);
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
        }, () -> openMain(player, 0));
    }

    private void promptMaterial(Player player, CustomItemDefinition item) {
        input(player, "Enter material id, e.g. diamond_sword", raw -> {
            ValidationUtil.material(raw).ifPresentOrElse(item::material, () -> error(player, "Invalid material."));
            openEditor(player, item);
        }, () -> openEditor(player, item));
    }

    private void promptName(Player player, CustomItemDefinition item) {
        input(player, "Enter custom name with & color codes", raw -> {
            item.customName(raw);
            openEditor(player, item);
        }, () -> openEditor(player, item));
    }

    private void promptLore(Player player, CustomItemDefinition item) {
        input(player, "Enter lore lines separated with |, or clear", raw -> {
            item.lore().clear();
            if (!raw.equalsIgnoreCase("clear")) {
                item.lore().addAll(Arrays.stream(raw.split("\\|")).map(String::trim).filter(s -> !s.isBlank()).toList());
            }
            openEditor(player, item);
        }, () -> openEditor(player, item));
    }

    private void save(Player player, CustomItemDefinition item) {
        for (Map.Entry<TriggerType, List<CustomItemDefinition.TriggerCommandDef>> entry : item.triggers().entrySet()) {
            List<CustomItemDefinition.TriggerCommandDef> commands = entry.getValue();
            for (int i = 0; i < commands.size(); i++) {
                commands.set(i, ActionFormatter.normalize(commands.get(i)));
            }
            if (TriggerExecutor.invalidVariable(commands, entry.getKey()).isPresent()) {
                error(player, "Invalid trigger variable in " + entry.getKey());
                return;
            }
            Optional<String> invalidAction = ActionValidator.invalidKnownAction(commands.stream().map(CustomItemDefinition.TriggerCommandDef::command).toList());
            if (invalidAction.isPresent()) {
                error(player, "Invalid action in " + entry.getKey() + ": " + invalidAction.get());
                return;
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
        }, reopen);
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

    private void input(Player player, String prompt, Consumer<String> action, Runnable cancel) {
        inputManager.prompt(player, prompt, action, cancel);
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
        }, () -> openExtras(player, item)));
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
        }, () -> openExtras(player, item)));
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

    private enum ParamKind {
        TEXT,
        INTEGER,
        DOUBLE,
        BOOLEAN,
        VARIABLE,
        TARGET_MODE,
        HITSCAN_TARGET
    }

    private record ActionParam(String key, String label, Material icon, String prompt, String defaultValue, ParamKind kind) {
    }

    private record ActionSpec(String name, Material icon, String group, List<ActionParam> params, boolean block, boolean hasBody) {
    }

    private static final class ActionDraft {
        private final ActionSpec spec;
        private final Map<String, String> values = new HashMap<>();
        private final List<String> body = new ArrayList<>();

        private ActionDraft(ActionSpec spec) {
            this.spec = spec;
        }

        static ActionDraft create(ActionSpec spec) {
            ActionDraft draft = new ActionDraft(spec);
            for (ActionParam param : spec.params()) {
                draft.values.put(param.key(), param.defaultValue());
            }
            if (spec.hasBody()) {
                draft.body.add(defaultBody(spec.name()));
            }
            return draft;
        }

        String value(String key) {
            return values.getOrDefault(key, "");
        }

        void put(String key, String value) {
            if (value != null && value.equalsIgnoreCase("clear")) {
                values.put(key, "");
                return;
            }
            values.put(key, value == null ? "" : value.trim());
        }

        List<String> body() {
            return body;
        }

        List<String> format() {
            String name = spec.name();
            return switch (name) {
                case "DELAY_TICK" -> List.of(join(name, value("ticks")));
                case "IF" -> List.of(join(name, value("condition"), chainBody()));
                case "LOOP_START" -> block("LOOP_START " + value("times") + " " + value("delay"), "LOOP_END");
                case "RANDOM_RUN" -> block("RANDOM_RUN selectionCount:" + value("selectionCount"), "RANDOM_END");
                case "FOR" -> block("FOR [" + value("values") + "] > " + value("variable").toUpperCase(Locale.ROOT), "END_FOR " + value("variable").toUpperCase(Locale.ROOT));
                case "AROUND", "MOB_AROUND", "NEAREST", "MOB_NEAREST" -> List.of(join(name, value("radius"), chainBody()));
                case "HITSCAN" -> List.of(formatHitscan());
                case "DAMAGE", "SET_HEALTH" -> List.of(join(name, value("amount")));
                case "HEAL" -> value("amount").isBlank() ? List.of(name) : List.of(join(name, value("amount")));
                case "KILL" -> List.of(name);
                case "BURN" -> List.of(join(name, value("seconds")));
                case "INVULNERABILITY" -> List.of(join(name, value("ticks")));
                case "TELEPORT" -> formatTeleport();
                case "VELOCITY" -> List.of(join(name, value("x"), value("y"), value("z")));
                case "DASH" -> List.of(join(name, value("strength")));
                case "SEND_MESSAGE", "ACTIONBAR" -> List.of(join(name, value("text")));
                case "PARTICLE" -> List.of(join(name, value("particle").toUpperCase(Locale.ROOT), value("count"), value("offset"), value("speed")));
                case "PARTICLE_LINE" -> List.of(join(name, value("particle").toUpperCase(Locale.ROOT), value("distance"), value("points"), value("offset"), value("speed")));
                case "SET_BLOCK" -> List.of(join(name, value("material").toUpperCase(Locale.ROOT)));
                case "SET_TEMP_BLOCK" -> List.of(join(name, value("material").toUpperCase(Locale.ROOT), value("ticks")));
                case "BREAK_BLOCK" -> List.of(name + " drop:" + value("drop").toLowerCase(Locale.ROOT));
                case "DROPITEM" -> List.of(join(name, value("material").toUpperCase(Locale.ROOT), value("amount")));
                case "VEINMINE" -> List.of(join(name, value("limit"), "drop:" + value("drop").toLowerCase(Locale.ROOT)));
                default -> List.of(name);
            };
        }

        private List<String> formatTeleport() {
            String mode = value("mode").toUpperCase(Locale.ROOT);
            if (mode.equals("SELF") || mode.equals("TARGET")) {
                return List.of("TELEPORT target:" + mode);
            }
            String world = value("world");
            return world.isBlank()
                ? List.of(join("TELEPORT", value("x"), value("y"), value("z")))
                : List.of(join("TELEPORT", value("x"), value("y"), value("z"), world));
        }

        private String formatHitscan() {
            List<String> parts = new ArrayList<>();
            parts.add("HITSCAN");
            parts.add(value("distance"));
            parts.add("target:" + value("target").toUpperCase(Locale.ROOT));
            parts.add("max-hits:" + value("maxHits").toLowerCase(Locale.ROOT));
            if (!value("particle").isBlank()) {
                parts.add("particle:" + value("particle").toUpperCase(Locale.ROOT));
                parts.add("points:" + value("points"));
                parts.add("offset:" + value("offset"));
                parts.add("speed:" + value("speed"));
            }
            parts.add(chainBody());
            return join(parts.toArray(String[]::new));
        }

        private List<String> block(String header, String terminator) {
            List<String> lines = new ArrayList<>();
            lines.add(header);
            lines.addAll(body.stream().filter(line -> !line.isBlank()).toList());
            lines.add(terminator);
            return lines;
        }

        private String chainBody() {
            return String.join(" <+> ", body.stream().filter(line -> !line.isBlank()).toList());
        }

        private static String defaultBody(String actionName) {
            return switch (actionName) {
                case "IF", "AROUND", "NEAREST" -> "SEND_MESSAGE &aReady";
                case "MOB_AROUND", "MOB_NEAREST", "HITSCAN" -> "DAMAGE 5";
                case "LOOP_START" -> "PARTICLE CRIT 15 0.2 0.01";
                case "RANDOM_RUN" -> "SEND_MESSAGE &aOption";
                case "FOR" -> "SEND_MESSAGE &e{VALUE}";
                default -> "";
            };
        }

        private static String join(String... parts) {
            return Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        }
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
