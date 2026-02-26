package com.pharogames.items.api;

import org.bukkit.event.block.Action;

/**
 * Describes which player interaction types trigger an item callback.
 */
public enum InteractType {
    RIGHT_CLICK,
    LEFT_CLICK,
    SHIFT_RIGHT_CLICK,
    SHIFT_LEFT_CLICK,
    ANY_RIGHT_CLICK,
    ANY_LEFT_CLICK,
    ANY_CLICK;

    /**
     * Returns true if this InteractType matches the given Bukkit action and sneak state.
     */
    public boolean matches(Action action, boolean isSneaking) {
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;

        return switch (this) {
            case RIGHT_CLICK -> rightClick && !isSneaking;
            case LEFT_CLICK -> leftClick && !isSneaking;
            case SHIFT_RIGHT_CLICK -> rightClick && isSneaking;
            case SHIFT_LEFT_CLICK -> leftClick && isSneaking;
            case ANY_RIGHT_CLICK -> rightClick;
            case ANY_LEFT_CLICK -> leftClick;
            case ANY_CLICK -> rightClick || leftClick;
        };
    }
}
