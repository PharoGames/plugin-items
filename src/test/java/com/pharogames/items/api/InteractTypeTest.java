package com.pharogames.items.api;

import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InteractType#matches(Action, boolean)} across every InteractType,
 * action, and sneak-state combination. Pure logic, no Bukkit server required.
 */
class InteractTypeTest {

    private static final Action[] RIGHT = {Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK};
    private static final Action[] LEFT = {Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK};

    @Test
    void rightClick_matchesOnlyRightClickNotSneaking() {
        for (Action a : RIGHT) {
            assertTrue(InteractType.RIGHT_CLICK.matches(a, false), a + " not sneaking");
            assertFalse(InteractType.RIGHT_CLICK.matches(a, true), a + " sneaking");
        }
        for (Action a : LEFT) {
            assertFalse(InteractType.RIGHT_CLICK.matches(a, false), a + " not sneaking");
        }
    }

    @Test
    void leftClick_matchesOnlyLeftClickNotSneaking() {
        for (Action a : LEFT) {
            assertTrue(InteractType.LEFT_CLICK.matches(a, false), a + " not sneaking");
            assertFalse(InteractType.LEFT_CLICK.matches(a, true), a + " sneaking");
        }
        for (Action a : RIGHT) {
            assertFalse(InteractType.LEFT_CLICK.matches(a, false), a + " not sneaking");
        }
    }

    @Test
    void shiftRightClick_matchesOnlyRightClickSneaking() {
        for (Action a : RIGHT) {
            assertTrue(InteractType.SHIFT_RIGHT_CLICK.matches(a, true), a + " sneaking");
            assertFalse(InteractType.SHIFT_RIGHT_CLICK.matches(a, false), a + " not sneaking");
        }
        for (Action a : LEFT) {
            assertFalse(InteractType.SHIFT_RIGHT_CLICK.matches(a, true), a + " sneaking");
        }
    }

    @Test
    void shiftLeftClick_matchesOnlyLeftClickSneaking() {
        for (Action a : LEFT) {
            assertTrue(InteractType.SHIFT_LEFT_CLICK.matches(a, true), a + " sneaking");
            assertFalse(InteractType.SHIFT_LEFT_CLICK.matches(a, false), a + " not sneaking");
        }
        for (Action a : RIGHT) {
            assertFalse(InteractType.SHIFT_LEFT_CLICK.matches(a, true), a + " sneaking");
        }
    }

    @Test
    void anyRightClick_matchesRightClickRegardlessOfSneak() {
        for (Action a : RIGHT) {
            assertTrue(InteractType.ANY_RIGHT_CLICK.matches(a, false));
            assertTrue(InteractType.ANY_RIGHT_CLICK.matches(a, true));
        }
        for (Action a : LEFT) {
            assertFalse(InteractType.ANY_RIGHT_CLICK.matches(a, false));
            assertFalse(InteractType.ANY_RIGHT_CLICK.matches(a, true));
        }
    }

    @Test
    void anyLeftClick_matchesLeftClickRegardlessOfSneak() {
        for (Action a : LEFT) {
            assertTrue(InteractType.ANY_LEFT_CLICK.matches(a, false));
            assertTrue(InteractType.ANY_LEFT_CLICK.matches(a, true));
        }
        for (Action a : RIGHT) {
            assertFalse(InteractType.ANY_LEFT_CLICK.matches(a, false));
            assertFalse(InteractType.ANY_LEFT_CLICK.matches(a, true));
        }
    }

    @Test
    void anyClick_matchesEveryClickButNotPhysical() {
        for (Action a : RIGHT) {
            assertTrue(InteractType.ANY_CLICK.matches(a, false));
            assertTrue(InteractType.ANY_CLICK.matches(a, true));
        }
        for (Action a : LEFT) {
            assertTrue(InteractType.ANY_CLICK.matches(a, false));
            assertTrue(InteractType.ANY_CLICK.matches(a, true));
        }
        assertFalse(InteractType.ANY_CLICK.matches(Action.PHYSICAL, false));
        assertFalse(InteractType.ANY_CLICK.matches(Action.PHYSICAL, true));
    }

    @Test
    void physicalAction_neverMatchesAnyClickType() {
        for (InteractType type : InteractType.values()) {
            assertFalse(type.matches(Action.PHYSICAL, false), type + " physical");
            assertFalse(type.matches(Action.PHYSICAL, true), type + " physical sneaking");
        }
    }
}
