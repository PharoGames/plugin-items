package com.pharogames.items.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ItemDefinition.Builder} validation and defaults. Pure, no Bukkit server.
 */
class ItemDefinitionTest {

    @Test
    void build_succeedsWithRequiredFields() {
        ItemDefinition def = ItemDefinition.builder("lobby.compass", "COMPASS").build();
        assertEquals("lobby.compass", def.getLogicalId());
        assertEquals("COMPASS", def.getMaterial());
    }

    @Test
    void build_rejectsNullLogicalId() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemDefinition.builder(null, "COMPASS").build());
    }

    @Test
    void build_rejectsBlankLogicalId() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemDefinition.builder("   ", "COMPASS").build());
    }

    @Test
    void build_rejectsNullMaterial() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemDefinition.builder("x.y", null).build());
    }

    @Test
    void build_rejectsBlankMaterial() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemDefinition.builder("x.y", "  ").build());
    }

    @Test
    void defaults_matchDocumentedContract() {
        ItemDefinition def = ItemDefinition.builder("x.y", "STONE").build();
        assertEquals(-1, def.getSlot());          // -1 = next available
        assertTrue(def.isDroppable());            // droppable defaults true
        assertTrue(def.isMovable());              // movable defaults true
        assertEquals(false, def.isLocked());      // locked defaults false
        assertEquals(false, def.isUnbreakable());
        assertTrue(def.getLore().isEmpty());
        assertTrue(def.getEnchantments().isEmpty());
        assertTrue(def.getMetadata().isEmpty());
    }

    @Test
    void loreList_isImmutableCopy() {
        List<String> mutable = new java.util.ArrayList<>(List.of("<gray>line"));
        ItemDefinition def = ItemDefinition.builder("x.y", "STONE").lore(mutable).build();
        mutable.add("mutated");
        assertEquals(1, def.getLore().size(), "definition lore must be an immutable snapshot");
        assertThrows(UnsupportedOperationException.class, () -> def.getLore().add("nope"));
    }

    @Test
    void customModelData_nullListsBecomeEmpty() {
        ItemDefinition.CustomModelDataDef cmd =
                new ItemDefinition.CustomModelDataDef(null, null, null, null);
        assertTrue(cmd.getStrings().isEmpty());
        assertTrue(cmd.getFloats().isEmpty());
        assertTrue(cmd.getFlags().isEmpty());
        assertTrue(cmd.getColors().isEmpty());
    }
}
