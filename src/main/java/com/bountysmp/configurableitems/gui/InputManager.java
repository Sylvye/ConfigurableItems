package com.bountysmp.configurableitems.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

public final class InputManager implements Listener {
    private final Plugin plugin;
    private final Map<UUID, PendingInput> inputs = new HashMap<>();

    public InputManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void prompt(Player player, String prompt, Consumer<String> handler, Runnable cancelHandler) {
        inputs.put(player.getUniqueId(), new PendingInput(handler, cancelHandler));
        player.closeInventory();
        player.sendMessage(Component.text(prompt, NamedTextColor.AQUA));
        player.sendMessage(Component.text("Type cancel or abort to return.", NamedTextColor.GRAY));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        PendingInput input = inputs.remove(event.getPlayer().getUniqueId());
        if (input == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abort")) {
                event.getPlayer().sendMessage(Component.text("Input cancelled.", NamedTextColor.RED));
                input.cancelHandler.run();
                return;
            }
            input.handler.accept(message);
        });
    }

    private record PendingInput(Consumer<String> handler, Runnable cancelHandler) {
    }
}
