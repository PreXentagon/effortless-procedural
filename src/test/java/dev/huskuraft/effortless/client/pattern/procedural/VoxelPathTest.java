package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoxelPathTest {

    @Test
    void lineIsDeterministicAndFaceConnected() {
        var start = new GridPosition(-2, 4, 7);
        var end = new GridPosition(5, -1, 11);
        var first = VoxelPath.faceConnectedLine(start, end);

        assertEquals(first, VoxelPath.faceConnectedLine(start, end));
        assertEquals(start, first.getFirst());
        assertEquals(end, first.getLast());
        assertEquals(
                VoxelPath.manhattanDistance(start, end) + 1,
                first.size()
        );
        for (int index = 1; index < first.size(); index++) {
            assertEquals(
                    1,
                    VoxelPath.manhattanDistance(
                            first.get(index - 1), first.get(index)
                    )
            );
        }
    }

    @Test
    void tieOrderIsExplicit() {
        var start = new GridPosition(0, 0, 0);
        var end = new GridPosition(2, 2, 0);

        assertEquals(
                new GridPosition(1, 0, 0),
                VoxelPath.faceConnectedLine(
                        start, end, VoxelPath.TieOrder.X_Y_Z
                ).get(1)
        );
        assertEquals(
                new GridPosition(0, 1, 0),
                VoxelPath.faceConnectedLine(
                        start, end, VoxelPath.TieOrder.Y_X_Z
                ).get(1)
        );
        assertTrue(VoxelPath.faceConnectedLine(start, start)
                .contains(start));
    }
}
