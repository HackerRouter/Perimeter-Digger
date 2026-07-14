# Perimeter Digger

[简体中文](README.zh-CN.md)

### I was too LAZY to write this README, so Codex did it for me.

------

### Dependency：[Baritone Modified](https://github.com/HackerRouter/baritone/releases)

------

Perimeter Digger is a client-side Fabric mod that automates large, vertically regular mining areas with a customized Baritone build. It supports irregular XZ footprints, liquid sealing, drop collection, unloading, consumable resupply, durability replacement, cross-dimensional repair trips, and Elytra travel.

The current target is Minecraft 26.1.1 with Fabric.

## Requirements

- Minecraft 26.1.1
- Fabric Loader 0.19.3 or newer
- Fabric API
- Java 25 or newer
- The customized Baritone build used by this project

This mod depends on API extensions and Elytra/pathing changes that are not present in an unmodified Baritone release. A standard Baritone JAR is not sufficient.

## Installation

1. Install Fabric Loader for Minecraft 26.1.1.
2. Put Fabric API, the customized Baritone JAR, and `perimeter-digger-1.0.0.jar` in the client `mods` directory.
3. Join a world or server and configure the mining area with `/perimeterdig` commands.

The mod is client-side. Commands are registered as client commands and support Tab completion.

## Main features

- Detect an irregular enclosed XZ area from a boundary block at one Y level.
- Plan an inclusive rectangular XZ area directly from coordinates.
- Mine the detected XZ columns through an inclusive regular Y range.
- Avoid, replace, or seal liquids with configurable sealing blocks.
- Mine in inventory-sized batches, then collect nearby drops before unloading.
- Navigate to vertical unloading shafts, stand at a safe edge, sneak, face downward, and discard mining products.
- Monitor all carried tools and equipped/carried Elytra for low durability.
- Replace low-durability equipment locally or travel through portals to repair it with furnace experience.
- Automatically eat configured food and replenish food and fireworks from a chest.
- Use walking or customized Elytra pathing, including ground takeoff with fireworks.
- Save configuration separately for each server or single-player world.

## Quick start

### Rectangular area

```text
/perimeterdig plan rectangle <x0> <z0> <x1> <z1> <minY> <maxY>
/perimeterdig start
```

Both corners and both Y limits are inclusive.

### Detected irregular area

1. At one Y level, surround the desired area with a single block type.
2. Stand inside the outer boundary, not on the boundary.
3. Make sure the entire boundary is inside currently loaded render chunks.
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

Cardinally or diagonally touching boundary blocks are treated as one boundary component. The outer boundary itself is excluded. For nested boundaries, the inside of the next ring is excluded, while that inner ring's block columns remain part of the mining area. Further nesting alternates by parity.

Detection has no configured area limit, but it can only inspect chunks loaded within the client's render distance.

## Commands

All commands support Tab completion.

### Runtime control

| Command | Description |
| --- | --- |
| `/perimeterdig` | Show the current automation state. |
| `/perimeterdig start` | Validate the mining area and start automation. |
| `/perimeterdig stop` | Stop automation and release Baritone control. |
| `/perimeterdig pause` | Pause active mining. |
| `/perimeterdig resume` | Resume paused mining. |
| `/perimeterdig status` | Show the current state, target, and navigation retry count. |
| `/perimeterdig status clear` | Stop automation and clear cached runtime state without deleting saved configuration. |
| `/perimeterdig reload` | Reload this world's configuration from disk and reset runtime state. |

### Area definition

| Command | Description |
| --- | --- |
| `/perimeterdig detect <block> <Y>` | Detect an enclosed irregular area at Y. |
| `/perimeterdig plan rectangle <x0> <z0> <x1> <z1> <minY> <maxY>` | Create an inclusive rectangular area and Y range. |
| `/perimeterdig config clear detected_area` | Remove the saved detected/planned area. |
| `/perimeterdig config set digging_min_y <Y>` | Set the inclusive minimum mining Y. |
| `/perimeterdig config set digging_max_y <Y>` | Set the inclusive maximum mining Y. |

### Points and routes

Every point command uses `<x> <y> <z>`.

```text
/perimeterdig config set consumable_supply_point <x> <y> <z>
/perimeterdig config set durability_supply_point <x> <y> <z>
/perimeterdig config set perimeter_portal_overworld <x> <y> <z>
/perimeterdig config set perimeter_portal_nether <x> <y> <z>
/perimeterdig config set repair_portal_overworld <x> <y> <z>
/perimeterdig config set repair_portal_nether <x> <y> <z>
/perimeterdig config set furnace_row_start <x> <y> <z>
/perimeterdig config set furnace_row_end <x> <y> <z>
```

Supply points are chest block coordinates; navigation targets the top of the chest. The furnace row must be axis-aligned. Only the first and last furnace coordinates are stored.

These points are optional for normal mining. If a related point or route is not configured, that automatic supply or repair action is skipped. Stage-specific debug commands still require all configuration used by that stage.

### Unloading points

```text
/perimeterdig config set unloading_point <name> <x> <minY> <z>
/perimeterdig config remove unloading_point <name>
```

An unloading point represents a vertical shaft at the given XZ. Its Y value is the shaft's minimum Y. Multiple named points are supported. The mod chooses a nearby safe standing block with the shortest XZ distance, preferring a Y close to the player's current Y and then the higher candidate. It moves to the edge, sneaks, looks down toward the configured shaft center, and drops disposable items.

Items are retained when they are damageable, tools, equippable items, Elytra, fireworks, food, or explicitly whitelisted. Other inventory items are treated as mining products.

```text
/perimeterdig config add unloading_whitelist <item>
/perimeterdig config remove unloading_whitelist <item>
```

### Liquid handling

```text
/perimeterdig config set liquid_policy avoid
/perimeterdig config set liquid_policy replace
/perimeterdig config set liquid_policy seal_boundary
/perimeterdig config add sealing_block <block>
/perimeterdig config remove sealing_block <block>
```

The default policy is `seal_boundary`, and the default sealing block list contains `minecraft:netherrack`. An empty sealing block list does not prevent mining from starting.

### Food and durability policy

```text
/perimeterdig config add food <item>
/perimeterdig config remove food <item>
/perimeterdig config set durability_recovery_mode repair_portal
/perimeterdig config set durability_recovery_mode supply_point
```

The default configured food is `minecraft:enchanted_golden_apple`.

- `repair_portal`: travel through the perimeter and repair portal pairs, then use furnace output experience to repair Mending equipment.
- `supply_point`: deposit low-durability equipment in the durability supply chest, take healthy replacements, and equip them.

Before either remote durability strategy, the mod first attempts to use a healthy matching replacement already in the inventory.

### Inspection and debugging

| Command | Description |
| --- | --- |
| `/perimeterdig config` | Show the current configuration. |
| `/perimeterdig config show` | Show the current configuration. |
| `/perimeterdig debug stage5` | Run only the unloading flow. |
| `/perimeterdig debug stage6 consumables` | Test consumable chest navigation and transfer. |
| `/perimeterdig debug stage6 durability` | Test durability chest replacement. |
| `/perimeterdig debug stage7` | Test the portal and furnace repair flow. |

## Automation behavior

### Mining and collection

The mining batch limit is calculated from the 36 main inventory slots:

```text
(empty slots - 1) * 64
```

If only one slot is empty, the limit is one block. The offhand slot is ignored when determining whether the inventory is full.

After a batch, the mod attempts to collect item entities within an 8-block horizontal radius. Collection ends after the total inventory item count has not increased for approximately 1.5 seconds. It then unloads and returns to the recorded mining departure point before starting the next batch.

### Consumables

- Automatic eating starts at food level 14 or lower when configured food is available.
- Food resupply triggers at a total configured-food count of 1 or lower and targets 64.
- Firework resupply triggers at 10 or fewer and targets 128.
- The consumable chest is visited before remote durability repair.

### Durability

- All damageable tool items in the main inventory and offhand are monitored.
- Carried and equipped Elytra are monitored.
- Tools and Elytra trigger replacement or recovery at 32 remaining durability or lower.
- Emergency supply/repair travel may continue using an Elytra above 5 remaining durability.
- Furnace repair returns as soon as all repair targets are fully repaired.
- Furnace outputs are taken one at a time; experience growth is checked before advancing to the next furnace and repair target.

### Navigation safety

- Baritone sprinting and parkour are enabled.
- Block placement is disabled for all non-mining destinations.
- Block breaking is enabled while navigating to perimeter portals.
- During the rest of a remote repair trip, block breaking and placement are disabled outside the mining area.
- Walking is attempted where appropriate; Elytra is used for distant or vertically unreachable destinations.
- A navigation watchdog retries stalled routes and reports an error after two failed retries.

## Configuration storage

Configuration is saved as JSON under:

```text
config/perimeter-digger/worlds/<world-id>.json
```

Each multiplayer server address and single-player save gets a separate file. Configuration persists across restarts. Active runtime progress is not checkpointed, and disconnect/reconnect task recovery is not implemented.

## Building

The Gradle build expects the customized Baritone Fabric JAR at the path declared in `build.gradle`, currently:

```text
../baritone/fabric/build/libs/baritone-fabric-1.11.1-17-gb8e1048f-dirty.jar
```

After building that Baritone project, build this mod with:

```text
./gradlew build
```

On Windows:

```text
gradlew.bat build
```

The output JAR is written to `build/libs/`.

## License

Perimeter Digger is available under the [MIT License](LICENSE).
