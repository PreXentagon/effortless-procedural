package dev.huskuraft.effortless.client.pattern.procedural;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralCompositionLayer;

/**
 * Pure, deterministic voxel-membership algorithms for reusable scene shapes.
 * Keeping them outside the compiler makes preview, placement and tests share
 * exactly one geometry definition.
 */
final class ProceduralPrimitiveEvaluator {

    private ProceduralPrimitiveEvaluator() {
    }

    static boolean contains(
            ProceduralCompositionLayer.Primitive primitive,
            double x,
            double y,
            double z,
            int sizeX,
            int sizeY,
            int sizeZ,
            int inset
    ) {
        double radiusX = sizeX * 0.5 - inset;
        double radiusZ = sizeZ * 0.5 - inset;
        double height = sizeY - inset * 2.0;
        double shiftedY = y - inset;
        if (radiusX <= 0.0 || radiusZ <= 0.0 || height <= 0.0
                || shiftedY < 0.0 || shiftedY > height) {
            return false;
        }
        return switch (primitive) {
            case BOX -> Math.abs(x) <= radiusX && Math.abs(z) <= radiusZ;
            case ELLIPSOID -> square(x / radiusX)
                    + square((shiftedY - height * 0.5) / (height * 0.5))
                    + square(z / radiusZ) <= 1.0;
            case CYLINDER -> square(x / radiusX)
                    + square(z / radiusZ) <= 1.0;
            case CONE -> cone(x, shiftedY, z, radiusX, height, radiusZ);
            case ARCH -> arch(x, shiftedY, z, radiusX, height, radiusZ);
            case DOME -> square(x / radiusX)
                    + square(z / radiusZ)
                    + square(shiftedY / height) <= 1.0;
            case WEDGE -> wedge(x, shiftedY, z, radiusX, height, radiusZ);
            case DIAMOND -> Math.abs(x / radiusX)
                    + Math.abs(z / radiusZ)
                    + Math.abs(
                            (shiftedY - height * 0.5) / (height * 0.5)
                    ) <= 1.0;
            case PYRAMID -> pyramid(
                    x, shiftedY, z, radiusX, height, radiusZ
            );
            case TORUS -> torus(
                    x, shiftedY, z, radiusX, height, radiusZ
            );
        };
    }

    private static boolean cone(
            double x,
            double y,
            double z,
            double radiusX,
            double height,
            double radiusZ
    ) {
        double scale = Math.max(0.0, 1.0 - y / height);
        return scale > 0.0
                && square(x / (radiusX * scale))
                + square(z / (radiusZ * scale)) <= 1.0;
    }

    private static boolean arch(
            double x,
            double y,
            double z,
            double radiusX,
            double height,
            double radiusZ
    ) {
        double spring = Math.max(0.0, height - radiusX);
        boolean crossSection = y <= spring
                ? Math.abs(x) <= radiusX
                : square(x) + square(y - spring) <= square(radiusX);
        return crossSection && Math.abs(z) <= radiusZ;
    }

    private static boolean wedge(
            double x,
            double y,
            double z,
            double radiusX,
            double height,
            double radiusZ
    ) {
        if (Math.abs(x) > radiusX || Math.abs(z) > radiusZ) {
            return false;
        }
        double progress = (z + radiusZ) / (radiusZ * 2.0);
        return y <= height * Math.max(0.0, Math.min(1.0, progress));
    }

    private static boolean pyramid(
            double x,
            double y,
            double z,
            double radiusX,
            double height,
            double radiusZ
    ) {
        double scale = Math.max(0.0, 1.0 - y / height);
        return scale > 0.0
                && Math.abs(x) <= radiusX * scale
                && Math.abs(z) <= radiusZ * scale;
    }

    private static boolean torus(
            double x,
            double y,
            double z,
            double radiusX,
            double height,
            double radiusZ
    ) {
        double radial = Math.sqrt(
                square(x / radiusX) + square(z / radiusZ)
        );
        double vertical = (y - height * 0.5) / (height * 0.5);
        return square(radial - 0.62) + square(vertical)
                <= square(0.38);
    }

    private static double square(double value) {
        return value * value;
    }
}
