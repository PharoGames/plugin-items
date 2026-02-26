package com.pharogames.items;

import com.pharogames.items.api.ItemsAPI;
import com.pharogames.items.config.ItemConfigLoader;
import com.pharogames.items.config.ItemDefinition;
import com.pharogames.items.internal.ItemsAPIImpl;
import com.pharogames.items.manager.CustomItemManager;
import com.pharogames.items.manager.InteractionManager;
import com.pharogames.items.manager.ProtectionListener;
import com.pharogames.items.registry.ItemRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

/**
 * Main plugin class for plugin-items.
 *
 * Startup order:
 *   1. Load all item definitions from YAML config
 *   2. Register them in the ItemRegistry
 *   3. Initialize managers (CustomItemManager, InteractionManager, ProtectionListener)
 *   4. Expose ItemsAPI singleton
 *
 * Fails fast (shuts down the server) if config is invalid,
 * following the project-wide fail-meaningfully convention.
 */
public class ItemsPlugin extends JavaPlugin {

    private static ItemsPlugin instance;
    private static ItemsAPI api;

    private ItemRegistry registry;
    private CustomItemManager itemManager;
    private InteractionManager interactionManager;

    @Override
    public void onEnable() {
        instance = this;

        try {
            loadItems();
            initManagers();
            registerListeners();
            initAPI();

            getLogger().info("========================================");
            getLogger().info("plugin-items enabled. " + registry.size() + " items registered.");
            getLogger().info("========================================");

        } catch (Exception e) {
            getLogger().severe("========================================");
            getLogger().severe("plugin-items FAILED TO ENABLE: " + e.getMessage());
            getLogger().severe("Server startup aborted.");
            getLogger().severe("========================================");
            getLogger().severe(e.toString());
            getServer().shutdown();
        }
    }

    @Override
    public void onDisable() {
        instance = null;
        api = null;
        getLogger().info("plugin-items disabled.");
    }

    // ========================== Startup ==========================

    private void loadItems() {
        ItemConfigLoader loader = new ItemConfigLoader(this);
        Collection<ItemDefinition> definitions = loader.loadAll();

        registry = new ItemRegistry(this);
        for (ItemDefinition def : definitions) {
            registry.register(def);
        }
    }

    private void initManagers() {
        itemManager = new CustomItemManager(this, registry);

        boolean papiLoaded = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (papiLoaded) {
            itemManager.setPapiAvailable(true);
            getLogger().info("PlaceholderAPI found -- lore placeholders enabled.");
        } else {
            getLogger().info("PlaceholderAPI not found -- lore placeholders disabled.");
        }

        interactionManager = new InteractionManager(itemManager, getLogger());
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(interactionManager, this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(itemManager), this);
    }

    private void initAPI() {
        api = new ItemsAPIImpl(itemManager, registry, interactionManager);
    }

    // ========================== Static accessors ==========================

    public static ItemsAPI getAPI() {
        return api;
    }

    public static ItemsPlugin getInstance() {
        return instance;
    }
}
