package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.Collection;

public record GenerationBounds(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {

    public static GenerationBounds enclosing(Collection<GridPosition> positions) {
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("Cannot calculate bounds for an empty position set");
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (var position : positions) {
            minX = Math.min(minX, position.x());
            minY = Math.min(minY, position.y());
            minZ = Math.min(minZ, position.z());
            maxX = Math.max(maxX, position.x());
            maxY = Math.max(maxY, position.y());
            maxZ = Math.max(maxZ, position.z());
        }
        return new GenerationBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return Math.multiplyExact(
                Math.multiplyExact((long) sizeX(), sizeY()),
                sizeZ()
        );
    }

    public double centerX() {
        return (minX + maxX + 1) * 0.5;
    }

    public double centerY() {
        return (minY + maxY + 1) * 0.5;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) * 0.5;
    }

    public GridPosition minimum() {
        return new GridPosition(minX, minY, minZ);
    }

    public GridPosition maximum() {
        return new GridPosition(maxX, maxY, maxZ);
    }

    public GridPosition normalize(GridPosition position) {
        return new GridPosition(
                position.x() - minX,
                position.y() - minY,
                position.z() - minZ
        );
    }

    public double normalizedX(GridPosition position) {
        return normalize(position.x(), minX, maxX);
    }

    public double normalizedY(GridPosition position) {
        return normalize(position.y(), minY, maxY);
    }

    public double normalizedZ(GridPosition position) {
        return normalize(position.z(), minZ, maxZ);
    }

    public double normalizedDistance(GridPosition position) {
        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double dx = position.x() - centerX;
        double dy = position.y() - centerY;
        double dz = position.z() - centerZ;
        double maxDx = (maxX - minX) / 2.0;
        double maxDy = (maxY - minY) / 2.0;
        double maxDz = (maxZ - minZ) / 2.0;
        double maximum = Math.sqrt(maxDx * maxDx + maxDy * maxDy + maxDz * maxDz);
        if (maximum == 0.0) {
            return 0.0;
        }
        return Math.min(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz) / maximum);
    }

    private static double normalize(int value, int minimum, int maximum) {
        if (minimum == maximum) {
            return 0.0;
        }
        return (double) (value - minimum) / (double) (maximum - minimum);
    }
}
