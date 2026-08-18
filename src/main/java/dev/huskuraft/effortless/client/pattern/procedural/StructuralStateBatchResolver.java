package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.huskuraft.effortless.building.clipboard.BlockData;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.BlockState;

/** Resolves placement-sensitive states against one completed voxel batch. */
public final class StructuralStateBatchResolver {

    private StructuralStateBatchResolver() {
    }

    public static List<BlockData> resolve(
            Collection<BlockData> blocks,
            Map<GridPosition, StructuralGeometry> geometries
    ) {
        var occupied = new HashSet<GridPosition>();
        var rawStates = new HashMap<GridPosition, BlockState>();
        for (var data : blocks) {
            if (data.blockState() != null && !data.blockState().isAir()) {
                var position = grid(data.blockPosition());
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
        return List.copyOf(resolved);
    }

    public static LinkedHashMap<BlockPosition, BlockData> resolveMap(
            Map<BlockPosition, BlockData> blocks,
            Map<GridPosition, StructuralGeometry> geometries
    ) {
        var result = new LinkedHashMap<BlockPosition, BlockData>();
        resolve(blocks.values(), geometries).forEach(data ->
                result.put(data.blockPosition(), data)
        );
        return result;
    }

    private static GridPosition grid(BlockPosition position) {
        return new GridPosition(position.x(), position.y(), position.z());
    }
}
