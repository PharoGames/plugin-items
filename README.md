# plugin-items

Centralized custom item registry for all PharoGames server types.

## Features

- **1.21 Data Component API**: `ITEM_MODEL`, `CUSTOM_MODEL_DATA`, `ITEM_NAME`, `LORE`, `RARITY`, `ENCHANTMENTS`, `ENCHANTMENT_GLINT_OVERRIDE`, `UNBREAKABLE`, `MAX_STACK_SIZE`, `TOOLTIP_DISPLAY`
- **MiniMessage formatting** for all display names and lore lines
- **PlaceholderAPI** support in lore (resolves per-player when a player context is provided)
- **Callback-based interaction system**: right-click, left-click, shift variants, any-click
- **Inventory protection**: per-item `locked`, `droppable`, `movable` flags baked into PDC
- **Player head support**: skin resolved at give-time via `GiveOptions.headOwner(Player/UUID)` using `PROFILE` component (never static, never refreshed)
- **Config-driven**: items defined in YAML (`plugins/Items/config.yml`) via Config Service scope `plugin:plugin-items`; bundled default is used when Config Service is unreachable

## Quick Start

```java
// Give a configured item
ItemsAPI.getInstance().giveItem(player, "lobby.compass");

// Right-click opens a GUI
ItemsAPI.getInstance().registerInteraction("lobby.compass", InteractType.RIGHT_CLICK,
    (p, item, type) -> openServerSelectorGUI(p));

// Register an item at runtime
ItemsAPI.getInstance().registerItem(
    ItemDefinition.builder("mygame.flag", "WHITE_BANNER")
        .displayName("<white><bold>Capture the Flag</bold>")
        .droppable(false)
        .build());

// Give a player head showing the player's own skin
ItemsAPI.getInstance().giveItem(player, "gui.profile",
    GiveOptions.builder().headOwner(player).slot(0).build());
```

## Dependencies

- **Purpur API 1.21.11** (provided)
- **PlaceholderAPI** (soft dependency)

## Documentation

See [`docs/configuration.md`](docs/configuration.md) for the full config schema and API reference.

## Building

```bash
mvn clean package
```
