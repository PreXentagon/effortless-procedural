package dev.huskuraft.effortless.client.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.clipboard.BlockData;
import dev.huskuraft.effortless.building.clipboard.Snapshot;
import dev.huskuraft.effortless.building.config.ProceduralSafetyConfig;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralContextCompiler;
import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralBlockStateResolver;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.road.RoadPatternCompiler;
import dev.huskuraft.universal.api.core.BlockPosition;
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
                player, source, treePreset, recipes, tree, safety, false
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
                player, source, treePreset, recipes, tree, safety, true
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
            var preset = recipes.getOrDefault(role, treePreset);
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
        if (absolute.isEmpty()) {
            return RoadPatternCompiler.CompilationResult.failure(
                    "Every tree cell was skipped",
                    List.of("Nothing would be sent to the server")
            );
        }
        resolvePlacementStates(absolute, tree);
        int minX = absolute.keySet().stream()
                .mapToInt(BlockPosition::x).min().orElseThrow();
        int minY = absolute.keySet().stream()
                .mapToInt(BlockPosition::y).min().orElseThrow();
        int minZ = absolute.keySet().stream()
                .mapToInt(BlockPosition::z).min().orElseThrow();
        int maxX = absolute.keySet().stream()
                .mapToInt(BlockPosition::x).max().orElseThrow();
        int maxY = absolute.keySet().stream()
                .mapToInt(BlockPosition::y).max().orElseThrow();
        int maxZ = absolute.keySet().stream()
                .mapToInt(BlockPosition::z).max().orElseThrow();
        long boxVolume = (long) (maxX - minX + 1)
                * (maxY - minY + 1) * (maxZ - minZ + 1);
        int serverLimit = source.configs().constraintConfig()
                .maxStructureCopyPasteVolume();
        if (boxVolume > serverLimit) {
            return RoadPatternCompiler.CompilationResult.failure(
                    "Tree bounding volume exceeds the server clipboard limit",
                    List.of(boxVolume + " blocks > " + serverLimit + " allowed")
            );
        }
        if (absolute.size() > safety.maxCompiledPositions()) {
            return RoadPatternCompiler.CompilationResult.failure(
                    "Tree contains too many explicit blocks",
                    List.of(absolute.size() + " blocks > "
                            + safety.maxCompiledPositions())
            );
        }
        long estimatedBytes = absolute.size()
                * ProceduralContextCompiler.ESTIMATED_BYTES_PER_POSITION;
        if (estimatedBytes > safety.maxEstimatedMemoryBytes()) {
            return RoadPatternCompiler.CompilationResult.failure(
                    "Tree compilation is estimated to use too much memory",
                    List.of(estimatedBytes + " estimated bytes > "
                            + safety.maxEstimatedMemoryBytes())
            );
        }
        var anchor = new BlockPosition(minX, minY, minZ);
        var blocks = new ArrayList<BlockData>(absolute.size());
        for (var entry : absolute.entrySet()) {
            var position = entry.getKey();
            var data = entry.getValue();
            blocks.add(new BlockData(
                    new BlockPosition(
                            position.x() - minX,
                            position.y() - minY,
                            position.z() - minZ
                    ),
                    data.blockState(),
                    data.entityTag()
            ));
        }
        return RoadPatternCompiler.CompilationResult.success(
                new Snapshot(
                        preview
                                ? "Procedural tree preview"
                                : "Procedural tree",
                        System.currentTimeMillis(),
                        blocks
                ),
                anchor,
                blocks.size(),
                boxVolume,
                estimatedBytes
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

    private static void resolvePlacementStates(
            LinkedHashMap<BlockPosition, BlockData> blocks,
            TreeVoxelizer.Result tree
    ) {
        var cells = new HashMap<GridPosition, StructuralGeometry>();
        for (var cell : tree.cells()) {
            cells.put(cell.position(), cell.geometry());
        }
        var occupied = new HashSet<GridPosition>();
        var rawStates = new HashMap<GridPosition,
                dev.huskuraft.universal.api.core.BlockState>();
        for (var entry : blocks.entrySet()) {
            if (entry.getValue().blockState() != null
                    && !entry.getValue().blockState().isAir()) {
                var position = grid(entry.getKey());
                occupied.add(position);
                rawStates.put(position, entry.getValue().blockState());
            }
        }
        blocks.replaceAll((position, data) -> {
            var geometry = cells.get(grid(position));
            if (geometry == null || data.blockState() == null
                    || data.blockState().isAir()) {
                return data;
            }
            return new BlockData(
                    data.blockPosition(),
                    StructuralBlockStateResolver.resolve(
                            data.blockState(), grid(position), geometry,
                            occupied,
                            cells, rawStates
                    ),
                    data.entityTag()
            );
        });
    }

    private static GridPosition grid(BlockPosition position) {
        return new GridPosition(position.x(), position.y(), position.z());
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
