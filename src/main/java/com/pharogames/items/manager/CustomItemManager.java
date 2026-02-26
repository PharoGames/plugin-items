package com.pharogames.items.manager;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.pharogames.items.api.GiveOptions;
import com.pharogames.items.config.ItemDefinition;
import com.pharogames.items.registry.ItemRegistry;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

/**
 * Creates ItemStack instances from ItemDefinitions using Paper's 1.21 DataComponent API.
 *
 * PDC keys stored on every custom item (namespace "items"):
 *   logical_id  -- String   the item's logical ID
 *   locked      -- byte (1/0)  cannot be moved between slots (default: 0 = not locked)
 *   droppable   -- byte (1/0)  can be dropped by the player (default: 1 = droppable)
 *   movable     -- byte (1/0)  can be moved within an inventory (default: 1 = movable)
 */
public class CustomItemManager {

    // PDC namespace -- separate from gameplay-runtime to avoid key collisions
    public static final String PDC_NAMESPACE = "items";

    private final JavaPlugin plugin;
    private final ItemRegistry registry;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final NamespacedKey keyLogicalId;
    private final NamespacedKey keyLocked;
    private final NamespacedKey keyDroppable;
    private final NamespacedKey keyMovable;

    private boolean papiAvailable = false;

    public CustomItemManager(JavaPlugin plugin, ItemRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.keyLogicalId = new NamespacedKey(PDC_NAMESPACE, "logical_id");
        this.keyLocked = new NamespacedKey(PDC_NAMESPACE, "locked");
        this.keyDroppable = new NamespacedKey(PDC_NAMESPACE, "droppable");
        this.keyMovable = new NamespacedKey(PDC_NAMESPACE, "movable");
    }

    public void setPapiAvailable(boolean available) {
        this.papiAvailable = available;
    }

    // ========================== Public API ==========================

    /**
     * Creates a custom item from its logical ID without a player context.
     * PAPI placeholders in lore will not be resolved.
     */
    public ItemStack createItem(String logicalId) {
        return createItem(logicalId, null, null);
    }

    /**
     * Creates a custom item with a player context for PAPI resolution.
     */
    public ItemStack createItem(String logicalId, Player player) {
        return createItem(logicalId, player, null);
    }

    /**
     * Creates a custom item, applying GiveOptions overrides to inventory behaviour flags.
     */
    public ItemStack createItem(String logicalId, Player player, GiveOptions options) {
        ItemDefinition def = registry.get(logicalId);
        if (def == null) {
            plugin.getLogger().warning("[Items] Unknown item: '" + logicalId + "'");
            return null;
        }
        return buildItemStack(def, player, options);
    }

    /**
     * Gives an item to a player. Respects the slot defined in GiveOptions (or the definition default).
     */
    public void giveItem(Player player, String logicalId, GiveOptions options) {
        ItemStack item = createItem(logicalId, player, options);
        if (item == null) return;

        int slot = (options != null && options.getSlot() != null)
                ? options.getSlot()
                : registry.get(logicalId).getSlot();

        if (slot >= 0 && slot < player.getInventory().getSize()) {
            player.getInventory().setItem(slot, item);
        } else {
            player.getInventory().addItem(item);
        }
    }

    // ========================== Identification ==========================

    public boolean isCustomItem(ItemStack item) {
        return getLogicalId(item) != null;
    }

    public String getLogicalId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(keyLogicalId, PersistentDataType.STRING);
    }

    /** Returns true if the item has the locked flag set to 1. */
    public boolean isLocked(ItemStack item) {
        return readPdcByte(item, keyLocked, (byte) 0) == 1;
    }

    /** Returns true if the item can be dropped (default true). */
    public boolean isDroppable(ItemStack item) {
        return readPdcByte(item, keyDroppable, (byte) 1) == 1;
    }

    /** Returns true if the item can be moved in inventory (default true). */
    public boolean isMovable(ItemStack item) {
        return readPdcByte(item, keyMovable, (byte) 1) == 1;
    }

    // ========================== Build logic ==========================

    @SuppressWarnings("UnstableApiUsage")
    private ItemStack buildItemStack(ItemDefinition def, Player player, GiveOptions options) {
        Material material;
        try {
            material = Material.valueOf(def.getMaterial().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("[Items] Invalid material '" + def.getMaterial() +
                    "' for item '" + def.getLogicalId() + "' -- skipping.");
            return null;
        }

        ItemStack item = new ItemStack(material);

        // --- ITEM_NAME (display name) ---
        // Uses ITEM_NAME (not CUSTOM_NAME) so the name is styled consistently and never italic.
        if (def.getDisplayName() != null) {
            Component nameComponent = miniMessage.deserialize(def.getDisplayName());
            item.setData(DataComponentTypes.ITEM_NAME, nameComponent);
        }

        // --- LORE ---
        if (!def.getLore().isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : def.getLore()) {
                String resolved = resolvePapi(line, player);
                loreComponents.add(miniMessage.deserialize(resolved));
            }
            item.setData(DataComponentTypes.LORE, ItemLore.lore(loreComponents));
        }

        // --- ITEM_MODEL (1.21+ primary custom model) ---
        if (def.getItemModel() != null) {
            try {
                item.setData(DataComponentTypes.ITEM_MODEL, Key.key(def.getItemModel()));
            } catch (Exception e) {
                plugin.getLogger().warning("[Items] Invalid item_model key '" + def.getItemModel() +
                        "' for item '" + def.getLogicalId() + "': " + e.getMessage());
            }
        }

        // --- CUSTOM_MODEL_DATA (1.21 multi-value form; legacy / resource-pack fallback) ---
        if (def.getCustomModelData() != null) {
            ItemDefinition.CustomModelDataDef cmd = def.getCustomModelData();
            if (!cmd.getStrings().isEmpty() || !cmd.getFloats().isEmpty()
                    || !cmd.getFlags().isEmpty() || !cmd.getColors().isEmpty()) {
                CustomModelData.Builder cmdBuilder = CustomModelData.customModelData();
                cmd.getStrings().forEach(cmdBuilder::addString);
                cmd.getFloats().forEach(cmdBuilder::addFloat);
                cmd.getFlags().forEach(cmdBuilder::addFlag);
                // Colors in config are ARGB integers (e.g. 0xFFFF0000); convert to Bukkit Color.
                cmd.getColors().forEach(argb -> cmdBuilder.addColor(Color.fromARGB(argb)));
                item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, cmdBuilder.build());
            }
        }

        // --- ENCHANTMENT_GLINT_OVERRIDE ---
        if (def.getEnchantmentGlint() != null) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, def.getEnchantmentGlint());
        }

        // --- RARITY ---
        if (def.getRarity() != null) {
            try {
                item.setData(DataComponentTypes.RARITY, ItemRarity.valueOf(def.getRarity()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Items] Unknown rarity '" + def.getRarity() +
                        "' for item '" + def.getLogicalId() + "'. Valid: COMMON, UNCOMMON, RARE, EPIC");
            }
        }

        // --- MAX_STACK_SIZE ---
        if (def.getMaxStackSize() != null) {
            item.setData(DataComponentTypes.MAX_STACK_SIZE, def.getMaxStackSize());
        }

        // --- UNBREAKABLE (NonValued -- just flag it as present) ---
        if (def.isUnbreakable()) {
            item.setData(DataComponentTypes.UNBREAKABLE);
        }

        // --- ENCHANTMENTS ---
        if (!def.getEnchantments().isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                for (Map.Entry<String, Integer> entry : def.getEnchantments().entrySet()) {
                    Enchantment enchantment = resolveEnchantment(entry.getKey());
                    if (enchantment != null) {
                        meta.addEnchant(enchantment, entry.getValue(), true);
                    } else {
                        plugin.getLogger().warning("[Items] Unknown enchantment '" + entry.getKey() +
                                "' for item '" + def.getLogicalId() + "'");
                    }
                }
                item.setItemMeta(meta);
            }
        }

        // --- TOOLTIP_DISPLAY (unified hide system replacing HIDE_TOOLTIP / HIDE_ADDITIONAL_TOOLTIP) ---
        if (def.isHideTooltip() || def.isHideAdditionalTooltip()) {
            TooltipDisplay.Builder tdBuilder = TooltipDisplay.tooltipDisplay()
                    .hideTooltip(def.isHideTooltip());
            if (def.isHideAdditionalTooltip()) {
                // Hide the most common vanilla "additional" tooltip components:
                // attribute modifiers, enchantment levels, stored enchantments (books),
                // and the unbreakable text line.
                tdBuilder.addHiddenComponents(
                        DataComponentTypes.ATTRIBUTE_MODIFIERS,
                        DataComponentTypes.ENCHANTMENTS,
                        DataComponentTypes.STORED_ENCHANTMENTS,
                        DataComponentTypes.UNBREAKABLE
                );
            }
            item.setData(DataComponentTypes.TOOLTIP_DISPLAY, tdBuilder.build());
        }

        // --- PLAYER_HEAD skin via Paper ResolvableProfile DataComponent ---
        if (material == Material.PLAYER_HEAD && options != null && options.getHeadOwner() != null) {
            applyHeadOwner(item, options.getHeadOwner());
        }

        // --- Resolve inventory behaviour: GiveOptions > definition defaults ---
        boolean locked = options != null && options.getLocked() != null
                ? options.getLocked() : def.isLocked();
        boolean droppable = options != null && options.getDroppable() != null
                ? options.getDroppable() : def.isDroppable();
        boolean movable = options != null && options.getMovable() != null
                ? options.getMovable() : def.isMovable();

        // --- Write PDC flags onto the item ---
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyLogicalId, PersistentDataType.STRING, def.getLogicalId());
            pdc.set(keyLocked,    PersistentDataType.BYTE, (byte) (locked    ? 1 : 0));
            pdc.set(keyDroppable, PersistentDataType.BYTE, (byte) (droppable ? 1 : 0));
            pdc.set(keyMovable,   PersistentDataType.BYTE, (byte) (movable   ? 1 : 0));
            item.setItemMeta(meta);
        }

        return item;
    }

    // ========================== Player head ==========================

    @SuppressWarnings("UnstableApiUsage")
    private void applyHeadOwner(ItemStack item, Object headOwner) {
        try {
            UUID uuid;
            String name;
            if (headOwner instanceof Player p) {
                uuid = p.getUniqueId();
                name = p.getName();
            } else if (headOwner instanceof UUID u) {
                uuid = u;
                name = null;
            } else {
                plugin.getLogger().warning("[Items] Invalid headOwner type: " + headOwner.getClass().getName());
                return;
            }

            // Build a Paper PlayerProfile and populate it (fetches textures from Mojang).
            // This is intentionally blocking -- skins are resolved once at item creation time.
            PlayerProfile profile = plugin.getServer().createProfile(uuid, name);
            if (!profile.isComplete() || !profile.hasTextures()) {
                try {
                    profile = profile.update().get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    plugin.getLogger().warning("[Items] Interrupted while fetching skin for UUID " + uuid);
                } catch (ExecutionException e) {
                    plugin.getLogger().log(Level.WARNING, "[Items] Could not fetch skin for UUID " + uuid, e);
                }
            }

            // Use the Paper PROFILE DataComponent instead of deprecated SkullMeta
            item.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(profile));

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Items] Failed to apply head owner", e);
        }
    }

    // ========================== Helpers ==========================

    private String resolvePapi(String text, Player player) {
        if (player == null || !papiAvailable) return text;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Exception e) {
            return text;
        }
    }

    @SuppressWarnings("deprecation")
    private Enchantment resolveEnchantment(String name) {
        // Support both "sharpness" and "minecraft:sharpness" forms.
        NamespacedKey key = name.contains(":")
                ? NamespacedKey.fromString(name)
                : NamespacedKey.minecraft(name);
        if (key == null) return null;
        return Enchantment.getByKey(key);
    }

    /**
     * Reads a byte from the item's PDC. Returns {@code defaultValue} if the key is absent.
     * Using per-key defaults prevents wrong behaviour when a key was never written.
     */
    private byte readPdcByte(ItemStack item, NamespacedKey key, byte defaultValue) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return defaultValue;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return defaultValue;
        Byte val = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return val != null ? val : defaultValue;
    }
}
