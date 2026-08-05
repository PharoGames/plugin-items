package com.pharogames.items.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ItemConfigLoader#validateMaxDamage(Object, String, String, ToIntFunction)}.
 *
 * <p>The durability lookup is injected because {@code Material.getMaxDurability()} resolves through
 * the item registry on this API version and throws {@code IllegalStateException: No RegistryAccess
 * implementation found} with no server running. The fake below stands in for it, which keeps these
 * tests headless while the production path still asks the real registry.
 *
 * <p>What each case protects: a maxDamage that never applied ships an unbreakable "5-use" bow into a
 * kit, so every failure aborts startup instead of degrading the item in silence.
 */
class MaxDamageValidationTest {

    /** Vanilla durabilities: BOW 384, IRON_SWORD 250; food and blocks are not damageable. */
    private static final ToIntFunction<String> DURABILITY = material -> switch (material) {
        case "BOW" -> 384;
        case "IRON_SWORD" -> 250;
        default -> 0;
    };

    @Test
    void acceptsPositiveValueOnDamageableMaterial() {
        assertEquals(5, ItemConfigLoader.validateMaxDamage(5, "BOW", "skywars.hunting_bow", DURABILITY));
        assertEquals(1, ItemConfigLoader.validateMaxDamage(1, "IRON_SWORD", "x.y", DURABILITY));
    }

    @Test
    void acceptsTheVanillaShortCeiling() {
        assertEquals(32767, ItemConfigLoader.validateMaxDamage(32767, "BOW", "x.y", DURABILITY));
    }

    @Test
    void rejectsZeroAndNegative() {
        for (int bad : new int[]{0, -1, -32768}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                    ItemConfigLoader.validateMaxDamage(bad, "BOW", "skywars.hunting_bow", DURABILITY));
            assertTrue(e.getMessage().contains("skywars.hunting_bow"), e.getMessage());
            assertTrue(e.getMessage().contains(String.valueOf(bad)), e.getMessage());
        }
    }

    @Test
    void rejectsValueAboveTheShortCeiling() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validateMaxDamage(32768, "BOW", "skywars.hunting_bow", DURABILITY));
        assertTrue(e.getMessage().contains("32768"), e.getMessage());
        assertTrue(e.getMessage().contains("skywars.hunting_bow"), e.getMessage());
    }

    @Test
    void rejectsNonDamageableMaterial() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validateMaxDamage(5, "COOKED_BEEF", "skywars.steak", DURABILITY));
        assertTrue(e.getMessage().contains("COOKED_BEEF"), e.getMessage());
        assertTrue(e.getMessage().contains("skywars.steak"), e.getMessage());
        assertTrue(e.getMessage().contains("not damageable"), e.getMessage());
    }

    /**
     * {@code getInt} would turn any of these into 0 and then complain about a "0" the author never
     * wrote, so the loader reads the raw value and names what it actually found.
     */
    @Test
    void rejectsValuesThatAreNotWholeNumbers() {
        for (Object bad : new Object[]{"five", 5.5d, true, null}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                    ItemConfigLoader.validateMaxDamage(bad, "BOW", "skywars.hunting_bow", DURABILITY));
            assertTrue(e.getMessage().contains("whole number"), e.getMessage());
            assertTrue(e.getMessage().contains("skywars.hunting_bow"), e.getMessage());
        }
    }

    /**
     * Locks in the type YAML actually hands the loader for {@code maxDamage: 5}. The validator reads
     * the raw value rather than {@code getInt}, so it matters that a plain YAML integer arrives as an
     * Integer and not, say, a String.
     */
    @Test
    void acceptsTheShapeRealConfigYamlProduces() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('\0');   // same as ItemConfigLoader
        config.loadFromString("material: BOW\nmaxDamage: 5");

        assertEquals(List.of(), ItemConfigLoader.unknownKeys(config.getKeys(false)));
        assertEquals(5, ItemConfigLoader.validateMaxDamage(
                config.get("maxDamage"), "BOW", "skywars.hunting_bow", DURABILITY));
    }

    /** SnakeYAML hands back a Long for a value outside int range; it must not wrap into range. */
    @Test
    void rejectsOversizedLongWithoutOverflowing() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validateMaxDamage(4294967301L, "BOW", "x.y", DURABILITY));
        assertTrue(e.getMessage().contains("4294967301"), e.getMessage());
    }
}
