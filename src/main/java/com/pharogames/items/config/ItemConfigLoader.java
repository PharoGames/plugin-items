package com.pharogames.items.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * Every key {@link #parseItem} reads. Anything else in an item's section is either a typo
     * ({@code enchantment} for {@code enchantments}) or a field a NEWER config expects from an
     * OLDER jar — both used to be dropped in silence, which is how a potion definition delivered
     * ahead of its jar becomes an empty bottle in a loot chest with nothing in the logs.
     */
    private static final Set<String> KNOWN_KEYS = Set.of(
            "logicalId", "material", "displayName", "lore", "itemModel", "customModelData",
            "enchantmentGlint", "rarity", "maxStackSize", "unbreakable", "enchantments",
            "hideTooltip", "hideAdditionalTooltip", "food", "potion",
            "slot", "locked", "droppable", "movable", "vanillaStack", "metadata");

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
        ConfigurationSection potionSection = s.getConfigurationSection("potion");
        if (potionSection != null) {
            builder.potionType(validatePotionType(potionSection.getString("type"), material, logicalId));
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
        List<String> unknown = new ArrayList<>();
        for (String key : keys) {
            if (!KNOWN_KEYS.contains(key)) {
                unknown.add(key);
            }
        }
        Collections.sort(unknown);
        return unknown;
    }
}
