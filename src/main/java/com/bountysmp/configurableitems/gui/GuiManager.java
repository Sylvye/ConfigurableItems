package com.bountysmp.configurableitems.gui;

import com.bountysmp.configurableitems.action.ActionFormatter;
import com.bountysmp.configurableitems.action.ActionParser;
import com.bountysmp.configurableitems.action.ActionValidator;
import com.bountysmp.configurableitems.action.HitboxOptions;
import com.bountysmp.configurableitems.action.HitscanOptions;
import com.bountysmp.configurableitems.action.TimerOptions;
import com.bountysmp.configurableitems.action.ProjectileTrailOptions;
import com.bountysmp.configurableitems.action.TargetKind;
import com.bountysmp.configurableitems.action.VeinmineOptions;
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
import java.util.Set;
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
        saveAll(menu, player, item);
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
        button(menu, 10, Material.LIME_DYE, NamedTextColor.GREEN, "Add Command", "Console command without slash", e -> input(player, rawCommandPrompt(type, "Enter console command"), raw -> {
            String command = ActionFormatter.normalizeLine(raw.startsWith("/") ? raw.substring(1) : raw);
            item.commands(type).add(new CustomItemDefinition.TriggerCommandDef(command));
            openTriggerCommands(player, item, type);
        }, () -> openTriggerCommands(player, item, type)));
        button(menu, 11, Material.PAPER, NamedTextColor.AQUA, "Variables", triggerVariableLore(type));
        button(menu, 12, Material.COMMAND_BLOCK, NamedTextColor.YELLOW, "Add Action", "Choose a CI action", e -> openActionSelector(player, item, type, -1, 0, ""));
        int slot = 19;
        List<CustomItemDefinition.TriggerCommandDef> commands = item.commands(type);
        for (ActionCommandRows.Row row : ActionCommandRows.rows(commandLines(commands))) {
            if (slot >= 44) {
                break;
            }
            int index = row.startIndex();
            CustomItemDefinition.TriggerCommandDef command = commands.get(index);
            Material icon = row.block() ? Material.COMMAND_BLOCK : Material.REDSTONE;
            NamedTextColor color = command.cooldownEnabled() ? NamedTextColor.GOLD : row.block() ? NamedTextColor.YELLOW : NamedTextColor.RED;
            button(menu, slot++, icon, color, row.summary(), cooldownLore(command, row), e -> {
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
        saveAll(menu, player, item);
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
            input(player, rawCommandPrompt(type, "Edit console command"), raw -> {
                String normalized = ActionFormatter.normalizeLine(raw.startsWith("/") ? raw.substring(1) : raw);
                commands.set(index, commands.get(index).withCommand(normalized));
                openTriggerCommands(player, item, type);
            }, () -> openTriggerCommands(player, item, type));
            return;
        }
        ActionSpec spec = actionSpec(actionName);
        if (spec == null) {
            input(player, rawCommandPrompt(type, "Edit action line"), raw -> {
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
        openSelector(player, item, "Select Action", actionCatalogOptions(type), page, filter,
            option -> {
                ActionSpec spec = actionSpec(option.key());
                if (spec == null) {
                    openTriggerCommands(player, item, type);
                    return;
                }
                ActionDraft draft = ActionDraft.create(spec);
                int index = upsertActionDraft(item, type, editIndex, draft);
                openActionEditor(player, item, type, index, draft, () -> openTriggerCommands(player, item, type));
            },
            () -> openTriggerCommands(player, item, type),
            null,
            () -> input(player, rawCommandPrompt(type, "Enter custom CI action"), raw -> {
                String normalized = ActionFormatter.normalizeLine(raw);
                if (editIndex >= 0) {
                    List<CustomItemDefinition.TriggerCommandDef> commands = item.commands(type);
                    replaceCommandRange(commands, editIndex, List.of(normalized));
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
        button(menu, 49, Material.ARROW, NamedTextColor.GRAY, "Back", "", e -> openTriggerCommands(player, item, type));
        saveAll(menu, player, item);
        player.openInventory(inv);
    }

    private void editActionParam(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft draft, ActionParam param, Runnable cancel) {
        if (param.kind() == ParamKind.BOOLEAN) {
            draft.put(param.key(), String.valueOf(!Boolean.parseBoolean(draft.value(param.key()))));
            upsertActionDraft(item, type, editIndex, draft);
            openActionEditor(player, item, type, editIndex, draft, cancel);
            return;
        }
        if (param.kind() == ParamKind.TARGET_MODE) {
            draft.put(param.key(), next(draft.value(param.key()), "CURRENT", "SELF", "TARGET"));
            upsertActionDraft(item, type, editIndex, draft);
            openActionEditor(player, item, type, editIndex, draft, cancel);
            return;
        }
        if (param.kind() == ParamKind.TELEPORT_TO) {
            draft.put(param.key(), next(draft.value(param.key()), "COORDS", "SELF", "TARGET", "CURRENT", "HIT", "BLOCK"));
            upsertActionDraft(item, type, editIndex, draft);
            openActionEditor(player, item, type, editIndex, draft, cancel);
            return;
        }
        if (param.kind() == ParamKind.AT_MODE) {
            draft.put(param.key(), next(draft.value(param.key()), "CURRENT", "SELF", "TARGET", "BLOCK", "HIT", "PROJECTILE"));
            upsertActionDraft(item, type, editIndex, draft);
            openActionEditor(player, item, type, editIndex, draft, cancel);
            return;
        }
        if (param.kind() == ParamKind.HITSCAN_TARGET) {
            draft.put(param.key(), next(draft.value(param.key()), "ENTITY", "PLAYER", "MOB", "BLOCK"));
            upsertActionDraft(item, type, editIndex, draft);
            openActionEditor(player, item, type, editIndex, draft, cancel);
            return;
        }
        if (param.kind() == ParamKind.SELECTOR_TARGET) {
            draft.put(param.key(), next(draft.value(param.key()), "PLAYER", "MOB", "ENTITY"));
            upsertActionDraft(item, type, editIndex, draft);
            openActionEditor(player, item, type, editIndex, draft, cancel);
            return;
        }
        if (param.kind() == ParamKind.VEINMINE_MODE) {
            draft.put(param.key(), next(draft.value(param.key()), "DESTROY", "REPLACE"));
            upsertActionDraft(item, type, editIndex, draft);
            openActionEditor(player, item, type, editIndex, draft, cancel);
            return;
        }
        if (param.kind() == ParamKind.VEINMINE_MATCH) {
            draft.put(param.key(), next(draft.value(param.key()), "ALL", "SAME_TYPE"));
            upsertActionDraft(item, type, editIndex, draft);
            openActionEditor(player, item, type, editIndex, draft, cancel);
            return;
        }
        input(player, param.prompt(), raw -> {
            String value = param.kind() == ParamKind.VARIABLE ? raw.toUpperCase(Locale.ROOT) : ActionFormatter.normalizeVariables(raw.trim());
            draft.put(param.key(), value);
            upsertActionDraft(item, type, editIndex, draft);
            openActionEditor(player, item, type, editIndex, draft, cancel);
        }, () -> openActionEditor(player, item, type, editIndex, draft, cancel));
    }

    private void openActionBodyEditor(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft draft, Runnable cancel) {
        openActionBodyEditor(player, item, type, editIndex, draft, cancel, () -> openActionEditor(player, item, type, editIndex, draft, cancel), () -> upsertActionDraft(item, type, editIndex, draft));
    }

    private void openActionBodyEditor(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft draft, Runnable cancel, Runnable backToAction) {
        openActionBodyEditor(player, item, type, editIndex, draft, cancel, backToAction, () -> upsertActionDraft(item, type, editIndex, draft));
    }

    private void openActionBodyEditor(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft draft, Runnable cancel, Runnable backToAction, Runnable bodyChanged) {
        Menu menu = new Menu("Action Body");
        Inventory inv = menu.inventory();
        frame(inv);
        button(menu, 10, Material.LIME_DYE, NamedTextColor.GREEN, "Add Nested Action", "Choose a CI action", e -> openNestedActionSelector(player, item, type, editIndex, draft, cancel, backToAction, bodyChanged, 0, ""));
        button(menu, 11, Material.NAME_TAG, NamedTextColor.YELLOW, "Add Raw Line", triggerContextSummary(type), e -> input(player, rawCommandPrompt(bodyContext(type, draft), "Enter nested action or command"), raw -> {
            draft.body().add(ActionFormatter.normalizeLine(raw));
            bodyChanged.run();
            openActionBodyEditor(player, item, type, editIndex, draft, cancel, backToAction, bodyChanged);
        }, () -> openActionBodyEditor(player, item, type, editIndex, draft, cancel, backToAction, bodyChanged)));
        int slot = 19;
        for (ActionCommandRows.Row row : ActionCommandRows.rows(draft.body())) {
            if (slot >= 44) {
                break;
            }
            int bodyIndex = row.startIndex();
            Material icon = row.block() ? Material.COMMAND_BLOCK : Material.REDSTONE;
            button(menu, slot++, icon, NamedTextColor.AQUA, row.summary(), "Left edit | Shift remove", e -> {
                if (e.isShiftClick()) {
                    removeLineRange(draft.body(), row.startIndex(), row.endIndex());
                    bodyChanged.run();
                    openActionBodyEditor(player, item, type, editIndex, draft, cancel, backToAction, bodyChanged);
                    return;
                }
                openNestedExistingActionEditor(player, item, type, editIndex, draft, bodyIndex, cancel, backToAction, bodyChanged);
            });
        }
        button(menu, 49, Material.ARROW, NamedTextColor.GRAY, "Back", "", e -> backToAction.run());
        saveAll(menu, player, item);
        player.openInventory(inv);
    }

    private void openNestedActionSelector(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft parent, Runnable cancel, Runnable parentBackToAction, Runnable parentChanged, int page, String filter) {
        openSelector(player, item, "Nested Action", actionCatalogOptions(false, bodyContext(type, parent)), page, filter,
            option -> {
                ActionSpec spec = actionSpec(option.key());
                if (spec != null) {
                    int bodyIndex = parent.body().size();
                    ActionDraft nested = ActionDraft.create(spec);
                    parent.body().add(ActionFormatter.normalizeLine(nested.format().getFirst()));
                    parentChanged.run();
                    openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction, parentChanged);
                    return;
                }
                openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction, parentChanged);
            },
            () -> openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction, parentChanged),
            null,
            () -> input(player, rawCommandPrompt(bodyContext(type, parent), "Enter nested action or command"), raw -> {
                parent.body().add(ActionFormatter.normalizeLine(raw));
                parentChanged.run();
                openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction, parentChanged);
            }, () -> openNestedActionSelector(player, item, type, editIndex, parent, cancel, parentBackToAction, parentChanged, page, filter))
        );
    }

    private void openNestedExistingActionEditor(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft parent, int bodyIndex, Runnable cancel, Runnable parentBackToAction, Runnable parentChanged) {
        if (bodyIndex < 0 || bodyIndex >= parent.body().size()) {
            openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction, parentChanged);
            return;
        }
        String line = parent.body().get(bodyIndex);
        ActionSpec spec = actionSpec(firstToken(line));
        if (spec == null || spec.block()) {
            input(player, rawCommandPrompt(bodyContext(type, parent), "Edit nested command"), raw -> {
                parent.body().set(bodyIndex, ActionFormatter.normalizeLine(raw));
                parentChanged.run();
                openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction, parentChanged);
            }, () -> openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction, parentChanged));
            return;
        }
        openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, draftFromLine(spec, line), cancel, parentBackToAction, parentChanged);
    }

    private void openNestedActionEditor(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft parent, int bodyIndex, ActionDraft nested, Runnable cancel, Runnable parentBackToAction, Runnable parentChanged) {
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
            button(menu, slot, param.icon(), NamedTextColor.AQUA, param.label(), nested.value(param.key()), e -> editNestedActionParam(player, item, type, editIndex, parent, bodyIndex, nested, param, cancel, parentBackToAction, parentChanged));
        }
        if (nested.spec.hasBody()) {
            Runnable backToNested = () -> openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction, parentChanged);
            Runnable nestedChanged = () -> {
                updateNestedLine(parent, bodyIndex, nested);
                parentChanged.run();
            };
            button(menu, 31, Material.WRITABLE_BOOK, NamedTextColor.YELLOW, "Nested Actions", nested.body().isEmpty() ? "Empty" : String.join(" <+> ", nested.body()), e -> openActionBodyEditor(player, item, type, editIndex, nested, backToNested, backToNested, nestedChanged));
        }
        button(menu, 49, Material.ARROW, NamedTextColor.GRAY, "Back", "", e -> openActionBodyEditor(player, item, type, editIndex, parent, cancel, parentBackToAction, parentChanged));
        saveAll(menu, player, item);
        player.openInventory(inv);
    }

    private void editNestedActionParam(Player player, CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft parent, int bodyIndex, ActionDraft nested, ActionParam param, Runnable cancel, Runnable parentBackToAction, Runnable parentChanged) {
        if (param.kind() == ParamKind.BOOLEAN) {
            nested.put(param.key(), String.valueOf(!Boolean.parseBoolean(nested.value(param.key()))));
            updateNestedLine(parent, bodyIndex, nested);
            parentChanged.run();
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction, parentChanged);
            return;
        }
        if (param.kind() == ParamKind.TARGET_MODE) {
            nested.put(param.key(), next(nested.value(param.key()), "CURRENT", "SELF", "TARGET"));
            updateNestedLine(parent, bodyIndex, nested);
            parentChanged.run();
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction, parentChanged);
            return;
        }
        if (param.kind() == ParamKind.TELEPORT_TO) {
            nested.put(param.key(), next(nested.value(param.key()), "COORDS", "SELF", "TARGET", "CURRENT", "HIT", "BLOCK"));
            updateNestedLine(parent, bodyIndex, nested);
            parentChanged.run();
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction, parentChanged);
            return;
        }
        if (param.kind() == ParamKind.AT_MODE) {
            nested.put(param.key(), next(nested.value(param.key()), "CURRENT", "SELF", "TARGET", "BLOCK", "HIT", "PROJECTILE"));
            updateNestedLine(parent, bodyIndex, nested);
            parentChanged.run();
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction, parentChanged);
            return;
        }
        if (param.kind() == ParamKind.HITSCAN_TARGET) {
            nested.put(param.key(), next(nested.value(param.key()), "ENTITY", "PLAYER", "MOB", "BLOCK"));
            updateNestedLine(parent, bodyIndex, nested);
            parentChanged.run();
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction, parentChanged);
            return;
        }
        if (param.kind() == ParamKind.SELECTOR_TARGET) {
            nested.put(param.key(), next(nested.value(param.key()), "PLAYER", "MOB", "ENTITY"));
            updateNestedLine(parent, bodyIndex, nested);
            parentChanged.run();
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction, parentChanged);
            return;
        }
        if (param.kind() == ParamKind.VEINMINE_MODE) {
            nested.put(param.key(), next(nested.value(param.key()), "DESTROY", "REPLACE"));
            updateNestedLine(parent, bodyIndex, nested);
            parentChanged.run();
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction, parentChanged);
            return;
        }
        if (param.kind() == ParamKind.VEINMINE_MATCH) {
            nested.put(param.key(), next(nested.value(param.key()), "ALL", "SAME_TYPE"));
            updateNestedLine(parent, bodyIndex, nested);
            parentChanged.run();
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction, parentChanged);
            return;
        }
        input(player, param.prompt(), raw -> {
            String value = param.kind() == ParamKind.VARIABLE ? raw.toUpperCase(Locale.ROOT) : ActionFormatter.normalizeVariables(raw.trim());
            nested.put(param.key(), value);
            updateNestedLine(parent, bodyIndex, nested);
            parentChanged.run();
            openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction, parentChanged);
        }, () -> openNestedActionEditor(player, item, type, editIndex, parent, bodyIndex, nested, cancel, parentBackToAction, parentChanged));
    }

    private int upsertActionDraft(CustomItemDefinition item, TriggerType type, int editIndex, ActionDraft draft) {
        List<String> lines = ActionFormatter.normalizeLines(draft.format());
        if (lines.isEmpty()) {
            return editIndex;
        }
        List<CustomItemDefinition.TriggerCommandDef> commands = item.commands(type);
        if (editIndex >= 0 && editIndex < commands.size()) {
            replaceCommandRange(commands, editIndex, lines);
            return editIndex;
        }
        int index = commands.size();
        for (String line : lines) {
            commands.add(new CustomItemDefinition.TriggerCommandDef(line));
        }
        return index;
    }

    private void updateNestedLine(ActionDraft parent, int bodyIndex, ActionDraft nested) {
        List<String> lines = ActionFormatter.normalizeLines(nested.format());
        if (lines.isEmpty() || bodyIndex < 0 || bodyIndex >= parent.body().size()) {
            return;
        }
        parent.body().set(bodyIndex, lines.getFirst());
    }

    private void replaceCommandRange(List<CustomItemDefinition.TriggerCommandDef> commands, int index, List<String> lines) {
        int end = blockEnd(commands, index);
        CustomItemDefinition.TriggerCommandDef first = commands.get(index).withCommand(lines.getFirst());
        for (int i = end; i >= index; i--) {
            commands.remove(i);
        }
        commands.add(index, first);
        for (int i = 1; i < lines.size(); i++) {
            commands.add(index + i, new CustomItemDefinition.TriggerCommandDef(lines.get(i)));
        }
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
            case "AROUND", "NEAREST" -> {
                draft.put("radius", token(tokens, 1, draft.value("radius")));
                draft.put("target", selectorTarget(tokens, firstToken(line)));
                draft.body().clear();
                draft.body().addAll(ActionParser.splitInline(bodyAfter(tokens, selectorBodyIndex(tokens))));
            }
            case "HITSCAN" -> {
                HitscanOptions options = HitscanOptions.parse(tokens);
                draft.put("distance", String.valueOf(options.distance()));
                draft.put("target", options.targetMode().name());
                draft.put("maxHits", options.allHits() ? "all" : String.valueOf(options.maxHits()));
                draft.put("raySpeed", options.speed());
                draft.put("particle", options.particle());
                draft.put("points", String.valueOf(options.points()));
                draft.put("offset", String.valueOf(options.offset()));
                draft.put("particleSpeed", String.valueOf(options.particleSpeed()));
                draft.body().clear();
                draft.body().addAll(ActionParser.splitInline(options.body()));
            }
            case "PROJECTILE_TRAIL" -> {
                ProjectileTrailOptions options = ProjectileTrailOptions.parse(tokens);
                draft.put("particle", options.particle());
                draft.put("count", String.valueOf(options.count()));
                draft.put("points", String.valueOf(options.points()));
                draft.put("interval", String.valueOf(options.interval()));
                draft.put("duration", String.valueOf(options.duration()));
                draft.put("offset", String.valueOf(options.offset()));
                draft.put("speed", String.valueOf(options.speed()));
                replaceBody(draft, lines, 1, lines.size() - 1);
            }
            case "HITBOX" -> {
                HitboxOptions options = HitboxOptions.parse(tokens);
                draft.put("shape", options.shape().name());
                draft.put("size", String.valueOf(options.size()));
                draft.put("at", options.at());
                draft.put("targets", String.join(",", options.targets().stream().map(Enum::name).toList()));
                draft.put("maxPlayers", String.valueOf(options.maxPlayers()));
                draft.put("maxEntities", String.valueOf(options.maxEntities()));
                draft.put("maxBlocks", String.valueOf(options.maxBlocks()));
                draft.put("particle", options.particle());
                draft.put("edgeParticles", String.valueOf(options.edgeParticles()));
                draft.put("points", String.valueOf(options.points()));
                replaceBody(draft, lines, 1, lines.size() - 1);
            }
            case "TIMER" -> {
                TimerOptions options = TimerOptions.parse(tokens);
                draft.put("duration", String.valueOf(options.duration()));
                draft.put("interval", String.valueOf(options.interval()));
                replaceBody(draft, lines, 1, lines.size() - 1);
            }
            case "DAMAGE", "SET_HEALTH" -> {
                draft.put("amount", tokenOrKey(tokens, "amount", firstValueIndex(tokens), draft.value("amount")));
                draft.put("receiver", keyArg(tokens, "target", draft.value("receiver")).toUpperCase(Locale.ROOT));
                if (spec.name().equals("DAMAGE")) {
                    draft.put("damageType", keyArg(tokens, "type", draft.value("damageType")).toUpperCase(Locale.ROOT));
                }
            }
            case "HEAL" -> {
                draft.put("amount", tokenOrKey(tokens, "amount", firstValueIndex(tokens), draft.value("amount")));
                draft.put("receiver", keyArg(tokens, "target", draft.value("receiver")).toUpperCase(Locale.ROOT));
            }
            case "BURN" -> {
                draft.put("seconds", token(tokens, firstValueIndex(tokens), draft.value("seconds")));
                draft.put("receiver", keyArg(tokens, "target", draft.value("receiver")).toUpperCase(Locale.ROOT));
            }
            case "KILL" -> draft.put("receiver", keyArg(tokens, "target", draft.value("receiver")).toUpperCase(Locale.ROOT));
            case "INVULNERABILITY" -> {
                draft.put("ticks", token(tokens, firstValueIndex(tokens), draft.value("ticks")));
                draft.put("receiver", keyArg(tokens, "target", draft.value("receiver")).toUpperCase(Locale.ROOT));
            }
            case "TELEPORT" -> parseTeleport(draft, tokens);
            case "LAUNCH_PROJECTILE" -> {
                int start = firstValueIndex(tokens);
                draft.put("projectile", token(tokens, start, draft.value("projectile")).toUpperCase(Locale.ROOT));
                draft.put("speed", keyArg(tokens, "speed", token(tokens, start + 1, draft.value("speed"))));
                draft.put("gravity", keyArg(tokens, "gravity", draft.value("gravity")));
                draft.put("track", keyArg(tokens, "track", draft.value("track")));
            }
            case "VELOCITY" -> {
                int start = firstValueIndex(tokens);
                draft.put("x", token(tokens, start, draft.value("x")));
                draft.put("y", token(tokens, start + 1, draft.value("y")));
                draft.put("z", token(tokens, start + 2, draft.value("z")));
                draft.put("receiver", keyArg(tokens, "target", draft.value("receiver")).toUpperCase(Locale.ROOT));
            }
            case "DASH" -> draft.put("strength", tokenOrKey(tokens, "strength", 1, draft.value("strength")));
            case "DAMAGE_ITEM" -> {
                draft.put("amount", token(tokens, firstValueIndex(tokens), draft.value("amount")));
                draft.put("item", keyArg(tokens, "item", draft.value("item")).toUpperCase(Locale.ROOT));
                draft.put("receiver", keyArg(tokens, "target", draft.value("receiver")).toUpperCase(Locale.ROOT));
            }
            case "TAKE_ITEM" -> {
                int start = firstValueIndex(tokens);
                draft.put("item", token(tokens, start, draft.value("item")).toUpperCase(Locale.ROOT));
                draft.put("amount", token(tokens, start + 1, draft.value("amount")));
                draft.put("receiver", keyArg(tokens, "target", draft.value("receiver")).toUpperCase(Locale.ROOT));
            }
            case "REPAIR_ITEM" -> {
                int start = firstValueIndex(tokens);
                draft.put("amount", token(tokens, start, draft.value("amount")));
                draft.put("item", keyArg(tokens, "item", token(tokens, start + 1, draft.value("item"))).toUpperCase(Locale.ROOT));
                draft.put("receiver", keyArg(tokens, "target", draft.value("receiver")).toUpperCase(Locale.ROOT));
            }
            case "IMPULSE" -> {
                int start = firstValueIndex(tokens);
                draft.put("x", token(tokens, start, draft.value("x")));
                draft.put("y", token(tokens, start + 1, draft.value("y")));
                draft.put("z", token(tokens, start + 2, draft.value("z")));
                draft.put("power", keyArg(tokens, "power", token(tokens, start + 3, draft.value("power"))));
                draft.put("targets", keyArg(tokens, "targets", draft.value("targets")).toUpperCase(Locale.ROOT));
                draft.put("radius", keyArg(tokens, "radius", draft.value("radius")));
                draft.put("normalize", keyArg(tokens, "normalize", draft.value("normalize")));
            }
            case "EXPLODE" -> {
                int start = firstValueIndex(tokens);
                draft.put("power", token(tokens, start, draft.value("power")));
                draft.put("x", token(tokens, start + 1, draft.value("x")));
                draft.put("y", token(tokens, start + 2, draft.value("y")));
                draft.put("z", token(tokens, start + 3, draft.value("z")));
                draft.put("world", token(tokens, start + 4, draft.value("world")));
                draft.put("fire", keyArg(tokens, "fire", draft.value("fire")));
                draft.put("breakBlocks", keyArg(tokens, "break-blocks", draft.value("breakBlocks")));
            }
            case "SEND_MESSAGE", "ACTIONBAR" -> {
                draft.put("receiver", keyArg(tokens, "target", draft.value("receiver")).toUpperCase(Locale.ROOT));
                draft.put("text", stripKeyToken(restAfter(line, 1), "target"));
            }
            case "PARTICLE" -> {
                int start = firstValueIndex(tokens);
                draft.put("particle", token(tokens, start, draft.value("particle")).toUpperCase(Locale.ROOT));
                draft.put("count", token(tokens, start + 1, draft.value("count")));
                draft.put("offset", token(tokens, start + 2, draft.value("offset")));
                draft.put("speed", token(tokens, start + 3, draft.value("speed")));
                draft.put("at", keyArg(tokens, "at", draft.value("at")).toUpperCase(Locale.ROOT));
                draft.put("shape", keyArg(tokens, "shape", draft.value("shape")).toUpperCase(Locale.ROOT));
                draft.put("size", keyArg(tokens, "size", draft.value("size")));
                draft.put("rotation", keyArg(tokens, "rotation", draft.value("rotation")));
                draft.put("points", keyArg(tokens, "points", draft.value("points")));
            }
            case "PARTICLE_LINE" -> {
                int start = firstValueIndex(tokens);
                draft.put("particle", token(tokens, start, draft.value("particle")).toUpperCase(Locale.ROOT));
                draft.put("distance", token(tokens, start + 1, draft.value("distance")));
                draft.put("points", token(tokens, start + 2, draft.value("points")));
                draft.put("offset", token(tokens, start + 3, draft.value("offset")));
                draft.put("speed", token(tokens, start + 4, draft.value("speed")));
                draft.put("at", keyArg(tokens, "at", draft.value("at")).toUpperCase(Locale.ROOT));
            }
            case "SET_BLOCK" -> draft.put("material", token(tokens, 1, draft.value("material")).toUpperCase(Locale.ROOT));
            case "SET_TEMP_BLOCK" -> {
                draft.put("material", token(tokens, 1, draft.value("material")).toUpperCase(Locale.ROOT));
                draft.put("ticks", token(tokens, 2, draft.value("ticks")));
            }
            case "BREAK_BLOCK" -> draft.put("drop", keyArg(tokens, "drop", draft.value("drop")));
            case "DROPITEM" -> {
                int start = firstValueIndex(tokens);
                draft.put("material", token(tokens, start, draft.value("material")).toUpperCase(Locale.ROOT));
                draft.put("amount", token(tokens, start + 1, draft.value("amount")));
                draft.put("at", keyArg(tokens, "at", draft.value("at")).toUpperCase(Locale.ROOT));
            }
            case "VEINMINE" -> {
                VeinmineOptions options = VeinmineOptions.parse(tokens);
                draft.put("limit", options.limit() == null ? draft.value("limit") : String.valueOf(options.limit()));
                draft.put("drop", String.valueOf(options.drop()));
                draft.put("filter", options.filter().raw());
                draft.put("match", options.matchMode().name());
                draft.put("mode", options.mode().name());
                draft.put("replace", options.replacement() == null ? "" : options.replacement().name());
                draft.put("useEnchants", String.valueOf(options.useEnchants()));
                draft.put("useDurability", String.valueOf(options.useDurability()));
                draft.put("effect", String.valueOf(options.effect()));
                draft.put("xp", String.valueOf(options.xp()));
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
        String target = keyArg(tokens, "target", "CURRENT");
        draft.put("receiver", target.toUpperCase(Locale.ROOT));
        draft.put("safe", keyArg(tokens, "safe", draft.value("safe")));
        String to = keyArg(tokens, "to", null);
        if (to != null) {
            draft.put("to", to.toUpperCase(Locale.ROOT));
            return;
        }
        if (tokens.size() == 2 && keyArg(tokens, "target", null) != null) {
            draft.put("receiver", "CURRENT");
            draft.put("to", target.toUpperCase(Locale.ROOT));
            return;
        }
        draft.put("to", "COORDS");
        int start = firstValueIndex(tokens);
        draft.put("x", token(tokens, start, draft.value("x")));
        draft.put("y", token(tokens, start + 1, draft.value("y")));
        draft.put("z", token(tokens, start + 2, draft.value("z")));
        draft.put("world", token(tokens, start + 3, draft.value("world")));
    }

    private String preview(ActionDraft draft) {
        String value = String.join(" | ", draft.format());
        return value.length() > 140 ? value.substring(0, 137) + "..." : value;
    }

    private int blockEnd(List<CustomItemDefinition.TriggerCommandDef> commands, int index) {
        return ActionCommandRows.blockEnd(commandLines(commands), index);
    }

    private static String firstToken(String line) {
        List<String> tokens = tokens(line);
        return tokens.isEmpty() ? "" : tokens.getFirst();
    }

    private static String canonicalActionName(String name) {
        String normalized = (name == null ? "" : name).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MOB_AROUND" -> "AROUND";
            case "MOB_NEAREST" -> "NEAREST";
            default -> normalized;
        };
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

    private static int firstValueIndex(List<String> tokens) {
        for (int i = 1; i < tokens.size(); i++) {
            if (!tokens.get(i).contains(":")) {
                return i;
            }
        }
        return tokens.size();
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

    private static String stripKeyToken(String text, String key) {
        String prefix = key.toLowerCase(Locale.ROOT) + ":";
        return Arrays.stream((text == null ? "" : text.trim()).split("\\s+"))
            .filter(token -> !token.toLowerCase(Locale.ROOT).startsWith(prefix))
            .reduce((left, right) -> left + " " + right)
            .orElse("");
    }

    private static String selectorTarget(List<String> tokens, String action) {
        String fallback = action != null && action.toUpperCase(Locale.ROOT).startsWith("MOB_") ? TargetKind.MOB.name() : TargetKind.PLAYER.name();
        String raw = keyArg(tokens, "target", fallback);
        return TargetKind.parse(raw).filter(target -> target != TargetKind.BLOCK).map(TargetKind::name).orElse(fallback);
    }

    private static int selectorBodyIndex(List<String> tokens) {
        int index = 2;
        while (index < tokens.size() && tokens.get(index).toLowerCase(Locale.ROOT).startsWith("target:")) {
            index++;
        }
        return index;
    }

    private static String bodyAfter(List<String> tokens, int start) {
        if (tokens.size() <= start) {
            return "";
        }
        return String.join(" ", tokens.subList(start, tokens.size()));
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

    private String cooldownLore(CustomItemDefinition.TriggerCommandDef command, ActionCommandRows.Row row) {
        String prefix = row.block() ? row.nestedLineCount() + " nested lines | " : "";
        if (!command.cooldownEnabled()) {
            return prefix + "Left edit | Right cooldown: Disabled | Shift remove";
        }
        return prefix + "Left edit | Right cooldown: " + command.cooldownTicks() + " ticks | Shift remove";
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

    private List<SelectorOption> actionCatalogOptions(TriggerType type) {
        return actionCatalogOptions(true, GuiContext.from(type));
    }

    private List<SelectorOption> actionCatalogOptions(boolean includeBlocks, GuiContext context) {
        return actionSpecs().stream()
            .filter(spec -> includeBlocks || !spec.block())
            .filter(spec -> actionAllowed(spec.name(), context))
            .map(spec -> new SelectorOption(spec.name(), spec.icon(), spec.name(), spec.group()))
            .toList();
    }

    private boolean actionAllowed(String action, GuiContext context) {
        if (Set.of("SET_BLOCK", "SET_TEMP_BLOCK", "BREAK_BLOCK", "VEINMINE").contains(action)) {
            return context.block();
        }
        if (action.equals("PROJECTILE_TRAIL")) {
            return context.projectile();
        }
        return true;
    }

    private ActionSpec actionSpec(String name) {
        String normalized = canonicalActionName(firstToken(name));
        return actionSpecs().stream().filter(spec -> spec.name().equals(normalized)).findFirst().orElse(null);
    }

    private List<ActionSpec> actionSpecs() {
        return List.of(
            spec("DELAY_TICK", Material.CLOCK, "Flow", false, false, p("ticks", "Ticks", Material.CLOCK, "Enter delay ticks", "20", ParamKind.INTEGER)),
            spec("IF", Material.COMPARATOR, "Flow", false, true, p("condition", "Condition", Material.COMPARATOR, "Enter condition, e.g. {SELF_X}>0", "1=1", ParamKind.TEXT)),
            spec("LOOP_START", Material.REPEATER, "Flow", true, true, p("times", "Times", Material.REPEATER, "Enter loop count", "3", ParamKind.INTEGER), p("delay", "Delay Ticks", Material.CLOCK, "Enter delay ticks between loops", "20", ParamKind.INTEGER)),
            spec("RANDOM_RUN", Material.DROPPER, "Flow", true, true, p("selectionCount", "Selection Count", Material.DROPPER, "Enter number of choices to run", "1", ParamKind.INTEGER)),
            spec("FOR", Material.HOPPER, "Flow", true, true, p("values", "Values", Material.HOPPER, "Enter comma-separated values", "one,two,three", ParamKind.TEXT), p("variable", "Variable", Material.NAME_TAG, "Enter variable name", "VALUE", ParamKind.VARIABLE)),
            spec("AROUND", Material.PLAYER_HEAD, "Selectors", false, true, p("radius", "Radius", Material.PLAYER_HEAD, "Enter radius", "10", ParamKind.DOUBLE), p("target", "Target", Material.PLAYER_HEAD, "Toggle target type", "PLAYER", ParamKind.SELECTOR_TARGET)),
            spec("NEAREST", Material.COMPASS, "Selectors", false, true, p("radius", "Radius", Material.COMPASS, "Enter radius", "10", ParamKind.DOUBLE), p("target", "Target", Material.PLAYER_HEAD, "Toggle target type", "PLAYER", ParamKind.SELECTOR_TARGET)),
            spec("HITSCAN", Material.SPYGLASS, "Selectors", false, true,
                p("distance", "Distance", Material.SPYGLASS, "Enter distance", "32", ParamKind.INTEGER),
                p("target", "Target", Material.PLAYER_HEAD, "Toggle target mode", "ENTITY", ParamKind.HITSCAN_TARGET),
                p("maxHits", "Max Hits", Material.CROSSBOW, "Enter positive integer or all", "1", ParamKind.TEXT),
                p("raySpeed", "Ray Speed", Material.SPECTRAL_ARROW, "Enter blocks per second, or instant", "instant", ParamKind.TEXT),
                p("particle", "Particle", Material.BLAZE_POWDER, "Enter particle, or clear", "", ParamKind.TEXT),
                p("points", "Points", Material.REPEATER, "Enter trail points", "24", ParamKind.INTEGER),
                p("offset", "Offset", Material.SUGAR, "Enter particle offset", "0", ParamKind.DOUBLE),
                p("particleSpeed", "Particle Speed", Material.FEATHER, "Enter particle speed", "0", ParamKind.DOUBLE)),
            spec("HITBOX", Material.MAGMA_CREAM, "Selectors", true, true,
                p("shape", "Shape", Material.MAGMA_CREAM, "Enter SPHERE, CUBE, or CONE", "SPHERE", ParamKind.TEXT),
                p("size", "Size", Material.SLIME_BALL, "Enter size", "3", ParamKind.DOUBLE),
                p("at", "At", Material.COMPASS, "Toggle location", "CURRENT", ParamKind.AT_MODE),
                p("targets", "Targets", Material.PLAYER_HEAD, "Enter PLAYER,MOB,ENTITY,BLOCK", "ENTITY", ParamKind.TEXT),
                p("maxPlayers", "Max Players", Material.PLAYER_HEAD, "Enter max player hits", "50", ParamKind.INTEGER),
                p("maxEntities", "Max Entities", Material.ZOMBIE_HEAD, "Enter max entity hits", "50", ParamKind.INTEGER),
                p("maxBlocks", "Max Blocks", Material.STONE, "Enter max block hits", "64", ParamKind.INTEGER),
                p("particle", "Particle", Material.BLAZE_POWDER, "Enter particle, or clear", "", ParamKind.TEXT),
                p("edgeParticles", "Edge Particles", Material.FIREWORK_STAR, "Toggle edge particles", "false", ParamKind.BOOLEAN),
                p("points", "Points", Material.REPEATER, "Enter particle points", "24", ParamKind.INTEGER)),
            spec("TIMER", Material.CLOCK, "Flow", true, true,
                p("duration", "Duration", Material.CLOCK, "Enter duration ticks", "20", ParamKind.INTEGER),
                p("interval", "Interval", Material.REPEATER, "Enter interval ticks", "1", ParamKind.INTEGER)),
            spec("LAUNCH_PROJECTILE", Material.ARROW, "Entity", false, false,
                p("projectile", "Projectile", Material.ARROW, "Enter projectile type", "ARROW", ParamKind.TEXT),
                p("speed", "Speed", Material.SPECTRAL_ARROW, "Enter speed", "1.5", ParamKind.DOUBLE),
                p("gravity", "Gravity", Material.FEATHER, "Toggle gravity", "true", ParamKind.BOOLEAN),
                p("track", "Track Hits", Material.COMPASS, "Toggle hit tracking", "true", ParamKind.BOOLEAN)),
            spec("DAMAGE", Material.IRON_SWORD, "Entity", false, false, p("amount", "Amount", Material.RED_DYE, "Enter damage amount", "5", ParamKind.DOUBLE), p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "CURRENT", ParamKind.TARGET_MODE), p("damageType", "Type", Material.NETHER_STAR, "Enter normal or true", "NORMAL", ParamKind.TEXT)),
            spec("HEAL", Material.GOLDEN_APPLE, "Entity", false, false, p("amount", "Amount", Material.GOLDEN_APPLE, "Enter heal amount, or leave blank for full heal", "5", ParamKind.DOUBLE), p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "CURRENT", ParamKind.TARGET_MODE)),
            spec("SET_HEALTH", Material.RED_DYE, "Entity", false, false, p("amount", "Amount", Material.RED_DYE, "Enter health amount", "10", ParamKind.DOUBLE), p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "CURRENT", ParamKind.TARGET_MODE)),
            spec("KILL", Material.WITHER_SKELETON_SKULL, "Entity", false, false, p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "CURRENT", ParamKind.TARGET_MODE)),
            spec("BURN", Material.FLINT_AND_STEEL, "Entity", false, false, p("seconds", "Seconds", Material.FLINT_AND_STEEL, "Enter burn seconds", "5", ParamKind.INTEGER), p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "CURRENT", ParamKind.TARGET_MODE)),
            spec("INVULNERABILITY", Material.SHIELD, "Entity", false, false, p("ticks", "Ticks", Material.SHIELD, "Enter invulnerability ticks", "40", ParamKind.INTEGER), p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "CURRENT", ParamKind.TARGET_MODE)),
            spec("TELEPORT", Material.ENDER_PEARL, "Entity", false, false, p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "CURRENT", ParamKind.TARGET_MODE), p("to", "To", Material.COMPASS, "Toggle destination", "COORDS", ParamKind.TELEPORT_TO), p("x", "X", Material.MAP, "Enter X", "{SELF_X}", ParamKind.TEXT), p("y", "Y", Material.MAP, "Enter Y", "{SELF_Y}", ParamKind.TEXT), p("z", "Z", Material.MAP, "Enter Z", "{SELF_Z}", ParamKind.TEXT), p("world", "World", Material.GRASS_BLOCK, "Enter world, or clear", "", ParamKind.TEXT), p("safe", "Safe", Material.SHIELD, "Toggle safe teleport", "false", ParamKind.BOOLEAN)),
            spec("VELOCITY", Material.SLIME_BALL, "Entity", false, false, p("x", "X", Material.SLIME_BALL, "Enter X velocity", "0", ParamKind.DOUBLE), p("y", "Y", Material.SLIME_BALL, "Enter Y velocity", "1", ParamKind.DOUBLE), p("z", "Z", Material.SLIME_BALL, "Enter Z velocity", "0", ParamKind.DOUBLE), p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "CURRENT", ParamKind.TARGET_MODE)),
            spec("DASH", Material.FEATHER, "Entity", false, false, p("strength", "Strength", Material.FEATHER, "Enter dash strength", "1.5", ParamKind.DOUBLE)),
            spec("DAMAGE_ITEM", Material.ANVIL, "Item", false, false, p("amount", "Amount", Material.ANVIL, "Enter damage amount", "1", ParamKind.INTEGER), p("item", "Item", Material.IRON_SWORD, "Enter SELF or material", "SELF", ParamKind.TEXT), p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "SELF", ParamKind.TARGET_MODE)),
            spec("TAKE_ITEM", Material.HOPPER, "Item", false, false, p("item", "Item", Material.HOPPER, "Enter SELF or material", "SELF", ParamKind.TEXT), p("amount", "Amount", Material.CHEST, "Enter amount", "1", ParamKind.INTEGER), p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "SELF", ParamKind.TARGET_MODE)),
            spec("REPAIR_ITEM", Material.GRINDSTONE, "Item", false, false, p("amount", "Amount", Material.GRINDSTONE, "Enter amount or full", "full", ParamKind.TEXT), p("item", "Item", Material.IRON_SWORD, "Enter SELF or material", "SELF", ParamKind.TEXT), p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "SELF", ParamKind.TARGET_MODE)),
            spec("IMPULSE", Material.WIND_CHARGE, "Entity", false, false, p("x", "X", Material.MAP, "Enter origin X", "{SELF_X}", ParamKind.TEXT), p("y", "Y", Material.MAP, "Enter origin Y", "{SELF_Y}", ParamKind.TEXT), p("z", "Z", Material.MAP, "Enter origin Z", "{SELF_Z}", ParamKind.TEXT), p("power", "Power", Material.FEATHER, "Enter power", "1", ParamKind.DOUBLE), p("targets", "Targets", Material.PLAYER_HEAD, "Enter targets", "CURRENT", ParamKind.TEXT), p("radius", "Radius", Material.COMPASS, "Enter radius", "8", ParamKind.DOUBLE), p("normalize", "Normalize", Material.SUGAR, "Toggle normalize", "true", ParamKind.BOOLEAN)),
            spec("EXPLODE", Material.TNT, "Entity", false, false, p("power", "Power", Material.TNT, "Enter explosion power", "2", ParamKind.DOUBLE), p("x", "X", Material.MAP, "Enter X", "{SELF_X}", ParamKind.TEXT), p("y", "Y", Material.MAP, "Enter Y", "{SELF_Y}", ParamKind.TEXT), p("z", "Z", Material.MAP, "Enter Z", "{SELF_Z}", ParamKind.TEXT), p("world", "World", Material.GRASS_BLOCK, "Enter world, or clear", "", ParamKind.TEXT), p("fire", "Fire", Material.FLINT_AND_STEEL, "Toggle fire", "false", ParamKind.BOOLEAN), p("breakBlocks", "Break Blocks", Material.STONE, "Toggle block breaking", "true", ParamKind.BOOLEAN)),
            spec("SEND_MESSAGE", Material.PAPER, "Message", false, false, p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "CURRENT", ParamKind.TARGET_MODE), p("text", "Text", Material.PAPER, "Enter message text", "&aHello", ParamKind.TEXT)),
            spec("ACTIONBAR", Material.OAK_SIGN, "Message", false, false, p("receiver", "Target", Material.PLAYER_HEAD, "Toggle receiver", "CURRENT", ParamKind.TARGET_MODE), p("text", "Text", Material.OAK_SIGN, "Enter actionbar text", "&eReady", ParamKind.TEXT)),
            spec("PARTICLE", Material.BLAZE_POWDER, "Visual", false, false, p("particle", "Particle", Material.BLAZE_POWDER, "Enter particle type", "FLAME", ParamKind.TEXT), p("count", "Count", Material.GUNPOWDER, "Enter count", "20", ParamKind.INTEGER), p("offset", "Offset", Material.SUGAR, "Enter offset", "0.2", ParamKind.DOUBLE), p("speed", "Speed", Material.FEATHER, "Enter speed", "0.01", ParamKind.DOUBLE), p("at", "At", Material.COMPASS, "Toggle location", "CURRENT", ParamKind.AT_MODE), p("shape", "Shape", Material.FIREWORK_STAR, "Enter shape", "POINT", ParamKind.TEXT), p("size", "Size", Material.SLIME_BALL, "Enter shape size", "1", ParamKind.DOUBLE), p("rotation", "Rotation", Material.COMPASS, "Enter x,y,z", "0,0,0", ParamKind.TEXT), p("points", "Points", Material.REPEATER, "Enter shape points", "24", ParamKind.INTEGER)),
            spec("PARTICLE_LINE", Material.BLAZE_ROD, "Visual", false, false, p("particle", "Particle", Material.BLAZE_POWDER, "Enter particle type", "FLAME", ParamKind.TEXT), p("distance", "Distance", Material.SPYGLASS, "Enter distance", "8", ParamKind.DOUBLE), p("points", "Points", Material.REPEATER, "Enter points", "24", ParamKind.INTEGER), p("offset", "Offset", Material.SUGAR, "Enter offset", "0", ParamKind.DOUBLE), p("speed", "Speed", Material.FEATHER, "Enter speed", "0", ParamKind.DOUBLE), p("at", "At", Material.COMPASS, "Toggle location", "CURRENT", ParamKind.AT_MODE)),
            spec("PROJECTILE_TRAIL", Material.FIREWORK_ROCKET, "Visual", true, true,
                p("particle", "Particle", Material.BLAZE_POWDER, "Enter particle, or clear", "FLAME", ParamKind.TEXT),
                p("count", "Count", Material.GUNPOWDER, "Enter particle count", "1", ParamKind.INTEGER),
                p("points", "Points", Material.REPEATER, "Enter interpolation points", "1", ParamKind.INTEGER),
                p("interval", "Interval", Material.CLOCK, "Enter tick interval", "1", ParamKind.INTEGER),
                p("duration", "Duration", Material.REDSTONE, "Enter max ticks", "100", ParamKind.INTEGER),
                p("offset", "Offset", Material.SUGAR, "Enter particle offset", "0", ParamKind.DOUBLE),
                p("speed", "Speed", Material.FEATHER, "Enter particle speed", "0", ParamKind.DOUBLE)),
            spec("SET_BLOCK", Material.STONE, "Block", false, false, p("material", "Material", Material.STONE, "Enter block material", "STONE", ParamKind.TEXT)),
            spec("SET_TEMP_BLOCK", Material.GLOWSTONE, "Block", false, false, p("material", "Material", Material.GLOWSTONE, "Enter block material", "GLOWSTONE", ParamKind.TEXT), p("ticks", "Ticks", Material.CLOCK, "Enter restore ticks", "100", ParamKind.INTEGER)),
            spec("BREAK_BLOCK", Material.IRON_PICKAXE, "Block", false, false, p("drop", "Drop Items", Material.CHEST, "Toggle drops", "true", ParamKind.BOOLEAN)),
            spec("DROPITEM", Material.DIAMOND, "Item", false, false, p("material", "Material", Material.DIAMOND, "Enter item material", "DIAMOND", ParamKind.TEXT), p("amount", "Amount", Material.CHEST, "Enter amount", "1", ParamKind.INTEGER), p("at", "At", Material.COMPASS, "Toggle location", "CURRENT", ParamKind.AT_MODE)),
            spec("VEINMINE", Material.DIAMOND_PICKAXE, "Block", false, false,
                p("limit", "Limit", Material.DIAMOND_PICKAXE, "Enter block limit", "64", ParamKind.INTEGER),
                p("drop", "Drop Items", Material.CHEST, "Toggle drops", "true", ParamKind.BOOLEAN),
                p("filter", "Filter", Material.HOPPER, "Enter material or #tag, or clear", "", ParamKind.TEXT),
                p("match", "Match", Material.COMPASS, "Toggle filter matching", "SAME_TYPE", ParamKind.VEINMINE_MATCH),
                p("mode", "Mode", Material.PISTON, "Toggle mode", "DESTROY", ParamKind.VEINMINE_MODE),
                p("replace", "Replace Block", Material.STONE, "Enter replace block, or clear", "", ParamKind.TEXT),
                p("useEnchants", "Use Enchants", Material.ENCHANTED_BOOK, "Toggle enchant use", "true", ParamKind.BOOLEAN),
                p("useDurability", "Use Durability", Material.ANVIL, "Toggle durability use", "true", ParamKind.BOOLEAN),
                p("effect", "Break Effect", Material.FIREWORK_STAR, "Toggle break effects", "true", ParamKind.BOOLEAN),
                p("xp", "Drop XP", Material.EXPERIENCE_BOTTLE, "Toggle XP drops", "true", ParamKind.BOOLEAN))
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
            Optional<String> invalidAction = ActionValidator.invalidKnownAction(commands.stream().map(CustomItemDefinition.TriggerCommandDef::command).toList(), entry.getKey());
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
        saveAll(menu, player, item);
    }

    private void saveAll(Menu menu, Player player, CustomItemDefinition item) {
        button(menu, 53, Material.LIME_DYE, NamedTextColor.GREEN, "Save All", "Write all current draft changes to YAML", e -> save(player, item));
    }

    private void button(Menu menu, int slot, Material material, NamedTextColor color, String name, String lore) {
        button(menu, slot, material, color, name, lore, e -> {});
    }

    private void button(Menu menu, int slot, Material material, NamedTextColor color, String name, String lore, Consumer<InventoryClickEvent> action) {
        menu.inventory().setItem(slot, TextUtil.button(material, color, name, lore == null ? "" : lore));
        menu.action(slot, action);
    }

    private void button(Menu menu, int slot, Material material, NamedTextColor color, String name, List<String> lore) {
        button(menu, slot, material, color, name, lore, e -> {});
    }

    private void button(Menu menu, int slot, Material material, NamedTextColor color, String name, List<String> lore, Consumer<InventoryClickEvent> action) {
        menu.inventory().setItem(slot, TextUtil.button(material, color, name, lore == null ? new String[0] : lore.toArray(String[]::new)));
        menu.action(slot, action);
    }

    private String rawCommandPrompt(TriggerType type, String action) {
        return rawCommandPrompt(GuiContext.from(type), action + ". Trigger: " + type.name() + ". " + triggerContextSummary(type));
    }

    private String rawCommandPrompt(GuiContext context, String action) {
        return action + ". Variables: " + sortedVariables(context);
    }

    private List<String> triggerVariableLore(TriggerType type) {
        List<String> lore = new ArrayList<>();
        lore.add(triggerContextSummary(type));
        lore.addAll(triggerVariableGroups(type));
        return lore;
    }

    private List<String> triggerVariableGroups(TriggerType type) {
        List<String> groups = new ArrayList<>();
        groups.add("Always: " + variables("SELF", "SELF_UUID", "SELF_WORLD", "SELF_X", "SELF_Y", "SELF_Z", "ITEM_ID", "ITEM_NAME"));
        if (TriggerType.TARGET_TRIGGERS.contains(type)) {
            groups.add("Target: " + variables("TARGET", "TARGET_UUID", "ENTITY", "ENTITY_UUID", "TARGET_WORLD", "TARGET_X", "TARGET_Y", "TARGET_Z"));
        }
        if (TriggerType.BLOCK_TRIGGERS.contains(type)) {
            groups.add("Block: " + variables("BLOCK", "BLOCK_WORLD", "BLOCK_X", "BLOCK_Y", "BLOCK_Z"));
        }
        if (TriggerType.PROJECTILE_TRIGGERS.contains(type)) {
            groups.add("Projectile: " + variables("PROJECTILE", "PROJECTILE_WORLD", "PROJECTILE_X", "PROJECTILE_Y", "PROJECTILE_Z"));
        }
        return groups;
    }

    private static String variables(String... names) {
        return Arrays.stream(names)
            .map(name -> "{" + name + "}")
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }

    private static String sortedVariables(TriggerType type) {
        return sortedVariables(GuiContext.from(type));
    }

    private static String sortedVariables(GuiContext context) {
        Set<String> allowed = new java.util.HashSet<>();
        allowed.addAll(TriggerExecutor.allowedVariables(null));
        if (context.target()) {
            allowed.addAll(List.of("TARGET", "TARGET_UUID", "ENTITY", "ENTITY_UUID", "TARGET_WORLD", "TARGET_X", "TARGET_Y", "TARGET_Z"));
        }
        if (context.block()) {
            allowed.addAll(List.of("BLOCK", "BLOCK_WORLD", "BLOCK_X", "BLOCK_Y", "BLOCK_Z"));
        }
        if (context.projectile()) {
            allowed.addAll(List.of("PROJECTILE", "PROJECTILE_WORLD", "PROJECTILE_X", "PROJECTILE_Y", "PROJECTILE_Z"));
        }
        if (context.hit()) {
            allowed.addAll(List.of("HIT_TYPE", "HIT_WORLD", "HIT_X", "HIT_Y", "HIT_Z"));
        }
        if (context.ticks()) {
            allowed.add("TICKS");
        }
        return allowed.stream()
            .sorted()
            .map(name -> "{" + name + "}")
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }

    private GuiContext bodyContext(TriggerType type, ActionDraft parent) {
        GuiContext base = GuiContext.from(type);
        String name = parent.spec.name();
        if (Set.of("AROUND", "NEAREST").contains(name)) {
            return base.withTarget();
        }
        if (name.equals("HITSCAN")) {
            return parent.value("target").equalsIgnoreCase("BLOCK")
                ? base.withBlock().withHit()
                : base.withTarget().withHit();
        }
        if (name.equals("PROJECTILE_TRAIL")) {
            return base.withProjectile();
        }
        if (name.equals("HITBOX")) {
            return base.withTarget().withBlock().withHit();
        }
        if (name.equals("TIMER")) {
            return base.withTicks();
        }
        return base;
    }

    private static List<String> commandLines(List<CustomItemDefinition.TriggerCommandDef> commands) {
        return commands.stream().map(CustomItemDefinition.TriggerCommandDef::command).toList();
    }

    private static void removeLineRange(List<String> lines, int start, int end) {
        for (int i = Math.min(end, lines.size() - 1); i >= start && i >= 0; i--) {
            lines.remove(i);
        }
    }

    private static String triggerContextSummary(TriggerType type) {
        return switch (type) {
            case RIGHT_CLICK -> "Starts at self. Use RIGHT_CLICK_BLOCK when {BLOCK} is needed.";
            case RIGHT_CLICK_BLOCK -> "Starts at the clicked block. Block context is available.";
            case LEFT_CLICK -> "Starts at self. Use LEFT_CLICK_BLOCK when {BLOCK} is needed.";
            case LEFT_CLICK_BLOCK -> "Starts at the clicked block. Block context is available.";
            case ALL_CLICK -> "Starts at self. Use block-specific click triggers when {BLOCK} is needed.";
            case CONSUME -> "Starts at self after the item is consumed.";
            case BLOCK_BREAK -> "Starts at the broken block. Block context is available.";
            case BLOCK_PLACE -> "Starts at the placed block. Block context is available.";
            case CLICK_ENTITY -> "Starts at self, target = clicked entity, location = target.";
            case CLICK_PLAYER -> "Starts at self, target = clicked player, location = target.";
            case HIT_ENTITY -> "Starts at self, target = hit entity, location = target.";
            case HIT_PLAYER -> "Starts at self, target = hit player, location = target.";
            case HIT_BY_ENTITY -> "Starts at self, target = attacking entity, location = target.";
            case HIT_BY_PLAYER -> "Starts at self, target = attacking player, location = target.";
            case HIT_GLOBAL -> "Starts at self, target = damager, location = target.";
            case KILL_ENTITY -> "Starts at self, target = killed entity, location = target.";
            case KILL_PLAYER -> "Starts at self, target = killed player, location = target.";
            case LAUNCH_PROJECTILE -> "Starts at self. Projectile context is available.";
            case PROJECTILE_HIT_BLOCK -> "Starts at projectile hit block. Projectile and block context are available.";
            case PROJECTILE_HIT_ENTITY -> "Starts at projectile hit entity. Projectile and target context are available.";
            case PROJECTILE_HIT_PLAYER -> "Starts at projectile hit player. Projectile and target context are available.";
            case DROP_SELF -> "Starts at self when the tagged item is dropped.";
            case SELECT_SELF -> "Starts at self when switching onto the tagged item.";
            case DESELECT_SELF -> "Starts at self when switching away from the tagged item.";
            case EQUIP_SELF -> "Starts at self when the tagged item is equipped.";
            case UNEQUIP_SELF -> "Starts at self when the tagged item is unequipped.";
            case ITEM_BREAK -> "Starts at self when the tagged item breaks.";
            case DEATH -> "Starts at self when the player dies with the tagged item in main hand.";
        };
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
        TELEPORT_TO,
        AT_MODE,
        HITSCAN_TARGET,
        SELECTOR_TARGET,
        VEINMINE_MODE,
        VEINMINE_MATCH
    }

    private record ActionParam(String key, String label, Material icon, String prompt, String defaultValue, ParamKind kind) {
    }

    private record ActionSpec(String name, Material icon, String group, List<ActionParam> params, boolean block, boolean hasBody) {
    }

    private record GuiContext(boolean target, boolean block, boolean projectile, boolean hit, boolean ticks) {
        static GuiContext from(TriggerType type) {
            return new GuiContext(
                TriggerType.TARGET_TRIGGERS.contains(type),
                TriggerType.BLOCK_TRIGGERS.contains(type),
                TriggerType.PROJECTILE_TRIGGERS.contains(type),
                false,
                false
            );
        }

        GuiContext withTarget() {
            return new GuiContext(true, block, projectile, hit, ticks);
        }

        GuiContext withBlock() {
            return new GuiContext(target, true, projectile, hit, ticks);
        }

        GuiContext withProjectile() {
            return new GuiContext(target, block, true, hit, ticks);
        }

        GuiContext withHit() {
            return new GuiContext(target, block, projectile, true, ticks);
        }

        GuiContext withTicks() {
            return new GuiContext(target, block, projectile, hit, true);
        }
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
                case "AROUND", "NEAREST" -> List.of(join(name, value("radius"), "target:" + value("target").toUpperCase(Locale.ROOT), chainBody()));
                case "HITSCAN" -> List.of(formatHitscan());
                case "HITBOX" -> block(formatHitbox(), "END_HITBOX");
                case "TIMER" -> block(formatTimer(), "END_TIMER");
                case "LAUNCH_PROJECTILE" -> List.of(formatLaunchProjectile());
                case "DAMAGE" -> List.of(join(name, value("amount"), "type:" + value("damageType").toLowerCase(Locale.ROOT), receiver()));
                case "SET_HEALTH" -> List.of(join(name, value("amount"), receiver()));
                case "HEAL" -> value("amount").isBlank() ? List.of(join(name, receiver())) : List.of(join(name, value("amount"), receiver()));
                case "KILL" -> List.of(join(name, receiver()));
                case "BURN" -> List.of(join(name, value("seconds"), receiver()));
                case "INVULNERABILITY" -> List.of(join(name, value("ticks"), receiver()));
                case "TELEPORT" -> formatTeleport();
                case "VELOCITY" -> List.of(join(name, value("x"), value("y"), value("z"), receiver()));
                case "DASH" -> List.of(join(name, value("strength")));
                case "DAMAGE_ITEM" -> List.of(join(name, value("amount"), "item:" + value("item").toUpperCase(Locale.ROOT), receiver()));
                case "TAKE_ITEM" -> List.of(join(name, value("item").toUpperCase(Locale.ROOT), value("amount"), receiver()));
                case "REPAIR_ITEM" -> List.of(join(name, value("amount"), "item:" + value("item").toUpperCase(Locale.ROOT), receiver()));
                case "IMPULSE" -> List.of(join(name, value("x"), value("y"), value("z"), "power:" + value("power"), "targets:" + value("targets").toUpperCase(Locale.ROOT), "radius:" + value("radius"), "normalize:" + value("normalize").toLowerCase(Locale.ROOT)));
                case "EXPLODE" -> List.of(formatExplode());
                case "SEND_MESSAGE", "ACTIONBAR" -> List.of(join(name, receiver(), value("text")));
                case "PARTICLE" -> List.of(formatParticle());
                case "PARTICLE_LINE" -> List.of(join(name, value("particle").toUpperCase(Locale.ROOT), value("distance"), value("points"), value("offset"), value("speed"), at()));
                case "PROJECTILE_TRAIL" -> block(formatProjectileTrail(), "END_PROJECTILE_TRAIL");
                case "SET_BLOCK" -> List.of(join(name, value("material").toUpperCase(Locale.ROOT)));
                case "SET_TEMP_BLOCK" -> List.of(join(name, value("material").toUpperCase(Locale.ROOT), value("ticks")));
                case "BREAK_BLOCK" -> List.of(name + " drop:" + value("drop").toLowerCase(Locale.ROOT));
                case "DROPITEM" -> List.of(join(name, value("material").toUpperCase(Locale.ROOT), value("amount"), at()));
                case "VEINMINE" -> List.of(formatVeinmine());
                default -> List.of(name);
            };
        }

        private List<String> formatTeleport() {
            String receiver = value("receiver").toUpperCase(Locale.ROOT);
            String to = value("to").toUpperCase(Locale.ROOT);
            if (!to.equals("COORDS")) {
                return List.of("TELEPORT target:" + receiver + " to:" + to + " safe:" + value("safe").toLowerCase(Locale.ROOT));
            }
            String world = value("world");
            return world.isBlank()
                ? List.of(join("TELEPORT", "target:" + receiver, value("x"), value("y"), value("z"), "safe:" + value("safe").toLowerCase(Locale.ROOT)))
                : List.of(join("TELEPORT", "target:" + receiver, value("x"), value("y"), value("z"), world, "safe:" + value("safe").toLowerCase(Locale.ROOT)));
        }

        private String formatHitscan() {
            List<String> parts = new ArrayList<>();
            parts.add("HITSCAN");
            parts.add(value("distance"));
            parts.add("target:" + value("target").toUpperCase(Locale.ROOT));
            parts.add("max-hits:" + value("maxHits").toLowerCase(Locale.ROOT));
            parts.add("speed:" + value("raySpeed").toLowerCase(Locale.ROOT));
            if (!value("particle").isBlank()) {
                parts.add("particle:" + value("particle").toUpperCase(Locale.ROOT));
                parts.add("points:" + value("points"));
                parts.add("offset:" + value("offset"));
                parts.add("particle-speed:" + value("particleSpeed"));
            }
            parts.add(chainBody());
            return join(parts.toArray(String[]::new));
        }

        private String receiver() {
            return "target:" + value("receiver").toUpperCase(Locale.ROOT);
        }

        private String at() {
            return "at:" + value("at").toUpperCase(Locale.ROOT);
        }

        private String formatHitbox() {
            List<String> parts = new ArrayList<>();
            parts.add("HITBOX");
            parts.add("shape:" + value("shape").toUpperCase(Locale.ROOT));
            parts.add("size:" + value("size"));
            parts.add("at:" + value("at").toUpperCase(Locale.ROOT));
            parts.add("targets:" + value("targets").toUpperCase(Locale.ROOT));
            parts.add("max-players:" + value("maxPlayers"));
            parts.add("max-entities:" + value("maxEntities"));
            parts.add("max-blocks:" + value("maxBlocks"));
            if (!value("particle").isBlank()) {
                parts.add("particle:" + value("particle").toUpperCase(Locale.ROOT));
            }
            parts.add("edge-particles:" + value("edgeParticles").toLowerCase(Locale.ROOT));
            parts.add("points:" + value("points"));
            return join(parts.toArray(String[]::new));
        }

        private String formatTimer() {
            return join("TIMER", "duration:" + value("duration"), "interval:" + value("interval"));
        }

        private String formatLaunchProjectile() {
            return join("LAUNCH_PROJECTILE", value("projectile").toUpperCase(Locale.ROOT), "speed:" + value("speed"), "gravity:" + value("gravity").toLowerCase(Locale.ROOT), "track:" + value("track").toLowerCase(Locale.ROOT));
        }

        private String formatParticle() {
            List<String> parts = new ArrayList<>();
            parts.add("PARTICLE");
            parts.add(value("particle").toUpperCase(Locale.ROOT));
            parts.add(value("count"));
            parts.add(value("offset"));
            parts.add(value("speed"));
            parts.add(at());
            parts.add("shape:" + value("shape").toUpperCase(Locale.ROOT));
            parts.add("size:" + value("size"));
            parts.add("rotation:" + value("rotation"));
            parts.add("points:" + value("points"));
            return join(parts.toArray(String[]::new));
        }

        private String formatExplode() {
            List<String> parts = new ArrayList<>();
            parts.add("EXPLODE");
            parts.add(value("power"));
            parts.add(value("x"));
            parts.add(value("y"));
            parts.add(value("z"));
            if (!value("world").isBlank()) {
                parts.add(value("world"));
            }
            parts.add("fire:" + value("fire").toLowerCase(Locale.ROOT));
            parts.add("break-blocks:" + value("breakBlocks").toLowerCase(Locale.ROOT));
            return join(parts.toArray(String[]::new));
        }

        private String formatVeinmine() {
            List<String> parts = new ArrayList<>();
            parts.add("VEINMINE");
            parts.add(value("limit"));
            parts.add("drop:" + value("drop").toLowerCase(Locale.ROOT));
            if (!value("filter").isBlank()) {
                parts.add("filter:" + value("filter"));
            }
            parts.add("match:" + value("match").toLowerCase(Locale.ROOT).replace('_', '-'));
            parts.add("mode:" + value("mode").toLowerCase(Locale.ROOT));
            if (value("mode").equalsIgnoreCase("REPLACE") && !value("replace").isBlank()) {
                parts.add("replace:" + value("replace").toUpperCase(Locale.ROOT));
            }
            parts.add("use-enchants:" + value("useEnchants").toLowerCase(Locale.ROOT));
            parts.add("use-durability:" + value("useDurability").toLowerCase(Locale.ROOT));
            parts.add("effect:" + value("effect").toLowerCase(Locale.ROOT));
            parts.add("xp:" + value("xp").toLowerCase(Locale.ROOT));
            return join(parts.toArray(String[]::new));
        }

        private String formatProjectileTrail() {
            List<String> parts = new ArrayList<>();
            parts.add("PROJECTILE_TRAIL");
            if (!value("particle").isBlank()) {
                parts.add("particle:" + value("particle").toUpperCase(Locale.ROOT));
            }
            parts.add("count:" + value("count"));
            parts.add("points:" + value("points"));
            parts.add("interval:" + value("interval"));
            parts.add("duration:" + value("duration"));
            parts.add("offset:" + value("offset"));
            parts.add("speed:" + value("speed"));
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
                case "HITSCAN" -> "DAMAGE 5";
                case "LOOP_START" -> "PARTICLE CRIT 15 0.2 0.01";
                case "RANDOM_RUN" -> "SEND_MESSAGE &aOption";
                case "FOR" -> "SEND_MESSAGE &e{VALUE}";
                case "PROJECTILE_TRAIL" -> "PARTICLE CRIT 2 0.05 0";
                case "HITBOX" -> "DAMAGE 5";
                case "TIMER" -> "PARTICLE CRIT 1";
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
