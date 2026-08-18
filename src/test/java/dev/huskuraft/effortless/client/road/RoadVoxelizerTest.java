package dev.huskuraft.effortless.client.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.VoxelPath;

class RoadVoxelizerTest {

    @Test
    void straightRoadHasExpectedWidthLengthAndThickness() {
        var result = RoadVoxelizer.voxelize(
                List.of(
                        new RoadPoint(0.5, 4.5, 0.5),
                        new RoadPoint(10.5, 4.5, 0.5)
                ),
                new RoadProfile(5, 2, 0, 0.0, 0.25)
        );

        assertTrue(result.isSuccess(), () -> result.errors().toString());
        assertEquals(11 * 5 * 2, result.cells().size());
        assertEquals(
                result.cells().size(),
                new HashSet<>(result.cells().stream()
                        .map(RoadCell::position)
                        .toList()).size()
        );
        assertTrue(result.cells().stream().anyMatch(cell ->
                cell.path() == 0.0 && cell.lateral() == 0.0
                        && cell.depth() == 0.0
        ));
        assertTrue(result.cells().stream().anyMatch(cell ->
                cell.path() == 1.0 && cell.lateral() == 1.0
                        && cell.depth() == 1.0
        ));
    }

    @Test
    void shouldersAndFoundationReceiveStableRoles() {
        var result = RoadVoxelizer.voxelize(
                List.of(
                        new RoadPoint(0.5, 2.5, 0.5),
                        new RoadPoint(4.5, 2.5, 0.5)
                ),
                new RoadProfile(3, 2, 1, 0.0, 0.25)
        );

        assertTrue(result.cells().stream()
                .anyMatch(cell -> cell.role() == RoadCell.Role.SHOULDER));
        assertTrue(result.cells().stream()
                .anyMatch(cell -> cell.role() == RoadCell.Role.SURFACE));
        assertTrue(result.cells().stream()
                .anyMatch(cell -> cell.role() == RoadCell.Role.FOUNDATION));
    }

    @Test
    void roadCellsExposeReusableDirectionAndSurfaceMetadata() {
        var result = RoadVoxelizer.voxelize(
                List.of(
                        new RoadPoint(0.5, 2.5, 0.5),
                        new RoadPoint(8.5, 6.5, 0.5)
                ),
                new RoadProfile(3, 1, 0, 0.0, 0.25)
        );

        assertTrue(result.isSuccess(), () -> result.errors().toString());
        assertTrue(result.cells().stream().allMatch(cell ->
                cell.geometry().tangentX() > 0.7
                        && cell.geometry().tangentY() > 0.3
        ));
        assertTrue(result.cells().stream()
                .filter(cell -> cell.depth() == 0.0)
                .allMatch(cell -> cell.geometry().normalY() > 0.7));
        assertTrue(result.cells().stream().anyMatch(cell ->
                cell.geometry().tip() > 0.9
        ));
    }

    @Test
    void curvedVoxelizationIsDeterministicAndConnected() {
        var points = List.of(
                new RoadPoint(0.5, 1.5, 0.5),
                new RoadPoint(6.5, 1.5, 5.5),
                new RoadPoint(12.5, 1.5, 0.5)
        );
        var profile = new RoadProfile(3, 1, 0, 0.0, 0.2);
        var first = RoadVoxelizer.voxelize(points, profile);
        var second = RoadVoxelizer.voxelize(points, profile);

        assertEquals(first, second);
        var positions = new HashSet<>(first.cells().stream()
                .map(RoadCell::position)
                .toList());
        for (var position : positions) {
            boolean connected = position.offset(1, 0, 0) != null
                    && (positions.contains(position.offset(1, 0, 0))
                    || positions.contains(position.offset(-1, 0, 0))
                    || positions.contains(position.offset(0, 0, 1))
                    || positions.contains(position.offset(0, 0, -1)));
            assertTrue(connected, () -> "Isolated road cell " + position);
        }
    }

    @Test
    void offsetBandLanesRemainFaceConnectedAcrossLargeCornerJumps() {
        var line = VoxelPath.faceConnectedLine(
                new GridPosition(0, 0, 0),
                new GridPosition(4, 1, 3)
        );

        assertEquals(9, line.size());
        for (int index = 1; index < line.size(); index++) {
            var previous = line.get(index - 1);
            var current = line.get(index);
            int manhattan = Math.abs(current.x() - previous.x())
                    + Math.abs(current.y() - previous.y())
                    + Math.abs(current.z() - previous.z());
            assertEquals(1, manhattan, () ->
                    "Disconnected bridge at " + previous + " -> " + current);
        }
        assertEquals(line, VoxelPath.faceConnectedLine(
                line.getFirst(), line.getLast()
        ));
    }

    @Test
    void tightCurvedStairBandsHaveNoDisconnectedLaneSegments() {
        var profile = new RoadProfile(7, 1, 0, 0.25, 0.35)
                .withCrossSectionBands(List.of(
                        new SplineCrossSectionBand(
                                "Curb stairs", 1, -1, 1,
                                SplineCrossSectionBand.Side.BOTH,
                                SplineCrossSectionBand.Placement.STAIR_OUTWARD,
                                ""
                        )
                ));
        var result = RoadVoxelizer.voxelize(
                List.of(
                        new RoadPoint(0.5, 0.5, 0.5),
                        new RoadPoint(6.5, 0.5, 0.5),
                        new RoadPoint(6.5, 0.5, 6.5)
                ),
                profile
        );

        assertTrue(result.isSuccess(), () -> result.errors().toString());
        var left = new HashSet<>(result.cells().stream()
                .filter(cell -> cell.role() == RoadCell.Role.BAND)
                .filter(cell -> cell.geometry().lateral() < 0.5)
                .map(RoadCell::position)
                .toList());
        var right = new HashSet<>(result.cells().stream()
                .filter(cell -> cell.role() == RoadCell.Role.BAND)
                .filter(cell -> cell.geometry().lateral() > 0.5)
                .map(RoadCell::position)
                .toList());
        assertEquals(1, connectedComponents(left),
                "The left curb lane must remain one ribbon");
        assertEquals(1, connectedComponents(right),
                "The right curb lane must remain one ribbon");
    }

    @Test
    void stairBandRibbonsStayConnectedAcrossSCurvesAndReversals() {
        var profile = new RoadProfile(7, 1, 0, 0.25, 0.3)
                .withCrossSectionBands(List.of(
                        new SplineCrossSectionBand(
                                "Curb stairs", 1, -1, 1,
                                SplineCrossSectionBand.Side.BOTH,
                                SplineCrossSectionBand.Placement.STAIR_OUTWARD,
                                ""
                        )
                ));
        var paths = List.of(
                List.of(
                        new RoadPoint(0.5, 0.5, 0.5),
                        new RoadPoint(7.5, 0.5, 5.5),
                        new RoadPoint(14.5, 0.5, 0.5)
                ),
                List.of(
                        new RoadPoint(0.5, 0.5, 0.5),
                        new RoadPoint(7.5, 0.5, 0.5),
                        new RoadPoint(3.5, 0.5, 5.5),
                        new RoadPoint(10.5, 0.5, 10.5)
                )
        );

        for (var path : paths) {
            var result = RoadVoxelizer.voxelize(path, profile);
            assertTrue(result.isSuccess(), () -> result.errors().toString());
            for (boolean leftSide : List.of(true, false)) {
                var lane = new HashSet<>(result.cells().stream()
                        .filter(cell -> cell.role() == RoadCell.Role.BAND)
                        .filter(cell -> leftSide
                                ? cell.geometry().lateral() < 0.5
                                : cell.geometry().lateral() > 0.5)
                        .map(RoadCell::position)
                        .toList());
                assertFalse(lane.isEmpty());
                assertEquals(
                        1, connectedComponents(lane),
                        () -> "Split " + (leftSide ? "left" : "right")
                                + " ribbon for path " + path
                );
            }
        }
    }

    private static int connectedComponents(
            Set<dev.huskuraft.effortless.client.pattern.procedural.GridPosition>
                    positions
    ) {
        var remaining = new HashSet<>(positions);
        int components = 0;
        while (!remaining.isEmpty()) {
            components++;
            var queue = new ArrayDeque<
                    dev.huskuraft.effortless.client.pattern.procedural
                            .GridPosition>();
            var first = remaining.iterator().next();
            remaining.remove(first);
            queue.add(first);
            while (!queue.isEmpty()) {
                var value = queue.removeFirst();
                for (int[] direction : new int[][]{
                        {1, 0, 0}, {-1, 0, 0},
                        {0, 1, 0}, {0, -1, 0},
                        {0, 0, 1}, {0, 0, -1}
                }) {
                    var neighbor = value.offset(
                            direction[0], direction[1], direction[2]
                    );
                    if (remaining.remove(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }
        return components;
    }

    @Test
    void malformedProfilesFailBeforeVoxelization() {
        var result = RoadVoxelizer.voxelize(
                List.of(
                        new RoadPoint(0.5, 0.5, 0.5),
                        new RoadPoint(1.5, 0.5, 0.5)
                ),
                new RoadProfile(0, 1, 0, 0.0, 0.25)
        );
        assertFalse(result.isSuccess());
        assertTrue(result.errors().get(0).contains("width"));
    }

    @Test
    void crownedRoadRaisesItsCenterAboveItsEdges() {
        var result = RoadVoxelizer.voxelize(
                List.of(
                        new RoadPoint(0.5, 4.5, 0.5),
                        new RoadPoint(8.5, 4.5, 0.5)
                ),
                RoadProfile.forSubtype(SplineSubtype.CROWNED_ROAD)
        );

        assertTrue(result.isSuccess(), () -> result.errors().toString());
        int centerY = result.cells().stream()
                .filter(cell -> cell.depth() == 0.0)
                .filter(cell -> Math.abs(cell.lateral() - 0.5) < 0.01)
                .mapToInt(cell -> cell.position().y())
                .max().orElseThrow();
        int edgeY = result.cells().stream()
                .filter(cell -> cell.depth() == 0.0)
                .filter(cell -> cell.lateral() < 0.01)
                .mapToInt(cell -> cell.position().y())
                .max().orElseThrow();
        assertTrue(centerY > edgeY);
    }

    @Test
    void embankmentWidensTowardItsFoundation() {
        var result = RoadVoxelizer.voxelize(
                List.of(
                        new RoadPoint(0.5, 8.5, 0.5),
                        new RoadPoint(4.5, 8.5, 0.5)
                ),
                RoadProfile.forSubtype(SplineSubtype.EMBANKMENT)
        );

        assertTrue(result.isSuccess(), () -> result.errors().toString());
        long topWidth = result.cells().stream()
                .filter(cell -> cell.path() == 0.0 && cell.depth() == 0.0)
                .count();
        long bottomWidth = result.cells().stream()
                .filter(cell -> cell.path() == 0.0 && cell.depth() == 1.0)
                .count();
        assertTrue(bottomWidth > topWidth);
    }

    @Test
    void linkedCurbsAndMarkingsCreateRealMaterialRegions() {
        var links = new SplineMaterialLinks(
                "", "", "", "curb", "marking", ""
        );
        var profile = RoadProfile.forSubtype(SplineSubtype.FLAT_ROAD)
                .withMaterialLinks(links);
        var result = RoadVoxelizer.voxelize(
                List.of(
                        new RoadPoint(0.5, 4.5, 0.5),
                        new RoadPoint(6.5, 4.5, 0.5)
                ),
                profile
        );

        assertTrue(result.isSuccess(), () -> result.errors().toString());
        assertTrue(result.cells().stream().anyMatch(cell ->
                cell.role() == RoadCell.Role.CURB));
        assertTrue(result.cells().stream().anyMatch(cell ->
                cell.role() == RoadCell.Role.MARKING));
    }
}
