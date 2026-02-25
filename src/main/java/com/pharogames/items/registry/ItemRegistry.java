package com.pharogames.items.registry;

import com.pharogames.items.config.ItemDefinition;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry for item definitions.
 * Thread-safe via ConcurrentHashMap.
 */
public class ItemRegistry {

    private final JavaPlugin plugin;
    private final Map<String, ItemDefinition> items = new ConcurrentHashMap<>();

    public ItemRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers an item definition. Logs a warning if the ID is already taken.
     * Throws if logicalId or material is missing.
     */
    public void register(ItemDefinition definition) {
        if (definition.getLogicalId() == null || definition.getLogicalId().isBlank()) {
            throw new IllegalArgumentException("Item logicalId cannot be null or blank");
        }
        if (items.containsKey(definition.getLogicalId())) {
            plugin.getLogger().warning("[ItemRegistry] Overwriting existing item: " + definition.getLogicalId());
        }
        items.put(definition.getLogicalId(), definition);
    }

    /**
     * Unregisters an item definition by logical ID.
     */
    public void unregister(String logicalId) {
        if (items.remove(logicalId) == null) {
            plugin.getLogger().warning("[ItemRegistry] Attempted to unregister unknown item: " + logicalId);
        }
    }

    /**
     * Returns the definition for the given logical ID, or null if not registered.
     */
    public ItemDefinition get(String logicalId) {
        return items.get(logicalId);
    }

    /**
     * Returns all registered logical IDs (unmodifiable).
     */
    public Collection<String> getAllIds() {
        return Collections.unmodifiableCollection(items.keySet());
    }

    /**
     * Returns all registered item definitions (unmodifiable).
     */
    public Collection<ItemDefinition> getAll() {
        return Collections.unmodifiableCollection(items.values());
    }

    public int size() {
        return items.size();
    }
}
