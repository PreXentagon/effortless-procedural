package dev.huskuraft.effortless.client.pattern.procedural;

/**
 * Stable, versioned ordering for all 26 neighboring cells.
 */
public enum NeighborDirection {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0),

    DOWN_NORTH(0, -1, -1),
    DOWN_SOUTH(0, -1, 1),
    DOWN_WEST(-1, -1, 0),
    DOWN_EAST(1, -1, 0),
    UP_NORTH(0, 1, -1),
    UP_SOUTH(0, 1, 1),
    UP_WEST(-1, 1, 0),
    UP_EAST(1, 1, 0),
    NORTH_WEST(-1, 0, -1),
    NORTH_EAST(1, 0, -1),
    SOUTH_WEST(-1, 0, 1),
    SOUTH_EAST(1, 0, 1),

    DOWN_NORTH_WEST(-1, -1, -1),
    DOWN_NORTH_EAST(1, -1, -1),
    DOWN_SOUTH_WEST(-1, -1, 1),
    DOWN_SOUTH_EAST(1, -1, 1),
    UP_NORTH_WEST(-1, 1, -1),
    UP_NORTH_EAST(1, 1, -1),
    UP_SOUTH_WEST(-1, 1, 1),
    UP_SOUTH_EAST(1, 1, 1);

    private final int dx;
    private final int dy;
    private final int dz;

    NeighborDirection(int dx, int dy, int dz) {
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

    public int changedAxes() {
        int count = 0;
        if (dx != 0) {
            count++;
        }
        if (dy != 0) {
            count++;
        }
        if (dz != 0) {
            count++;
        }
        return count;
    }

    public NeighborDirection opposite() {
        for (var value : values()) {
            if (value.dx == -dx && value.dy == -dy && value.dz == -dz) {
                return value;
            }
        }
        throw new IllegalStateException("Missing opposite direction for " + this);
    }
}
