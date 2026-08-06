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
  <yaml-key>:
    # ===== Identity (optional) =====
    logicalId: <registry-id>         # If set, this value is the Items API id (for recipes, code).
                                      # If omitted, the YAML map key is the logical ID.
                                      # Use a dot-free key + explicit logicalId for config-service-safe entries
                                      # (e.g. key hungergames_kill_token, logicalId hungergames.kill_token).

    # ===== Required =====
    material: <BUKKIT_MATERIAL>        # e.g. COMPASS, NETHERITE_SWORD, PLAYER_HEAD

    # ===== Presentation =====
    displayName: "<minimessage>"       # MiniMessage string -> ITEM_NAME component (no italics)
    lore:
      - "<minimessage line>"           # MiniMessage + PAPI placeholders -> LORE component (no italics;
                                        #   builder forces ITALIC=false, add <i>...</i> to opt back in)

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
    maxDamage: 5                       # -> MAX_DAMAGE (durability). Damageable materials only;
                                       #    a BOW with 5 breaks after 5 shots. 1-32767.
    unbreakable: true                  # -> UNBREAKABLE (non-valued component)
    enchantments:                      # -> ENCHANTMENTS
      sharpness: 5
      fire_aspect: 2
    hideTooltip: false                 # -> TOOLTIP_DISPLAY.hideTooltip (hides entire tooltip)
    hideAdditionalTooltip: false       # -> TOOLTIP_DISPLAY.addHiddenComponents (hides ATTRIBUTE_MODIFIERS,
                                       #    ENCHANTMENTS, STORED_ENCHANTMENTS, and UNBREAKABLE from tooltip)
    potion:                             # Optional. POTION / SPLASH_POTION / LINGERING_POTION / TIPPED_ARROW only
      type: FIRE_RESISTANCE             # Bukkit PotionType constant -> PotionMeta.setBasePotionType
      effects:                          # Optional. Custom effects -> PotionMeta.addCustomEffect.
        - type: INVISIBILITY            #   Bukkit PotionEffectType constant
          durationSeconds: 180          #   Seconds (x20 -> ticks). Delivered as written; see Potions
          amplifier: 0                  #   Optional, 0-255, default 0 (= level I)

    food:                               # Optional. -> FOOD (FoodProperties); overrides type defaults on this stack
      nutrition: 0                      # Hunger points restored (0 = none)
      saturation: 0.0                   # Saturation restored
      canAlwaysEat: true                # Eat even at full hunger (e.g. tokens)

    # ===== Inventory Behaviour (defaults, overridable per giveItem call) =====
    slot: 4                            # Default inventory slot. -1 = next available.
    locked: true                       # Cannot be moved between slots
    droppable: false                   # Cannot be dropped by the player
    movable: false                     # Cannot be moved within an inventory

    # ===== Stacking =====
    vanillaStack: true                 # Ship a bare vanilla stack: no PDC identity, so it merges
                                       # with vanilla items of the same material. See below.

    # ===== Arbitrary Metadata =====
    metadata:
      type: gui                        # Read via ItemsAPI.getDefinition(logicalId).getMetadata()
      gui: battlepass                  # Lobby: plugin-lobby ItemsIntegration opens Battle Pass when set (with hotbar-items listing this logical id)
```

**Lobby hotbar GUIs** (`plugin-lobby` `ItemsIntegration`): optional `metadata.gui` routes right-click — `cosmetics`, `start_game`, **`battlepass`**, etc. The item must still be listed under the lobby plugin’s `hotbar-items` (see `plugin-lobby` / `server-image-lobby` docs).

### Potions

A bare `POTION` stack carries no effect and renders as an empty *Uncraftable Potion*, so a potion item is only
useful with a base type:

```yaml
skywars_fire_resistance_potion:
  logicalId: skywars.fire_resistance_potion
  material: POTION
  potion:
    type: FIRE_RESISTANCE
```

The type sets both the effect **and** its vanilla duration — pick the constant that carries the duration you
want rather than looking for a duration field:

| Constant | Effect | Duration |
|---|---|---|
| `FIRE_RESISTANCE` | Fire Resistance | 3:00 |
| `LONG_FIRE_RESISTANCE` | Fire Resistance | 8:00 |
| `REGENERATION` | Regeneration I | 0:45 |
| `LONG_REGENERATION` | Regeneration I | 1:30 |
| `STRONG_REGENERATION` | Regeneration II | 0:22 |

Any Bukkit `PotionType` constant works (`SWIFTNESS`, `LONG_SWIFTNESS`, `STRONG_HEALING`, …). Rules:

- Valid only on `POTION`, `SPLASH_POTION`, `LINGERING_POTION`, `TIPPED_ARROW`. On anything else the item is
  **rejected at load** and the server aborts, rather than shipping a stack that reads as configured and behaves
  vanilla.
- An unknown constant is likewise rejected at load, naming the item and the bad value.
- Values are case-insensitive; `fire_resistance` and `FIRE_RESISTANCE` are the same type.
- `displayName`/`lore` still apply. Leave them out and the potion reads as the plain vanilla item, which is what
  loot-pool potions want.
- A potion's `displayName` is applied as **CUSTOM_NAME**, not the `ITEM_NAME` every other material gets. A potion
  names itself from its contents ("Splash Potion of Invisibility", "Splash Potion of Water" for a bare one) and
  that name outranks `ITEM_NAME`, so a named potion used to reach the client under the material's name instead of
  the configured one. `CUSTOM_NAME` is the only name it cannot talk over. Italic is forced off, so the two render
  identically — this is invisible unless you are reading the components.

#### Custom effects

`potion.effects` covers what a base type cannot: a duration vanilla has no constant for. Each entry becomes a
`PotionMeta.addCustomEffect`, and may appear with or without `potion.type`.

```yaml
skywars_vanishing_flask:
  logicalId: skywars.vanishing_flask
  material: SPLASH_POTION
  potion:
    effects:
      - type: INVISIBILITY
        durationSeconds: 180      # 3:00 at the impact point -- see the splash maths below
        amplifier: 0              # optional, 0-255, default 0 (= level I)
```

**A SPLASH potion applies the FULL configured duration**, both to an entity it hits directly and to anyone
standing at the impact point. Write the duration you want the player to get. The old "splash potions last 3/4
as long" rule was removed in 15w31a and has not applied since 1.9 — do not divide by 0.75.

What *does* scale a splash is distance: the duration falls off linearly with range from the impact, reaching
nothing at 4 blocks, and an effect scaled below 1 second is not applied at all. A potion thrown at your own feet
is at range ~0, so the thrower gets the number as written.

| Material | Vanilla factor | Configured 180s delivers |
|---|---|---|
| `POTION` (drink) | 1x | 3:00 |
| `SPLASH_POTION` (direct hit, or at the impact point) | 1x | **3:00** |
| `SPLASH_POTION` (2 blocks from the impact) | ~0.5x | ~1:30 |
| `LINGERING_POTION` (per touch of the cloud) | 0.25x | 0:45, reapplied |
| `TIPPED_ARROW` (on hit) | 0.125x | 0:22 |

Custom effects **layer on top of** `potion.type` rather than replacing it — a potion with both applies both. When
the two name the same effect, the player ends up with one instance of it: the longer (or stronger) wins, and the
other is kept hidden until it does. Two entries in the same `effects` list naming one type are collapsed at build
time, last entry wins.

Rules, all **rejected at load** naming the item and the bad value, for the same reason a bad `potion.type` is —
an effect that never applied leaves a potion that reads as configured in YAML and does nothing in the hand:

- Valid only on `POTION`, `SPLASH_POTION`, `LINGERING_POTION`, `TIPPED_ARROW`.
- `type` must be a Bukkit `PotionEffectType` constant (case-insensitive), and is required on every entry.
- `durationSeconds` must be a positive whole number (and small enough that `x20` fits in an int).
- `amplifier` must be 0–255. `0` is level I, `1` is level II.

Prefer a base `type` when a constant already carries the duration you want — it is one line, and it is what the
vanilla item shows in its tooltip.

### Durability (`maxDamage`)

`maxDamage` overrides the item type's durability bar, which is how a kit ships a deliberately fragile tool:

```yaml
skywars_hunting_bow:
  logicalId: skywars.hunting_bow
  material: BOW
  maxDamage: 5        # breaks after 5 shots (vanilla BOW is 384)
```

- Valid only on a **damageable** material (one with a vanilla durability bar). On anything else — food, blocks,
  a spawn egg — the item is **rejected at load** and the server aborts, rather than shipping a stack that reads
  as configured and behaves vanilla.
- Must be 1–32767 (vanilla stores durability as a short). A non-integer, `0`, or a negative is rejected at load
  naming the item and the value.
- Independent of `unbreakable`, which is the opposite request. Setting both leaves an item that cannot be
  damaged, so the durability is decoration — don't.

### `vanillaStack` — plain ingredients that stack with vanilla

Every item this plugin builds normally carries four PDC entries (`logical_id`, `locked`, `droppable`,
`movable`). Those land in the `minecraft:custom_data` component, and `ItemStack.isSimilar` compares
components — so a definition that is *only* `material: COOKED_BEEF` still produces a steak that will **never
merge with a vanilla steak**. A kit hands out 2 tagged steak, a loot chest drops 3 plain ones, and the player
has two stacks that refuse to combine.

`vanillaStack: true` drops the PDC, so the stack is byte-identical to the vanilla item:

```yaml
cooked_beef:
  material: COOKED_BEEF
  vanillaStack: true
```

Use it for definitions that exist only to give a kit (`plugin-gameplay-runtime` `KitItemEntry.logicalId`) or a
loot table a name for a plain vanilla ingredient. What you give up is everything that identity buys:

- `ItemsAPI.getLogicalId(stack)` returns null — the item cannot be recognised after it is given, so it cannot
  be a crafting-recipe ingredient, an ability trigger, or anything else matched by logical id.
- No interaction callback can fire for it (dispatch is keyed on the logical id).
- `locked`, `droppable: false`, `movable: false` are **rejected at load** in combination with it, because
  those flags live in the PDC it strips. The same request via `GiveOptions` at give-time logs a WARN and is
  ignored.

Anything with a `displayName`, `lore`, `itemModel`, enchantments, or a behaviour handler should *not* set it:
those items are meant to be distinct, and are not stackable materials in the first place.

### Unrecognised keys

Any key in an item's section that the loader does not read is logged once at startup:

```
[Items] Item 'skywars.fire_resistance_potion' has unrecognised config key(s) [potion] -- ignored.
Either a typo, or this config expects a newer plugin-items than the one running; the item is built WITHOUT them.
```

The `potion` section gets the same treatment one level down (`[Items] Item '...' has unrecognised 'potion'
key(s) [effect]`), because the top-level check only ever sees an item's direct keys — a typo nested inside
`potion:` would otherwise be swallowed exactly the way top-level keys used to be.

It warns rather than aborts. plugin-items is baked into the shared base image while its config ships through
config-service, so a config can legitimately reach a pod minutes before the jar that understands it; aborting
would crashloop every game pod over one field. The WARN is the signal — `logs_errors({service: "..."})` finds
it, and the alternative (what this replaced) was an item silently built without the field, which surfaces as
wrong loot mid-match instead.

### Logical ID conventions

- All IDs must be unique across all plugins using this registry
- Use dot-namespacing: `<plugin>.<name>` (e.g. `lobby.compass`, `microbattles.flag`)
- **YAML map keys:** Prefer dot-free keys for config-service compatibility; set `logicalId: myplugin.my_item` when the registry id must contain dots (e.g. recipe ingredients). Without `logicalId`, the map key itself is the logical ID.
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

Always pass your plugin instance as the first argument (the owner). Owner-bearing handlers are
automatically purged when your plugin disables; the owner-less overload is deprecated because its
handlers leak on disable/reload and pin classes from a dead classloader.

```java
// Right-click handler (lobby compass opens server selector)
items.registerInteraction(this, "lobby.compass", InteractType.RIGHT_CLICK,
    (player, item, type) -> openServerSelectorGUI(player));

// Shift-right-click for a different action
items.registerInteraction(this, "lobby.compass", InteractType.SHIFT_RIGHT_CLICK,
    (player, item, type) -> sendMessage(player, "<gray>No shift action registered."));

// Optional explicit cleanup (owner-bearing handlers are also auto-purged on disable)
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

A locked item is always also non-movable. Locked or non-movable custom items cannot be placed as blocks (e.g. a CHEST material used as a menu button).

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
