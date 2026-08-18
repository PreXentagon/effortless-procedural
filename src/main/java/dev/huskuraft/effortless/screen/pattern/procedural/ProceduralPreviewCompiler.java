package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.structure.BuildMode;
import dev.huskuraft.effortless.building.structure.builder.Structure;
import dev.huskuraft.effortless.client.generator.ClientToolSubtype;
import dev.huskuraft.effortless.client.pattern.procedural.CoordinateLookup;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationProgress;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationBounds;
import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralCompositionEngine;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralMaterial;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralRecipeEngine;
import dev.huskuraft.effortless.client.pattern.procedural.StableRandom;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.road.RoadCell;
import dev.huskuraft.effortless.client.road.RoadPoint;
import dev.huskuraft.effortless.client.road.RoadProfile;
import dev.huskuraft.effortless.client.road.RoadSpline;
import dev.huskuraft.effortless.client.road.RoadVoxelizer;
import dev.huskuraft.effortless.client.road.SplineCutoutGeometry;
import dev.huskuraft.effortless.client.road.SplineMaterialResolver;
import dev.huskuraft.effortless.client.road.SplineSubtype;
import dev.huskuraft.effortless.client.tree.TreeArchetype;
import dev.huskuraft.effortless.client.tree.TreeCell;
import dev.huskuraft.effortless.client.tree.TreeGenerationConfig;
import dev.huskuraft.effortless.client.tree.TreeMaterialResolver;
import dev.huskuraft.effortless.client.tree.TreeProfile;
import dev.huskuraft.effortless.client.tree.TreeVoxelizer;
import dev.huskuraft.effortless.screen.pattern.procedural.ProceduralPreviewWidget.PreviewGeometry;
import dev.huskuraft.effortless.screen.pattern.procedural.ProceduralPreviewWidget.PreviewKey;
import dev.huskuraft.effortless.screen.pattern.procedural.ProceduralPreviewWidget.PreviewModel;
import dev.huskuraft.universal.api.core.BlockInteraction;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.Direction;

/**
 * Pure preview compilation pipeline. The widget owns only cache/camera/render
 * state; this class owns synthetic geometry evaluation, role recipes,
 * cutouts, composition layers and deterministic material generation.
 */
final class ProceduralPreviewCompiler {

    private ProceduralPreviewCompiler() {
    }

    static ProceduralPatternPreset applySubtype(
            ProceduralPatternPreset value,
            ProceduralPreviewType previewType,
            ClientToolSubtype selected
    ) {
        if (previewType.isRoad()
                && selected instanceof SplineSubtype subtype) {
            return value.withAdvanced(value.advanced().withRoadProfile(
                    value.advanced().roadProfile()
                            .withSubtypePreservingGeometry(subtype)
            ));
        }
        if (previewType.isTree()
                && selected instanceof TreeArchetype archetype) {
            return value.withAdvanced(value.advanced().withTreeGeneration(
                    value.advanced().treeGeneration()
                            .withArchetypePreservingGeometry(archetype)
            ));
        }
        return value;
    }

    static PreviewModel generate(PreviewKey key, Monitor monitor) {
        try {
            var geometry = key.type().isRoad()
                    ? roadGeometry(
                            key.preset().advanced().roadProfile()
                    )
                    : key.type().isTree()
                            ? treeGeometry(
                                    key.preset().advanced().treeGeneration(),
                                    key.preset().seed()
                            )
                            : new PreviewGeometry(
                                    positions(
                                            key.type(),
                                            key.orientation(),
                                            key.structure()
                                    ),
                                    CoordinateLookup.NONE,
                                    Map.of(), Map.of(), Map.of(), Map.of(),
                                    List.of()
                            );
            var positions = geometry.positions();
            monitor.setTotal(positions.size());
            if (key.type().isTree()) {
                return generateTreeMaterials(key, monitor, geometry);
            } else if (key.type().isRoad()) {
                return generateSplineMaterials(key, monitor, geometry);
            }
            return generateOrdinaryMaterials(key, monitor, geometry);
        } catch (RuntimeException exception) {
            return PreviewModel.failure(
                    exception.getClass().getSimpleName() + ": "
                            + exception.getMessage()
            );
        }
    }

    private static PreviewModel generateOrdinaryMaterials(
            PreviewKey key,
            Monitor monitor,
            PreviewGeometry geometry
    ) {
        var generated = ProceduralRecipeEngine.generate(
                ProceduralRecipeEngine.Request.preview(
                        key.preset(),
                        "Preview",
                        key.preset().seed(),
                        geometry.positions(),
                        monitor::cancelled,
                        (stage, completed, total) -> {
                            monitor.setCompleted(completed);
                            monitor.setTotal(total);
                        },
                        geometry.coordinates()
                )
        );
        if (!generated.isSuccess()) {
            return PreviewModel.failure(generated.formattedError());
        }
        return compose(
                key, monitor, geometry, generated.placements(),
                geometry.treeCells(), geometry.roadCells()
        );
    }

    private static PreviewModel generateTreeMaterials(
            PreviewKey key,
            Monitor monitor,
            PreviewGeometry geometry
    ) {
        var resolution = TreeMaterialResolver.resolve(
                key.library(), key.preset()
        );
        if (!resolution.isSuccess()) {
            return PreviewModel.failure(
                    String.join("; ", resolution.errors())
            );
        }
        var placements = new LinkedHashMap<
                GridPosition, ProceduralMaterial>();
        int completedRoles = 0;
        for (var role : TreeCell.Role.values()) {
            var rolePositions = geometry.positions().stream()
                    .filter(position -> geometry.treeRoles().get(position)
                            == role)
                    .toList();
            if (rolePositions.isEmpty()) {
                continue;
            }
            var rolePreset = resolution.recipes().getOrDefault(
                    role, key.preset()
            );
            int roleOffset = completedRoles;
            var generated = ProceduralRecipeEngine.generate(
                    ProceduralRecipeEngine.Request.preview(
                            rolePreset,
                            role.name(),
                            rolePreset.seed(),
                            rolePositions,
                            monitor::cancelled,
                            (stage, completed, total) -> monitor.setCompleted(
                                    Math.min(
                                            geometry.positions().size(),
                                            roleOffset + completed
                                    )
                            ),
                            geometry.coordinates()
                    )
            );
            if (!generated.isSuccess()) {
                return PreviewModel.failure(
                        generated.formattedError()
                );
            }
            placements.putAll(generated.placements());
            completedRoles += rolePositions.size();
            monitor.setCompleted(completedRoles);
        }
        return compose(
                key, monitor, geometry, placements,
                geometry.treeCells(), geometry.roadCells()
        );
    }

    private static PreviewModel generateSplineMaterials(
            PreviewKey key,
            Monitor monitor,
            PreviewGeometry geometry
    ) {
        var resolution = SplineMaterialResolver.resolve(
                key.library(), key.preset()
        );
        if (!resolution.isSuccess()) {
            return PreviewModel.failure(
                    String.join("; ", resolution.errors())
            );
        }
        var placements = new LinkedHashMap<
                GridPosition, ProceduralMaterial>();
        var cells = new LinkedHashMap<>(geometry.roadCells());
        for (var role : List.of(
                RoadCell.Role.SURFACE,
                RoadCell.Role.SHOULDER,
                RoadCell.Role.CURB,
                RoadCell.Role.MARKING,
                RoadCell.Role.FOUNDATION
        )) {
            var positions = cells.entrySet().stream()
                    .filter(entry -> entry.getValue().role() == role)
                    .map(Map.Entry::getKey)
                    .toList();
            var generated = generateRecipe(
                    resolution.recipes().getOrDefault(role, key.preset()),
                    role.name(), role.name(), positions, cells, monitor
            );
            if (!generated.isSuccess()) {
                return PreviewModel.failure(generated.error());
            }
            placements.putAll(generated.placements());
        }

        var bands = key.preset().advanced().roadProfile()
                .crossSectionBands();
        for (int index = 0; index < bands.size(); index++) {
            int bandIndex = index;
            var positions = cells.entrySet().stream()
                    .filter(entry -> entry.getValue().role()
                            == RoadCell.Role.BAND)
                    .filter(entry -> entry.getValue().bandIndex()
                            == bandIndex)
                    .map(Map.Entry::getKey)
                    .toList();
            var bandPreset = index < resolution.bandRecipes().size()
                    ? resolution.bandRecipes().get(index) : key.preset();
            var generated = generateRecipe(
                    bandPreset,
                    "BAND " + (index + 1),
                    "SPLINE_BAND_" + index,
                    positions, cells, monitor
            );
            if (!generated.isSuccess()) {
                return PreviewModel.failure(generated.error());
            }
            placements.putAll(generated.placements());
        }

        var selectedCutoutCells = new HashSet<GridPosition>();
        if (resolution.damageRecipe().isPresent()) {
            var positions = cells.keySet().stream()
                    .filter(position -> {
                        var role = cells.get(position).role();
                        return role == RoadCell.Role.SURFACE
                                || role == RoadCell.Role.MARKING;
                    })
                    .toList();
            var generated = generateRecipe(
                    resolution.damageRecipe().orElseThrow(),
                    "DAMAGE", "SPLINE_DAMAGE", positions, cells, monitor
            );
            if (!generated.isSuccess()) {
                return PreviewModel.failure(generated.error());
            }
            generated.placements().forEach((position, material) -> {
                if (material.kind() != ProceduralMaterial.Kind.SKIP) {
                    placements.put(position, material);
                    if (material.kind() == ProceduralMaterial.Kind.ERASER) {
                        selectedCutoutCells.add(position);
                    }
                }
            });
        }

        var cutout = SplineCutoutGeometry.expand(
                selectedCutoutCells,
                cells,
                key.preset().advanced().roadProfile().cutout()
        );
        if (!cutout.isSuccess()) {
            return PreviewModel.failure(
                    String.join("; ", cutout.errors())
            );
        }
        for (var cell : cutout.airCells()) {
            cells.put(cell.position(), cell);
            placements.put(cell.position(), ProceduralMaterial.eraser());
        }
        var cutoutRecipes = List.of(
                new CutoutRecipe(
                        cutout.wallCells(),
                        resolution.cutoutWallRecipe().orElse(key.preset()),
                        "CUTOUT WALLS", "SPLINE_CUTOUT_WALL"
                ),
                new CutoutRecipe(
                        cutout.floorCells(),
                        resolution.cutoutFloorRecipe().orElse(key.preset()),
                        "CUTOUT FLOOR", "SPLINE_CUTOUT_FLOOR"
                )
        );
        for (var recipe : cutoutRecipes) {
            recipe.cells().forEach(cell -> cells.put(cell.position(), cell));
            var positions = recipe.cells().stream()
                    .map(RoadCell::position)
                    .toList();
            var generated = generateRecipe(
                    recipe.preset(), recipe.label(), recipe.seedSalt(),
                    positions, cells, monitor
            );
            if (!generated.isSuccess()) {
                return PreviewModel.failure(generated.error());
            }
            placements.putAll(generated.placements());
        }
        return compose(
                key, monitor, geometry, placements,
                geometry.treeCells(), cells
        );
    }

    private static PreviewModel compose(
            PreviewKey key,
            Monitor monitor,
            PreviewGeometry geometry,
            Map<GridPosition, ProceduralMaterial> sourcePlacements,
            Map<GridPosition, TreeCell> sourceTreeCells,
            Map<GridPosition, RoadCell> sourceRoadCells
    ) {
        var placements = new LinkedHashMap<>(sourcePlacements);
        var treeCells = new LinkedHashMap<>(sourceTreeCells);
        var roadCells = new LinkedHashMap<>(sourceRoadCells);
        if (key.preset().advanced().compositionLayers().isEmpty()) {
            return new PreviewModel(
                    placements,
                    placements.keySet().stream()
                            .sorted(GridPosition.TRAVERSAL_ORDER)
                            .toList(),
                    "", treeCells, roadCells
            );
        }
        var basePositions = placements.entrySet().stream()
                .filter(entry -> entry.getValue().kind()
                        != ProceduralMaterial.Kind.SKIP)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(
                        java.util.LinkedHashSet::new
                ));
        var composition = ProceduralCompositionEngine.compose(
                basePositions,
                geometry.pathSamples(),
                key.library(),
                key.preset(),
                key.maximumPositions(),
                monitor::cancelled
        );
        if (!composition.isSuccess()) {
            return PreviewModel.failure(
                    "Composition: "
                            + String.join("; ", composition.errors())
            );
        }
        var allowed = new HashSet<>(composition.positions());
        placements.keySet().removeIf(position -> !allowed.contains(position));
        treeCells.keySet().removeIf(position -> !allowed.contains(position));
        roadCells.keySet().removeIf(position -> !allowed.contains(position));

        var additionsByPreset = new LinkedHashMap<
                ProceduralPatternPreset, List<GridPosition>>();
        composition.additions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        GridPosition.TRAVERSAL_ORDER
                ))
                .forEach(entry -> {
                    var position = entry.getKey();
                    var generated = entry.getValue();
                    roadCells.put(position, new RoadCell(
                            position,
                            RoadCell.Role.SURFACE,
                            generated.geometry()
                    ));
                    additionsByPreset.computeIfAbsent(
                            generated.preset(), ignored -> new java.util.ArrayList<>()
                    ).add(position);
                });
        for (var entry : additionsByPreset.entrySet()) {
            var generated = generateRecipe(
                    entry.getKey(),
                    "COMPOSITION",
                    "COMPOSITION_" + entry.getKey().id(),
                    entry.getValue(), roadCells, monitor
            );
            if (!generated.isSuccess()) {
                return PreviewModel.failure(generated.error());
            }
            placements.putAll(generated.placements());
        }
        composition.erasers().forEach(position ->
                placements.put(position, ProceduralMaterial.eraser())
        );
        return new PreviewModel(
                placements,
                composition.positions().stream()
                        .sorted(GridPosition.TRAVERSAL_ORDER)
                        .toList(),
                "", treeCells, roadCells
        );
    }

    private static RecipeResult generateRecipe(
            ProceduralPatternPreset preset,
            String label,
            String seedSalt,
            List<GridPosition> positions,
            Map<GridPosition, RoadCell> cells,
            Monitor monitor
    ) {
        if (positions.isEmpty()) {
            return RecipeResult.success(Map.of());
        }
        long seed = StableRandom.mixSeed(
                preset.seed(), StableRandom.stableStringHash(seedSalt)
        );
        CoordinateLookup coordinates = (coordinate, position) -> {
            var cell = cells.get(position);
            return cell == null
                    ? OptionalDouble.empty()
                    : cell.geometry().sample(coordinate);
        };
        var generated = ProceduralRecipeEngine.generate(
                ProceduralRecipeEngine.Request.preview(
                        preset, label, seed, positions,
                        monitor::cancelled,
                        GenerationProgress.NONE,
                        coordinates
                )
        );
        if (!generated.isSuccess()) {
            return RecipeResult.failure(generated.formattedError());
        }
        monitor.addCompleted(positions.size());
        return RecipeResult.success(generated.placements());
    }

    interface Monitor {
        boolean cancelled();

        void setCompleted(int value);

        void addCompleted(int value);

        void setTotal(int value);
    }

    static List<GridPosition> positions(
            ProceduralPreviewType type,
            PreviewOrientation orientation,
            Structure structure
    ) {
        var context = Context.defaultSet().withStructure(structure);
        for (var anchor : anchors(type.stockMode(), orientation)) {
            context = context.withNextInteraction(interaction(anchor));
        }
        var absolute = structure.collect(context).toList();
        if (absolute.isEmpty()) {
            return List.of(new GridPosition(0, 0, 0));
        }
        var positions = absolute.stream()
                .map(position -> new GridPosition(
                        position.x(), position.y(), position.z()
                ))
                .distinct()
                .toList();
        var bounds = GenerationBounds.enclosing(positions);
        return positions.stream().map(bounds::normalize).toList();
    }

    private static List<BlockPosition> anchors(
            BuildMode mode,
            PreviewOrientation orientation
    ) {
        var origin = new BlockPosition(0, 0, 0);
        return switch (mode) {
            case SINGLE -> List.of(origin);
            case LINE -> List.of(origin, switch (orientation) {
                case Y -> new BlockPosition(0, 11, 0);
                case Z -> new BlockPosition(0, 0, 15);
                default -> new BlockPosition(15, 0, 0);
            });
            case WALL -> List.of(
                    origin,
                    orientation == PreviewOrientation.XY
                            ? new BlockPosition(18, 11, 0)
                            : new BlockPosition(0, 11, 18)
            );
            case FLOOR -> List.of(origin, new BlockPosition(15, 0, 15));
            case CUBOID -> List.of(
                    origin,
                    new BlockPosition(8, 8, 0),
                    new BlockPosition(8, 8, 8)
            );
            case DIAGONAL_LINE -> List.of(origin, switch (orientation) {
                case XZ -> new BlockPosition(12, 0, 12);
                case YZ -> new BlockPosition(0, 8, 12);
                case XYZ -> new BlockPosition(10, 7, 10);
                default -> new BlockPosition(12, 8, 0);
            });
            case DIAGONAL_WALL -> List.of(
                    origin,
                    new BlockPosition(13, 0, 10),
                    new BlockPosition(13, 8, 10)
            );
            case SLOPE_FLOOR -> List.of(
                    origin,
                    new BlockPosition(12, 0, 8),
                    new BlockPosition(12, 7, 8)
            );
            case CIRCLE -> List.of(origin, switch (orientation) {
                case XY -> new BlockPosition(13, 9, 0);
                case XZ -> new BlockPosition(13, 0, 13);
                default -> new BlockPosition(0, 9, 13);
            });
            case CYLINDER -> switch (orientation) {
                case X -> List.of(
                        origin,
                        new BlockPosition(0, 8, 8),
                        new BlockPosition(9, 8, 8)
                );
                case Z -> List.of(
                        origin,
                        new BlockPosition(8, 8, 0),
                        new BlockPosition(8, 8, 9)
                );
                default -> List.of(
                        origin,
                        new BlockPosition(8, 0, 8),
                        new BlockPosition(8, 9, 8)
                );
            };
            case SPHERE -> List.of(
                    origin,
                    new BlockPosition(8, 0, 8),
                    new BlockPosition(8, 8, 8)
            );
            case PYRAMID, CONE -> List.of(
                    origin,
                    new BlockPosition(10, 0, 10),
                    new BlockPosition(10, 9, 10)
            );
            case DISABLED -> List.of(origin);
        };
    }

    static PreviewGeometry roadGeometry(RoadProfile profile) {
        var road = RoadVoxelizer.voxelize(
                List.of(
                        new RoadPoint(0.5, 2.5, 0.5),
                        new RoadPoint(7.5, 2.5, 0.5),
                        new RoadPoint(13.5, 2.5, 8.5),
                        new RoadPoint(20.5, 2.5, 8.5)
                ),
                profile
        );
        if (!road.isSuccess()) {
            throw new IllegalArgumentException(
                    String.join("; ", road.errors())
            );
        }
        var bounds = GenerationBounds.enclosing(road.cells().stream()
                .map(RoadCell::position)
                .toList());
        var normalizedCells = new LinkedHashMap<GridPosition, RoadCell>();
        for (var cell : road.cells()) {
            var normalized = bounds.normalize(cell.position());
            normalizedCells.put(normalized, new RoadCell(
                    normalized, cell.role(), cell.geometry(),
                    cell.bandIndex()
            ));
        }
        CoordinateLookup coordinates = (coordinate, position) -> {
            var cell = normalizedCells.get(position);
            return cell == null
                    ? OptionalDouble.empty()
                    : cell.geometry().sample(coordinate);
        };
        var roles = new LinkedHashMap<GridPosition, RoadCell.Role>();
        normalizedCells.forEach((position, cell) ->
                roles.put(position, cell.role()));
        var normalizedSamples = road.samples().stream()
                .map(sample -> new RoadSpline.Sample(
                        new RoadPoint(
                                sample.point().x() - bounds.minX(),
                                sample.point().y() - bounds.minY(),
                                sample.point().z() - bounds.minZ()
                        ),
                        sample.tangent(),
                        sample.progress(),
                        sample.segmentIndex(),
                        sample.segmentT()
                ))
                .toList();
        return new PreviewGeometry(
                List.copyOf(normalizedCells.keySet()),
                coordinates,
                Map.of(), roles, normalizedCells, Map.of(),
                normalizedSamples
        );
    }

    static PreviewGeometry treeGeometry(TreeProfile profile) {
        return treeGeometry(TreeGenerationConfig.legacySkeleton(profile), 0L);
    }

    static PreviewGeometry treeGeometry(
            TreeGenerationConfig config,
            long seed
    ) {
        double expectedHeight = (
                config.minimumHeight() + config.maximumHeight()
        ) * 0.5;
        var tree = TreeVoxelizer.generateGuided(
                List.of(
                        new RoadPoint(0.5, 0.5, 0.5),
                        new RoadPoint(0.5, expectedHeight + 0.5, 0.5)
                ),
                config,
                seed,
                config.variant(),
                100_000
        );
        if (!tree.isSuccess()) {
            throw new IllegalArgumentException(
                    String.join("; ", tree.errors())
            );
        }
        var bounds = GenerationBounds.enclosing(tree.cells().stream()
                .map(TreeCell::position)
                .toList());
        var normalizedCells = new LinkedHashMap<GridPosition, TreeCell>();
        var roles = new LinkedHashMap<GridPosition, TreeCell.Role>();
        for (var cell : tree.cells()) {
            var normalized = bounds.normalize(cell.position());
            normalizedCells.put(normalized, new TreeCell(
                    normalized, cell.role(), cell.geometry()
            ));
            roles.put(normalized, cell.role());
        }
        CoordinateLookup coordinates = (coordinate, position) -> {
            var cell = normalizedCells.get(position);
            return cell == null
                    ? OptionalDouble.empty()
                    : cell.geometry().sample(coordinate);
        };
        var normalizedSamples = tree.asRoadResult().samples().stream()
                .map(sample -> new RoadSpline.Sample(
                        new RoadPoint(
                                sample.point().x() - bounds.minX(),
                                sample.point().y() - bounds.minY(),
                                sample.point().z() - bounds.minZ()
                        ),
                        sample.tangent(),
                        sample.progress(),
                        sample.segmentIndex(),
                        sample.segmentT()
                ))
                .toList();
        return new PreviewGeometry(
                List.copyOf(normalizedCells.keySet()),
                coordinates,
                roles, Map.of(), Map.of(), normalizedCells,
                normalizedSamples
        );
    }

    private static BlockInteraction interaction(BlockPosition position) {
        return new BlockInteraction(
                position.getCenter(), Direction.UP, position, true
        );
    }

    private record CutoutRecipe(
            List<RoadCell> cells,
            ProceduralPatternPreset preset,
            String label,
            String seedSalt
    ) {
    }

    private record RecipeResult(
            Map<GridPosition, ProceduralMaterial> placements,
            String error
    ) {
        static RecipeResult success(
                Map<GridPosition, ProceduralMaterial> placements
        ) {
            return new RecipeResult(Map.copyOf(placements), "");
        }

        static RecipeResult failure(String error) {
            return new RecipeResult(Map.of(), error);
        }

        boolean isSuccess() {
            return error.isEmpty();
        }
    }
}
