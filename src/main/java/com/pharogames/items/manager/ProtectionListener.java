package com.pharogames.items.manager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Enforces per-item inventory protection flags baked into each item's PDC.
 *
 * <ul>
 *   <li><b>locked</b>   – item cannot be moved between slots in any inventory</li>
 *   <li><b>droppable</b> – item cannot be dropped by the player</li>
 *   <li><b>movable</b>  – item cannot be moved within an inventory (weaker than locked)</li>
 * </ul>
 *
 * The flags are read directly from the ItemStack's PDC (self-describing items).
 */
public class ProtectionListener implements Listener {

    private final CustomItemManager itemManager;

    public ProtectionListener(CustomItemManager itemManager) {
        this.itemManager = itemManager;
    }

    // ========================== Drop prevention ==========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (itemManager.isCustomItem(dropped) && !itemManager.isDroppable(dropped)) {
            event.setCancelled(true);
        }
    }

    // ========================== Click protection ==========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Check the item currently in the clicked slot
        ItemStack current = event.getCurrentItem();
        if (shouldBlock(current)) {
            event.setCancelled(true);
            return;
        }

        // Also check the cursor item (player dragging something onto a locked slot)
        ItemStack cursor = event.getCursor();
        if (shouldBlock(cursor)) {
            event.setCancelled(true);
            return;
        }

        // Check hotbar swap target (number key swap)
        if (event.getHotbarButton() >= 0) {
            ItemStack hotbarItem = event.getView().getBottomInventory().getItem(event.getHotbarButton());
            if (shouldBlock(hotbarItem)) {
                event.setCancelled(true);
            }
        }
    }

    // ========================== Drag protection ==========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        for (ItemStack item : event.getNewItems().values()) {
            if (shouldBlock(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ========================== Off-hand swap prevention ==========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (shouldBlock(event.getMainHandItem()) || shouldBlock(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    // ========================== Helpers ==========================

    /**
     * Returns true if the item should be blocked from moving (locked or not movable).
     */
    private boolean shouldBlock(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!itemManager.isCustomItem(item)) return false;
        return itemManager.isLocked(item) || !itemManager.isMovable(item);
    }
}
