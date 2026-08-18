![Logo](assets/logo.png)

# Effortless Procedural

[Effortless Procedural](https://github.com/PreXentagon/effortless-procedural) is a community fork of
[Effortless Structure](https://github.com/huskuraft/effortless). It retains the original multiplatform building tools
while extending the client pattern system with named procedural recipes, gradients, seeded noise, layered masks,
placement constraints, quotas, and deterministic generation.

Procedural recipes are compiled on the client into placement data already supported by a compatible, unchanged
Effortless Structure server. No procedural rule type or fork-specific packet is required on the server. Current
development and validation focus on Minecraft 1.21.1 with NeoForge.

The implementation boundaries and extension rules are documented in the
[client generation engine guide](docs/ENGINE.md).

<div style="text-align: center">
    <a href="https://modrinth.com/mod/effortless">Upstream Modrinth</a>
    <span> | </span>
    <a href="https://www.curseforge.com/minecraft/mc-mods/effortless">Upstream CurseForge</a>
    <span> | </span>
    <a href="https://github.com/PreXentagon/effortless-procedural">Fork GitHub</a>
    <span> | </span>
    <a href="https://github.com/huskuraft/effortless">Upstream GitHub</a>
    <span> | </span>
    <a href="https://github.com/huskuraft/effortless/wiki">Upstream Documentation</a>
    <span> | </span>
    <a href="https://discord.gg/FwbBg8uUZ7">Discord</a>
</div>

## Features

- Pure Vanilla Compatibility: This mod is designed to be fully compatible with a pure vanilla game without adding new
  items or making incompatible modifications.
- Item Randomizer: This mod includes an item randomizer that lets players place blocks and entities randomly from a
  pre-defined list.
- Effortless Workbench: Create named material recipes with weighted selection, ordered gradients, seeded noise, spatial
  masks, adjacency and directional rules, spacing, quotas, cleanup, and deterministic seeds.
- Server-Compatible Compilation: Client-only procedural recipes resolve into the original server-supported pattern
  representation before placement.
- Clipboard: This mod includes a clipboard that lets players copy and paste blocks and entities between worlds.

## Platforms

- The base Effortless mod must be installed on both the client and server.
- A client running this fork can use its procedural workbench with a compatible unchanged original server because the
  generated result is compiled into the existing protocol representation.
- You can use this mod on servers with different platforms from your client.
- You can use the same mod jar file on multiple targets.

### Targets

| Filename                      | Targets                  | Fabric  |  Quilt  |  Forge  | NeoForge |
|-------------------------------|--------------------------|:-------:|:-------:|:-------:|:--------:|
| `effortless-1.21.3-3.2.0.jar` | `1.21.3` `1.21.2`        | &check; | &check; | &check; | &check;  |
| `effortless-1.21.1-3.2.0.jar` | `1.21.1` `1.21`          | &check; | &check; | &check; | &check;  |
| `effortless-1.20.6-3.2.0.jar` | `1.20.6` `1.20.5`        | &check; | &check; | &check; | &check;  |
| `effortless-1.20.4-3.2.0.jar` | `1.20.4` `1.20.3`        | &check; | &check; | &check; |          |
| `effortless-1.20.2-3.2.0.jar` | `1.20.2`                 | &check; | &check; | &check; |          |
| `effortless-1.20.1-3.2.0.jar` | `1.20.1` `1.20`          | &check; | &check; | &check; |          |
| `effortless-1.19.4-3.2.0.jar` | `1.19.4`                 | &check; | &check; | &check; |          |
| `effortless-1.19.3-3.2.0.jar` | `1.19.3`                 | &check; | &check; | &check; |          |
| `effortless-1.19.2-3.2.0.jar` | `1.19.2` `1.19.1` `1.19` | &check; | &check; | &check; |          |
| `effortless-1.18.2-3.2.0.jar` | `1.18.2`                 | &check; | &check; | &check; |          |
| `effortless-1.18.1-3.2.0.jar` | `1.18.1` `1.18`          | &check; | &check; | &check; |          |
| `effortless-1.17.1-3.2.0.jar` | `1.17.1`                 | &check; | &check; | &check; |          |
|                               | `1.17  `                 |         |         |         |          |

### Plugins

- You can use this mod together with these plugins.

| Name                                                                                                                                                                    | Supported | Note                                                   |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:---------:|--------------------------------------------------------|
| [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims)                                                                                             |  &check;  | Allows you to claim chunks and add build permissions.  |
| [FTB Chunks Fabric](https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-fabric) / [FTB Chunks Forge](https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-forge) |  &check;  | Allows you to claim chunks and add build permissions.  |
| [ViaFabric](https://github.com/ViaVersion/ViaFabric) / [ViaForge](https://github.com/ViaVersion/ViaForge)                                                               |  &check;  | Allows you to connect to a different Minecraft version |

## How to Use

- Press **LEFT_ALT/LEFT_OPTION** to open the **Effortless Workbench**. The scrollable tool rail above the live preview
  selects the real placement tool; pressing the selected tool again expands its stock features or spline/tree subtype.
  Recipes remain on the left, the active tool and recipe inspector is on the right, and **Clipboard** and **Settings**
  are available from the footer.

- Click **ATTACK/DESTROY** key to start destroying blocks.
- Click **USE_ITEM/PLACE_BLOCK** key to start placing/interacting blocks.

- Click **LEFT_BRACKET** key to perform **Undo**. You can undo your last build operation.
- Click **RIGHT_BRACKET** key to perform **Redo**. You can redo your last build operation.

### Build Modes

- Build modes are the basic shapes used to create a structure. The workbench includes every stock shape plus the
  client-compiled spline and tree generators. Each tool exposes its applicable features in the expandable rail.

- **Disable**: Place in the vanilla way.
- **Single**: Place with increased reach distance.
- **Line**: Place a line in any of the three axes.
- **Wall**: Place a wall in X or Z axis.
- **Floor**: Place a floor in Y axis.
- **Diagonal Line**: Place a line at any angle.
- **Diagonal Wall**: Place a wall at any angle.
- **Slope Floor**: Place a sloped floor at any angle.
- **Cube**: Place a cube.
- **Circle**: Place a circle shape in any of the three axes.
- **Cylinder**: Place a cylindrical shape in any of the three axes.
- **Sphere**: Place a sphere made of blocks.
- **Pyramid**: Place a pyramid made of blocks.
- **Cone**: Place a cone made of blocks.

### Replace

- You can choose how to replace blocks when placing new blocks.

- **Disable**: Replace air and replaceable blocks like grass only when placing new blocks.
- **Blocks and Air**: Replace air and blocks that can be destroyed by tools when placing new blocks.
- **Blocks Only**: Replace blocks that can be destroyed by tools only when placing new blocks.
- **Offhand Only**: Replace blocks that holding in your offhand only when placing new blocks.

### Pattern

- You can create complex shapes by combining different transformers. You can use a mirror to create a mirrored copy of a
  wall shape, or use an item randomizer to create a wall of random blocks. There are currently 4 types of transformers.

- **Mirror**: Mirrors blocks and entities for even and uneven builds.
- **Array**: Copies blocks and entities in a specific direction for a specified number of times.
- **Radial**: Places blocks and entities in a circular pattern around a central point. The circle can be divided
  into sections, and each section will contain a copy of the block placements.
- **Item Randomizer**: Randomizes the placement of blocks.

### Clipboard

- You can use clipboard to transfer structures between worlds by copying and pasting blocks.

### Transformers

## Dependencies

## Fabric

| Dependency    | Download                                                      |
|---------------|---------------------------------------------------------------|
| Fabric Loader | https://fabricmc.net/use/installer/                           |
| Fabric API    | https://www.curseforge.com/minecraft/mc-mods/fabric-api/files |

## Quilt

| Dependency         | Download                              |
|--------------------|---------------------------------------|
| Quilt Loader       | https://quiltmc.org/install/          |
| Quilted Fabric API | https://modrinth.com/mod/qsl/versions |

## Forge

| Dependency   | Download                                                   |
|--------------|------------------------------------------------------------|
| Forge Loader | https://files.minecraftforge.net/net/minecraftforge/forge/ |

## NeoForge

| Dependency      | Download                                   |
|-----------------|--------------------------------------------|
| NeoForge Loader | https://neoforged.net/categories/releases/ |

## Credits

* **[Huskuraft](https://github.com/huskuraft)** and all contributors to the
  [original Effortless Structure project](https://github.com/huskuraft/effortless), which this fork is based on
* **[Requioss](https://www.curseforge.com/members/requioss)**, the author
  of [Effortless Building](https://www.curseforge.com/minecraft/mc-mods/effortless-building)
* **[loehnertj](https://github.com/loehnertj)**, for porting to 1.20.2
* **[PreXentagon](https://github.com/PreXentagon)**, maintainer of the Effortless Procedural fork

## Development and AI Assistance

Development of this fork used AI-assisted software-development tooling, including OpenAI Codex, to accelerate
codebase analysis, implementation, automated testing, debugging, UI iteration, and documentation under limited
development time. AI-assisted changes remain subject to project review and testing; the fork maintainer retains
responsibility for release decisions, code quality, security, attribution, and license compliance.

## License

Effortless Procedural remains licensed under LGPLv3, consistent with the upstream Effortless Structure project.
