package com.pharogames.items.manager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Enforces per-item inventory protection flags baked into each item's PDC.
 *
 * <ul>
 *   <li><b>locked</b>   – item cannot be moved between slots in any inventory</li>
 *   <li><b>droppable</b> – item cannot be dropped by the player</li>
 *   <li><b>movable</b>  – item cannot be moved within an inventory (weaker than locked)</li>
 *   <li>locked / non-movable items also cannot be used to place blocks in the world</li>
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

    // ========================== Block placement ==========================

    /**
     * Prevents placing blocks with locked lobby-style items (e.g. CHEST used as a cosmetics button).
     * Same rules as inventory protection: locked or non-movable custom items cannot be placed.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (shouldBlock(item)) {
            event.setCancelled(true);
        }
    }

    // ========================== Entity Interactions ==========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (shouldBlock(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (shouldBlock(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    // ========================== Block Interactions ==========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (shouldBlock(item)) {
            Block block = event.getClickedBlock();
            if (block != null) {
                Material type = block.getType();
                // Blocks that can consume or store items on right click
                if (type == Material.FLOWER_POT ||
                    type == Material.JUKEBOX ||
                    type == Material.COMPOSTER ||
                    type == Material.CAMPFIRE ||
                    type == Material.SOUL_CAMPFIRE ||
                    type == Material.CHISELED_BOOKSHELF ||
                    type == Material.DECORATED_POT ||
                    type == Material.LODESTONE ||
                    type == Material.RESPAWN_ANCHOR ||
                    type == Material.END_PORTAL_FRAME ||
                    type.name().endsWith("_CAULDRON") ||
                    type == Material.CAVE_VINES ||
                    type == Material.CAVE_VINES_PLANT ||
                    type == Material.SWEET_BERRY_BUSH) {
                    event.setUseItemInHand(Event.Result.DENY);
                }
            }
        }
    }

    // ========================== Death drops ==========================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        java.util.Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack item = it.next();
            if (shouldBlock(item)) {
                it.remove();
                event.getItemsToKeep().add(item);
            }
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
