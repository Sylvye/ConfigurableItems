package com.bountysmp.configurableitems.command;

import com.bountysmp.configurableitems.gui.GuiManager;
import com.bountysmp.configurableitems.item.ItemFactory;
import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.storage.ItemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class ConfigurableItemsCommand implements CommandExecutor, TabCompleter {
    public static final String PERMISSION = "configurableitems.admin";
    public static final String DENIED = "You do not have permission to use ConfigurableItems";

    private final ItemRepository repository;
    private final ItemFactory itemFactory;
    private final GuiManager guiManager;

    public ConfigurableItemsCommand(ItemRepository repository, ItemFactory itemFactory, GuiManager guiManager) {
        this.repository = repository;
        this.itemFactory = itemFactory;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text(DENIED, NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can open the GUI.", NamedTextColor.RED));
                return true;
            }
            guiManager.openMain(player, 0);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                repository.load();
                sender.sendMessage(Component.text("Reloaded " + repository.all().size() + " configurable items.", NamedTextColor.GREEN));
                return true;
            }
            case "give" -> {
                return give(sender, args);
            }
            default -> {
                sender.sendMessage(Component.text("Usage: /" + label + " [give <id> [player] [amount]|reload]", NamedTextColor.GRAY));
                return true;
            }
        }
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /ci give <id> [player] [amount]", NamedTextColor.GRAY));
            return true;
        }
        CustomItemDefinition item = repository.get(args[1]);
        if (item == null) {
            sender.sendMessage(Component.text("Unknown item id: " + args[1], NamedTextColor.RED));
            return true;
        }
        Player target;
        int amount;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
                return true;
            }
            amount = args.length >= 4 ? parseAmount(args[3]) : 1;
        } else if (sender instanceof Player player) {
            target = player;
            amount = 1;
        } else {
            sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
            return true;
        }
        target.getInventory().addItem(itemFactory.create(item, amount));
        sender.sendMessage(Component.text("Gave " + amount + "x " + item.id() + " to " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    private static int parseAmount(String raw) {
        try {
            return Math.max(1, Math.min(99, Integer.parseInt(raw)));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return prefix(args[0], List.of("give", "reload"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return prefix(args[1], repository.all().stream().map(CustomItemDefinition::id).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return prefix(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return List.of();
    }

    private static List<String> prefix(String raw, List<String> values) {
        String prefix = raw.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(value);
            }
        }
        return matches;
    }
}
