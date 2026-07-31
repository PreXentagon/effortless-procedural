package dev.huskuraft.effortless.client.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;

class SplineCrossSectionAndCutoutTest {

    @Test
    void orderedBandsBuildIndependentLayeredRoadEdges() {
        var profile = new RoadProfile(
                5, 1, 0, 0.0, 0.25,
                SplineSubtype.CUSTOM, SplineMaterialLinks.DEFAULT,
                List.of(
                        new SplineCrossSectionBand(
                                "Stair", 1, -1, 1,
                                SplineCrossSectionBand.Side.BOTH,
                                SplineCrossSectionBand.Placement
                                        .STAIR_OUTWARD,
                                ""
                        ),
                        new SplineCrossSectionBand(
                                "Border", 1, 0, 1,
                                SplineCrossSectionBand.Side.BOTH,
                                SplineCrossSectionBand.Placement.FULL_BLOCK,
                                ""
                        ),
                        new SplineCrossSectionBand(
                                "Elevated curb", 3, 1, 2,
                                SplineCrossSectionBand.Side.BOTH,
                                SplineCrossSectionBand.Placement.AUTO,
                                ""
                        )
                ),
                SplineCutoutConfig.DEFAULT
        );
        var road = RoadVoxelizer.voxelize(
                List.of(
                        new RoadPoint(0.5, 10.5, 0.5),
                        new RoadPoint(4.5, 10.5, 0.5)
                ),
                profile
        );

        assertTrue(road.isSuccess(), () -> road.errors().toString());
        var atStart = road.cells().stream()
                .filter(cell -> cell.path() == 0.0)
                .toList();
        assertEquals(2, atStart.stream()
                .filter(cell -> cell.bandIndex() == 0)
                .filter(cell -> cell.position().y() == 9)
                .count());
        assertEquals(2, atStart.stream()
                .filter(cell -> cell.bandIndex() == 1)
                .filter(cell -> cell.position().y() == 10)
                .count());
        assertEquals(12, atStart.stream()
                .filter(cell -> cell.bandIndex() == 2)
                .count());

        var stairs = atStart.stream()
                .filter(cell -> cell.bandIndex() == 0)
                .toList();
        assertTrue(stairs.stream().allMatch(cell -> cell.depth() == 1.0));
        assertTrue(stairs.stream().anyMatch(cell ->
                cell.position().z() < 0 && cell.geometry().normalZ() < -0.9
        ));
        assertTrue(stairs.stream().anyMatch(cell ->
                cell.position().z() > 0 && cell.geometry().normalZ() > 0.9
        ));
    }

    @Test
    void deepCutoutDoesNotRequireDeepRoadFoundation() {
        var cells = surface(5, 5, 10);
        var selected = Set.copyOf(cells.keySet());
        var config = new SplineCutoutConfig(
                true, 6, 1, 0.0, true, true, "", ""
        );

        var first = SplineCutoutGeometry.expand(selected, cells, config);
        var second = SplineCutoutGeometry.expand(selected, cells, config);

        assertEquals(first, second);
        assertTrue(first.isSuccess(), () -> first.errors().toString());
        assertEquals(45, first.airCells().size());
        assertEquals(80, first.wallCells().size());
        assertEquals(25, first.floorCells().size());
        assertEquals(4, first.floorCells().stream()
                .mapToInt(cell -> cell.position().y()).min().orElseThrow());
        assertTrue(first.airCells().stream().allMatch(cell ->
                cell.position().y() < 10
        ));
    }

    @Test
    void taperContractsFootprintPredictably() {
        var cells = surface(5, 5, 10);
        var result = SplineCutoutGeometry.expand(
                Set.copyOf(cells.keySet()), cells,
                new SplineCutoutConfig(
                        true, 3, 0, 1.0,
                        false, true, "", ""
                )
        );

        assertTrue(result.isSuccess());
        assertEquals(25 + 9, result.airCells().size());
        assertEquals(1, result.floorCells().size());
        assertEquals(new GridPosition(2, 7, 2),
                result.floorCells().get(0).position());
    }

    private static LinkedHashMap<GridPosition, RoadCell> surface(
            int width,
            int length,
            int y
    ) {
        var result = new LinkedHashMap<GridPosition, RoadCell>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                var position = new GridPosition(x, y, z);
                result.put(position, new RoadCell(
                        position, RoadCell.Role.SURFACE,
                        new StructuralGeometry(
                                x / (double) Math.max(1, width - 1),
                                z / (double) Math.max(1, length - 1),
                                0.0, 0.0, 0.0, 0.0, 0.0,
                                1.0, 0.0, 0.0,
                                0.0, 1.0, 0.0
                        )
                ));
            }
        }
        return result;
    }
}
