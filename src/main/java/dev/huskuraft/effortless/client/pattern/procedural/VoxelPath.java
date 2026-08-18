package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.List;

/** Reusable deterministic paths over the integer voxel grid. */
public final class VoxelPath {

    private VoxelPath() {
    }

    public static List<GridPosition> faceConnectedLine(
            GridPosition start,
            GridPosition end
    ) {
        return faceConnectedLine(start, end, TieOrder.X_Y_Z);
    }

    /**
     * Manhattan supercover. Exactly one axis changes per step, so every pair
     * of consecutive voxels shares a face. The explicit tie order makes the
     * same endpoints stable across JVMs and releases.
     */
    public static List<GridPosition> faceConnectedLine(
            GridPosition start,
            GridPosition end,
            TieOrder tieOrder
    ) {
        int[] current = {start.x(), start.y(), start.z()};
        int[] remaining = {
                Math.abs(end.x() - start.x()),
                Math.abs(end.y() - start.y()),
                Math.abs(end.z() - start.z())
        };
        int[] direction = {
                Integer.signum(end.x() - start.x()),
                Integer.signum(end.y() - start.y()),
                Integer.signum(end.z() - start.z())
        };
        int[] completed = new int[3];
        var result = new ArrayList<GridPosition>(
                1 + remaining[0] + remaining[1] + remaining[2]
        );
        result.add(start);
        while (completed[0] < remaining[0]
                || completed[1] < remaining[1]
                || completed[2] < remaining[2]) {
            int selectedAxis = -1;
            double selectedCrossing = Double.POSITIVE_INFINITY;
            for (int axis : tieOrder.axes) {
                if (completed[axis] >= remaining[axis]) {
                    continue;
                }
                double crossing = (completed[axis] + 0.5)
                        / remaining[axis];
                if (crossing < selectedCrossing) {
                    selectedCrossing = crossing;
                    selectedAxis = axis;
                }
            }
            current[selectedAxis] += direction[selectedAxis];
            completed[selectedAxis]++;
            result.add(new GridPosition(
                    current[0], current[1], current[2]
            ));
        }
        return List.copyOf(result);
    }

    public static int manhattanDistance(
            GridPosition first,
            GridPosition second
    ) {
        return Math.abs(first.x() - second.x())
                + Math.abs(first.y() - second.y())
                + Math.abs(first.z() - second.z());
    }

    public enum TieOrder {
        X_Y_Z(0, 1, 2),
        Y_X_Z(1, 0, 2),
        Y_Z_X(1, 2, 0);

        private final int[] axes;

        TieOrder(int... axes) {
            this.axes = axes;
        }
    }
}
