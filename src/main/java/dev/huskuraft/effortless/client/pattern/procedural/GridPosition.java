package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.Comparator;
import java.util.List;

/**
 * Loader-independent relative position used by the procedural compiler.
 *
 * <p>The natural order is the versioned MVP traversal order: Y, then Z, then X.
 * Changing this order changes deterministic output and therefore requires an
 * explicit procedural format/version migration.</p>
 */
public record GridPosition(int x, int y, int z) implements Comparable<GridPosition> {

    public static final Comparator<GridPosition> TRAVERSAL_ORDER =
            Comparator.comparingInt(GridPosition::y)
                    .thenComparingInt(GridPosition::z)
                    .thenComparingInt(GridPosition::x);

    public static final List<Direction> ORTHOGONAL_DIRECTIONS = List.of(
            Direction.DOWN,
            Direction.UP,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    );

    public GridPosition offset(Direction direction) {
        return new GridPosition(x + direction.dx(), y + direction.dy(), z + direction.dz());
    }

    public GridPosition offset(NeighborDirection direction) {
        return new GridPosition(
                x + direction.dx(),
                y + direction.dy(),
                z + direction.dz()
        );
    }

    public GridPosition offset(int dx, int dy, int dz) {
        return new GridPosition(x + dx, y + dy, z + dz);
    }

    @Override
    public int compareTo(GridPosition other) {
        return TRAVERSAL_ORDER.compare(this, other);
    }

    public enum Direction {
        DOWN(0, -1, 0),
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);

        private final int dx;
        private final int dy;
        private final int dz;

        Direction(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public int dx() {
            return dx;
        }

        public int dy() {
            return dy;
        }

        public int dz() {
            return dz;
        }
    }
}
