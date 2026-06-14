package com.pharogames.items.api;

import com.pharogames.items.config.ItemDefinition;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

/**
 * Public API for plugin-items.
 * Other plugins access this via {@code ItemsAPI.getInstance()}.
 *
 * <h3>Example usage (lobby compass)</h3>
 * <pre>{@code
 * ItemsAPI items = ItemsAPI.getInstance();
 *
 * // Give a compass locked in slot 8
 * items.giveItem(player, "lobby.compass");
 *
 * // Register a right-click handler (pass your plugin as owner for auto-cleanup on disable)
 * items.registerInteraction(this, "lobby.compass", InteractType.RIGHT_CLICK,
 *     (p, item, type) -> openServerSelectorGUI(p));
 *
 * // Register a custom item at runtime (e.g. from a gamemode plugin's onEnable)
 * ItemDefinition flag = ItemDefinition.builder("ctf.flag", "WHITE_BANNER")
 *     .displayName("<white>Capture the Flag")
 *     .locked(false)
 *     .droppable(false)
 *     .build();
 * items.registerItem(flag);
 *
 * // Give a player head showing the player's own skin
 * items.giveItem(player, "gui.profile",
 *     GiveOptions.builder().headOwner(player).slot(0).build());
 * }</pre>
 */
public interface ItemsAPI {

    /**
     * Returns the active ItemsAPI instance.
     *
     * @throws IllegalStateException if plugin-items is not currently enabled (it has not finished
     *         enabling yet, has been disabled, or its onEnable failed). Consumers must declare
     *         {@code depend: [Items]} in plugin.yml so plugin-items loads first; calling this
     *         before that ordering is satisfied throws an actionable error naming plugin-items
     *         instead of an opaque NPE deep in consumer code.
     */
    static ItemsAPI getInstance() {
        ItemsAPI api = com.pharogames.items.ItemsPlugin.getAPI();
        if (api == null) {
            throw new IllegalStateException(
                    "plugin-items is not enabled (check load order -- add 'depend: [Items]' to your "
                            + "plugin.yml, or plugin-items failed to enable; see server logs).");
        }
        return api;
    }

    // ========== Item Creation ==========

    /**
     * Creates an ItemStack from the given logical ID without a player context.
     * PAPI placeholders in lore will not be resolved.
     * Returns null and logs a warning if the ID is unknown.
     */
    ItemStack createItem(String logicalId);

    /**
     * Creates an ItemStack with a player context so PAPI placeholders in lore are resolved.
     * Returns null and logs a warning if the ID is unknown.
     */
    ItemStack createItem(String logicalId, Player player);

    // ========== Giving Items ==========

    /**
     * Gives an item to a player using the definition's default slot/protection flags.
     * For PLAYER_HEAD items, use {@link #giveItem(Player, String, GiveOptions)} with headOwner set.
     */
    void giveItem(Player player, String logicalId);

    /**
     * Gives an item to a player with overrides applied at give-time.
     * Overrides can change slot, locked, droppable, movable, and headOwner (for PLAYER_HEAD).
     */
    void giveItem(Player player, String logicalId, GiveOptions options);

    // ========== Identification ==========

    /** Returns true if the item carries a plugin-items logical ID in its PDC. */
    boolean isCustomItem(ItemStack item);

    /** Returns the logical ID from the item's PDC, or null if it is not a custom item. */
    String getLogicalId(ItemStack item);

    // ========== Registry Access ==========

    /**
     * Returns the ItemDefinition for the given logical ID, or null if not registered.
     */
    ItemDefinition getDefinition(String logicalId);

    /** Returns all registered logical IDs (unmodifiable). */
    Collection<String> getAllItemIds();

    // ========== Runtime Registration ==========

    /**
     * Registers a new item definition. Overwrites any existing definition with the same ID.
     * Intended for gamemode plugins that define items at startup.
     */
    void registerItem(ItemDefinition definition);

    /**
     * Removes an item definition from the registry.
     * Existing ItemStacks with this ID are unaffected.
     */
    void unregisterItem(String logicalId);

    // ========== Interaction Callbacks ==========

    /**
     * Registers a callback invoked when a player interacts with an item of the given logical ID.
     * The handler is automatically removed when {@code owner} is disabled.
     *
     * @param owner      the plugin registering this handler (used for auto-cleanup on reload)
     * @param logicalId  the item's logical ID
     * @param type       the interact type(s) that trigger the handler
     * @param handler    the handler to invoke
     */
    void registerInteraction(Plugin owner, String logicalId, InteractType type, ItemInteractHandler handler);

    /**
     * Registers a callback without an owner. Prefer
     * {@link #registerInteraction(Plugin, String, InteractType, ItemInteractHandler)}.
     *
     * @deprecated Pass the owning plugin so stale handlers are auto-purged on reload.
     */
    @Deprecated
    void registerInteraction(String logicalId, InteractType type, ItemInteractHandler handler);

    /**
     * Removes all interaction callbacks registered for the given logical ID.
     */
    void unregisterInteractions(String logicalId);
}
