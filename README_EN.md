# ME Placement Tool for gto

> [!WARNING]
> This project contains code and documentation generated or produced with AI assistance. It may contain errors, security issues, or incompatibilities with upstream APIs and licenses. Review and test it before use, modification, or redistribution.

[中文](README_ZH.md) | [Development docs](docs/README.md) | [Changelog](CHANGELOG.md) | [Local dependency guide](libs/README.md)

This is a standalone port of ME Placement Tool `2.1.4` for Minecraft 1.20.1 Forge, GregTech Odyssey `0.5.6-beta`, and its customized Applied Energistics 2 runtime. It retains the upstream `meplacementtool` namespace and does not depend on GTOHJS.

Current version: `2.1.4-for-gtocore-0.5.6-beta`

## Compatibility

| Component | Version or range |
| --- | --- |
| Minecraft | `1.20.1` |
| Forge | `47.4.20`, runtime range `[47.4.20, 48)` |
| GTOCore | `[0.5.6-beta, 0.5.7)` |
| GTCEu | `[26.7.3, 26.8)` |
| Applied Energistics 2 | `[15.267.4, 15.268)` |
| Build JDK | Java 21 |
| Target bytecode | Java 17 |

## Added Items

| Registry ID | Purpose |
| --- | --- |
| `meplacementtool:me_placement_tool` | Places blocks, AE2 parts, facades, and source fluids from a linked ME network |
| `meplacementtool:multiblock_placement_tool` | Bulk placement with selectable counts, directions, previews, and undo |
| `meplacementtool:me_cable_placement_tool` | Line, plane-fill, and branching placement for AE2 cables |
| `meplacementtool:prism_core` | Crafting component for the Key of Spectrum |
| `meplacementtool:key_of_spectrum` | Cable-tool upgrade that enables free color selection |

All five items include models, textures, translations, and GTO-native shaped crafting recipes. The cable-tool recipe accepts every color in the `ae2:smart_dense_cable` tag.

## Linking And Power

1. Insert a placement tool into an AE2 ME Wireless Access Point to bind it. Its tooltip reports the linked state.
2. The tool stores the access point's dimension and position, resolves its grid when used, and uses both the player and access point as the AE action source.
3. All three tools are AE-powered items and charge at `800 AE/t` by default.
4. They extend AE's powered-item base directly instead of `WirelessTerminalItem`, preventing unrelated integrations from treating them as wireless terminals.

## Controls

| Tool | Action | Default input |
| --- | --- | --- |
| ME Placement Tool | Open the 18-slot configuration | Right-click air |
| ME Placement Tool | Select a configured target | Press `G` for the radial menu |
| ME Placement Tool | Place the selected block, part, facade, or fluid | Right-click a target block |
| ME Multiblock Placement Tool | Open the 18-slot configuration | Right-click air |
| ME Multiblock Placement Tool | Select target, count, and direction | Press `G` for the dual-layer radial menu |
| ME Multiblock Placement Tool | Bulk place with a client preview | Right-click a target block |
| ME Multiblock Placement Tool | Undo the latest eligible operation | Hold left `Ctrl` and left-click near it |
| ME Cable Placement Tool | Open cable, mode, and color settings | Press `G` |
| ME Cable Placement Tool | Set points or confirm placement | Right-click blocks; line mode can confirm in air after point 1 |
| ME Cable Placement Tool | Clear current points | Left-click |
| ME Cable Placement Tool | Undo the latest cable operation | Hold left `Ctrl` and left-click near it |

Bindings are configurable in Minecraft's Controls menu. The cable color-mark shortcut defaults to `A` while its GUI is open.

## ME Placement Tool

- Stores 18 ghost targets in two `3 x 3` pages.
- Supports ordinary `BlockItem` values, AE2 `IPartItem` parts, AE2 facades, and fluids that can form source blocks.
- Opens AE2's crafting-amount menu when the selected item is absent but craftable.
- Uses a simulate, reserve, place, and rollback transaction so rejected placement does not silently consume network items.
- Ignores NBT during matching by default. `ae2:facade` is strict by default so facade textures remain correct.
- When a configured AE2 memory card is held offhand, the tool attempts to apply its settings and prechecks required upgrades and patterns.

## ME Multiblock Placement Tool

- Placement counts: `1`, `8`, `64`, `256`, and `1024`.
- Directions: automatic face plane, north-south, east-west, and vertical.
- Automatic mode uses a bounded BFS; client preview and server execution share the same placement rules.
- Supports bulk blocks, AE2 parts, facades, and source fluids.
- Retains one operation per player for undo. Undo requires the matching tool, the same dimension, and a click near the placement.
- Operations that applied memory-card settings are deliberately not undoable.

## ME Cable Placement Tool

Cable families: glass, covered, smart, dense covered, and dense smart. Structure modes are line, plane fill, and three-point branching.

Color behavior:

- Without a Key of Spectrum or offhand dye, the tool places transparent Fluix cables.
- Without the upgrade, an offhand dye selects the target color. Existing cables of that color are used first; recoloring costs one dye per eight cables.
- Dye is consumed from the ME network, then the player inventory, then the offhand stack.
- With a Key of Spectrum installed, the GUI-selected color is free. Offhand dye can temporarily override it without being consumed.
- Undo removes the new center cable parts and returns cable items to ME storage; overflow returns to the player inventory.

## Configuration

Common file: `config/meplacementtool-common.toml`

| Option | Default | Meaning |
| --- | ---: | --- |
| `mePlacementToolEnergyCapacity` | `1600000` | ME Placement Tool capacity in AE |
| `mePlacementToolEnergyCost` | `50` | Cost per placement |
| `multiblockPlacementToolEnergyCapacity` | `3200000` | Multiblock tool capacity |
| `multiblockPlacementToolBaseEnergyCost` | `200` | Base cost per successful target |
| `cablePlacementToolEnergyCapacity` | `1600000` | Cable tool capacity |
| `cablePlacementToolEnergyCost` | `10` | Cost per placed cable |
| `nbtWhitelistMods` | `[]` | Mod namespaces requiring strict NBT matching |
| `nbtWhitelistItems` | `["ae2:facade"]` | Strict item IDs; supports `modid:*` |

Client file: `config/meplacementtool-client.toml`

- `hudDisplayDuration = 2000`: HUD duration in milliseconds after switching to a Mod item; `0` disables it and `-1` keeps it visible.

## GTO Adaptation

- Forge lifecycle, menus, packets, items, resources, and recipes all use the `meplacementtool` namespace.
- A minimal CoreMod registers the five recipes immediately after GTO's `RecipeFilter.init()`, preventing GTO's recipe lifecycle from discarding normal registrations.
- Load-complete validation checks items, menus, link handlers, and recipe state; dedicated-server startup validates all five final RecipeManager entries.
- Storage operations use the bound wireless access point as action host and support the customized AE2 runtime's long-count storage API.
- This Mod is independent from GTOHJS. It requires GTOCore, GTCEu, and the pack's AE2 runtime, not GTOHJS.

## Build

Third-party Mod JARs are excluded from clean source releases. Populate the active project's `libs` directory according to the [local dependency guide](libs/README.md).

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat clean build --no-daemon
```

Release JAR: `build\libs\ME-Placement-Tool-for-gto-2.1.4-for-gtocore-0.5.6-beta.jar`

See the [development documentation](docs/README.md) for architecture, adaptation, and clean-release details.

Source code is licensed under [LGPL-3.0-only](LICENSE). Third-party algorithms, UI assets, and item artwork retain their upstream terms; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
