package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.Map;
import java.util.Set;

import dev.huskuraft.effortless.client.tree.TreeBlockStateResolver;
import dev.huskuraft.universal.api.core.BlockState;

/**
 * Public generator-independent facade for placement-sensitive block states.
 * The implementation only selects variants already present in the stock
 * block-state registry.
 */
public final class StructuralBlockStateResolver {

    private StructuralBlockStateResolver() {
    }

    public static BlockState resolve(
            BlockState original,
            GridPosition position,
            StructuralGeometry geometry,
            Set<GridPosition> occupied,
            Map<GridPosition, StructuralGeometry> geometries,
            Map<GridPosition, BlockState> rawStates
    ) {
        return TreeBlockStateResolver.resolveGeometry(
                original, position, geometry, occupied,
                geometries, rawStates
        );
    }
}
