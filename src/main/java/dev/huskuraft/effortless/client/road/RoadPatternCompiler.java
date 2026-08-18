package dev.huskuraft.effortless.client.road;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.clipboard.BlockData;
import dev.huskuraft.effortless.building.clipboard.Snapshot;
import dev.huskuraft.effortless.building.config.ProceduralSafetyConfig;
import dev.huskuraft.effortless.client.pattern.procedural.CoordinateLookup;
import dev.huskuraft.effortless.client.pattern.procedural.CoordinateSpace;
import dev.huskuraft.effortless.client.pattern.procedural.ExistingNeighborLookup;
import dev.huskuraft.effortless.client.pattern.procedural.ExplicitSnapshotAssembler;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationProgress;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationBounds;
import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralContextCompiler;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralCompositionEngine;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralMaterial;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralPresetAdapter;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralRecipeEngine;
import dev.huskuraft.effortless.client.pattern.procedural.StableRandom;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralStateBatchResolver;
import dev.huskuraft.effortless.client.pattern.procedural.config.PatternMaterialSource;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.Items;
import dev.huskuraft.universal.api.core.Player;

/**
 * Resolves a client-only road and procedural preset into a stock clipboard
 * snapshot. Neither road metadata nor procedural rule types enter the packet.
 */
public final class RoadPatternCompiler {

    private RoadPatternCompiler() {
    }

    public static CompilationResult compile(
            Player player,
            Context source,
            ProceduralPatternPreset preset,
            RoadVoxelizer.Result road
    ) {
        return compile(
                player,
                source,
                preset,
                road,
                true,
                ProceduralSafetyConfig.DEFAULT,
                localLibrary(preset),
                Map.of(),
                Optional.empty(), List.of(),
                Optional.empty(), Optional.empty()
        );
    }

    public static CompilationResult compile(
            Player player,
            Context source,
            ProceduralPatternPreset preset,
            RoadVoxelizer.Result road,
            ProceduralSafetyConfig safety
    ) {
        return compile(
                player, source, preset, road, true, safety,
                localLibrary(preset),
                Map.of(), Optional.empty(), List.of(),
                Optional.empty(), Optional.empty()
        );
    }

    public static CompilationResult compile(
            Player player,
            Context source,
            ProceduralPatternLibrary library,
            ProceduralPatternPreset preset,
            RoadVoxelizer.Result road,
            ProceduralSafetyConfig safety
    ) {
        var roles = SplineMaterialResolver.resolve(library, preset);
        if (!roles.isSuccess()) {
            return CompilationResult.failure(
                    "Spline material recipes are invalid", roles.errors()
            );
        }
        return compile(
                player, source, preset, road, true, safety,
                library,
                roles.recipes(), roles.damageRecipe(),
                roles.bandRecipes(), roles.cutoutWallRecipe(),
                roles.cutoutFloorRecipe()
        );
    }

    /**
     * Materializes the deterministic road output for display without applying
     * the placement-time reach check. Rendering has its own distance cutoff.
     */
    public static CompilationResult compilePreview(
            Player player,
            Context source,
            ProceduralPatternPreset preset,
            RoadVoxelizer.Result road
    ) {
        return compile(
                player,
                source,
                preset,
                road,
                false,
                ProceduralSafetyConfig.DEFAULT,
                localLibrary(preset),
                Map.of(),
                Optional.empty(), List.of(),
                Optional.empty(), Optional.empty()
        );
    }

    public static CompilationResult compilePreview(
            Player player,
            Context source,
            ProceduralPatternPreset preset,
            RoadVoxelizer.Result road,
            ProceduralSafetyConfig safety
    ) {
        return compile(
                player, source, preset, road, false, safety,
                localLibrary(preset),
                Map.of(), Optional.empty(), List.of(),
                Optional.empty(), Optional.empty()
        );
    }

    public static CompilationResult compilePreview(
            Player player,
            Context source,
            ProceduralPatternLibrary library,
            ProceduralPatternPreset preset,
            RoadVoxelizer.Result road,
            ProceduralSafetyConfig safety
    ) {
        var roles = SplineMaterialResolver.resolve(library, preset);
        if (!roles.isSuccess()) {
            return CompilationResult.failure(
                    "Spline material recipes are invalid", roles.errors()
            );
        }
        return compile(
                player, source, preset, road, false, safety,
                library,
                roles.recipes(), roles.damageRecipe(),
                roles.bandRecipes(), roles.cutoutWallRecipe(),
                roles.cutoutFloorRecipe()
        );
    }

    private static CompilationResult compile(
            Player player,
            Context source,
            ProceduralPatternPreset preset,
            RoadVoxelizer.Result road,
            boolean enforceReach,
            ProceduralSafetyConfig safety,
            ProceduralPatternLibrary library,
            Map<RoadCell.Role, ProceduralPatternPreset> rolePresets,
            Optional<ProceduralPatternPreset> damagePreset,
            List<ProceduralPatternPreset> bandPresets,
            Optional<ProceduralPatternPreset> cutoutWallPreset,
            Optional<ProceduralPatternPreset> cutoutFloorPreset
    ) {
        if (!road.isSuccess()) {
            return CompilationResult.failure(
                    "Spline geometry is invalid",
                    road.errors()
            );
        }
        if (road.cells().isEmpty()) {
            return CompilationResult.failure(
                    "Spline geometry contains no blocks",
                    List.of()
            );
        }

        var effectivePreset = preset.materialSource()
                == PatternMaterialSource.CUSTOM_PALETTE
                ? preset
                : ProceduralContextCompiler.resolvePreviewMaterials(
                        player,
                        preset
                );
        var adaptation = ProceduralPresetAdapter.adapt(effectivePreset);
        if (!adaptation.isSuccess()) {
            return CompilationResult.failure(
                    "The active pattern is invalid",
                    adaptation.errors()
            );
        }

        var bounds = GenerationBounds.enclosing(road.cells().stream()
                .map(RoadCell::position)
                .toList());
        int minX = bounds.minX();
        int minY = bounds.minY();
        int minZ = bounds.minZ();
        int maxX = bounds.maxX();
        int maxY = bounds.maxY();
        int maxZ = bounds.maxZ();

        long boxVolume = bounds.volume();
        int serverVolumeLimit = source.configs().constraintConfig()
                .maxStructureCopyPasteVolume();
        if (boxVolume > serverVolumeLimit) {
            return CompilationResult.failure(
                    "Spline bounding volume exceeds the server clipboard limit",
                    List.of(boxVolume + " blocks > "
                            + serverVolumeLimit + " allowed")
            );
        }
        if (road.cells().size() > safety.maxCompiledPositions()) {
            return CompilationResult.failure(
                    "Spline contains too many explicit blocks",
                    List.of(road.cells().size() + " blocks > "
                            + safety.maxCompiledPositions())
            );
        }
        long estimatedBytes = road.cells().size()
                * ProceduralContextCompiler.ESTIMATED_BYTES_PER_POSITION;
        if (estimatedBytes > safety.maxEstimatedMemoryBytes()) {
            return CompilationResult.failure(
                    "Spline compilation is estimated to use too much memory",
                    List.of(estimatedBytes + " estimated bytes > "
                            + safety.maxEstimatedMemoryBytes())
            );
        }

        if (enforceReach) {
            double reach = source.maxReachDistance();
            var outOfReach = road.cells().stream()
                    .map(RoadCell::position)
                    .map(RoadPatternCompiler::blockPosition)
                    .filter(position -> position.getCenter()
                            .distance(player.getEyePosition()) > reach)
                    .findFirst();
            if (outOfReach.isPresent()) {
                return CompilationResult.failure(
                        "Spline contains a block beyond the server reach limit",
                        List.of(outOfReach.get() + " is farther than "
                                + reach + " blocks")
                );
            }
        }

        var cellsByRelativePosition =
                new LinkedHashMap<GridPosition, RoadCell>();
        for (var cell : road.cells()) {
            var absolute = cell.position();
            var relative = new GridPosition(
                    absolute.x() - minX,
                    absolute.y() - minY,
                    absolute.z() - minZ
            );
            cellsByRelativePosition.put(relative, cell);
        }

        var generatedMaterials = new LinkedHashMap<
                GridPosition, ProceduralMaterial>();
        var generatedTraversal = new ArrayList<GridPosition>();
        var recipes = rolePresets.isEmpty()
                ? Map.of(
                        RoadCell.Role.SURFACE, effectivePreset,
                        RoadCell.Role.SHOULDER, effectivePreset,
                        RoadCell.Role.CURB, effectivePreset,
                        RoadCell.Role.MARKING, effectivePreset,
                        RoadCell.Role.FOUNDATION, effectivePreset
                )
                : rolePresets;
        for (var role : List.of(
                RoadCell.Role.SURFACE,
                RoadCell.Role.SHOULDER,
                RoadCell.Role.CURB,
                RoadCell.Role.MARKING,
                RoadCell.Role.FOUNDATION
        )) {
            var rolePreset = recipes.getOrDefault(role, effectivePreset);
            var rolePositions = cellsByRelativePosition.entrySet().stream()
                    .filter(entry -> entry.getValue().role() == role)
                    .map(Map.Entry::getKey)
                    .toList();
            if (rolePositions.isEmpty()) {
                continue;
            }
            var generated = generateRecipe(
                    player,
                    rolePreset,
                    "Spline " + role.name().toLowerCase(),
                    role.name(),
                    rolePositions,
                    cellsByRelativePosition,
                    road.cells().size(),
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    serverVolumeLimit,
                    safety
            );
            if (!generated.isSuccess()) {
                return CompilationResult.failure(
                        generated.message(), generated.details()
                );
            }
            generatedMaterials.putAll(generated.materials());
            generatedTraversal.addAll(generated.traversal());
        }
        for (int bandIndex = 0;
                bandIndex < preset.advanced().roadProfile()
                        .crossSectionBands().size();
                bandIndex++) {
            int selectedBand = bandIndex;
            var positions = cellsByRelativePosition.entrySet().stream()
                    .filter(entry -> entry.getValue().role()
                            == RoadCell.Role.BAND)
                    .filter(entry -> entry.getValue().bandIndex()
                            == selectedBand)
                    .map(Map.Entry::getKey)
                    .toList();
            if (positions.isEmpty()) {
                continue;
            }
            var bandPreset = bandIndex < bandPresets.size()
                    ? bandPresets.get(bandIndex) : effectivePreset;
            var generated = generateRecipe(
                    player, bandPreset,
                    "Spline band " + (bandIndex + 1),
                    "SPLINE_BAND_" + bandIndex,
                    positions, cellsByRelativePosition,
                    road.cells().size(), minX, minY, minZ,
                    maxX, maxY, maxZ, serverVolumeLimit, safety
            );
            if (!generated.isSuccess()) {
                return CompilationResult.failure(
                        generated.message(), generated.details()
                );
            }
            generatedMaterials.putAll(generated.materials());
            generatedTraversal.addAll(generated.traversal());
        }
        var selectedCutoutSurface = new HashSet<GridPosition>();
        if (damagePreset.isPresent()) {
            var overlayPositions = cellsByRelativePosition.entrySet().stream()
                    .filter(entry -> entry.getValue().role()
                            == RoadCell.Role.SURFACE
                            || entry.getValue().role()
                            == RoadCell.Role.MARKING)
                    .map(Map.Entry::getKey)
                    .toList();
            if (!overlayPositions.isEmpty()) {
                var generated = generateRecipe(
                        player,
                        damagePreset.orElseThrow(),
                        "Spline damage",
                        "SPLINE_DAMAGE",
                        overlayPositions,
                        cellsByRelativePosition,
                        road.cells().size(),
                        minX, minY, minZ,
                        maxX, maxY, maxZ,
                        serverVolumeLimit,
                        safety
                );
                if (!generated.isSuccess()) {
                    return CompilationResult.failure(
                            generated.message(), generated.details()
                    );
                }
                generated.materials().forEach((relative, material) -> {
                    if (material.kind() != ProceduralMaterial.Kind.SKIP) {
                        generatedMaterials.put(
                                relative,
                                material
                        );
                        if (material.kind()
                                == ProceduralMaterial.Kind.ERASER) {
                            selectedCutoutSurface.add(
                                    cellsByRelativePosition.get(relative)
                                            .position()
                            );
                        }
                    }
                });
            }
        }

        var absoluteRoadCells = new LinkedHashMap<
                GridPosition, RoadCell>();
        road.cells().forEach(cell ->
                absoluteRoadCells.put(cell.position(), cell)
        );
        var cutout = SplineCutoutGeometry.expand(
                selectedCutoutSurface,
                absoluteRoadCells,
                preset.advanced().roadProfile().cutout()
        );
        if (!cutout.isSuccess()) {
            return CompilationResult.failure(
                    "Spline cutout configuration is invalid",
                    cutout.errors()
            );
        }
        for (var cell : cutout.airCells()) {
            var relative = relative(
                    cell.position(), minX, minY, minZ
            );
            cellsByRelativePosition.put(relative, cell);
            generatedMaterials.put(relative, ProceduralMaterial.eraser());
            generatedTraversal.add(relative);
        }
        var cutoutRecipes = List.of(
                new CutoutRecipe(
                        cutout.wallCells(),
                        cutoutWallPreset.orElse(effectivePreset),
                        "Spline cutout walls", "SPLINE_CUTOUT_WALL"
                ),
                new CutoutRecipe(
                        cutout.floorCells(),
                        cutoutFloorPreset.orElse(effectivePreset),
                        "Spline cutout floor", "SPLINE_CUTOUT_FLOOR"
                )
        );
        for (var cutoutRecipe : cutoutRecipes) {
            var positions = new ArrayList<GridPosition>();
            for (var cell : cutoutRecipe.cells()) {
                var relative = relative(
                        cell.position(), minX, minY, minZ
                );
                cellsByRelativePosition.put(relative, cell);
                positions.add(relative);
            }
            if (positions.isEmpty()) {
                continue;
            }
            var generated = generateRecipe(
                    player, cutoutRecipe.preset(),
                    cutoutRecipe.label(), cutoutRecipe.seedSalt(),
                    positions, cellsByRelativePosition,
                    road.cells().size() + cutout.size(),
                    minX, minY, minZ, maxX, maxY, maxZ,
                    serverVolumeLimit, safety
            );
            if (!generated.isSuccess()) {
                return CompilationResult.failure(
                        generated.message(), generated.details()
                );
            }
            generatedMaterials.putAll(generated.materials());
            generatedTraversal.addAll(generated.traversal());
        }

        if (!effectivePreset.advanced().compositionLayers().isEmpty()) {
            var basePositions = generatedMaterials.entrySet().stream()
                    .filter(entry -> entry.getValue().kind()
                            != ProceduralMaterial.Kind.SKIP)
                    .map(entry -> cellsByRelativePosition.get(entry.getKey()))
                    .filter(java.util.Objects::nonNull)
                    .map(RoadCell::position)
                    .collect(java.util.stream.Collectors.toCollection(
                            java.util.LinkedHashSet::new
                    ));
            var composition = ProceduralCompositionEngine.compose(
                    basePositions,
                    road.samples(),
                    library,
                    effectivePreset,
                    Math.min(
                            serverVolumeLimit,
                            safety.maxCompiledPositions()
                    )
            );
            if (!composition.isSuccess()) {
                return CompilationResult.failure(
                        "Scene composition is invalid",
                        composition.errors()
                );
            }
            var allowed = Set.copyOf(composition.positions());
            generatedMaterials.entrySet().removeIf(entry -> {
                var cell = cellsByRelativePosition.get(entry.getKey());
                return cell == null || !allowed.contains(cell.position());
            });
            cellsByRelativePosition.entrySet().removeIf(
                    entry -> !allowed.contains(entry.getValue().position())
            );

            var additionsByPreset = new LinkedHashMap<
                    ProceduralPatternPreset, List<GridPosition>>();
            composition.additions().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            GridPosition.TRAVERSAL_ORDER
                    ))
                    .forEach(entry -> {
                        var absolute = entry.getKey();
                        var relative = relative(
                                absolute, minX, minY, minZ
                        );
                        var generatedCell = entry.getValue();
                        cellsByRelativePosition.put(
                                relative,
                                new RoadCell(
                                        absolute,
                                        RoadCell.Role.SURFACE,
                                        generatedCell.geometry()
                                )
                        );
                        additionsByPreset.computeIfAbsent(
                                generatedCell.preset(),
                                ignored -> new ArrayList<>()
                        ).add(relative);
                    });
            for (var entry : additionsByPreset.entrySet()) {
                var generated = generateRecipe(
                        player,
                        entry.getKey(),
                        "Composition layer",
                        "COMPOSITION_" + entry.getKey().id(),
                        entry.getValue(),
                        cellsByRelativePosition,
                        composition.positions().size(),
                        minX, minY, minZ, maxX, maxY, maxZ,
                        serverVolumeLimit,
                        safety
                );
                if (!generated.isSuccess()) {
                    return CompilationResult.failure(
                            generated.message(), generated.details()
                    );
                }
                generatedMaterials.putAll(generated.materials());
            }
            for (var absolute : composition.erasers()) {
                var relative = relative(absolute, minX, minY, minZ);
                cellsByRelativePosition.putIfAbsent(
                        relative,
                        new RoadCell(
                                absolute,
                                RoadCell.Role.CUTOUT_WALL,
                                StructuralGeometry.NONE
                        )
                );
                generatedMaterials.put(
                        relative,
                        ProceduralMaterial.eraser()
                );
            }
        }

        var blocks = new ArrayList<BlockData>(generatedMaterials.size());
        var air = Items.AIR.item().getBlock().getDefaultBlockState();
        for (var generation : generatedMaterials.keySet().stream()
                .sorted(GridPosition.TRAVERSAL_ORDER).toList()) {
            ProceduralMaterial material =
                    generatedMaterials.get(generation);
            if (material.kind() == ProceduralMaterial.Kind.SKIP) {
                continue;
            }
            var absolute = cellsByRelativePosition.get(generation).position();
            blocks.add(new BlockData(
                    blockPosition(absolute),
                    material.kind() == ProceduralMaterial.Kind.ERASER
                            ? air
                            : material.placeableBlock().orElseThrow()
                                    .getBlock().getDefaultBlockState(),
                    null
            ));
        }
        var geometries = new LinkedHashMap<
                GridPosition, StructuralGeometry>();
        cellsByRelativePosition.values().forEach(cell ->
                geometries.put(cell.position(), cell.geometry())
        );
        blocks = new ArrayList<>(StructuralStateBatchResolver.resolve(
                blocks, geometries
        ));
        return fromAssembly(ExplicitSnapshotAssembler.assemble(
                player,
                source,
                safety,
                new ExplicitSnapshotAssembler.Request(
                        "Procedural spline",
                        "Spline",
                        blocks,
                        enforceReach
                )
        ));
    }

    private static CompilationResult fromAssembly(
            ExplicitSnapshotAssembler.Result assembled
    ) {
        if (!assembled.isSuccess()) {
            return CompilationResult.failure(
                    assembled.message(), assembled.details()
            );
        }
        return CompilationResult.success(
                assembled.snapshot().orElseThrow(),
                assembled.anchor().orElseThrow(),
                assembled.positionCount(),
                assembled.boundingVolume(),
                assembled.estimatedMemoryBytes()
        );
    }

    private static RecipeGeneration generateRecipe(
            Player player,
            ProceduralPatternPreset configuredPreset,
            String label,
            String seedSalt,
            List<GridPosition> relativePositions,
            Map<GridPosition, RoadCell> cellsByRelativePosition,
            int cellCount,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            int serverVolumeLimit,
            ProceduralSafetyConfig safety
    ) {
        var effective = configuredPreset.materialSource()
                == PatternMaterialSource.CUSTOM_PALETTE
                ? configuredPreset
                : ProceduralContextCompiler.resolvePreviewMaterials(
                        player, configuredPreset
                );
        boolean world = effective.advanced().coordinateSpace()
                == CoordinateSpace.WORLD;
        var positions = relativePositions.stream()
                .map(position -> generationPosition(
                        position, world, minX, minY, minZ
                ))
                .toList();
        long seed = effectiveSeed(
                effective, cellCount,
                minX, minY, minZ, maxX, maxY, maxZ
        );
        seed = StableRandom.mixSeed(
                seed, StableRandom.stableStringHash(seedSalt)
        );
        var generated = ProceduralRecipeEngine.generate(
                new ProceduralRecipeEngine.Request(
                        effective,
                        label,
                        seed,
                        positions,
                        effective.inspectExistingWorld()
                                ? worldNeighbors(
                                        player, world, minX, minY, minZ
                                )
                                : ExistingNeighborLookup.NONE,
                        Math.min(
                                serverVolumeLimit,
                                safety.maxCompiledPositions()
                        ),
                        safety.maxEstimatedWork(),
                        Thread.currentThread()::isInterrupted,
                        GenerationProgress.NONE,
                        coordinates(
                                cellsByRelativePosition,
                                world, minX, minY, minZ
                        )
                )
        );
        if (!generated.isSuccess()) {
            return RecipeGeneration.failure(
                    generated.message(), generated.details()
            );
        }
        var materials = new LinkedHashMap<
                GridPosition, ProceduralMaterial>();
        generated.placements().forEach((position, material) ->
                materials.put(
                        relativePosition(
                                position, world, minX, minY, minZ
                        ),
                        material
                )
        );
        var traversal = generated.traversal().stream()
                .map(position -> relativePosition(
                        position, world, minX, minY, minZ
                ))
                .toList();
        return RecipeGeneration.success(materials, traversal);
    }

    private record CutoutRecipe(
            List<RoadCell> cells,
            ProceduralPatternPreset preset,
            String label,
            String seedSalt
    ) {
    }

    private record RecipeGeneration(
            Map<GridPosition, ProceduralMaterial> materials,
            List<GridPosition> traversal,
            String message,
            List<String> details
    ) {
        static RecipeGeneration success(
                Map<GridPosition, ProceduralMaterial> materials,
                List<GridPosition> traversal
        ) {
            return new RecipeGeneration(
                    Map.copyOf(materials), List.copyOf(traversal),
                    "", List.of()
            );
        }

        static RecipeGeneration failure(
                String message,
                List<String> details
        ) {
            return new RecipeGeneration(
                    Map.of(), List.of(), message, List.copyOf(details)
            );
        }

        boolean isSuccess() {
            return message.isEmpty();
        }
    }

    private static GridPosition relative(
            GridPosition position,
            int minX,
            int minY,
            int minZ
    ) {
        return new GridPosition(
                position.x() - minX,
                position.y() - minY,
                position.z() - minZ
        );
    }

    private static GridPosition generationPosition(
            GridPosition relative,
            boolean worldCoordinates,
            int minX,
            int minY,
            int minZ
    ) {
        return worldCoordinates
                ? new GridPosition(
                        relative.x() + minX,
                        relative.y() + minY,
                        relative.z() + minZ
                )
                : relative;
    }

    private static GridPosition relativePosition(
            GridPosition generation,
            boolean worldCoordinates,
            int minX,
            int minY,
            int minZ
    ) {
        return worldCoordinates
                ? new GridPosition(
                        generation.x() - minX,
                        generation.y() - minY,
                        generation.z() - minZ
                )
                : generation;
    }

    private static CoordinateLookup coordinates(
            Map<GridPosition, RoadCell> cellsByRelativePosition,
            boolean worldCoordinates,
            int minX,
            int minY,
            int minZ
    ) {
        return (coordinate, position) -> {
            var relative = relativePosition(
                    position, worldCoordinates, minX, minY, minZ
            );
            var cell = cellsByRelativePosition.get(relative);
            if (cell == null) {
                return OptionalDouble.empty();
            }
            var structural = cell.geometry().sample(coordinate);
            if (structural.isPresent()) {
                return structural;
            }
            return OptionalDouble.empty();
        };
    }

    private static ExistingNeighborLookup worldNeighbors(
            Player player,
            boolean worldCoordinates,
            int minX,
            int minY,
            int minZ
    ) {
        return generation -> {
            var absolute = worldCoordinates
                    ? generation
                    : new GridPosition(
                            generation.x() + minX,
                            generation.y() + minY,
                            generation.z() + minZ
                    );
            var state = player.getWorld().getBlockState(
                    blockPosition(absolute)
            );
            return Optional.of(state.getItem().getId().getString());
        };
    }

    private static GridPosition grid(BlockPosition position) {
        return new GridPosition(position.x(), position.y(), position.z());
    }

    /**
     * Compiles the road cells to a stock clipboard snapshot containing only
     * air states. The unchanged server interprets these as ordinary block
     * update operations and therefore retains its normal protection, tool,
     * permission and undo checks.
     */
    public static CompilationResult compileDestruction(
            Player player,
            Context source,
            RoadVoxelizer.Result road
    ) {
        return compileDestruction(
                player,
                source,
                road,
                ProceduralSafetyConfig.DEFAULT
        );
    }

    public static CompilationResult compileDestruction(
            Player player,
            Context source,
            RoadVoxelizer.Result road,
            ProceduralSafetyConfig safety
    ) {
        if (!road.isSuccess()) {
            return CompilationResult.failure(
                    "Road geometry is invalid",
                    road.errors()
            );
        }
        if (road.cells().isEmpty()) {
            return CompilationResult.failure(
                    "Road geometry contains no blocks",
                    List.of()
            );
        }
        var air = Items.AIR.item().getBlock().getDefaultBlockState();
        var blocks = new ArrayList<BlockData>(road.cells().size());
        for (var cell : road.cells()) {
            var absolute = cell.position();
            blocks.add(new BlockData(
                    blockPosition(absolute),
                    air,
                    null
            ));
        }
        return fromAssembly(ExplicitSnapshotAssembler.assemble(
                player,
                source,
                safety,
                new ExplicitSnapshotAssembler.Request(
                        "Procedural road clearing",
                        "Road",
                        blocks,
                        true
                )
        ));
    }

    private static BlockPosition blockPosition(GridPosition position) {
        return new BlockPosition(position.x(), position.y(), position.z());
    }

    private static ProceduralPatternLibrary localLibrary(
            ProceduralPatternPreset preset
    ) {
        return new ProceduralPatternLibrary(
                false,
                preset.id(),
                List.of(preset)
        );
    }

    private static long effectiveSeed(
            ProceduralPatternPreset preset,
            int count,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        long seed = preset.seed();
        return switch (preset.advanced().seedMode()) {
            case FIXED -> seed;
            case SHAPE -> {
                seed = StableRandom.mixSeed(seed, maxX - minX + 1L);
                seed = StableRandom.mixSeed(seed, maxY - minY + 1L);
                seed = StableRandom.mixSeed(seed, maxZ - minZ + 1L);
                yield StableRandom.mixSeed(seed, count);
            }
            case WORLD_ANCHORED -> {
                seed = StableRandom.mixSeed(seed, minX);
                seed = StableRandom.mixSeed(seed, minY);
                seed = StableRandom.mixSeed(seed, minZ);
                seed = StableRandom.mixSeed(seed, maxX);
                seed = StableRandom.mixSeed(seed, maxY);
                yield StableRandom.mixSeed(seed, maxZ);
            }
        };
    }

    public record CompilationResult(
            Optional<Snapshot> snapshot,
            Optional<BlockPosition> anchor,
            int positionCount,
            long boundingVolume,
            long estimatedMemoryBytes,
            String message,
            List<String> details
    ) {

        public CompilationResult {
            details = List.copyOf(details);
        }

        public static CompilationResult success(
                Snapshot snapshot,
                BlockPosition anchor,
                int positionCount,
                long boundingVolume,
                long estimatedMemoryBytes
        ) {
            return new CompilationResult(
                    Optional.of(snapshot),
                    Optional.of(anchor),
                    positionCount,
                    boundingVolume,
                    estimatedMemoryBytes,
                    "",
                    List.of()
            );
        }

        public static CompilationResult failure(
                String message,
                List<String> details
        ) {
            return new CompilationResult(
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    0L,
                    0L,
                    message,
                    details
            );
        }

        public boolean isSuccess() {
            return snapshot.isPresent();
        }
    }
}
