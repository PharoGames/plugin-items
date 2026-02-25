package com.pharogames.items.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Handler invoked when a player interacts with a custom item.
 * Implementations should be registered via {@code ItemsAPI.registerInteraction}.
 */
@FunctionalInterface
public interface ItemInteractHandler {

    /**
     * Called when a player interacts with the registered custom item.
     *
     * @param player     the player who interacted
     * @param item       the ItemStack held at the time of interaction
     * @param interactType the resolved interact type that triggered this handler
     */
    void onInteract(Player player, ItemStack item, InteractType interactType);
}
