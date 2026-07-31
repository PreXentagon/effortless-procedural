package dev.huskuraft.effortless.client.road;

import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;

/** One explicit generator voxel and its normalized local geometry. */
public record RoadCell(
        GridPosition position,
        Role role,
        StructuralGeometry geometry,
        int bandIndex
) {

    public RoadCell(
            GridPosition position,
            Role role,
            StructuralGeometry geometry
    ) {
        this(position, role, geometry, -1);
    }

    public RoadCell(
            GridPosition position,
            double path,
            double lateral,
            double depth,
            Role role
    ) {
        this(
                position, role,
                StructuralGeometry.basic(path, lateral, depth), -1
        );
    }

    public double path() {
        return geometry.path();
    }

    public double lateral() {
        return geometry.lateral();
    }

    public double depth() {
        return geometry.depth();
    }

    public enum Role {
        SURFACE,
        SHOULDER,
        CURB,
        MARKING,
        FOUNDATION,
        BAND,
        CUTOUT_WALL,
        CUTOUT_FLOOR
    }
}
