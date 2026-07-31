package dev.huskuraft.effortless.client.road;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.clipboard.BlockData;
import dev.huskuraft.effortless.building.clipboard.Snapshot;
import dev.huskuraft.effortless.building.config.ProceduralSafetyConfig;
import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;
import dev.huskuraft.effortless.client.pattern.procedural.CoordinateLookup;
import dev.huskuraft.effortless.client.pattern.procedural.CoordinateSpace;
import dev.huskuraft.effortless.client.pattern.procedural.ExistingNeighborLookup;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationProgress;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationRequest;
import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralContextCompiler;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralGenerator;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralMaterial;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralPresetAdapter;
import dev.huskuraft.effortless.client.pattern.procedural.StableRandom;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralBlockStateResolver;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;
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

        int minX = road.cells().stream()
                .mapToInt(cell -> cell.position().x()).min().orElseThrow();
        int minY = road.cells().stream()
                .mapToInt(cell -> cell.position().y()).min().orElseThrow();
        int minZ = road.cells().stream()
                .mapToInt(cell -> cell.position().z()).min().orElseThrow();
        int maxX = road.cells().stream()
                .mapToInt(cell -> cell.position().x()).max().orElseThrow();
        int maxY = road.cells().stream()
                .mapToInt(cell -> cell.position().y()).max().orElseThrow();
        int maxZ = road.cells().stream()
                .mapToInt(cell -> cell.position().z()).max().orElseThrow();

        long boxVolume = (long) (maxX - minX + 1)
                * (maxY - minY + 1) * (maxZ - minZ + 1);
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
            var effectiveRole = rolePreset.materialSource()
                    == PatternMaterialSource.CUSTOM_PALETTE
                    ? rolePreset
                    : ProceduralContextCompiler.resolvePreviewMaterials(
                            player, rolePreset
                    );
            boolean roleWorld = effectiveRole.advanced().coordinateSpace()
                    == CoordinateSpace.WORLD;
            var rolePositions = cellsByRelativePosition.entrySet().stream()
                    .filter(entry -> entry.getValue().role() == role)
                    .map(entry -> generationPosition(
                            entry.getKey(), roleWorld, minX, minY, minZ
                    ))
                    .toList();
            if (rolePositions.isEmpty()) {
                continue;
            }
            var roleAdaptation = ProceduralPresetAdapter.adapt(effectiveRole);
            if (!roleAdaptation.isSuccess()) {
                return CompilationResult.failure(
                        "Spline " + role.name().toLowerCase()
                                + " pattern is invalid",
                        roleAdaptation.errors()
                );
            }
            ExistingNeighborLookup roleNeighbors =
                    effectiveRole.inspectExistingWorld()
                    ? worldNeighbors(
                            player, roleWorld, minX, minY, minZ
                    )
                    : ExistingNeighborLookup.NONE;
            long roleSeed = effectiveSeed(
                    effectiveRole, road.cells().size(),
                    minX, minY, minZ, maxX, maxY, maxZ
            );
            roleSeed = StableRandom.mixSeed(
                    roleSeed,
                    StableRandom.stableStringHash(role.name())
            );
            var request = new GenerationRequest<>(
                    roleSeed,
                    rolePositions,
                    roleAdaptation.ruleSet().orElseThrow(),
                    roleNeighbors,
                    Math.min(
                            serverVolumeLimit,
                            safety.maxCompiledPositions()
                    ),
                    Thread.currentThread()::isInterrupted,
                    GenerationProgress.NONE,
                    coordinates(
                            cellsByRelativePosition,
                            roleWorld,
                            minX, minY, minZ
                    )
            );
            var generated = ProceduralGenerator.generate(
                    request,
                    safety.maxEstimatedWork()
            );
            if (!generated.isSuccess()) {
                var failure = generated.failure().orElseThrow();
                return CompilationResult.failure(
                        "Spline " + role.name().toLowerCase() + ": "
                                + failure.message(),
                        failure.details()
                );
            }
            generated.placements().forEach((position, material) ->
                    generatedMaterials.put(
                            relativePosition(
                                    position, roleWorld, minX, minY, minZ
                            ),
                            material
                    ));
            generated.traversal().forEach(position ->
                    generatedTraversal.add(relativePosition(
                            position, roleWorld, minX, minY, minZ
                    )));
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
            var configuredDamage = damagePreset.orElseThrow();
            var effectiveDamage = configuredDamage.materialSource()
                    == PatternMaterialSource.CUSTOM_PALETTE
                    ? configuredDamage
                    : ProceduralContextCompiler.resolvePreviewMaterials(
                            player, configuredDamage
                    );
            boolean damageWorld = effectiveDamage.advanced()
                    .coordinateSpace() == CoordinateSpace.WORLD;
            var overlayPositions = cellsByRelativePosition.entrySet().stream()
                    .filter(entry -> entry.getValue().role()
                            == RoadCell.Role.SURFACE
                            || entry.getValue().role()
                            == RoadCell.Role.MARKING)
                    .map(entry -> generationPosition(
                            entry.getKey(), damageWorld, minX, minY, minZ
                    ))
                    .toList();
            if (!overlayPositions.isEmpty()) {
                var damageAdaptation = ProceduralPresetAdapter.adapt(
                        effectiveDamage
                );
                if (!damageAdaptation.isSuccess()) {
                    return CompilationResult.failure(
                            "Spline damage pattern is invalid",
                            damageAdaptation.errors()
                    );
                }
                long damageSeed = effectiveSeed(
                        effectiveDamage, road.cells().size(),
                        minX, minY, minZ, maxX, maxY, maxZ
                );
                damageSeed = StableRandom.mixSeed(
                        damageSeed,
                        StableRandom.stableStringHash("SPLINE_DAMAGE")
                );
                var request = new GenerationRequest<>(
                        damageSeed,
                        overlayPositions,
                        damageAdaptation.ruleSet().orElseThrow(),
                        effectiveDamage.inspectExistingWorld()
                                ? worldNeighbors(
                                        player, damageWorld,
                                        minX, minY, minZ
                                )
                                : ExistingNeighborLookup.NONE,
                        Math.min(
                                serverVolumeLimit,
                                safety.maxCompiledPositions()
                        ),
                        Thread.currentThread()::isInterrupted,
                        GenerationProgress.NONE,
                        coordinates(
                                cellsByRelativePosition,
                                damageWorld,
                                minX, minY, minZ
                        )
                );
                var generated = ProceduralGenerator.generate(
                        request, safety.maxEstimatedWork()
                );
                if (!generated.isSuccess()) {
                    var failure = generated.failure().orElseThrow();
                    return CompilationResult.failure(
                            "Spline damage: " + failure.message(),
                            failure.details()
                    );
                }
                generated.placements().forEach((position, material) -> {
                    if (material.kind() != ProceduralMaterial.Kind.SKIP) {
                        var relative = relativePosition(
                                position, damageWorld,
                                minX, minY, minZ
                        );
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

        var orderedTraversal = generatedTraversal.stream()
                .distinct()
                .sorted(GridPosition.TRAVERSAL_ORDER)
                .toList();

        int snapshotMinX = cellsByRelativePosition.values().stream()
                .mapToInt(cell -> cell.position().x()).min().orElse(minX);
        int snapshotMinY = cellsByRelativePosition.values().stream()
                .mapToInt(cell -> cell.position().y()).min().orElse(minY);
        int snapshotMinZ = cellsByRelativePosition.values().stream()
                .mapToInt(cell -> cell.position().z()).min().orElse(minZ);
        int snapshotMaxX = cellsByRelativePosition.values().stream()
                .mapToInt(cell -> cell.position().x()).max().orElse(maxX);
        int snapshotMaxY = cellsByRelativePosition.values().stream()
                .mapToInt(cell -> cell.position().y()).max().orElse(maxY);
        int snapshotMaxZ = cellsByRelativePosition.values().stream()
                .mapToInt(cell -> cell.position().z()).max().orElse(maxZ);
        long finalBoxVolume = (long) (snapshotMaxX - snapshotMinX + 1)
                * (snapshotMaxY - snapshotMinY + 1)
                * (snapshotMaxZ - snapshotMinZ + 1);
        if (finalBoxVolume > serverVolumeLimit) {
            return CompilationResult.failure(
                    "Spline bounding volume exceeds the server clipboard limit",
                    List.of(finalBoxVolume + " blocks > "
                            + serverVolumeLimit + " allowed")
            );
        }
        if (generatedMaterials.size() > safety.maxCompiledPositions()) {
            return CompilationResult.failure(
                    "Spline contains too many explicit blocks",
                    List.of(generatedMaterials.size() + " blocks > "
                            + safety.maxCompiledPositions())
            );
        }
        long finalEstimatedBytes = generatedMaterials.size()
                * ProceduralContextCompiler.ESTIMATED_BYTES_PER_POSITION;
        if (finalEstimatedBytes > safety.maxEstimatedMemoryBytes()) {
            return CompilationResult.failure(
                    "Spline compilation is estimated to use too much memory",
                    List.of(finalEstimatedBytes + " estimated bytes > "
                            + safety.maxEstimatedMemoryBytes())
            );
        }
        if (enforceReach) {
            double reach = source.maxReachDistance();
            var outOfReach = cellsByRelativePosition.values().stream()
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

        var anchor = new BlockPosition(
                snapshotMinX, snapshotMinY, snapshotMinZ
        );
        var blocks = new ArrayList<BlockData>(generatedMaterials.size());
        var air = Items.AIR.item().getBlock().getDefaultBlockState();
        boolean containsPlacement = false;
        boolean containsErase = false;
        for (var generation : orderedTraversal) {
            ProceduralMaterial material =
                    generatedMaterials.get(generation);
            if (material.kind() == ProceduralMaterial.Kind.SKIP) {
                continue;
            }
            containsErase |= material.kind()
                    == ProceduralMaterial.Kind.ERASER;
            containsPlacement |= material.kind()
                    == ProceduralMaterial.Kind.BLOCK;
            var absolute = cellsByRelativePosition.get(generation).position();
            var relative = new BlockPosition(
                    absolute.x() - snapshotMinX,
                    absolute.y() - snapshotMinY,
                    absolute.z() - snapshotMinZ
            );
            blocks.add(new BlockData(
                    relative,
                    material.kind() == ProceduralMaterial.Kind.ERASER
                            ? air
                            : material.placeableBlock().orElseThrow()
                                    .getBlock().getDefaultBlockState(),
                    null
            ));
        }
        if (blocks.isEmpty()) {
            return CompilationResult.failure(
                    "Every spline cell was skipped",
                    List.of("Nothing would be sent to the server")
            );
        }
        var constraints = source.configs().constraintConfig();
        if (containsPlacement && !constraints.allowPlaceBlocks()) {
            return CompilationResult.failure(
                    "The server does not allow block placement",
                    List.of()
            );
        }
        if (containsErase && !constraints.allowBreakBlocks()) {
            return CompilationResult.failure(
                    "The server does not allow block breaking",
                    List.of()
            );
        }
        blocks = resolvePlacementStates(
                blocks, cellsByRelativePosition.values(),
                snapshotMinX, snapshotMinY, snapshotMinZ
        );
        var snapshot = new Snapshot(
                "Procedural spline",
                System.currentTimeMillis(),
                blocks
        );
        return CompilationResult.success(
                snapshot,
                anchor,
                blocks.size(),
                finalBoxVolume,
                finalEstimatedBytes
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
        var adaptation = ProceduralPresetAdapter.adapt(effective);
        if (!adaptation.isSuccess()) {
            return RecipeGeneration.failure(
                    label + " pattern is invalid", adaptation.errors()
            );
        }
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
        var request = new GenerationRequest<>(
                seed,
                positions,
                adaptation.ruleSet().orElseThrow(),
                effective.inspectExistingWorld()
                        ? worldNeighbors(player, world, minX, minY, minZ)
                        : ExistingNeighborLookup.NONE,
                Math.min(serverVolumeLimit, safety.maxCompiledPositions()),
                Thread.currentThread()::isInterrupted,
                GenerationProgress.NONE,
                coordinates(
                        cellsByRelativePosition,
                        world, minX, minY, minZ
                )
        );
        var generated = ProceduralGenerator.generate(
                request, safety.maxEstimatedWork()
        );
        if (!generated.isSuccess()) {
            var failure = generated.failure().orElseThrow();
            return RecipeGeneration.failure(
                    label + ": " + failure.message(), failure.details()
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

    private static ArrayList<BlockData> resolvePlacementStates(
            List<BlockData> blocks,
            Iterable<RoadCell> cells,
            int minX,
            int minY,
            int minZ
    ) {
        var geometries = new HashMap<GridPosition, StructuralGeometry>();
        for (var cell : cells) {
            geometries.put(
                    relative(cell.position(), minX, minY, minZ),
                    cell.geometry()
            );
        }
        var occupied = new HashSet<GridPosition>();
        var rawStates = new HashMap<GridPosition,
                dev.huskuraft.universal.api.core.BlockState>();
        for (var data : blocks) {
            var position = grid(data.blockPosition());
            if (data.blockState() != null && !data.blockState().isAir()) {
                occupied.add(position);
                rawStates.put(position, data.blockState());
            }
        }
        var resolved = new ArrayList<BlockData>(blocks.size());
        for (var data : blocks) {
            var position = grid(data.blockPosition());
            var geometry = geometries.get(position);
            if (geometry == null || data.blockState() == null
                    || data.blockState().isAir()) {
                resolved.add(data);
                continue;
            }
            resolved.add(new BlockData(
                    data.blockPosition(),
                    StructuralBlockStateResolver.resolve(
                            data.blockState(), position, geometry,
                            occupied, geometries, rawStates
                    ),
                    data.entityTag()
            ));
        }
        return resolved;
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
        if (!source.configs().constraintConfig().allowBreakBlocks()) {
            return CompilationResult.failure(
                    "The server does not allow block breaking",
                    List.of()
            );
        }

        int minX = road.cells().stream()
                .mapToInt(cell -> cell.position().x()).min().orElseThrow();
        int minY = road.cells().stream()
                .mapToInt(cell -> cell.position().y()).min().orElseThrow();
        int minZ = road.cells().stream()
                .mapToInt(cell -> cell.position().z()).min().orElseThrow();
        int maxX = road.cells().stream()
                .mapToInt(cell -> cell.position().x()).max().orElseThrow();
        int maxY = road.cells().stream()
                .mapToInt(cell -> cell.position().y()).max().orElseThrow();
        int maxZ = road.cells().stream()
                .mapToInt(cell -> cell.position().z()).max().orElseThrow();
        long boxVolume = (long) (maxX - minX + 1)
                * (maxY - minY + 1) * (maxZ - minZ + 1);
        int serverVolumeLimit = source.configs().constraintConfig()
                .maxStructureCopyPasteVolume();
        if (boxVolume > serverVolumeLimit) {
            return CompilationResult.failure(
                    "Road bounding volume exceeds the server clipboard limit",
                    List.of(boxVolume + " blocks > "
                            + serverVolumeLimit + " allowed")
            );
        }
        if (road.cells().size() > safety.maxCompiledPositions()) {
            return CompilationResult.failure(
                    "Road contains too many explicit blocks",
                    List.of(road.cells().size() + " blocks > "
                            + safety.maxCompiledPositions())
            );
        }
        long estimatedBytes = road.cells().size()
                * ProceduralContextCompiler.ESTIMATED_BYTES_PER_POSITION;
        if (estimatedBytes > safety.maxEstimatedMemoryBytes()) {
            return CompilationResult.failure(
                    "Road clearing is estimated to use too much memory",
                    List.of(estimatedBytes + " estimated bytes > "
                            + safety.maxEstimatedMemoryBytes())
            );
        }

        double reach = source.maxReachDistance();
        var outOfReach = road.cells().stream()
                .map(RoadCell::position)
                .map(RoadPatternCompiler::blockPosition)
                .filter(position -> position.getCenter()
                        .distance(player.getEyePosition()) > reach)
                .findFirst();
        if (outOfReach.isPresent()) {
            return CompilationResult.failure(
                    "Road contains a block beyond the server reach limit",
                    List.of(outOfReach.get() + " is farther than "
                            + reach + " blocks")
            );
        }

        var anchor = new BlockPosition(minX, minY, minZ);
        var air = Items.AIR.item().getBlock().getDefaultBlockState();
        var blocks = new ArrayList<BlockData>(road.cells().size());
        for (var cell : road.cells()) {
            var absolute = cell.position();
            blocks.add(new BlockData(
                    new BlockPosition(
                            absolute.x() - minX,
                            absolute.y() - minY,
                            absolute.z() - minZ
                    ),
                    air,
                    null
            ));
        }
        return CompilationResult.success(
                new Snapshot(
                        "Procedural road clearing",
                        System.currentTimeMillis(),
                        blocks
                ),
                anchor,
                blocks.size(),
                boxVolume,
                estimatedBytes
        );
    }

    private static BlockPosition blockPosition(GridPosition position) {
        return new BlockPosition(position.x(), position.y(), position.z());
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
