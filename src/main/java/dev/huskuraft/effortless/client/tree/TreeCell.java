package dev.huskuraft.effortless.client.tree;

import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;

/** One deterministic tree voxel and its generator-independent geometry. */
public record TreeCell(
        GridPosition position,
        Role role,
        StructuralGeometry geometry
) {

    public TreeCell {
        if (position == null || role == null) {
            throw new IllegalArgumentException(
                    "Tree cell position and role must be selected"
            );
        }
        geometry = geometry == null ? StructuralGeometry.NONE : geometry;
    }

    public TreeCell(
            GridPosition position,
            double path,
            double lateral,
            double depth,
            Role role
    ) {
        this(
                position,
                role,
                StructuralGeometry.basic(path, lateral, depth)
        );
    }

    public TreeCell(
            GridPosition position,
            double path,
            double lateral,
            double depth,
            Role role,
            double tangentX,
            double tangentY,
            double tangentZ
    ) {
        this(
                position,
                role,
                new StructuralGeometry(
                        path, lateral, depth,
                        0.0, 0.0, StructuralGeometry.tipFromPath(path), 0.0,
                        tangentX, tangentY, tangentZ,
                        0.0, 0.0, 0.0
                )
        );
    }

    public TreeCell withGeometry(StructuralGeometry value) {
        return new TreeCell(position, role, value);
    }

    public TreeCell withJunction(double value) {
        return withGeometry(geometry.withJunction(value));
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

    public double thickness() {
        return geometry.thickness();
    }

    public double slope() {
        return geometry.slope();
    }

    public double tip() {
        return geometry.tip();
    }

    public double junction() {
        return geometry.junction();
    }

    public double tangentX() {
        return geometry.tangentX();
    }

    public double tangentY() {
        return geometry.tangentY();
    }

    public double tangentZ() {
        return geometry.tangentZ();
    }

    public double normalX() {
        return geometry.normalX();
    }

    public double normalY() {
        return geometry.normalY();
    }

    public double normalZ() {
        return geometry.normalZ();
    }

    public enum Role {
        TRUNK,
        BRANCH,
        ROOT,
        CANOPY
    }
}
