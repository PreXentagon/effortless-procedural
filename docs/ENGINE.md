# Effortless client generation engine

The extended builder is a client-side voxel engine. It never sends a new
packet, rule, shape, tree, spline, or serializer type to the server. The final
stage always emits an ordinary clipboard snapshot understood by the original
server mod.

## Pipeline

1. A saved recipe is adapted to an immutable rule set.
2. A build tool produces explicit integer target positions and structural
   coordinates.
3. `ProceduralRecipeEngine` evaluates materials in stable traversal order.
4. `ProceduralCompositionEngine` applies ordered union, replace, subtract,
   skip, and intersect layers.
5. `StructuralStateBatchResolver` resolves directional logs, stairs, slabs,
   fences, and similar neighbor-sensitive states after geometry is complete.
6. `ExplicitSnapshotAssembler` performs bounds, memory, reach, permission,
   and volume checks and creates a stock `Snapshot`.

Preview compilation uses those same recipe, geometry, composition, and block
state stages on a small synthetic selection. `ProceduralPreviewCompiler` owns
the asynchronous model build; `ProceduralPreviewWidget` owns only caching,
camera input, clipping, and rendering.

## Shared algorithms

- `GenerationBounds` is the canonical bounds, normalization, size, center,
  and volume calculation.
- `VoxelPath.faceConnectedLine` is the canonical deterministic Manhattan
  supercover used by roads and trees. Its explicit tie order prevents release
  or JVM iteration changes from altering a result.
- `ControlPointDraft`, `ControlAxis`, and `EditorGeometry` are shared by
  in-world spline and guided-tree editors.
- `ProceduralSettingOptionsList.addVectorEntry` builds validated XYZ controls
  without three copies of update logic.

## Extension rules

New generators should return explicit cells plus optional structural geometry.
They should not construct packets. New material behavior should be expressed
as a recipe/rule-set stage. New scene behavior should be an ordered composition
operation. All randomized values must derive from the recipe seed, position,
and a stable salt; a shared mutable random stream is not allowed.

External geometry libraries are deliberately not required at present. The
engine works on a discrete Minecraft voxel grid, and its core operations are
small, deterministic, cancellation-aware algorithms. A library should only be
introduced when it replaces a well-defined subsystem (for example robust mesh
voxelization), has a compatible license, and can be shaded without changing
the multiplayer protocol or distribution requirements.

## Workbench model

The UI follows a persistent-tool/workspace model:

- recipe library on the left;
- authoritative tool, preview, and palette in the center;
- context-sensitive inspector on the right;
- searchable commands with `Ctrl+P`;
- recipe search with `Ctrl+F`;
- undo/redo and dirty/preview/layer status in the footer;
- temporary scene-layer solo/mute controls that do not alter the server
  protocol.

The command palette and persistent inspector are intentionally similar to
desktop content tools: actions stay discoverable without nesting every
operation behind another modal screen.
