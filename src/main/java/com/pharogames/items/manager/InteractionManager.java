package com.pharogames.items.manager;

import com.pharogames.items.api.InteractType;
import com.pharogames.items.api.ItemInteractHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Listens to {@link PlayerInteractEvent} and dispatches calls to registered
 * {@link ItemInteractHandler} callbacks.
 *
 * Handlers are keyed by {@code logicalId + InteractType} so multiple handlers
 * can coexist for the same item with different click types.
 */
public class InteractionManager implements Listener {

    private final CustomItemManager itemManager;
    private final Logger logger;

    /**
     * Map of logicalId -> list of (InteractType, handler) pairs.
     * ConcurrentHashMap for thread-safety on registration; list mutations are synchronised.
     */
    private final Map<String, List<HandlerEntry>> handlers = new ConcurrentHashMap<>();

    public InteractionManager(CustomItemManager itemManager, Logger logger) {
        this.itemManager = itemManager;
        this.logger = logger;
    }

    // ========================== Registration ==========================

    public void register(String logicalId, InteractType type, ItemInteractHandler handler) {
        handlers.computeIfAbsent(logicalId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new HandlerEntry(type, handler));
    }

    public void unregisterAll(String logicalId) {
        handlers.remove(logicalId);
    }

    // ========================== Event handling ==========================

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only process main hand to avoid double-firing
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action == Action.PHYSICAL) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        String logicalId = itemManager.getLogicalId(item);
        if (logicalId == null) return;

        List<HandlerEntry> entries = handlers.get(logicalId);
        if (entries == null || entries.isEmpty()) return;

        boolean isSneaking = event.getPlayer().isSneaking();

        for (HandlerEntry entry : new ArrayList<>(entries)) {
            if (entry.type.matches(action, isSneaking)) {
                try {
                    entry.handler.onInteract(event.getPlayer(), item, entry.type);
                } catch (Exception e) {
                    logger.severe("[Items] Exception in interaction handler for '" + logicalId + "': " + e.getMessage());
                }
            }
        }
    }

    // ========================== Internal ==========================

    private record HandlerEntry(InteractType type, ItemInteractHandler handler) {}
}
