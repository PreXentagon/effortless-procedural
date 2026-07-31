package dev.huskuraft.effortless.client.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;

class TreeBlockStateResolverTest {

    private static final Set<String> BOOLEAN = Set.of("false", "true");

    @Test
    void horizontalBranchSelectsLogAxisAndFenceConnections() {
        var position = new GridPosition(4, 8, 2);
        var cell = new TreeCell(
                position, 0.6, 0.35, 0.0,
                TreeCell.Role.BRANCH, 0.92, 0.16, 0.21
        );
        var desired = TreeBlockStateResolver.desiredProperties(
                cell,
                Set.of(
                        position,
                        position.offset(-1, 0, 0),
                        position.offset(1, 0, 0)
                ),
                Map.of(
                        "axis", Set.of("x", "y", "z"),
                        "north", BOOLEAN,
                        "south", BOOLEAN,
                        "west", BOOLEAN,
                        "east", BOOLEAN
                )
        );

        assertEquals("x", desired.get("axis"));
        assertEquals("true", desired.get("west"));
        assertEquals("true", desired.get("east"));
        assertEquals("false", desired.get("north"));
        assertEquals("false", desired.get("south"));
    }

    @Test
    void descendingBranchOrientsStairAndSlabState() {
        var cell = new TreeCell(
                new GridPosition(0, 0, 0), 0.5, 0.35, 0.0,
                TreeCell.Role.BRANCH, 0.1, -0.6, -0.8
        );
        var stair = TreeBlockStateResolver.desiredProperties(
                cell,
                Set.of(cell.position()),
                Map.of(
                        "facing", Set.of("north", "south", "east", "west"),
                        "half", Set.of("top", "bottom"),
                        "shape", Set.of("straight", "inner_left", "outer_left")
                )
        );
        var slab = TreeBlockStateResolver.desiredProperties(
                cell,
                Set.of(cell.position()),
                Map.of("type", Set.of("top", "bottom", "double"))
        );

        assertEquals("north", stair.get("facing"));
        assertEquals("top", stair.get("half"));
        assertEquals("straight", stair.get("shape"));
        assertEquals("top", slab.get("type"));
    }

    @Test
    void foliageIsMadePersistent() {
        var cell = new TreeCell(
                new GridPosition(0, 0, 0), 1.0, 1.0, 0.2,
                TreeCell.Role.CANOPY
        );
        var desired = TreeBlockStateResolver.desiredProperties(
                cell,
                Set.of(cell.position()),
                Map.of(
                        "persistent", BOOLEAN,
                        "distance", Set.of("1", "2", "7")
                )
        );

        assertEquals("true", desired.get("persistent"));
    }

    @Test
    void shellStairUsesOutwardNormalInsteadOfVerticalPath() {
        var position = new GridPosition(0, 4, 0);
        var geometry = new StructuralGeometry(
                0.5, 0.0, 0.95,
                0.7, 0.5, 0.0, 0.0,
                0.0, 1.0, 0.0,
                0.9, 0.25, 0.0
        );
        var desired = TreeBlockStateResolver.desiredProperties(
                geometry,
                position,
                Set.of(position),
                Map.of(),
                Map.of(
                        "facing", Set.of("north", "south", "east", "west"),
                        "half", Set.of("top", "bottom"),
                        "shape", Set.of("straight")
                )
        );

        assertEquals("east", desired.get("facing"));
        assertEquals("bottom", desired.get("half"));
    }
}
