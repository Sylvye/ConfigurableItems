package com.bountysmp.configurableitems.restriction;

import com.bountysmp.configurableitems.item.ItemFactory;
import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.storage.ItemRepository;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.block.Crafter;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;

public final class RestrictionListener implements Listener {
    private final ItemFactory itemFactory;
    private final ItemRepository repository;

    public RestrictionListener(ItemFactory itemFactory, ItemRepository repository) {
        this.itemFactory = itemFactory;
        this.repository = repository;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (restricted(event.getItemDrop().getItemStack(), restrictions -> restrictions.cancelDrop)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (restricted(event.getItemInHand(), restrictions -> restrictions.cancelPlacement)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (restricted(event.getItem(), restrictions -> restrictions.cancelConsumption)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToolInteraction(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getItem() == null) {
            return;
        }
        if (isToolInteractionMaterial(event.getItem().getType()) && restricted(event.getItem(), restrictions -> restrictions.cancelToolInteractions)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        if (anyRestricted(inventory.getMatrix(), restrictions -> restrictions.cancelCraft) || restricted(inventory.getResult(), restrictions -> restrictions.cancelCraft)) {
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (anyRestricted(event.getInventory().getMatrix(), restrictions -> restrictions.cancelCraft) || restricted(event.getCurrentItem(), restrictions -> restrictions.cancelCraft)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        boolean blocked = restricted(event.getResult(), restrictions -> restrictions.cancelCraft);
        if (!blocked && event.getBlock().getState() instanceof Crafter crafter) {
            blocked = anyRestricted(crafter.getInventory().getContents(), restrictions -> restrictions.cancelCraft);
        }
        if (blocked) {
            event.setCancelled(true);
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (restricted(event.getItem(), restrictions -> restrictions.cancelEnchantAnvil)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (restricted(event.getItem(), restrictions -> restrictions.cancelEnchantAnvil)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        Inventory inventory = event.getInventory();
        if (anyRestricted(inventory.getContents(), restrictions -> restrictions.cancelEnchantAnvil) || restricted(event.getResult(), restrictions -> restrictions.cancelEnchantAnvil)) {
            event.setResult(null);
        }
    }

    private boolean anyRestricted(ItemStack[] stacks, Predicate<CustomItemDefinition.RestrictionsDef> predicate) {
        for (ItemStack stack : stacks) {
            if (restricted(stack, predicate)) {
                return true;
            }
        }
        return false;
    }

    private boolean restricted(ItemStack stack, Predicate<CustomItemDefinition.RestrictionsDef> predicate) {
        Optional<String> itemId = itemFactory.itemId(stack);
        if (itemId.isEmpty()) {
            return false;
        }
        CustomItemDefinition definition = repository.get(itemId.get());
        return definition != null && predicate.test(definition.restrictions());
    }

    private static boolean isToolInteractionMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_AXE")
            || name.endsWith("_SHOVEL")
            || name.endsWith("_HOE")
            || material == Material.BRUSH
            || material == Material.SHEARS
            || material == Material.FLINT_AND_STEEL
            || material == Material.FIRE_CHARGE;
    }
}
