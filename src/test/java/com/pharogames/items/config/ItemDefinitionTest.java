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
    void vanillaStack_defaultsOff() {
        assertEquals(false, ItemDefinition.builder("x.y", "COOKED_BEEF").build().isVanillaStack());
    }

    @Test
    void vanillaStack_acceptsDefaultInventoryFlags() {
        ItemDefinition def = ItemDefinition.builder("skywars.beef", "COOKED_BEEF")
                .vanillaStack(true).build();
        assertTrue(def.isVanillaStack());
    }

    @Test
    void vanillaStack_rejectsFlagsItCannotCarry() {
        // locked / droppable / movable live in the PDC that vanillaStack strips, so accepting both
        // would hand out an item whose lock does nothing.
        assertThrows(IllegalArgumentException.class, () -> ItemDefinition.builder("x.y", "PAPER")
                .vanillaStack(true).locked(true).build());
        assertThrows(IllegalArgumentException.class, () -> ItemDefinition.builder("x.y", "PAPER")
                .vanillaStack(true).droppable(false).build());
        assertThrows(IllegalArgumentException.class, () -> ItemDefinition.builder("x.y", "PAPER")
                .vanillaStack(true).movable(false).build());
    }

    /**
     * The back-compat guard for the maxDamage / potion.effects additions: a definition that names
     * neither must be byte-identical to what it was before those fields existed, so every config
     * already on the network keeps building the same item.
     */
    @Test
    void newOptionalFields_defaultToAbsent() {
        ItemDefinition def = ItemDefinition.builder("skywars.hunting_bow", "BOW")
                .displayName("<gold>Hunting Bow")
                .build();
        assertEquals(null, def.getMaxDamage(), "maxDamage must stay unset when not configured");
        assertTrue(def.getPotionEffects().isEmpty(), "potionEffects must stay empty when not configured");
        assertEquals(null, def.getPotionType());
    }

    @Test
    void potionEffects_areAnImmutableSnapshot() {
        List<ItemDefinition.PotionEffectDef> mutable = new java.util.ArrayList<>(
                List.of(new ItemDefinition.PotionEffectDef("INVISIBILITY", 180, 0)));
        ItemDefinition def = ItemDefinition.builder("skywars.vanishing_flask", "SPLASH_POTION")
                .potionEffects(mutable).build();
        mutable.clear();
        assertEquals(1, def.getPotionEffects().size(), "definition effects must be an immutable snapshot");
        assertThrows(UnsupportedOperationException.class, () -> def.getPotionEffects().clear());
    }

    @Test
    void potionEffect_convertsSecondsToTicks() {
        // 180s of Invisibility -> 3600 ticks, delivered as written by a SPLASH at the impact point.
        assertEquals(3600, new ItemDefinition.PotionEffectDef("INVISIBILITY", 180, 0).getDurationTicks());
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
