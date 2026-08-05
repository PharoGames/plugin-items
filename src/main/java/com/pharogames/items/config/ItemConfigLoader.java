package com.pharogames.items.config;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.logging.Level;

/**
 * Loads ItemDefinitions from the plugin's YAML config.
 *
 * Config is read from {@code plugins/Items/config.yml} (the standard Bukkit data folder).
 * The configloader init container writes this file from the config-service before the
 * Minecraft server starts (manifest entry: plugin="Items", filename="config.yml").
 * If the file does not exist, the bundled {@code config.yml} from the JAR is used as a default.
 */
public class ItemConfigLoader {

    /** The only materials whose ItemMeta is a PotionMeta, i.e. that can hold a base potion type. */
    private static final Set<String> POTION_MATERIALS =
            Set.of("POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW");

    /** Vanilla stores durability as a short, so a larger MAX_DAMAGE cannot round-trip. */
    private static final int MAX_DAMAGE_CEILING = 32767;

    /** Above this the seconds -> ticks conversion overflows an int and yields a negative duration. */
    private static final int MAX_EFFECT_SECONDS = Integer.MAX_VALUE / 20;

    /** Vanilla amplifier is a byte: 0 = level I, 255 = level CCLVI. */
    private static final int MAX_EFFECT_AMPLIFIER = 255;

    /**
     * Every key {@link #parseItem} reads. Anything else in an item's section is either a typo
     * ({@code enchantment} for {@code enchantments}) or a field a NEWER config expects from an
     * OLDER jar — both used to be dropped in silence, which is how a potion definition delivered
     * ahead of its jar becomes an empty bottle in a loot chest with nothing in the logs.
     */
    private static final Set<String> KNOWN_KEYS = Set.of(
            "logicalId", "material", "displayName", "lore", "itemModel", "customModelData",
            "enchantmentGlint", "rarity", "maxStackSize", "maxDamage", "unbreakable", "enchantments",
            "hideTooltip", "hideAdditionalTooltip", "food", "potion",
            "slot", "locked", "droppable", "movable", "vanillaStack", "metadata");

    /**
     * Every key read inside an item's {@code potion} section. Checked separately from
     * {@link #KNOWN_KEYS} because {@link #unknownKeys} only ever sees one section's direct children,
     * so a typo nested one level down ({@code effect:} for {@code effects:}) would otherwise be
     * invisible — the exact silence the top-level warning exists to close.
     */
    static final Set<String> KNOWN_POTION_KEYS = Set.of("type", "effects");

    private final JavaPlugin plugin;

    public ItemConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all item definitions from config.
     * Returns an empty list and logs an error if config cannot be read.
     */
    public Collection<ItemDefinition> loadAll() {
        FileConfiguration config = loadConfig();
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            plugin.getLogger().info("No 'items' section found in config -- no items registered.");
            return Collections.emptyList();
        }

        List<ItemDefinition> definitions = new ArrayList<>();
        for (String logicalId : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(logicalId);
            if (section == null) {
                plugin.getLogger().warning("Item '" + logicalId + "' has invalid config section, skipping.");
                continue;
            }
            try {
                definitions.add(parseItem(logicalId, section));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to parse item '" + logicalId + "': " + e.getMessage() +
                        " -- server will abort.", e);
                throw new IllegalStateException("Invalid item definition '" + logicalId + "': " + e.getMessage(), e);
            }
        }
        plugin.getLogger().info("Loaded " + definitions.size() + " item definitions from config.");
        return definitions;
    }

    /**
     * Validates a {@code potion.type} value and returns it normalised to the enum constant's case.
     *
     * <p>Both failure modes throw so the fail-fast {@link #loadAll()} path aborts server start: a
     * potion whose type never applied would look configured in YAML and behave like an empty
     * "Uncraftable Potion" in game, which is the kind of silent wrong-loot that only surfaces mid-match.
     *
     * @param rawType    the configured type, case-insensitive
     * @param material   the item's material, used to reject a potion type on a non-potion stack
     * @param logicalId  the item id, for the error message
     */
    static String validatePotionType(String rawType, String material, String logicalId) {
        if (rawType == null || rawType.isBlank()) {
            throw new IllegalArgumentException("'potion.type' is required when a 'potion' section is present "
                    + "for item '" + logicalId + "'");
        }
        String normalised = rawType.trim().toUpperCase();
        try {
            PotionType.valueOf(normalised);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown potion type '" + rawType + "' for item '" + logicalId
                    + "' -- not a Bukkit PotionType constant (e.g. FIRE_RESISTANCE, LONG_FIRE_RESISTANCE, "
                    + "REGENERATION, STRONG_REGENERATION).", e);
        }
        // A base potion type only lives on a stack whose meta is a PotionMeta. Anywhere else it is
        // dropped at give-time, so reject it here instead of shipping an item that reads as
        // configured and behaves vanilla.
        if (material == null || !POTION_MATERIALS.contains(material.trim().toUpperCase())) {
            throw new IllegalArgumentException("Item '" + logicalId + "' sets 'potion.type' but its material is '"
                    + material + "'; a base potion type applies only to POTION, SPLASH_POTION, "
                    + "LINGERING_POTION, TIPPED_ARROW.");
        }
        return normalised;
    }

    /**
     * Validates a {@code maxDamage} value and returns it as an int.
     *
     * <p>Throws on every failure so the fail-fast {@link #loadAll()} path aborts server start:
     * durability is the whole point of the field (a 5-use bow is balance, not decoration), so a
     * value that silently did nothing would ship an infinite bow into a kit.
     *
     * @param rawValue        the configured value, straight out of YAML and not yet typed
     * @param material        the item's material, used to reject durability on a non-damageable item
     * @param logicalId       the item id, for the error message
     * @param maxDurabilityOf resolves a material name to its vanilla max durability (0 = not
     *                        damageable). Injected because {@code Material.getMaxDurability()} is
     *                        registry-backed on this API and throws with no server running.
     */
    static int validateMaxDamage(Object rawValue, String material, String logicalId,
                                 ToIntFunction<String> maxDurabilityOf) {
        long value = wholeNumber(rawValue, "maxDamage", logicalId);
        if (value < 1 || value > MAX_DAMAGE_CEILING) {
            throw new IllegalArgumentException("'maxDamage' must be between 1 and " + MAX_DAMAGE_CEILING
                    + " for item '" + logicalId + "', got: " + value);
        }
        // Durability only exists on an item type that can take damage. On anything else the
        // component is dropped at give-time, so reject it here rather than shipping an item that
        // reads as configured and behaves vanilla.
        if (maxDurabilityOf.applyAsInt(material) <= 0) {
            throw new IllegalArgumentException("Item '" + logicalId + "' sets 'maxDamage' but its material '"
                    + material + "' is not damageable; durability applies only to tools, weapons, armour "
                    + "and other item types with a vanilla durability bar.");
        }
        return (int) value;
    }

    /**
     * Validates a {@code potion.effects} list and returns it as definitions, or an empty list when
     * no effects are configured.
     *
     * <p>Throws on every failure, for the same reason {@link #validatePotionType} does: an effect
     * that never applied leaves a potion that looks configured in YAML and does nothing in the hand.
     *
     * @param rawEffects       the configured list, straight out of YAML
     * @param material         the item's material, used to reject effects on a non-potion stack
     * @param logicalId        the item id, for the error message
     * @param effectTypeExists tells whether an upper-case effect name is a real
     *                         {@code PotionEffectType}. Injected because that lookup is
     *                         registry-backed and needs a running server.
     */
    static List<ItemDefinition.PotionEffectDef> validatePotionEffects(
            List<?> rawEffects, String material, String logicalId, Predicate<String> effectTypeExists) {
        if (rawEffects == null || rawEffects.isEmpty()) {
            return List.of();
        }
        if (material == null || !POTION_MATERIALS.contains(material.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Item '" + logicalId + "' sets 'potion.effects' but its material is '"
                    + material + "'; custom effects apply only to POTION, SPLASH_POTION, LINGERING_POTION, "
                    + "TIPPED_ARROW.");
        }

        List<ItemDefinition.PotionEffectDef> effects = new ArrayList<>();
        for (Object raw : rawEffects) {
            if (!(raw instanceof Map<?, ?> entry)) {
                throw new IllegalArgumentException("Every 'potion.effects' entry must be a map of "
                        + "{type, durationSeconds, amplifier} for item '" + logicalId + "', got: " + raw);
            }

            Object rawType = entry.get("type");
            if (rawType == null || rawType.toString().isBlank()) {
                throw new IllegalArgumentException("'potion.effects[].type' is required for item '"
                        + logicalId + "'");
            }
            String type = rawType.toString().trim().toUpperCase(Locale.ROOT);
            if (!effectTypeExists.test(type)) {
                throw new IllegalArgumentException("Unknown potion effect type '" + rawType + "' for item '"
                        + logicalId + "' -- not a Bukkit PotionEffectType constant (e.g. INVISIBILITY, SPEED, "
                        + "REGENERATION, FIRE_RESISTANCE).");
            }

            Object rawDuration = entry.get("durationSeconds");
            if (rawDuration == null) {
                throw new IllegalArgumentException("'durationSeconds' is required on the '" + type
                        + "' entry of 'potion.effects' for item '" + logicalId + "'");
            }
            long duration = wholeNumber(rawDuration, "potion.effects[" + type + "].durationSeconds", logicalId);
            if (duration < 1 || duration > MAX_EFFECT_SECONDS) {
                throw new IllegalArgumentException("'durationSeconds' on the '" + type + "' entry of "
                        + "'potion.effects' for item '" + logicalId + "' must be between 1 and "
                        + MAX_EFFECT_SECONDS + " seconds, got: " + duration);
            }

            long amplifier = 0;
            if (entry.get("amplifier") != null) {
                amplifier = wholeNumber(entry.get("amplifier"), "potion.effects[" + type + "].amplifier", logicalId);
                if (amplifier < 0 || amplifier > MAX_EFFECT_AMPLIFIER) {
                    throw new IllegalArgumentException("'amplifier' on the '" + type + "' entry of "
                            + "'potion.effects' for item '" + logicalId + "' must be between 0 and "
                            + MAX_EFFECT_AMPLIFIER + " (0 = level I), got: " + amplifier);
                }
            }

            effects.add(new ItemDefinition.PotionEffectDef(type, (int) duration, (int) amplifier));
        }
        return List.copyOf(effects);
    }

    /**
     * The {@code potion.effects} list, or null when the key is absent.
     *
     * <p>Exists because {@code getList} returns null for anything that is not a YAML list, and the
     * two shapes an author is most likely to reach for by mistake are both non-lists: a bare scalar
     * ({@code effects: INVISIBILITY}), and the map-of-maps that every OTHER section in this config
     * uses ({@code enchantments}, {@code food}, {@code metadata}, {@code customModelData}).
     *
     * <p>Left unguarded that null silently disables the whole effects block, and because
     * {@code effects} IS a known potion key the unrecognised-key WARN cannot fire either — so the
     * item builds with its base type, no custom contents, and no log line anywhere. An invisibility
     * flask that reads as configured in YAML and does nothing in the hand is exactly the silent
     * degradation the rest of this validation exists to prevent, so this throws instead.
     */
    static List<?> requireEffectsList(ConfigurationSection potionSection, String logicalId) {
        List<?> rawEffects = potionSection.getList("effects");
        if (rawEffects == null && potionSection.contains("effects")) {
            throw new IllegalArgumentException("'potion.effects' must be a YAML LIST of "
                    + "{type, durationSeconds, amplifier} entries for item '" + logicalId
                    + "', got: " + potionSection.get("effects") + ". Write it as a list:\n"
                    + "  potion:\n    effects:\n      - type: INVISIBILITY\n"
                    + "        durationSeconds: 180");
        }
        return rawEffects;
    }

    /**
     * Reads a YAML value that must be a whole number. SnakeYAML hands back an Integer or a Long for
     * one; anything else (a quoted string, a decimal) is a config error worth naming, because
     * {@code getInt} would quietly turn it into 0 and fail a range check with a value the author
     * never wrote.
     */
    private static long wholeNumber(Object rawValue, String field, String logicalId) {
        if (rawValue instanceof Integer || rawValue instanceof Long
                || rawValue instanceof Short || rawValue instanceof Byte) {
            return ((Number) rawValue).longValue();
        }
        throw new IllegalArgumentException("'" + field + "' must be a whole number for item '"
                + logicalId + "', got: " + rawValue);
    }

    /**
     * Resolves a {@code PotionEffectType} constant name (or {@code namespace:key}) to the effect,
     * returning null when it names nothing. Both callers turn that null into an actionable message:
     * the loader aborts start, the item manager warns and skips the effect.
     *
     * <p>Registry-backed, so it only works with a running server — never call it from a test.
     */
    public static PotionEffectType resolveEffectType(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        try {
            NamespacedKey namespacedKey = key.contains(":")
                    ? NamespacedKey.fromString(key)
                    : NamespacedKey.minecraft(key);
            return namespacedKey != null ? Registry.MOB_EFFECT.get(namespacedKey) : null;
        } catch (IllegalArgumentException e) {
            // Not even a well-formed key (spaces, illegal characters) -- same outcome as unknown.
            return null;
        }
    }

    /** Vanilla max durability of a material name, or 0 when the material is not damageable. */
    private static int vanillaMaxDurability(String material) {
        Material resolved = Material.matchMaterial(material);
        return resolved != null ? resolved.getMaxDurability() : 0;
    }

    private FileConfiguration loadConfig() {
        // The configloader writes to plugins/Items/config.yml in the server working dir.
        // saveDefaultConfig() writes the bundled config.yml to the same location if absent.
        plugin.saveDefaultConfig();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        plugin.getLogger().info("Loading items config from " + configFile.getPath());

        // Bukkit uses '.' as a path separator by default, which breaks logical IDs
        // that use dot-namespacing (e.g. "lobby.start_game" → nested "lobby" → "start_game").
        // Using a NUL separator preserves dotted keys as flat string keys.
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('\0');
        try {
            config.load(configFile);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load items config: " + configFile.getPath(), e);
        }
        return config;
    }

    private ItemDefinition parseItem(String mapKey, ConfigurationSection s) {
        String logicalId = s.getString("logicalId");
        if (logicalId == null || logicalId.isBlank()) {
            logicalId = mapKey;
        }

        String material = s.getString("material");
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("'material' is required for item '" + logicalId + "'");
        }
        // Validate the material name resolves to a real Bukkit Material at parse time so a
        // typo'd or renamed material (e.g. CHAIN -> IRON_CHAIN on Purpur 1.21.11) aborts
        // startup via the fail-fast loadAll() path instead of silently no-op'ing at give-time.
        // matchMaterial accepts both "NETHERITE_SWORD" and "minecraft:netherite_sword" forms.
        if (Material.matchMaterial(material) == null) {
            throw new IllegalArgumentException("Unknown material '" + material + "' for item '" + logicalId
                    + "' -- not a valid Bukkit Material (check for typos or a renamed material).");
        }

        ItemDefinition.Builder builder = ItemDefinition.builder(logicalId, material.toUpperCase())
                .displayName(s.getString("displayName"))
                .lore(s.getStringList("lore"))
                .itemModel(s.getString("itemModel"))
                .unbreakable(s.getBoolean("unbreakable", false))
                .hideTooltip(s.getBoolean("hideTooltip", false))
                .hideAdditionalTooltip(s.getBoolean("hideAdditionalTooltip", false))
                .slot(s.getInt("slot", -1))
                .locked(s.getBoolean("locked", false))
                .droppable(s.getBoolean("droppable", true))
                .movable(s.getBoolean("movable", true))
                .vanillaStack(s.getBoolean("vanillaStack", false));

        if (s.contains("enchantmentGlint")) {
            builder.enchantmentGlint(s.getBoolean("enchantmentGlint"));
        }
        if (s.contains("rarity")) {
            builder.rarity(s.getString("rarity").toUpperCase());
        }
        if (s.contains("maxStackSize")) {
            int size = s.getInt("maxStackSize");
            if (size < 1 || size > 99) {
                throw new IllegalArgumentException("maxStackSize must be between 1 and 99, got: " + size);
            }
            builder.maxStackSize(size);
        }
        if (s.contains("maxDamage")) {
            builder.maxDamage(validateMaxDamage(s.get("maxDamage"), material, logicalId,
                    ItemConfigLoader::vanillaMaxDurability));
        }

        // Enchantments
        ConfigurationSection enchSection = s.getConfigurationSection("enchantments");
        if (enchSection != null) {
            Map<String, Integer> enchantments = new HashMap<>();
            for (String enchKey : enchSection.getKeys(false)) {
                enchantments.put(enchKey.toLowerCase(), enchSection.getInt(enchKey));
            }
            builder.enchantments(enchantments);
        }

        // customModelData block
        ConfigurationSection cmdSection = s.getConfigurationSection("customModelData");
        if (cmdSection != null) {
            List<String> strings = cmdSection.getStringList("strings");
            List<Float> floats = new ArrayList<>();
            for (double d : cmdSection.getDoubleList("floats")) {
                floats.add((float) d);
            }
            List<Boolean> flags = cmdSection.getBooleanList("flags");
            List<Integer> colors = cmdSection.getIntegerList("colors");
            builder.customModelData(new ItemDefinition.CustomModelDataDef(strings, floats, flags, colors));
        }

        // Potion contents. A bare POTION stack renders as an empty "Uncraftable Potion", so a
        // potion item is only ever useful with a base type; the type also carries the vanilla
        // DURATION (FIRE_RESISTANCE 3:00 vs LONG_FIRE_RESISTANCE 8:00), which is why there is no
        // separate duration field to get out of step with the constant.
        //
        // 'effects' covers what a base type cannot: a duration vanilla has no constant for. It may
        // appear with a base type, or alone.
        ConfigurationSection potionSection = s.getConfigurationSection("potion");
        if (potionSection != null) {
            List<?> rawEffects = requireEffectsList(potionSection, logicalId);
            boolean hasEffects = rawEffects != null && !rawEffects.isEmpty();

            // A potion section carrying neither a base type nor an effect is the empty-bottle bug in
            // config form, so keep demanding a type unless effects supply the contents.
            if (potionSection.contains("type") || !hasEffects) {
                builder.potionType(validatePotionType(potionSection.getString("type"), material, logicalId));
            }
            if (hasEffects) {
                builder.potionEffects(validatePotionEffects(rawEffects, material, logicalId,
                        type -> resolveEffectType(type) != null));
            }

            List<String> unknownPotionKeys = unknownKeys(potionSection.getKeys(false), KNOWN_POTION_KEYS);
            if (!unknownPotionKeys.isEmpty()) {
                plugin.getLogger().warning("[Items] Item '" + logicalId + "' has unrecognised 'potion' key(s) "
                        + unknownPotionKeys + " -- ignored. Either a typo, or this config expects a newer "
                        + "plugin-items than the one running; the potion is built WITHOUT them.");
            }
        }

        ConfigurationSection foodSection = s.getConfigurationSection("food");
        if (foodSection != null) {
            int nutrition = foodSection.getInt("nutrition", 0);
            float saturation = (float) foodSection.getDouble("saturation", 0.0);
            boolean canAlwaysEat = foodSection.getBoolean("canAlwaysEat", false);
            builder.food(new ItemDefinition.FoodDef(nutrition, saturation, canAlwaysEat));
        }

        // Arbitrary metadata
        ConfigurationSection metaSection = s.getConfigurationSection("metadata");
        if (metaSection != null) {
            Map<String, Object> metadata = new HashMap<>();
            for (String key : metaSection.getKeys(false)) {
                metadata.put(key, metaSection.get(key));
            }
            builder.metadata(metadata);
        }

        List<String> unknown = unknownKeys(s.getKeys(false));
        if (!unknown.isEmpty()) {
            plugin.getLogger().warning("[Items] Item '" + logicalId + "' has unrecognised config key(s) "
                    + unknown + " -- ignored. Either a typo, or this config expects a newer plugin-items "
                    + "than the one running; the item is built WITHOUT them.");
        }

        return builder.build();
    }

    /**
     * Returns the keys in an item's section that this loader does not read, sorted for a stable
     * log line.
     *
     * <p>The caller WARNs rather than throws on purpose. An unknown key is usually a config
     * delivered ahead of the jar that understands it — on a shared plugin baked into the base image
     * that skew is a routine few minutes of a rollout, and aborting start would turn every game pod
     * on the network into a crashloop over a field the item does not strictly need. A WARN naming
     * the item and the key is enough to find it in {@code logs_errors} instead of discovering it as
     * wrong loot mid-match.
     */
    static List<String> unknownKeys(Collection<String> keys) {
        return unknownKeys(keys, KNOWN_KEYS);
    }

    /** As {@link #unknownKeys(Collection)}, for a nested section with its own known-key set. */
    static List<String> unknownKeys(Collection<String> keys, Set<String> knownKeys) {
        List<String> unknown = new ArrayList<>();
        for (String key : keys) {
            if (!knownKeys.contains(key)) {
                unknown.add(key);
            }
        }
        Collections.sort(unknown);
        return unknown;
    }
}
