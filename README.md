# Perimeter Digger

[简体中文](README.zh-CN.md)

Perimeter Digger is a Fabric mod for Minecraft 26.1.1 that uses a modified Baritone build to automate large-scale excavation.

------

#### If you are reading this
#### I am preparing an introduction and usage-guide video for mod v1.1
#### Everything below is written for mod v1.1
#### Mod v1.1 has not yet been uploaded to Releases
#### It will be available soon
#### This is a substantial REFACTORINNNNNNG

------

#### Required mod:
#### [Modified Baritone](https://github.com/HackerRouter/baritone/releases)

------

It supports:

- Excavating irregular XZ areas
- Sealing fluids along the excavation boundary and handling fluids inside it
- Automatically collecting drops
- Automatically unloading at the nearest unloading point
- Restocking consumables (fireworks and food) at a supply point
- Replacing low-durability tools and Elytra at a supply point
- Traveling across dimensions to repair tools and Elytra at XP furnaces
- Automatically sleeping
- Walking and Elytra pathfinding

## Requirements

- Minecraft 26.1.1
- Fabric Loader 0.19.3 or newer
- Fabric API
- Java 25 or newer
- **[Modified Baritone](https://github.com/HackerRouter/baritone/releases)**

Unmodified Baritone does not include custom-XZ area mining or the extended Elytra/pathfinding APIs required by this mod.

## Installation

1. Install Fabric Loader for Minecraft 26.1.1.
2. Put Fabric API, the latest [Modified Baritone](https://github.com/HackerRouter/baritone/releases), and [Perimeter Digger](https://github.com/HackerRouter/Perimeter-Digger/releases) in the client `mods` directory.

All commands are client-side commands and support basic Tab completion.
The interface follows the language selected in Minecraft and currently includes English and Simplified Chinese.

## Features

- Detect an irregular enclosed XZ excavation area from a chosen boundary block at one Y level.
- Plan an inclusive rectangular XZ excavation area directly from coordinates.
- Excavate the selected columns through a specified Y range.
- Avoid, replace, or seal fluids with configurable blocks.
- Mine according to the remaining inventory capacity and collect nearby drops.
- When the inventory is full, travel to the nearest vertical unloading shaft and discard excavation products.
- Monitor tool and Elytra durability.
- Replace low-durability tools locally or travel to a supply point or XP furnaces, with cross-dimensional repair enabled by default.
- Automatically eat, restock food and fireworks, and optionally sleep at night.
- Use walking and Elytra pathfinding.
- Store configuration separately for each server or single-player save.

## Quick start

### Excavate a regular rectangular XZ area specified by command

```text
/perimeterdig plan rectangle <x0> <z0> <x1> <z1> <minY> <maxY>
/perimeterdig start
```

Both XZ corners and both Y limits are inclusive.

### Excavate an automatically detected irregular XZ area

1. At one Y level, surround the desired area with one block type.
2. Stand inside the outer boundary.
3. Keep the outlined area inside chunks rendered by the client.
4. Run:

```text
/perimeterdig detect <boundaryBlock> <boundaryY>
/perimeterdig config set digging_min_y <Y>
/perimeterdig config set digging_max_y <Y>
/perimeterdig start
```

Example:

```text
/perimeterdig detect minecraft:red_wool 80
/perimeterdig config set digging_min_y -59
/perimeterdig config set digging_max_y 79
/perimeterdig start
```

Cardinally or diagonally touching boundary blocks belong to the same component. The outer boundary columns are excluded. With nested rings, the next interior is excluded while that ring's block columns remain mineable; further nesting alternates by parity.

Detection has no configured size limit, but only chunks loaded within the client's render distance can be inspected.

## Commands

### Runtime and status

| Command | Description |
| --- | --- |
| `/perimeterdig` | Show the current state and detail. |
| `/perimeterdig start` | Validate the area and start automation. Missing optional facilities only disable their related actions. |
| `/perimeterdig stop` | Stop automation and release Baritone input. |
| `/perimeterdig pause` | Pause active mining. |
| `/perimeterdig resume` | Resume paused mining. |
| `/perimeterdig status` | Show the current state, detail, target, and retry count. |
| `/perimeterdig status clear` | Stop and clear cached runtime state without deleting configuration. |
| `/perimeterdig status history` | Show the 16 newest state transitions. |
| `/perimeterdig status history all` | Show up to 64 state transitions. |
| `/perimeterdig status history clear` | Clear transition history. |
| `/perimeterdig reload` | Reload the current world's configuration and reset runtime state. |

### Area and base configuration

| Command | Description |
| --- | --- |
| `/perimeterdig detect <block> <Y>` | Detect an enclosed irregular area. |
| `/perimeterdig plan rectangle <x0> <z0> <x1> <z1> <minY> <maxY>` | Create an inclusive rectangle and Y range. |
| `/perimeterdig config` | Show the current configuration. |
| `/perimeterdig config show` | Show the current configuration. |
| `/perimeterdig config clear detected_area` | Delete the saved area. |
| `/perimeterdig config set digging_min_y <Y>` | Set the inclusive minimum mining Y. |
| `/perimeterdig config set digging_max_y <Y>` | Set the inclusive maximum mining Y. |

### Facility points

Every point uses the block coordinate `<x> <y> <z>`:

```text
/perimeterdig config set consumable_supply_point <x> <y> <z>
/perimeterdig config set durability_supply_point <x> <y> <z>
/perimeterdig config set bed_point <x> <y> <z>
/perimeterdig config set perimeter_portal_overworld <x> <y> <z>
/perimeterdig config set perimeter_portal_nether <x> <y> <z>
/perimeterdig config set repair_portal_overworld <x> <y> <z>
/perimeterdig config set repair_portal_nether <x> <y> <z>
/perimeterdig config set furnace_row_start <x> <y> <z>
/perimeterdig config set furnace_row_end <x> <y> <z>
```

Supply points are chest block coordinates. The bed point is the bed block. The furnace row must be axis-aligned; only its first and last furnace positions are stored. Navigation chooses a nearby position from which the chest or furnace can be reached. Sleeping uses a stricter dedicated stand search and approaches within a 3-block eye-to-bed-center distance before interacting.

All facilities are optional during normal mining. A missing point or route suppresses only the related automatic action. Debug commands still require the facility they test.

### Unloading points

```text
/perimeterdig config set unloading_point <name> <x> <minY> <z>
/perimeterdig config remove unloading_point <name>
/perimeterdig config add unloading_whitelist <item>
/perimeterdig config remove unloading_whitelist <item>
```

An unloading point represents a vertical shaft at its XZ coordinate; Y is the shaft's minimum Y. Multiple named shafts are supported. The mod chooses a nearby safe standing block with minimum XZ distance, prefers a Y near the player's current Y, moves to the edge with a 0.2-block inset, sneaks, faces the configured shaft center, and drops disposable items.

Damageable items, tools, equippable items, Elytra, fireworks, configured food, and whitelisted items are retained. Other inventory items are treated as mining products.

### Fluid handling

```text
/perimeterdig config set liquid_policy avoid
/perimeterdig config set liquid_policy replace
/perimeterdig config set liquid_policy seal_boundary
/perimeterdig config add sealing_block <block>
/perimeterdig config remove sealing_block <block>
```

The default policy is `seal_boundary`. The default sealing block is `minecraft:netherrack`. Mining may start with an empty sealing-block list.

### Food and durability

```text
/perimeterdig config add food <item>
/perimeterdig config remove food <item>
/perimeterdig config set durability_recovery_mode repair_portal
/perimeterdig config set durability_recovery_mode supply_point
```

The default food is `minecraft:enchanted_golden_apple`.

- `repair_portal`: travel through the mining-area and XP-furnace portal pairs, then use XP furnaces to repair Mending tools and Elytra.
- `supply_point`: put low-durability tools and Elytra into the supply chest, take higher-durability replacements, and equip them automatically.

A higher-durability matching replacement already in the inventory is always preferred before either remote strategy.

### Function switches

```text
/perimeterdig function
/perimeterdig function enable <function>
/perimeterdig function disable <function>
```

Available functions:

| Function | Default | Effect when disabled |
| --- | --- | --- |
| `collect_drops` | `true` | Skip the post-batch collection phase. A full inventory may still trigger unloading. |
| `unload` | `true` | Do not automatically unload. |
| `eat` | `true` | Do not automatically eat. |
| `durability_recovery` | `true` | Do not travel to a supply point or XP furnaces. Local higher-durability replacement remains enabled. |
| `cross_dimension_repair` | `true` | Travel directly to the XP furnaces in the current dimension instead of using cross-dimensional portals. |
| `resupply` | `true` | Do not replenish food or fireworks. |
| `elytra_navigation` | `true` | Disable Elytra navigation, firework-triggered resupply, and Elytra durability replacement or recovery. Tool durability handling remains active. |
| `sleep` | `false` | Do not sleep automatically at night. |

### Advanced configuration

```text
/perimeterdig config_advanced
/perimeterdig config_advanced show
/perimeterdig config_advanced set <key> <value>
```

| Group | Keys and defaults |
| --- | --- |
| Durability | `tool_durability_threshold=32`, `elytra_durability_threshold=32`, `emergency_flight_durability_threshold=5` |
| Consumables | `food_level_threshold=14`, `health_eating_threshold=16`, `food_resupply_trigger=1`, `food_resupply_target=64`, `firework_resupply_trigger=10`, `firework_resupply_target=128` |
| Mining and unloading | `drop_collection_radius=8`, `drop_collection_stable_seconds=1.5`, `inventory_reserved_slots=1`, `mining_blocks_per_empty_slot=64`, `unload_landing_search_radius=16`, `unload_edge_inset=0.2` |
| Navigation | `elytra_navigation_min_distance=32`, `navigation_stall_timeout_seconds=10`, `navigation_retry_count=2`, `flight_retry_count=2`, `portal_transition_cost=16`, `portal_transition_timeout_seconds=20` |
| Portal exit | `portal_exit_timeout_seconds=20`, `portal_exit_min_radius=3`, `portal_exit_max_radius=8`, `portal_exit_vertical_radius=4` |
| Interaction | `repair_experience_stable_seconds=1.5`, `supply_interaction_timeout_seconds=2`, `furnace_interaction_timeout_seconds=2` |

Values are range-checked, and related minimum/maximum and trigger/target values are validated together.
Keys and current values support basic Tab completion.

### Debug flows

| Command | Description |
| --- | --- |
| `/perimeterdig debug unload` | Run the unloading flow. |
| `/perimeterdig debug supply consumables` | Test taking items from the consumable supply chest. |
| `/perimeterdig debug supply durability` | Test replacement at the tool/Elytra supply point. |
| `/perimeterdig debug repair` | Test the XP-furnace repair flow. |
| `/perimeterdig debug sleep` | Test automatic sleeping. |

## Automation behavior

The default batch limit is `(empty main-inventory slots - 1) * 64`. If only one slot is empty, the limit is one block. The offhand is ignored when deciding whether the inventory is full.

After each batch, drops within an 8-block horizontal radius are collected until the inventory item count has not increased for about 1.5 seconds. After unloading, the mod returns to the recorded mining departure point before continuing.

Automatic eating starts when food level is 14 or lower, or when health is 16 or lower while hunger is not full. Food is replenished at a total count of 1 or lower to a target of 64. Fireworks are replenished at 10 or fewer to a target of 128. Consumable supply has priority over remote durability recovery.

All damageable tools in the main inventory and offhand are monitored. Equipped or carried Elytra are monitored only while `elytra_navigation` is enabled. At 32 remaining durability or lower, the mod first tries a higher-durability local replacement and then follows the configured recovery strategy. Emergency travel may continue with an Elytra above 5 remaining durability.

During XP-furnace repair, outputs are collected one at a time. The mod switches repair targets while experience is still being absorbed, advances to the next furnace after durability growth stops, and returns as soon as every target is fully repaired. With `cross_dimension_repair` disabled, only the XP-furnace coordinates are required and are interpreted in the current dimension; the trip does not use portals in either direction.

Baritone sprinting and parkour are always enabled.

Placement is disabled at every non-mining destination. Breaking is enabled while navigating to mining-area portals, but both breaking and placement are disabled during the rest of a repair trip outside the mining area.

Walking is attempted first where appropriate. Elytra is used for distant destinations or destinations that cannot be reached by walking directly upward. The navigation watchdog retries stalled routes before reporting an error.

## Configuration and localization

World-specific JSON files are stored under:

```text
config/perimeter-digger/worlds/<world-id>.json
```

Each server address and single-player save gets a separate file. Configuration is schema-versioned; old unversioned files are migrated automatically, while files from an unsupported newer schema are rejected. Runtime task progress is not checkpointed, and disconnect/reconnect task recovery is not implemented.

Language resources are stored in `assets/perimeter-digger/lang/en_us.json` and `zh_cn.json`. State history stores translation keys and arguments, so it is rendered in the currently selected client language. Build tests require both language files to have the same ordered keys and compatible placeholders.

## Building

The build expects the modified Baritone Fabric JAR at the path configured in `build.gradle`, currently:

```text
../baritone/fabric/build/libs/baritone-fabric-1.11.1-17-gb8e1048f-dirty.jar
```

On Windows:

```text
gradlew.bat clean build
```

The mod JAR is written to `build/libs/`.

## License

Perimeter Digger is available under the [MIT License](LICENSE).
