package com.pharogames.items.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ItemConfigLoader#validatePotionEffects(List, String, String, Predicate)}.
 *
 * <p>The effect-type check is injected because resolving a {@code PotionEffectType} goes through the
 * server's effect registry, which does not exist in a unit test. The fake below stands in for it;
 * production passes {@code ItemConfigLoader::resolveEffectType}.
 *
 * <p>Every failure aborts startup for the same reason a bad {@code potion.type} does: an effect that
 * never applied leaves a potion that reads as configured in YAML and does nothing in the hand.
 */
class PotionEffectValidationTest {

    private static final Set<String> REAL_EFFECTS = Set.of("INVISIBILITY", "SPEED", "REGENERATION");
    private static final Predicate<String> EFFECT_EXISTS = REAL_EFFECTS::contains;

    private static Map<String, Object> effect(Object type, Object durationSeconds, Object amplifier) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", type);
        entry.put("durationSeconds", durationSeconds);
        if (amplifier != null) {
            entry.put("amplifier", amplifier);
        }
        return entry;
    }

    @Test
    void parsesEffectAndConvertsSecondsToTicks() {
        List<ItemDefinition.PotionEffectDef> effects = ItemConfigLoader.validatePotionEffects(
                List.of(effect("INVISIBILITY", 240, null)),
                "SPLASH_POTION", "skywars.vanishing_flask", EFFECT_EXISTS);

        assertEquals(1, effects.size());
        ItemDefinition.PotionEffectDef def = effects.get(0);
        assertEquals("INVISIBILITY", def.getType());
        assertEquals(240, def.getDurationSeconds());
        // 240s -> 4800 ticks. A SPLASH potion delivers 0.75x of that at ground zero = 3:00.
        assertEquals(4800, def.getDurationTicks());
        assertEquals(0, def.getAmplifier(), "amplifier defaults to 0 (level I)");
    }

    @Test
    void parsesMultipleEffectsInOrderWithExplicitAmplifier() {
        List<ItemDefinition.PotionEffectDef> effects = ItemConfigLoader.validatePotionEffects(
                List.of(effect("SPEED", 30, 1), effect("REGENERATION", 5, 0)),
                "POTION", "x.y", EFFECT_EXISTS);

        assertEquals(2, effects.size());
        assertEquals("SPEED", effects.get(0).getType());
        assertEquals(1, effects.get(0).getAmplifier());
        assertEquals(600, effects.get(0).getDurationTicks());
        assertEquals("REGENERATION", effects.get(1).getType());
        assertEquals(100, effects.get(1).getDurationTicks());
    }

    @Test
    void normalisesCaseAndSurroundingSpace() {
        List<ItemDefinition.PotionEffectDef> effects = ItemConfigLoader.validatePotionEffects(
                List.of(effect("  invisibility ", 10, null)), "POTION", "x.y", EFFECT_EXISTS);
        assertEquals("INVISIBILITY", effects.get(0).getType());
    }

    @Test
    void absentOrEmptyListYieldsNoEffects() {
        assertEquals(List.of(),
                ItemConfigLoader.validatePotionEffects(null, "COOKED_BEEF", "x.y", EFFECT_EXISTS));
        assertEquals(List.of(),
                ItemConfigLoader.validatePotionEffects(List.of(), "COOKED_BEEF", "x.y", EFFECT_EXISTS));
    }

    @Test
    void acceptsEveryPotionShapedMaterial() {
        for (String material : new String[]{"POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW"}) {
            assertEquals(1, ItemConfigLoader.validatePotionEffects(
                    List.of(effect("SPEED", 10, null)), material, "x.y", EFFECT_EXISTS).size(), material);
        }
    }

    @Test
    void rejectsUnknownEffectType() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionEffects(List.of(effect("SUPER_SNEAK", 10, null)),
                        "POTION", "skywars.bogus", EFFECT_EXISTS));
        assertTrue(e.getMessage().contains("SUPER_SNEAK"), e.getMessage());
        assertTrue(e.getMessage().contains("skywars.bogus"), e.getMessage());
    }

    @Test
    void rejectsEffectsOnNonPotionMaterial() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionEffects(List.of(effect("SPEED", 10, null)),
                        "GLASS_BOTTLE", "skywars.wrong_material", EFFECT_EXISTS));
        assertTrue(e.getMessage().contains("GLASS_BOTTLE"), e.getMessage());
        assertTrue(e.getMessage().contains("skywars.wrong_material"), e.getMessage());
        assertTrue(e.getMessage().contains("SPLASH_POTION"), e.getMessage());
    }

    @Test
    void rejectsMissingOrBlankType() {
        Map<String, Object> noType = new HashMap<>();
        noType.put("durationSeconds", 10);
        assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionEffects(List.of(noType), "POTION", "x.y", EFFECT_EXISTS));
        assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionEffects(List.of(effect("   ", 10, null)),
                        "POTION", "x.y", EFFECT_EXISTS));
    }

    @Test
    void rejectsMissingDuration() {
        Map<String, Object> noDuration = new HashMap<>();
        noDuration.put("type", "SPEED");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionEffects(List.of(noDuration), "POTION", "x.y", EFFECT_EXISTS));
        assertTrue(e.getMessage().contains("durationSeconds"), e.getMessage());
        assertTrue(e.getMessage().contains("x.y"), e.getMessage());
    }

    @Test
    void rejectsNonPositiveDuration() {
        for (int bad : new int[]{0, -1}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                    ItemConfigLoader.validatePotionEffects(List.of(effect("SPEED", bad, null)),
                            "POTION", "skywars.bad_duration", EFFECT_EXISTS));
            assertTrue(e.getMessage().contains("durationSeconds"), e.getMessage());
            assertTrue(e.getMessage().contains("skywars.bad_duration"), e.getMessage());
        }
    }

    /** seconds * 20 must not overflow an int into a negative (instantly expiring) duration. */
    @Test
    void rejectsDurationThatWouldOverflowTicks() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionEffects(List.of(effect("SPEED", 200_000_000, null)),
                        "POTION", "x.y", EFFECT_EXISTS));
        assertTrue(e.getMessage().contains("durationSeconds"), e.getMessage());
    }

    @Test
    void rejectsAmplifierOutsideVanillaByteRange() {
        for (int bad : new int[]{-1, 256}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                    ItemConfigLoader.validatePotionEffects(List.of(effect("SPEED", 10, bad)),
                            "POTION", "skywars.bad_amplifier", EFFECT_EXISTS));
            assertTrue(e.getMessage().contains("amplifier"), e.getMessage());
            assertTrue(e.getMessage().contains("skywars.bad_amplifier"), e.getMessage());
        }
        // The edges themselves are legal.
        assertEquals(0, ItemConfigLoader.validatePotionEffects(List.of(effect("SPEED", 10, 0)),
                "POTION", "x.y", EFFECT_EXISTS).get(0).getAmplifier());
        assertEquals(255, ItemConfigLoader.validatePotionEffects(List.of(effect("SPEED", 10, 255)),
                "POTION", "x.y", EFFECT_EXISTS).get(0).getAmplifier());
    }

    @Test
    void rejectsNonNumericDurationAndAmplifier() {
        assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionEffects(List.of(effect("SPEED", "ten", null)),
                        "POTION", "x.y", EFFECT_EXISTS));
        assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionEffects(List.of(effect("SPEED", 10, 1.5d)),
                        "POTION", "x.y", EFFECT_EXISTS));
    }

    /**
     * The seam the hand-built maps above cannot check: that YAML actually parses an {@code effects:}
     * block into the {@code List<Map>} this validator expects. {@code YamlConfiguration} is pure
     * SnakeYAML plus MemorySection, so it runs headless -- unlike {@code Material}, which does not.
     */
    @Test
    void parsesTheShapeRealConfigYamlProduces() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('\0');   // same as ItemConfigLoader: keeps dotted ids flat
        config.loadFromString(String.join("\n",
                "material: SPLASH_POTION",
                "potion:",
                "  effects:",
                "    - type: INVISIBILITY",
                "      durationSeconds: 240"));

        ConfigurationSection potion = config.getConfigurationSection("potion");
        assertEquals(List.of(), ItemConfigLoader.unknownKeys(
                potion.getKeys(false), ItemConfigLoader.KNOWN_POTION_KEYS));

        List<ItemDefinition.PotionEffectDef> effects = ItemConfigLoader.validatePotionEffects(
                potion.getList("effects"), "SPLASH_POTION", "skywars.vanishing_flask", EFFECT_EXISTS);
        assertEquals(1, effects.size());
        assertEquals("INVISIBILITY", effects.get(0).getType());
        assertEquals(4800, effects.get(0).getDurationTicks());
    }

    @Test
    void rejectsEntryThatIsNotAMap() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ItemConfigLoader.validatePotionEffects(List.of("INVISIBILITY"),
                        "POTION", "skywars.shorthand", EFFECT_EXISTS));
        assertTrue(e.getMessage().contains("skywars.shorthand"), e.getMessage());
    }
}
