package com.bountysmp.configurableitems.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;

public final class CustomItemDefinition {
    private String id;
    private Material material = Material.STICK;
    private String customName = "&bNew Item";
    private final List<String> lore = new ArrayList<>();
    private final List<EnchantDef> enchantments = new ArrayList<>();
    private boolean showEnchantments = true;
    private Boolean glintOverride;
    private final List<AttributeDef> attributes = new ArrayList<>();
    private boolean showAttributes = true;
    private FoodDef food = new FoodDef();
    private ToolDef tool = new ToolDef();
    private EquipDef equip = new EquipDef();
    private ExtrasDef extras = new ExtrasDef();
    private final Map<TriggerType, List<String>> triggers = new EnumMap<>(TriggerType.class);

    public CustomItemDefinition(String id) {
        this.id = id;
    }

    public CustomItemDefinition copy() {
        CustomItemDefinition copy = new CustomItemDefinition(id);
        copy.material = material;
        copy.customName = customName;
        copy.lore.addAll(lore);
        enchantments.forEach(e -> copy.enchantments.add(e.copy()));
        copy.showEnchantments = showEnchantments;
        copy.glintOverride = glintOverride;
        attributes.forEach(a -> copy.attributes.add(a.copy()));
        copy.showAttributes = showAttributes;
        copy.food = food.copy();
        copy.tool = tool.copy();
        copy.equip = equip.copy();
        copy.extras = extras.copy();
        triggers.forEach((type, commands) -> copy.triggers.put(type, new ArrayList<>(commands)));
        return copy;
    }

    public String id() {
        return id;
    }

    public void id(String id) {
        this.id = id.toLowerCase(Locale.ROOT);
    }

    public Material material() {
        return material;
    }

    public void material(Material material) {
        this.material = material;
    }

    public String customName() {
        return customName;
    }

    public void customName(String customName) {
        this.customName = customName;
    }

    public List<String> lore() {
        return lore;
    }

    public List<EnchantDef> enchantments() {
        return enchantments;
    }

    public boolean showEnchantments() {
        return showEnchantments;
    }

    public void showEnchantments(boolean showEnchantments) {
        this.showEnchantments = showEnchantments;
    }

    public Boolean glintOverride() {
        return glintOverride;
    }

    public void glintOverride(Boolean glintOverride) {
        this.glintOverride = glintOverride;
    }

    public List<AttributeDef> attributes() {
        return attributes;
    }

    public boolean showAttributes() {
        return showAttributes;
    }

    public void showAttributes(boolean showAttributes) {
        this.showAttributes = showAttributes;
    }

    public FoodDef food() {
        return food;
    }

    public ToolDef tool() {
        return tool;
    }

    public EquipDef equip() {
        return equip;
    }

    public ExtrasDef extras() {
        return extras;
    }

    public Map<TriggerType, List<String>> triggers() {
        return triggers;
    }

    public List<String> commands(TriggerType type) {
        return triggers.computeIfAbsent(type, ignored -> new ArrayList<>());
    }

    public record EnchantDef(String key, int level) {
        public EnchantDef copy() {
            return new EnchantDef(key, level);
        }
    }

    public record AttributeDef(String key, AttributeModifier.Operation operation, double value, EquipmentSlotGroup slot) {
        public AttributeDef copy() {
            return new AttributeDef(key, operation, value, slot);
        }
    }

    public static final class FoodDef {
        public boolean enabled;
        public int nutrition = 1;
        public float saturation = 0.2f;
        public boolean alwaysEat;
        public float eatSeconds = 1.6f;
        public String animation = "EAT";
        public final List<EffectDef> consumedEffects = new ArrayList<>();

        FoodDef copy() {
            FoodDef copy = new FoodDef();
            copy.enabled = enabled;
            copy.nutrition = nutrition;
            copy.saturation = saturation;
            copy.alwaysEat = alwaysEat;
            copy.eatSeconds = eatSeconds;
            copy.animation = animation;
            consumedEffects.forEach(e -> copy.consumedEffects.add(e.copy()));
            return copy;
        }
    }

    public static final class ToolDef {
        public boolean enabled;
        public float defaultSpeed = 1.0f;

        ToolDef copy() {
            ToolDef copy = new ToolDef();
            copy.enabled = enabled;
            copy.defaultSpeed = defaultSpeed;
            return copy;
        }
    }

    public static final class EquipDef {
        public boolean enabled;
        public EquipmentSlot slot = EquipmentSlot.HEAD;
        public boolean glider;
        public String equipSound = "minecraft:item.armor.equip_generic";
        public boolean dispensable = true;
        public boolean swappable = true;
        public boolean damageOnHurt = true;
        public final List<EffectDef> deathEffects = new ArrayList<>();

        EquipDef copy() {
            EquipDef copy = new EquipDef();
            copy.enabled = enabled;
            copy.slot = slot;
            copy.glider = glider;
            copy.equipSound = equipSound;
            copy.dispensable = dispensable;
            copy.swappable = swappable;
            copy.damageOnHurt = damageOnHurt;
            deathEffects.forEach(e -> copy.deathEffects.add(e.copy()));
            return copy;
        }
    }

    public static final class ExtrasDef {
        public boolean unbreakable;
        public boolean showUnbreakable = true;
        public Integer maxDurability;
        public Integer maxStackSize;
        public boolean fireResistant;
        public boolean explosionResistant;
        public String repairItem;
        public String useRemainder;
        public Float useCooldownSeconds;
        public String itemModel;

        ExtrasDef copy() {
            ExtrasDef copy = new ExtrasDef();
            copy.unbreakable = unbreakable;
            copy.showUnbreakable = showUnbreakable;
            copy.maxDurability = maxDurability;
            copy.maxStackSize = maxStackSize;
            copy.fireResistant = fireResistant;
            copy.explosionResistant = explosionResistant;
            copy.repairItem = repairItem;
            copy.useRemainder = useRemainder;
            copy.useCooldownSeconds = useCooldownSeconds;
            copy.itemModel = itemModel;
            return copy;
        }
    }

    public record EffectDef(String key, int seconds, int amplifier, float chance) {
        public EffectDef copy() {
            return new EffectDef(key, seconds, amplifier, chance);
        }
    }
}
