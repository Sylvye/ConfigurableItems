package com.bountysmp.configurableitems.trigger;

import com.bountysmp.configurableitems.item.ItemFactory;
import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.model.TriggerType;
import com.bountysmp.configurableitems.storage.ItemRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;

public final class TriggerListener implements Listener {
    private final Plugin plugin;
    private final ItemFactory itemFactory;
    private final ItemRepository repository;
    private final TriggerExecutor executor;
    private final Map<UUID, String> projectileItems = new HashMap<>();
    private final Map<UUID, Long> clickDedup = new HashMap<>();

    public TriggerListener(Plugin plugin, ItemFactory itemFactory, ItemRepository repository, TriggerExecutor executor) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.repository = repository;
        this.executor = executor;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.hasItem()) {
            return;
        }
        Action action = event.getAction();
        if (!shouldHandleInteract(action, event.hasBlock(), event.useInteractedBlock())) {
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            fire(event.getPlayer(), event.getItem(), TriggerType.RIGHT_CLICK, ctx -> {});
            if (event.getClickedBlock() != null) {
                fire(event.getPlayer(), event.getItem(), TriggerType.RIGHT_CLICK_BLOCK, ctx -> ctx.block(event.getClickedBlock()));
            }
            fire(event.getPlayer(), event.getItem(), TriggerType.ALL_CLICK, ctx -> {});
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            fire(event.getPlayer(), event.getItem(), TriggerType.LEFT_CLICK, ctx -> {});
            if (event.getClickedBlock() != null) {
                fire(event.getPlayer(), event.getItem(), TriggerType.LEFT_CLICK_BLOCK, ctx -> ctx.block(event.getClickedBlock()));
            }
            fire(event.getPlayer(), event.getItem(), TriggerType.ALL_CLICK, ctx -> {});
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        fire(event.getPlayer(), event.getItem(), TriggerType.CONSUME, ctx -> {});
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        fire(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand(), TriggerType.BLOCK_BREAK, ctx -> ctx.block(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        fire(event.getPlayer(), event.getItemInHand(), TriggerType.BLOCK_PLACE, ctx -> ctx.block(event.getBlockPlaced()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        TriggerType type = event.getRightClicked() instanceof Player ? TriggerType.CLICK_PLAYER : TriggerType.CLICK_ENTITY;
        fire(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand(), type, ctx -> ctx.target(event.getRightClicked()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = directPlayerDamager(event.getDamager());
        if (damager instanceof Player player) {
            TriggerType type = event.getEntity() instanceof Player ? TriggerType.HIT_PLAYER : TriggerType.HIT_ENTITY;
            fire(player, player.getInventory().getItemInMainHand(), type, ctx -> ctx.target(event.getEntity()));
        }
        if (event.getEntity() instanceof Player victim) {
            ItemStack item = victim.getInventory().getItemInMainHand();
            TriggerType type = damager instanceof Player ? TriggerType.HIT_BY_PLAYER : TriggerType.HIT_BY_ENTITY;
            fire(victim, item, type, ctx -> ctx.target(event.getDamager()));
            fire(victim, item, TriggerType.HIT_GLOBAL, ctx -> ctx.target(event.getDamager()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            fire(player, player.getInventory().getItemInMainHand(), TriggerType.DEATH, ctx -> {});
        }
        Entity killerEntity = event.getDamageSource().getCausingEntity();
        if (killerEntity instanceof Player killer) {
            TriggerType type = event.getEntity() instanceof Player ? TriggerType.KILL_PLAYER : TriggerType.KILL_ENTITY;
            fire(killer, killer.getInventory().getItemInMainHand(), type, ctx -> ctx.target(event.getEntity()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        fire(event.getPlayer(), event.getItemDrop().getItemStack(), TriggerType.DROP_SELF, ctx -> {});
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        ItemStack oldItem = event.getPlayer().getInventory().getItem(event.getPreviousSlot());
        ItemStack newItem = event.getPlayer().getInventory().getItem(event.getNewSlot());
        fire(event.getPlayer(), oldItem, TriggerType.DESELECT_SELF, ctx -> {});
        fire(event.getPlayer(), newItem, TriggerType.SELECT_SELF, ctx -> {});
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent event) {
        fire(event.getPlayer(), event.getBrokenItem(), TriggerType.ITEM_BREAK, ctx -> {});
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEquipment(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        for (EntityEquipmentChangedEvent.EquipmentChange change : event.getEquipmentChanges().values()) {
            fire(player, change.oldItem(), TriggerType.UNEQUIP_SELF, ctx -> {});
            fire(player, change.newItem(), TriggerType.EQUIP_SELF, ctx -> {});
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof Player player)) {
            return;
        }
        ItemStack item = taggedHand(player);
        Optional<String> id = itemFactory.itemId(item);
        if (id.isPresent()) {
            projectileItems.put(event.getEntity().getUniqueId(), id.get());
            fire(player, item, TriggerType.LAUNCH_PROJECTILE, ctx -> ctx.projectile(event.getEntity()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        String itemId = projectileItems.remove(event.getEntity().getUniqueId());
        if (itemId == null || !(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        CustomItemDefinition definition = repository.get(itemId);
        if (definition == null) {
            return;
        }
        ItemStack item = itemFactory.create(definition);
        if (event.getHitBlock() != null) {
            fire(player, item, TriggerType.PROJECTILE_HIT_BLOCK, ctx -> ctx.projectile(event.getEntity()).block(event.getHitBlock()));
        }
        if (event.getHitEntity() != null) {
            TriggerType type = event.getHitEntity() instanceof Player ? TriggerType.PROJECTILE_HIT_PLAYER : TriggerType.PROJECTILE_HIT_ENTITY;
            fire(player, item, type, ctx -> ctx.projectile(event.getEntity()).target(event.getHitEntity()));
        }
    }

    private Entity directPlayerDamager(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    private ItemStack taggedHand(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (itemFactory.itemId(main).isPresent()) {
            return main;
        }
        return player.getInventory().getItemInOffHand();
    }

    private void fire(Player player, ItemStack stack, TriggerType type, ContextMutator mutator) {
        Optional<String> itemId = itemFactory.itemId(stack);
        if (itemId.isEmpty()) {
            return;
        }
        CustomItemDefinition definition = repository.get(itemId.get());
        if (definition == null) {
            return;
        }
        if (!definition.triggers().containsKey(type) || definition.triggers().get(type).isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID dedupKey = new UUID(player.getUniqueId().getMostSignificantBits(), player.getUniqueId().getLeastSignificantBits() ^ type.ordinal());
        Long last = clickDedup.get(dedupKey);
        if (isClickTrigger(type) && last != null && now - last < 50L) {
            return;
        }
        clickDedup.put(dedupKey, now);
        TriggerContext context = new TriggerContext(type, itemId.get(), definition.customName(), player);
        mutator.apply(context);
        executor.fire(context);
    }

    private boolean isClickTrigger(TriggerType type) {
        return type == TriggerType.RIGHT_CLICK
            || type == TriggerType.RIGHT_CLICK_BLOCK
            || type == TriggerType.LEFT_CLICK
            || type == TriggerType.LEFT_CLICK_BLOCK
            || type == TriggerType.ALL_CLICK;
    }

    static boolean shouldHandleInteract(Action action, boolean hasClickedBlock, Event.Result useInteractedBlock) {
        return !(hasClickedBlock
            && (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK)
            && useInteractedBlock == Event.Result.DENY);
    }

    @FunctionalInterface
    private interface ContextMutator {
        void apply(TriggerContext context);
    }
}
