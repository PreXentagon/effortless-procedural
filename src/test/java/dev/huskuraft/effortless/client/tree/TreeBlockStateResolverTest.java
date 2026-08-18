package dev.huskuraft.effortless.client.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;
import dev.huskuraft.effortless.client.road.RoadCell;
import dev.huskuraft.effortless.client.road.RoadPoint;
import dev.huskuraft.effortless.client.road.RoadProfile;
import dev.huskuraft.effortless.client.road.RoadVoxelizer;
import dev.huskuraft.effortless.client.road.SplineCrossSectionBand;

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

    @Test
    void openFrontTurnUsesOuterCornerShape() {
        var current = new GridPosition(0, 0, 0);
        var front = current.offset(1, 0, 0);
        var geometries = new LinkedHashMap<
                GridPosition, StructuralGeometry>();
        geometries.put(current, stairFacing(1.0, 0.0));
        geometries.put(front, stairFacing(0.0, 1.0));

        assertEquals(
                "outer_right",
                TreeBlockStateResolver.stairShape(
                        current, geometries.get(current), geometries,
                        Set.copyOf(geometries.keySet())
                )
        );
    }

    @Test
    void parallelSideRunPreventsDisconnectedOuterCorner() {
        var current = new GridPosition(0, 0, 0);
        var front = current.offset(1, 0, 0);
        var side = current.offset(0, 0, -1);
        var geometries = new LinkedHashMap<
                GridPosition, StructuralGeometry>();
        geometries.put(current, stairFacing(1.0, 0.0));
        geometries.put(front, stairFacing(0.0, 1.0));
        geometries.put(side, stairFacing(1.0, 0.0));

        assertEquals(
                "straight",
                TreeBlockStateResolver.stairShape(
                        current, geometries.get(current), geometries,
                        Set.copyOf(geometries.keySet())
                )
        );
    }

    @Test
    void openBackTurnUsesInnerCornerShape() {
        var current = new GridPosition(0, 0, 0);
        var back = current.offset(-1, 0, 0);
        var geometries = new LinkedHashMap<
                GridPosition, StructuralGeometry>();
        geometries.put(current, stairFacing(1.0, 0.0));
        geometries.put(back, stairFacing(0.0, -1.0));

        assertEquals(
                "inner_left",
                TreeBlockStateResolver.stairShape(
                        current, geometries.get(current), geometries,
                        Set.copyOf(geometries.keySet())
                )
        );
    }

    @Test
    void curvedSplineBandProducesConnectedCornerStates() {
        var profile = new RoadProfile(5, 1, 0, 0.2, 0.2)
                .withCrossSectionBands(List.of(
                        new SplineCrossSectionBand(
                                "Stairs", 1, -1, 1,
                                SplineCrossSectionBand.Side.BOTH,
                                SplineCrossSectionBand.Placement.STAIR_OUTWARD,
                                ""
                        )
                ));
        var road = RoadVoxelizer.voxelize(
                List.of(
                        new RoadPoint(0.5, 0.5, 0.5),
                        new RoadPoint(8.5, 0.5, 0.5),
                        new RoadPoint(8.5, 0.5, 8.5)
                ),
                profile
        );
        assertTrue(road.isSuccess(), () -> road.errors().toString());

        var geometries = new LinkedHashMap<
                GridPosition, StructuralGeometry>();
        road.cells().stream()
                .filter(cell -> cell.role() == RoadCell.Role.BAND)
                .forEach(cell -> geometries.put(
                        cell.position(), cell.geometry()
                ));
        var stairs = Set.copyOf(geometries.keySet());
        var shapes = new LinkedHashMap<GridPosition, String>();
        geometries.forEach((position, geometry) -> shapes.put(
                position,
                TreeBlockStateResolver.stairShape(
                        position, geometry, geometries, stairs
                )
        ));

        assertTrue(
                shapes.values().stream().anyMatch(shape ->
                        shape.startsWith("inner_")),
                "The curved stair band should contain an inner corner"
        );
        assertTrue(
                shapes.entrySet().stream()
                        .filter(entry -> geometries.get(entry.getKey())
                                .lateral() < 0.5)
                        .anyMatch(entry -> !entry.getValue()
                                .equals("straight")),
                () -> "The left curved stair band needs a corner: " + shapes
        );
        assertTrue(
                shapes.entrySet().stream()
                        .filter(entry -> geometries.get(entry.getKey())
                                .lateral() > 0.5)
                        .anyMatch(entry -> !entry.getValue()
                                .equals("straight")),
                () -> "The right curved stair band needs a corner: "
                        + geometries.entrySet().stream()
                                .filter(entry -> entry.getValue().lateral()
                                        > 0.5)
                                .toList()
        );
        var repeated = new LinkedHashMap<GridPosition, String>();
        geometries.forEach((position, geometry) -> repeated.put(
                position,
                TreeBlockStateResolver.stairShape(
                        position, geometry, geometries, stairs
                )
        ));
        assertEquals(shapes, repeated);
    }

    private static StructuralGeometry stairFacing(
            double normalX,
            double normalZ
    ) {
        return new StructuralGeometry(
                0.5, 0.5, 0.95,
                1.0, 0.0, 0.0, 0.0,
                1.0, 0.0, 0.0,
                normalX, 0.0, normalZ
        );
    }

}
