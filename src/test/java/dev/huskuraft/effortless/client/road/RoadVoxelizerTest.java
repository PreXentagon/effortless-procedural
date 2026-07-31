package dev.huskuraft.effortless.client.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

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
