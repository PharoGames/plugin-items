# plugin-items Configuration & API

## Overview

`plugin-items` is the centralized custom item registry for all PharoGames server types. It provides:

- Config-driven item definitions (material, display name, lore, model, enchantments, inventory protection)
- Paper 1.21 Data Component API (`ITEM_MODEL`, `CUSTOM_MODEL_DATA`, `ITEM_NAME`, `LORE`, `RARITY`, etc.)
- MiniMessage formatting for all text fields
- PlaceholderAPI support in lore lines
- Callback-based interaction system (right-click, left-click, shift variants)
- Per-item inventory protection (locked slots, drop prevention, move prevention)
- Player head items with owner set at give-time

## Dependencies

- Purpur API 1.21.11 (provided)
- Adventure MiniMessage 4.14.0 (provided by Purpur)
- PlaceholderAPI (soft dependency -- enables PAPI in lore)

## Configuration

Config is loaded from the plugin's standard Bukkit data folder: `plugins/Items/config.yml`.
The configloader init container fetches this file from Config Service and writes it to
`plugins/Items/config.yml` before the server starts (manifest entry: `plugin="Items"`,
`filename="config.yml"`). If the file is absent, the bundled `config.yml` from the JAR
is saved as the default via `saveDefaultConfig()`.

Config Service scope: `plugin:plugin-items`

### Item Definition Schema

```yaml
items:
  <logical-id>:
    # ===== Required =====
    material: <BUKKIT_MATERIAL>        # e.g. COMPASS, NETHERITE_SWORD, PLAYER_HEAD

    # ===== Presentation =====
    displayName: "<minimessage>"       # MiniMessage string -> ITEM_NAME component (no italics)
    lore:
      - "<minimessage line>"           # MiniMessage + PAPI placeholders -> LORE component

    # ===== Model (1.21) =====
    itemModel: "<namespace:path>"      # -> ITEM_MODEL component (e.g. "pharogames:weapons/sword")
    customModelData:                   # -> CUSTOM_MODEL_DATA component (legacy/advanced)
      strings: ["my_model"]
      floats: []
      flags: []
      colors: []                       # ARGB integers (e.g. 0xFFFF0000 for red)

    # ===== Data Components =====
    enchantmentGlint: true             # -> ENCHANTMENT_GLINT_OVERRIDE (boolean)
    rarity: RARE                       # -> RARITY: COMMON | UNCOMMON | RARE | EPIC
    maxStackSize: 1                    # -> MAX_STACK_SIZE (1-99)
    unbreakable: true                  # -> UNBREAKABLE (non-valued component)
    enchantments:                      # -> ENCHANTMENTS
      sharpness: 5
      fire_aspect: 2
    hideTooltip: false                 # -> TOOLTIP_DISPLAY.hideTooltip (hides entire tooltip)
    hideAdditionalTooltip: false       # -> TOOLTIP_DISPLAY.addHiddenComponents (hides ATTRIBUTE_MODIFIERS,
                                       #    ENCHANTMENTS, STORED_ENCHANTMENTS, and UNBREAKABLE from tooltip)

    # ===== Inventory Behaviour (defaults, overridable per giveItem call) =====
    slot: 4                            # Default inventory slot. -1 = next available.
    locked: true                       # Cannot be moved between slots
    droppable: false                   # Cannot be dropped by the player
    movable: false                     # Cannot be moved within an inventory

    # ===== Arbitrary Metadata =====
    metadata:
      type: gui                        # Read via ItemsAPI.getDefinition(logicalId).getMetadata()
```

### Logical ID conventions

- All IDs must be unique across all plugins using this registry
- Use dot-namespacing: `<plugin>.<name>` (e.g. `lobby.compass`, `microbattles.flag`)
- Items defined in Config Service under scope `plugin:plugin-items` are loaded on all server types that include this plugin

### Startup behaviour

- `saveDefaultConfig()` is called first: if `plugins/Items/config.yml` does not exist it is
  created from the bundled JAR default. The configloader normally overwrites this with the
  Config Service version before the server starts, so the bundled default acts only as a
  last-resort fallback.
- Config is parsed at `onEnable`. Fails fast (shuts down the server) if any item definition is invalid.
- The bundled `config.yml` contains lobby items and sample kit items as examples. Production
  items are managed via Config Service under scope `plugin:plugin-items`.

---

## API Usage

Other plugins access the item system via `ItemsAPI.getInstance()`.

```java
ItemsAPI items = ItemsAPI.getInstance();
```

### Creating and giving items

```java
// Create without a player context (PAPI not resolved in lore)
ItemStack sword = items.createItem("microbattles.iron_sword");

// Create with player context (PAPI placeholders resolved in lore)
ItemStack sword = items.createItem("microbattles.iron_sword", player);

// Give using the definition's default slot and protection flags
items.giveItem(player, "lobby.compass");

// Give with overrides
items.giveItem(player, "lobby.compass",
    GiveOptions.builder()
        .slot(4)
        .locked(true)
        .droppable(false)
        .build());
```

### Player head items

For `PLAYER_HEAD` material, the skin is never static in config.
It must be provided at give-time via `GiveOptions.headOwner()`.
The skin is fetched from Mojang once at item creation time (blocking call on the calling thread).

```java
// Head shows the player's own skin
items.giveItem(player, "gui.player_head",
    GiveOptions.builder().headOwner(player).slot(0).build());

// Head shows another player's skin by UUID
items.giveItem(player, "gui.friend_head",
    GiveOptions.builder().headOwner(friendUuid).slot(1).build());
```

### Identifying custom items

```java
if (items.isCustomItem(itemStack)) {
    String logicalId = items.getLogicalId(itemStack);
    ItemDefinition def = items.getDefinition(logicalId);
    String type = (String) def.getMetadata().get("type");
}
```

### Interaction callbacks

Callbacks are registered per logical ID and interact type. Multiple handlers can be registered for the same item.

```java
// Right-click handler (lobby compass opens server selector)
items.registerInteraction("lobby.compass", InteractType.RIGHT_CLICK,
    (player, item, type) -> openServerSelectorGUI(player));

// Shift-right-click for a different action
items.registerInteraction("lobby.compass", InteractType.SHIFT_RIGHT_CLICK,
    (player, item, type) -> sendMessage(player, "<gray>No shift action registered."));

// Clean up when your plugin disables
@Override
public void onDisable() {
    items.unregisterInteractions("lobby.compass");
}
```

**InteractType values:**

| Value | Description |
|---|---|
| `RIGHT_CLICK` | Right click (not sneaking) |
| `LEFT_CLICK` | Left click (not sneaking) |
| `SHIFT_RIGHT_CLICK` | Right click while sneaking |
| `SHIFT_LEFT_CLICK` | Left click while sneaking |
| `ANY_RIGHT_CLICK` | Any right click (sneak or not) |
| `ANY_LEFT_CLICK` | Any left click (sneak or not) |
| `ANY_CLICK` | Any interaction |

### Runtime registration

Gamemode plugins can register items at startup:

```java
@Override
public void onEnable() {
    ItemsAPI items = ItemsAPI.getInstance();

    ItemDefinition flag = ItemDefinition.builder("ctf.flag", "WHITE_BANNER")
        .displayName("<white><bold>Capture the Flag</bold>")
        .lore(List.of("<gray>Bring it to your base!"))
        .itemModel("pharogames:ctf/flag")
        .enchantmentGlint(true)
        .rarity("EPIC")
        .locked(false)
        .droppable(false)
        .build();

    items.registerItem(flag);
}

@Override
public void onDisable() {
    ItemsAPI.getInstance().unregisterItem("ctf.flag");
}
```

---

## Inventory Protection

Flags are baked into each ItemStack's PersistentDataContainer (PDC namespace: `items`).
They are self-describing -- the ProtectionListener reads them directly from the item, not from the registry.

| Flag | PDC key | Default | Behaviour |
|---|---|---|---|
| `locked` | `items:locked` | false | Blocks all inventory clicks moving the item; blocks off-hand swap |
| `droppable` | `items:droppable` | true | If false, cancels PlayerDropItemEvent |
| `movable` | `items:movable` | true | If false, blocks inventory click moves (weaker than locked) |

A locked item is always also non-movable.

---

## Integration with plugin-gameplay-runtime

`plugin-gameplay-runtime` depends on `plugin-items` (hard dependency).
Kit item creation in `KitManager` calls `ItemsAPI.getInstance().createItem(logicalId, player)`.

Items used in kits must be registered in plugin-items config (or via runtime API).
The `items` section has been removed from `plugin-gameplay-runtime`'s config.

---

## Building

```bash
mvn clean package
```

No S3 compile-time dependencies required (no pharogames plugin dependencies, only Purpur API + PAPI).

Output: `target/plugin-items-1.0.0-SNAPSHOT.jar`

## CI/CD

The GitHub Actions workflow (`.github/workflows/build.yml`) runs on every push to `main`:

1. **Build plugin** -- `mvn clean package`
2. **Upload JAR to S3** -- `s3://pharogames-plugins/plugin-items.jar`
3. **Update plugin registry** -- commits updated artifact metadata to the infrastructure repo

### Required Secrets

| Secret | Description |
|---|---|
| `AWS_ACCESS_KEY_ID` | AWS credentials for S3 upload |
| `AWS_SECRET_ACCESS_KEY` | AWS credentials for S3 upload |
| `APP_ID` | GitHub App ID for infrastructure repo access |
| `APP_PRIVATE_KEY` | GitHub App private key |
