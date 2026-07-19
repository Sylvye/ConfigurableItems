package com.bountysmp.configurableitems.item;

import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.util.TextUtil;
import com.bountysmp.configurableitems.util.ValidationUtil;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.DeathProtection;
import io.papermc.paper.datacomponent.item.Repairable;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistrySet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.tag.DamageTypeTags;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ItemFactory {
    private final Plugin plugin;
    private final NamespacedKey itemIdKey;

    public ItemFactory(Plugin plugin) {
        this.plugin = plugin;
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
    }

    public NamespacedKey itemIdKey() {
        return itemIdKey;
    }

    public ItemStack create(CustomItemDefinition definition) {
        return create(definition, 1);
    }

    public ItemStack create(CustomItemDefinition definition, int amount) {
        ItemStack stack = new ItemStack(definition.material(), Math.max(1, Math.min(99, amount)));
        stack.editMeta(meta -> {
            meta.displayName(TextUtil.legacy(definition.customName()));
            meta.lore(TextUtil.legacyLines(definition.lore()));
            meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, definition.id());

            for (CustomItemDefinition.EnchantDef entry : definition.enchantments()) {
                Optional<ValidationUtil.EnchantResult> result = ValidationUtil.enchantment(entry.key());
                result.ifPresent(enchant -> meta.addEnchant(enchant.enchantment(), Math.max(1, entry.level()), true));
            }
            meta.setEnchantmentGlintOverride(definition.glintOverride());

            for (CustomItemDefinition.AttributeDef entry : definition.attributes()) {
                Optional<ValidationUtil.AttributeResult> result = ValidationUtil.attribute(entry.key());
                if (result.isPresent()) {
                    NamespacedKey key = new NamespacedKey(plugin, "attr_" + Math.abs(entry.hashCode()));
                    AttributeModifier modifier = new AttributeModifier(key, entry.value(), entry.operation(), entry.slot());
                    meta.addAttributeModifier(result.get().attribute(), modifier);
                }
            }

            CustomItemDefinition.ExtrasDef extras = definition.extras();
            meta.setUnbreakable(extras.unbreakable);
            if (extras.explosionResistant) {
                meta.setDamageResistant(DamageTypeTags.IS_EXPLOSION);
            } else if (extras.fireResistant) {
                meta.setDamageResistant(DamageTypeTags.IS_FIRE);
            }
            if (extras.maxStackSize != null) {
                meta.setMaxStackSize(Math.max(1, Math.min(99, extras.maxStackSize)));
            }
            if (extras.useRemainder != null && !extras.useRemainder.isBlank()) {
                ValidationUtil.material(extras.useRemainder).ifPresent(material -> meta.setUseRemainder(new ItemStack(material)));
            }
            if (extras.useCooldownSeconds != null && extras.useCooldownSeconds > 0) {
                UseCooldownComponent cooldown = meta.getUseCooldown();
                cooldown.setCooldownSeconds(extras.useCooldownSeconds);
                meta.setUseCooldown(cooldown);
            }
            if (extras.itemModel != null && !extras.itemModel.isBlank()) {
                ValidationUtil.key(extras.itemModel, plugin).ifPresent(meta::setItemModel);
            }

            if (definition.food().enabled) {
                FoodComponent food = meta.getFood();
                food.setNutrition(Math.max(0, definition.food().nutrition));
                food.setSaturation(Math.max(0.0f, definition.food().saturation));
                food.setCanAlwaysEat(definition.food().alwaysEat);
                meta.setFood(food);
            }

            if (definition.tool().enabled) {
                ToolComponent tool = meta.getTool();
                tool.setDefaultMiningSpeed(Math.max(0.0f, definition.tool().defaultSpeed));
                meta.setTool(tool);
            }

            if (definition.equip().enabled) {
                EquippableComponent equip = meta.getEquippable();
                equip.setSlot(definition.equip().slot);
                ValidationUtil.sound(definition.equip().equipSound).ifPresent(sound -> equip.setEquipSound(sound.sound()));
                equip.setDispensable(definition.equip().dispensable);
                equip.setSwappable(definition.equip().swappable);
                equip.setDamageOnHurt(definition.equip().damageOnHurt);
                meta.setEquippable(equip);
            }
            meta.setGlider(definition.equip().enabled && definition.equip().glider);
        });

        if (definition.extras().maxDurability != null && definition.extras().maxDurability > 0) {
            stack.setData(DataComponentTypes.MAX_DAMAGE, definition.extras().maxDurability);
        }
        if (definition.extras().repairItem != null && !definition.extras().repairItem.isBlank()) {
            ValidationUtil.material(definition.extras().repairItem).ifPresent(material -> {
                if (material.asItemType() != null) {
                    stack.setData(DataComponentTypes.REPAIRABLE, Repairable.repairable(RegistrySet.keySetFromValues(RegistryKey.ITEM, List.of(material.asItemType()))));
                }
            });
        }
        applyConsumable(stack, definition);
        applyDeathProtection(stack, definition);
        applyTooltipDisplay(stack, definition);
        return stack;
    }

    public Optional<String> itemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        return id == null || id.isBlank() ? Optional.empty() : Optional.of(id);
    }

    private void applyConsumable(ItemStack stack, CustomItemDefinition definition) {
        if (!definition.food().enabled) {
            return;
        }
        Consumable.Builder builder = Consumable.consumable()
            .consumeSeconds(Math.max(0.05f, definition.food().eatSeconds))
            .animation(animation(definition.food().animation))
            .hasConsumeParticles(true);
        List<ConsumeEffect> effects = consumeEffects(definition.food().consumedEffects);
        if (!effects.isEmpty()) {
            builder.effects(effects);
        }
        stack.setData(DataComponentTypes.CONSUMABLE, builder);
    }

    private void applyDeathProtection(ItemStack stack, CustomItemDefinition definition) {
        if (!definition.equip().enabled || definition.equip().deathEffects.isEmpty()) {
            return;
        }
        List<ConsumeEffect> effects = consumeEffects(definition.equip().deathEffects);
        if (!effects.isEmpty()) {
            stack.setData(DataComponentTypes.DEATH_PROTECTION, DeathProtection.deathProtection(effects));
        }
    }

    private void applyTooltipDisplay(ItemStack stack, CustomItemDefinition definition) {
        Set<DataComponentType> hidden = new HashSet<>();
        if (!definition.showEnchantments()) {
            hidden.add(DataComponentTypes.ENCHANTMENTS);
        }
        if (!definition.showAttributes()) {
            hidden.add(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        }
        if (!definition.extras().showUnbreakable) {
            hidden.add(DataComponentTypes.UNBREAKABLE);
        }
        if (!hidden.isEmpty()) {
            stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hiddenComponents(hidden));
        }
    }

    private List<ConsumeEffect> consumeEffects(List<CustomItemDefinition.EffectDef> definitions) {
        List<PotionEffect> potionEffects = new ArrayList<>();
        float chance = 1.0f;
        for (CustomItemDefinition.EffectDef effect : definitions) {
            Optional<ValidationUtil.PotionResult> result = ValidationUtil.potion(effect.key());
            if (result.isPresent()) {
                PotionEffectType type = result.get().type();
                potionEffects.add(new PotionEffect(type, Math.max(1, effect.seconds()) * 20, Math.max(0, effect.amplifier())));
                chance = Math.min(chance, Math.max(0.0f, Math.min(1.0f, effect.chance())));
            }
        }
        if (potionEffects.isEmpty()) {
            return List.of();
        }
        return List.of(ConsumeEffect.applyStatusEffects(potionEffects, chance));
    }

    private static ItemUseAnimation animation(String raw) {
        try {
            return ItemUseAnimation.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ItemUseAnimation.EAT;
        }
    }

    public static EquipmentSlotGroup groupForSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HAND -> EquipmentSlotGroup.HAND;
            case OFF_HAND -> EquipmentSlotGroup.OFFHAND;
            case FEET -> EquipmentSlotGroup.FEET;
            case LEGS -> EquipmentSlotGroup.LEGS;
            case CHEST -> EquipmentSlotGroup.CHEST;
            case HEAD -> EquipmentSlotGroup.HEAD;
            case BODY -> EquipmentSlotGroup.BODY;
            case SADDLE -> EquipmentSlotGroup.SADDLE;
        };
    }
}
