package com.pharogames.items.gui;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;

/**
 * Standard item models for inventory navigation (back, previous/next page).
 */
public final class GuiNavigationItemModels {

    public static final Key LEFT_ARROW = Key.key("pharogames:gui_left_arrow");
    public static final Key RIGHT_ARROW = Key.key("pharogames:gui_right_arrow");

    private GuiNavigationItemModels() {}

    public static void applyLeftArrow(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        item.setData(DataComponentTypes.ITEM_MODEL, LEFT_ARROW);
    }

    public static void applyRightArrow(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        item.setData(DataComponentTypes.ITEM_MODEL, RIGHT_ARROW);
    }
}
