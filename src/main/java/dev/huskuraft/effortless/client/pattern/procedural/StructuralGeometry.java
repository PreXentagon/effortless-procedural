package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.OptionalDouble;

/**
 * Generator-independent local geometry for one explicit output cell.
 *
 * <p>Generators may provide as much of this data as they can calculate. The
 * procedural rule engine consumes the normalized scalar channels, while the
 * final block-state resolver uses the tangent and surface normal. Nothing in
 * this record is serialized to the server.</p>
 */
public record StructuralGeometry(
        double path,
        double lateral,
        double depth,
        double thickness,
        double slope,
        double tip,
        double junction,
        double tangentX,
        double tangentY,
        double tangentZ,
        double normalX,
        double normalY,
        double normalZ
) {

    public static final StructuralGeometry NONE = new StructuralGeometry(
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 0.0
    );

    public StructuralGeometry {
        path = clamp01(path);
        lateral = clamp01(lateral);
        depth = clamp01(depth);
        thickness = clamp01(thickness);
        slope = clamp01(slope);
        tip = clamp01(tip);
        junction = clamp01(junction);

        double tangentLength = length(tangentX, tangentY, tangentZ);
        if (tangentLength <= 1.0e-9) {
            tangentX = 0.0;
            tangentY = 1.0;
            tangentZ = 0.0;
        } else {
            tangentX /= tangentLength;
            tangentY /= tangentLength;
            tangentZ /= tangentLength;
        }

        double normalLength = length(normalX, normalY, normalZ);
        if (normalLength <= 1.0e-9) {
            normalX = 0.0;
            normalY = 0.0;
            normalZ = 0.0;
        } else {
            normalX /= normalLength;
            normalY /= normalLength;
            normalZ /= normalLength;
        }
    }

    public static StructuralGeometry basic(
            double path,
            double lateral,
            double depth
    ) {
        return new StructuralGeometry(
                path, lateral, depth,
                0.0, 0.0, tipFromPath(path), 0.0,
                0.0, 1.0, 0.0,
                0.0, 0.0, 0.0
        );
    }

    public OptionalDouble sample(Coordinate coordinate) {
        return switch (coordinate) {
            case PATH -> OptionalDouble.of(path);
            case LATERAL -> OptionalDouble.of(lateral);
            case DEPTH -> OptionalDouble.of(depth);
            case THICKNESS -> OptionalDouble.of(thickness);
            case SLOPE -> OptionalDouble.of(slope);
            case TIP -> OptionalDouble.of(tip);
            case JUNCTION -> OptionalDouble.of(junction);
            case X, Y, Z, DISTANCE, TRAVERSAL -> OptionalDouble.empty();
        };
    }

    public StructuralGeometry withJunction(double value) {
        return copy(
                path, lateral, depth, thickness, slope, tip, value,
                tangentX, tangentY, tangentZ,
                normalX, normalY, normalZ
        );
    }

    public StructuralGeometry withTip(double value) {
        return copy(
                path, lateral, depth, thickness, slope, value, junction,
                tangentX, tangentY, tangentZ,
                normalX, normalY, normalZ
        );
    }

    private StructuralGeometry copy(
            double path,
            double lateral,
            double depth,
            double thickness,
            double slope,
            double tip,
            double junction,
            double tangentX,
            double tangentY,
            double tangentZ,
            double normalX,
            double normalY,
            double normalZ
    ) {
        return new StructuralGeometry(
                path, lateral, depth, thickness, slope, tip, junction,
                tangentX, tangentY, tangentZ,
                normalX, normalY, normalZ
        );
    }

    public static double tipFromPath(double path) {
        return clamp01((path - 0.72) / 0.28);
    }

    private static double length(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
