package dev.huskuraft.effortless.client.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.clipboard.BlockData;
import dev.huskuraft.effortless.building.clipboard.Snapshot;
import dev.huskuraft.effortless.building.config.ProceduralSafetyConfig;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralCompositionEngine;
import dev.huskuraft.effortless.client.pattern.procedural.ExplicitSnapshotAssembler;
import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralStateBatchResolver;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.road.RoadCell;
import dev.huskuraft.effortless.client.road.RoadPatternCompiler;
import dev.huskuraft.effortless.client.road.RoadVoxelizer;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.Items;
import dev.huskuraft.universal.api.core.Player;

/**
 * Tree-named facade over the generic explicit-cell compatibility compiler.
 */
public final class TreePatternCompiler {

    private TreePatternCompiler() {
    }

    public static RoadPatternCompiler.CompilationResult compile(
            Player player,
            Context source,
            ProceduralPatternPreset preset,
            TreeVoxelizer.Result tree,
            ProceduralSafetyConfig safety
    ) {
        return relabel(
                RoadPatternCompiler.compile(
                        player,
                        source,
                        preset,
                        tree.asRoadResult(),
                        safety
                ),
                "Procedural tree"
        );
    }

    public static RoadPatternCompiler.CompilationResult compile(
            Player player,
            Context source,
            ProceduralPatternPreset treePreset,
            Map<TreeCell.Role, ProceduralPatternPreset> recipes,
            TreeVoxelizer.Result tree,
            ProceduralSafetyConfig safety
    ) {
        return compileRoles(
                player, source, localLibrary(treePreset, recipes),
                treePreset, recipes, tree, safety, false
        );
    }

    public static RoadPatternCompiler.CompilationResult compile(
            Player player,
            Context source,
            ProceduralPatternLibrary library,
            ProceduralPatternPreset treePreset,
            Map<TreeCell.Role, ProceduralPatternPreset> recipes,
            TreeVoxelizer.Result tree,
            ProceduralSafetyConfig safety
    ) {
        return compileRoles(
                player, source, library, treePreset, recipes,
                tree, safety, false
        );
    }

    public static RoadPatternCompiler.CompilationResult compilePreview(
            Player player,
            Context source,
            ProceduralPatternPreset preset,
            TreeVoxelizer.Result tree,
            ProceduralSafetyConfig safety
    ) {
        return relabel(
                RoadPatternCompiler.compilePreview(
                        player,
                        source,
                        preset,
                        tree.asRoadResult(),
                        safety
                ),
                "Procedural tree preview"
        );
    }

    public static RoadPatternCompiler.CompilationResult compilePreview(
            Player player,
            Context source,
            ProceduralPatternPreset treePreset,
            Map<TreeCell.Role, ProceduralPatternPreset> recipes,
            TreeVoxelizer.Result tree,
            ProceduralSafetyConfig safety
    ) {
        return compileRoles(
                player, source, localLibrary(treePreset, recipes),
                treePreset, recipes, tree, safety, true
        );
    }

    public static RoadPatternCompiler.CompilationResult compilePreview(
            Player player,
            Context source,
            ProceduralPatternLibrary library,
            ProceduralPatternPreset treePreset,
            Map<TreeCell.Role, ProceduralPatternPreset> recipes,
            TreeVoxelizer.Result tree,
            ProceduralSafetyConfig safety
    ) {
        return compileRoles(
                player, source, library, treePreset, recipes,
                tree, safety, true
        );
    }

    public static RoadPatternCompiler.CompilationResult compileDestruction(
            Player player,
            Context source,
            TreeVoxelizer.Result tree,
            ProceduralSafetyConfig safety
    ) {
        return relabel(
                RoadPatternCompiler.compileDestruction(
                        player,
                        source,
                        tree.asRoadResult(),
                        safety
                ),
                "Procedural tree clearing"
        );
    }

    private static RoadPatternCompiler.CompilationResult compileRoles(
            Player player,
            Context source,
            ProceduralPatternLibrary library,
            ProceduralPatternPreset treePreset,
            Map<TreeCell.Role, ProceduralPatternPreset> recipes,
            TreeVoxelizer.Result tree,
            ProceduralSafetyConfig safety,
            boolean preview
    ) {
        if (!tree.isSuccess()) {
            return RoadPatternCompiler.CompilationResult.failure(
                    "Tree geometry is invalid",
                    tree.errors()
            );
        }
        var absolute = new LinkedHashMap<BlockPosition, BlockData>();
        for (var role : TreeCell.Role.values()) {
            var cells = tree.cells().stream()
                    .filter(cell -> cell.role() == role)
                    .toList();
            if (cells.isEmpty()) {
                continue;
            }
            var roleTree = TreeVoxelizer.Result.success(cells, List.of());
            var preset = withoutComposition(
                    recipes.getOrDefault(role, treePreset)
            );
            var compiled = preview
                    ? RoadPatternCompiler.compilePreview(
                            player, source, preset,
                            roleTree.asRoadResult(), safety
                    )
                    : RoadPatternCompiler.compile(
                            player, source, preset,
                            roleTree.asRoadResult(), safety
                    );
            if (!compiled.isSuccess()) {
                if ("Every road cell was skipped".equals(
                        compiled.message()
                )) {
                    continue;
                }
                return relabel(compiled, "Procedural tree");
            }
            var anchor = compiled.anchor().orElseThrow();
            for (var data : compiled.snapshot().orElseThrow().blockData()) {
                var position = anchor.add(data.blockPosition());
                absolute.put(position, new BlockData(
                        position,
                        data.blockState(),
                        data.entityTag()
                ));
            }
        }
        var compositionGeometries = new HashMap<
                GridPosition, StructuralGeometry>();
        if (!treePreset.advanced().compositionLayers().isEmpty()) {
            var basePositions = absolute.keySet().stream()
                    .map(TreePatternCompiler::grid)
                    .collect(java.util.stream.Collectors.toCollection(
                            LinkedHashSet::new
                    ));
            var composition = ProceduralCompositionEngine.compose(
                    basePositions,
                    tree.asRoadResult().samples(),
                    library,
                    treePreset,
                    Math.min(
                            source.configs().constraintConfig()
                                    .maxStructureCopyPasteVolume(),
                            safety.maxCompiledPositions()
                    )
            );
            if (!composition.isSuccess()) {
                return RoadPatternCompiler.CompilationResult.failure(
                        "Tree composition is invalid",
                        composition.errors()
                );
            }
            var allowed = new HashSet<>(composition.positions());
            absolute.entrySet().removeIf(entry ->
                    !allowed.contains(grid(entry.getKey()))
            );
            var additionsByPreset = new LinkedHashMap<
                    ProceduralPatternPreset, List<RoadCell>>();
            composition.additions().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            GridPosition.TRAVERSAL_ORDER
                    ))
                    .forEach(entry -> {
                        var generated = entry.getValue();
                        compositionGeometries.put(
                                entry.getKey(), generated.geometry()
                        );
                        additionsByPreset.computeIfAbsent(
                                generated.preset(),
                                ignored -> new ArrayList<>()
                        ).add(new RoadCell(
                                entry.getKey(),
                                RoadCell.Role.SURFACE,
                                generated.geometry()
                        ));
                    });
            for (var entry : additionsByPreset.entrySet()) {
                var layerRoad = RoadVoxelizer.Result.success(
                        entry.getValue(), List.of(), 0.0
                );
                var compiled = preview
                        ? RoadPatternCompiler.compilePreview(
                                player, source, entry.getKey(),
                                layerRoad, safety
                        )
                        : RoadPatternCompiler.compile(
                                player, source, entry.getKey(),
                                layerRoad, safety
                        );
                if (!compiled.isSuccess()) {
                    return relabel(compiled, "Procedural tree");
                }
                var anchor = compiled.anchor().orElseThrow();
                for (var data : compiled.snapshot().orElseThrow()
                        .blockData()) {
                    var position = anchor.add(data.blockPosition());
                    absolute.put(position, new BlockData(
                            position,
                            data.blockState(),
                            data.entityTag()
                    ));
                }
            }
            var air = Items.AIR.item().getBlock().getDefaultBlockState();
            for (var position : composition.erasers()) {
                var absolutePosition = new BlockPosition(
                        position.x(), position.y(), position.z()
                );
                absolute.put(
                        absolutePosition,
                        new BlockData(absolutePosition, air, null)
                );
            }
        }
        if (absolute.isEmpty()) {
            return RoadPatternCompiler.CompilationResult.failure(
                    "Every tree cell was skipped",
                    List.of("Nothing would be sent to the server")
            );
        }
        var geometries = new LinkedHashMap<
                GridPosition, StructuralGeometry>();
        tree.cells().forEach(cell ->
                geometries.put(cell.position(), cell.geometry())
        );
        geometries.putAll(compositionGeometries);
        absolute = StructuralStateBatchResolver.resolveMap(
                absolute, geometries
        );
        var assembled = ExplicitSnapshotAssembler.assemble(
                player,
                source,
                safety,
                new ExplicitSnapshotAssembler.Request(
                        preview
                                ? "Procedural tree preview"
                                : "Procedural tree",
                        "Tree",
                        absolute.values(),
                        !preview
                )
        );
        return assembled.isSuccess()
                ? RoadPatternCompiler.CompilationResult.success(
                        assembled.snapshot().orElseThrow(),
                        assembled.anchor().orElseThrow(),
                        assembled.positionCount(),
                        assembled.boundingVolume(),
                        assembled.estimatedMemoryBytes()
                )
                : RoadPatternCompiler.CompilationResult.failure(
                        assembled.message(), assembled.details()
                );
    }

    private static RoadPatternCompiler.CompilationResult relabel(
            RoadPatternCompiler.CompilationResult result,
            String snapshotName
    ) {
        if (!result.isSuccess()) {
            return RoadPatternCompiler.CompilationResult.failure(
                    replaceRoad(result.message()),
                    result.details().stream()
                            .map(TreePatternCompiler::replaceRoad)
                            .toList()
            );
        }
        var source = result.snapshot().orElseThrow();
        var snapshot = new Snapshot(
                snapshotName,
                source.createdTimestamp(),
                source.blockData()
        );
        return RoadPatternCompiler.CompilationResult.success(
                snapshot,
                result.anchor().orElseThrow(),
                result.positionCount(),
                result.boundingVolume(),
                result.estimatedMemoryBytes()
        );
    }

    private static GridPosition grid(BlockPosition position) {
        return new GridPosition(position.x(), position.y(), position.z());
    }

    private static ProceduralPatternPreset withoutComposition(
            ProceduralPatternPreset preset
    ) {
        return preset.withAdvanced(
                preset.advanced().withCompositionLayers(List.of())
        );
    }

    private static ProceduralPatternLibrary localLibrary(
            ProceduralPatternPreset root,
            Map<TreeCell.Role, ProceduralPatternPreset> recipes
    ) {
        var presets = new LinkedHashMap<java.util.UUID,
                ProceduralPatternPreset>();
        presets.put(root.id(), root);
        recipes.values().forEach(preset ->
                presets.put(preset.id(), preset)
        );
        return new ProceduralPatternLibrary(
                false, root.id(), List.copyOf(presets.values())
        );
    }

    private static String replaceRoad(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.replace("Road", "Tree")
                .replace("road", "tree")
                .replace("ROAD", "TREE");
    }
}
