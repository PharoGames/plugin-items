package com.pharogames.items.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ItemConfigLoader#validatePotionType(String, String, String)}.
 *
 * <p>Resolving a {@code PotionType} constant needs no running server, so these run headless.
 * The point of each case is that a misconfigured potion aborts startup instead of shipping a
 * stack that renders as an empty "Uncraftable Potion" in a loot chest.
 */
class PotionTypeValidationTest {

    @Test
    void acceptsVanillaTypeOnPotionMaterial() {
        assertEquals("FIRE_RESISTANCE",
                ItemConfigLoader.validatePotionType("FIRE_RESISTANCE", "POTION", "skywars.fire_res"));
        assertEquals("REGENERATION",
                ItemConfigLoader.validatePotionType("REGENERATION", "POTION", "skywars.regen"));
    }

    @Test
    void normalisesCaseAndSurroundingSpace() {
        assertEquals("LONG_FIRE_RESISTANCE",
                ItemConfigLoader.validatePotionType("  long_fire_resistance ", "SPLASH_POTION", "x.y"));
    }

    @Test
    void acceptsEveryPotionShapedMaterial() {
        for (String material : new String[]{"POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW"}) {
            assertEquals("REGENERATION",
                    ItemConfigLoader.validatePotionType("REGENERATION", material, "x.y"), material);
        }
    }

    @Test
    void rejectsUnknownType() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionType("SUPER_SPEED", "POTION", "skywars.bogus"));
        assertTrue(e.getMessage().contains("SUPER_SPEED"), e.getMessage());
        assertTrue(e.getMessage().contains("skywars.bogus"), e.getMessage());
    }

    @Test
    void rejectsMissingOrBlankType() {
        assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionType(null, "POTION", "skywars.blank"));
        assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionType("   ", "POTION", "skywars.blank"));
    }

    @Test
    void rejectsPotionTypeOnNonPotionMaterial() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionType("FIRE_RESISTANCE", "GLASS_BOTTLE", "skywars.wrong_material"));
        assertTrue(e.getMessage().contains("GLASS_BOTTLE"), e.getMessage());
        assertTrue(e.getMessage().contains("POTION"), e.getMessage());
    }

    /**
     * The skew guard: a config that names a field this jar cannot read must leave a log line. An
     * older jar reading the new {@code potion} key is exactly the case that would otherwise ship an
     * empty bottle into a loot chest in silence.
     */
    @Test
    void flagsOnlyKeysTheLoaderDoesNotRead() {
        assertEquals(List.of(), ItemConfigLoader.unknownKeys(List.of(
                "logicalId", "material", "displayName", "lore", "itemModel", "customModelData",
                "enchantmentGlint", "rarity", "maxStackSize", "unbreakable", "enchantments",
                "hideTooltip", "hideAdditionalTooltip", "food", "potion",
                "slot", "locked", "droppable", "movable", "vanillaStack", "metadata")));

        // 'potion' and 'vanillaStack' are readable on this jar, so they must NOT be flagged.
        assertEquals(List.of(), ItemConfigLoader.unknownKeys(List.of("material", "potion", "vanillaStack")));

        // Typos and fields from a newer config are flagged, sorted, and nothing else is.
        assertEquals(List.of("enchantment", "potions"),
                ItemConfigLoader.unknownKeys(List.of("material", "potions", "enchantment")));
    }
}
