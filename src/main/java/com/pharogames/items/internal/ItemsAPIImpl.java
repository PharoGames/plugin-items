package com.pharogames.items.internal;

import com.pharogames.items.api.GiveOptions;
import com.pharogames.items.api.InteractType;
import com.pharogames.items.api.ItemInteractHandler;
import com.pharogames.items.api.ItemsAPI;
import com.pharogames.items.config.ItemDefinition;
import com.pharogames.items.manager.CustomItemManager;
import com.pharogames.items.manager.InteractionManager;
import com.pharogames.items.registry.ItemRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Concrete implementation of ItemsAPI.
 * Delegates to CustomItemManager, ItemRegistry, and InteractionManager.
 */
public class ItemsAPIImpl implements ItemsAPI {

    private final CustomItemManager itemManager;
    private final ItemRegistry registry;
    private final InteractionManager interactionManager;

    public ItemsAPIImpl(CustomItemManager itemManager, ItemRegistry registry,
                        InteractionManager interactionManager) {
        this.itemManager = itemManager;
        this.registry = registry;
        this.interactionManager = interactionManager;
    }

    // ========== Item Creation ==========

    @Override
    public ItemStack createItem(String logicalId) {
        return itemManager.createItem(logicalId);
    }

    @Override
    public ItemStack createItem(String logicalId, Player player) {
        return itemManager.createItem(logicalId, player);
    }

    // ========== Giving Items ==========

    @Override
    public void giveItem(Player player, String logicalId) {
        itemManager.giveItem(player, logicalId, null);
    }

    @Override
    public void giveItem(Player player, String logicalId, GiveOptions options) {
        itemManager.giveItem(player, logicalId, options);
    }

    // ========== Identification ==========

    @Override
    public boolean isCustomItem(ItemStack item) {
        return itemManager.isCustomItem(item);
    }

    @Override
    public String getLogicalId(ItemStack item) {
        return itemManager.getLogicalId(item);
    }

    // ========== Registry Access ==========

    @Override
    public ItemDefinition getDefinition(String logicalId) {
        return registry.get(logicalId);
    }

    @Override
    public Collection<String> getAllItemIds() {
        return registry.getAllIds();
    }

    // ========== Runtime Registration ==========

    @Override
    public void registerItem(ItemDefinition definition) {
        registry.register(definition);
    }

    @Override
    public void unregisterItem(String logicalId) {
        registry.unregister(logicalId);
    }

    // ========== Interaction Callbacks ==========

    @Override
    public void registerInteraction(String logicalId, InteractType type, ItemInteractHandler handler) {
        interactionManager.register(logicalId, type, handler);
    }

    @Override
    public void unregisterInteractions(String logicalId) {
        interactionManager.unregisterAll(logicalId);
    }
}
