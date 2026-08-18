package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.ArrayList;
import java.util.List;

/**
 * One ordered client-only scene-composition node. Referenced presets provide
 * materials and generator parameters; the flattened block/air result is the
 * only representation that can cross the server boundary.
 */
public record ProceduralCompositionLayer(
        String name,
        boolean enabled,
        Operation operation,
        Generator generator,
        Anchor anchor,
        Primitive primitive,
        String presetId,
        double offsetX,
        double offsetY,
        double offsetZ,
        int sizeX,
        int sizeY,
        int sizeZ,
        double rotationDegrees,
        boolean hollow,
        int shellThickness,
        double spacing,
        int maximumInstances,
        long seedSalt,
        boolean safeVariation
) {

    public static ProceduralCompositionLayer defaultLayer() {
        return new ProceduralCompositionLayer(
                "Geometry layer",
                true,
                Operation.UNION,
                Generator.PRIMITIVE,
                Anchor.BOUNDS_CENTER,
                Primitive.BOX,
                "",
                0.0, 0.0, 0.0,
                5, 5, 5,
                0.0,
                false,
                1,
                4.0,
                256,
                0L,
                true
        );
    }

    public ProceduralCompositionLayer {
        name = name == null || name.isBlank() ? "Geometry layer" : name;
        presetId = presetId == null ? "" : presetId;
    }

    public List<String> validate() {
        var errors = new ArrayList<String>();
        if (operation == null || generator == null || anchor == null
                || primitive == null) {
            errors.add("Composition operation, generator, anchor and shape are required");
        }
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY)
                || !Double.isFinite(offsetZ)) {
            errors.add("Composition offsets must be finite");
        }
        if (sizeX < 1 || sizeX > 4096 || sizeY < 1 || sizeY > 4096
                || sizeZ < 1 || sizeZ > 4096) {
            errors.add("Composition sizes must be between 1 and 4096 blocks");
        }
        if (!Double.isFinite(rotationDegrees)) {
            errors.add("Composition rotation must be finite");
        }
        if (shellThickness < 1
                || shellThickness > Math.max(sizeX, Math.max(sizeY, sizeZ))) {
            errors.add("Composition shell thickness is outside the shape size");
        }
        if (!Double.isFinite(spacing) || spacing < 0.5 || spacing > 4096.0) {
            errors.add("Composition spacing must be between 0.5 and 4096 blocks");
        }
        if (maximumInstances < 1 || maximumInstances > 4096) {
            errors.add("Composition instance limit must be between 1 and 4096");
        }
        return List.copyOf(errors);
    }

    public ProceduralCompositionLayer withName(String value) {
        return copy(value, enabled, operation, generator, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, shellThickness, spacing,
                maximumInstances, seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withEnabled(boolean value) {
        return copy(name, value, operation, generator, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, shellThickness, spacing,
                maximumInstances, seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withOperation(Operation value) {
        return copy(name, enabled, value, generator, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, shellThickness, spacing,
                maximumInstances, seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withGenerator(Generator value) {
        return copy(name, enabled, operation, value, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, shellThickness, spacing,
                maximumInstances, seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withAnchor(Anchor value) {
        return copy(name, enabled, operation, generator, value, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, shellThickness, spacing,
                maximumInstances, seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withPrimitive(Primitive value) {
        return copy(name, enabled, operation, generator, anchor, value,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, shellThickness, spacing,
                maximumInstances, seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withPresetId(String value) {
        return copy(name, enabled, operation, generator, anchor, primitive,
                value, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, shellThickness, spacing,
                maximumInstances, seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withOffsetX(double value) {
        return withOffsets(value, offsetY, offsetZ);
    }

    public ProceduralCompositionLayer withOffsetY(double value) {
        return withOffsets(offsetX, value, offsetZ);
    }

    public ProceduralCompositionLayer withOffsetZ(double value) {
        return withOffsets(offsetX, offsetY, value);
    }

    public ProceduralCompositionLayer withOffsets(
            double x,
            double y,
            double z
    ) {
        return copy(name, enabled, operation, generator, anchor, primitive,
                presetId, x, y, z, sizeX, sizeY, sizeZ, rotationDegrees,
                hollow, shellThickness, spacing, maximumInstances, seedSalt,
                safeVariation);
    }

    public ProceduralCompositionLayer withSizeX(int value) {
        return withSizes(value, sizeY, sizeZ);
    }

    public ProceduralCompositionLayer withSizeY(int value) {
        return withSizes(sizeX, value, sizeZ);
    }

    public ProceduralCompositionLayer withSizeZ(int value) {
        return withSizes(sizeX, sizeY, value);
    }

    public ProceduralCompositionLayer withSizes(int x, int y, int z) {
        return copy(name, enabled, operation, generator, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, x, y, z,
                rotationDegrees, hollow, shellThickness, spacing,
                maximumInstances, seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withRotation(double value) {
        return copy(name, enabled, operation, generator, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                value, hollow, shellThickness, spacing, maximumInstances,
                seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withHollow(boolean value) {
        return copy(name, enabled, operation, generator, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, value, shellThickness, spacing,
                maximumInstances, seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withShellThickness(int value) {
        return copy(name, enabled, operation, generator, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, value, spacing, maximumInstances,
                seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withSpacing(double value) {
        return copy(name, enabled, operation, generator, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, shellThickness, value,
                maximumInstances, seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withMaximumInstances(int value) {
        return copy(name, enabled, operation, generator, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, shellThickness, spacing, value,
                seedSalt, safeVariation);
    }

    public ProceduralCompositionLayer withSeedSalt(long value) {
        return copy(name, enabled, operation, generator, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, shellThickness, spacing,
                maximumInstances, value, safeVariation);
    }

    public ProceduralCompositionLayer withSafeVariation(boolean value) {
        return copy(name, enabled, operation, generator, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, shellThickness, spacing,
                maximumInstances, seedSalt, value);
    }

    private static ProceduralCompositionLayer copy(
            String name,
            boolean enabled,
            Operation operation,
            Generator generator,
            Anchor anchor,
            Primitive primitive,
            String presetId,
            double offsetX,
            double offsetY,
            double offsetZ,
            int sizeX,
            int sizeY,
            int sizeZ,
            double rotationDegrees,
            boolean hollow,
            int shellThickness,
            double spacing,
            int maximumInstances,
            long seedSalt,
            boolean safeVariation
    ) {
        return new ProceduralCompositionLayer(
                name, enabled, operation, generator, anchor, primitive,
                presetId, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ,
                rotationDegrees, hollow, shellThickness, spacing,
                maximumInstances, seedSalt, safeVariation
        );
    }

    public enum Operation {
        UNION,
        REPLACE,
        SUBTRACT,
        SKIP,
        INTERSECT
    }

    public enum Generator {
        PRIMITIVE,
        TREE
    }

    public enum Anchor {
        BOUNDS_CENTER,
        BOUNDS_TOP,
        PATH,
        GENERATED_PATH,
        ALL_PATHS
    }

    public enum Primitive {
        BOX,
        ELLIPSOID,
        CYLINDER,
        CONE,
        ARCH,
        DOME,
        WEDGE,
        DIAMOND,
        PYRAMID,
        TORUS
    }
}
