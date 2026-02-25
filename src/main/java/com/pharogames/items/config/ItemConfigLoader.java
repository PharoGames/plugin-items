package com.pharogames.items.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private FileConfiguration loadConfig() {
        // The configloader writes to plugins/Items/config.yml in the server working dir.
        // saveDefaultConfig() writes the bundled config.yml to the same location if absent.
        plugin.saveDefaultConfig();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        plugin.getLogger().info("Loading items config from " + configFile.getPath());
        return YamlConfiguration.loadConfiguration(configFile);
    }

    private ItemDefinition parseItem(String logicalId, ConfigurationSection s) {
        String material = s.getString("material");
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("'material' is required");
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
                .movable(s.getBoolean("movable", true));

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

        // Arbitrary metadata
        ConfigurationSection metaSection = s.getConfigurationSection("metadata");
        if (metaSection != null) {
            Map<String, Object> metadata = new HashMap<>();
            for (String key : metaSection.getKeys(false)) {
                metadata.put(key, metaSection.get(key));
            }
            builder.metadata(metadata);
        }

        return builder.build();
    }
}
