package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only scalar field used to drive gradients. The field is sampled from
 * stable selection/world coordinates and is fully resolved before any stock
 * packet is created.
 */
public record SpatialField(
        Shape shape,
        Coordinate coordinate,
        double centerX,
        double centerY,
        double centerZ,
        double scaleX,
        double scaleY,
        double scaleZ,
        double rotationDegrees,
        int polygonSides,
        double curvature,
        int repeat,
        boolean inverted,
        double warpAmount,
        double warpFrequency,
        long warpSalt
) {

    public static final SpatialField DEFAULT = linear(Coordinate.Y);

    public static SpatialField linear(Coordinate coordinate) {
        return new SpatialField(
                Shape.LINEAR,
                coordinate,
                0.5,
                0.5,
                0.5,
                1.0,
                1.0,
                1.0,
                0.0,
                3,
                0.0,
                1,
                false,
                0.0,
                0.1,
                0L
        );
    }

    public SpatialField withShape(Shape value) {
        return copy(value, coordinate, centerX, centerY, centerZ, scaleX,
                scaleY, scaleZ, rotationDegrees, polygonSides, curvature,
                repeat, inverted, warpAmount, warpFrequency, warpSalt);
    }

    public SpatialField withCoordinate(Coordinate value) {
        return copy(shape, value, centerX, centerY, centerZ, scaleX, scaleY,
                scaleZ, rotationDegrees, polygonSides, curvature, repeat,
                inverted, warpAmount, warpFrequency, warpSalt);
    }

    public SpatialField withCenter(double x, double y, double z) {
        return copy(shape, coordinate, x, y, z, scaleX, scaleY, scaleZ,
                rotationDegrees, polygonSides, curvature, repeat, inverted,
                warpAmount, warpFrequency, warpSalt);
    }

    public SpatialField withScale(double x, double y, double z) {
        return copy(shape, coordinate, centerX, centerY, centerZ, x, y, z,
                rotationDegrees, polygonSides, curvature, repeat, inverted,
                warpAmount, warpFrequency, warpSalt);
    }

    public SpatialField withRotation(double value) {
        return copy(shape, coordinate, centerX, centerY, centerZ, scaleX,
                scaleY, scaleZ, value, polygonSides, curvature, repeat,
                inverted, warpAmount, warpFrequency, warpSalt);
    }

    public SpatialField withPolygonSides(int value) {
        return copy(shape, coordinate, centerX, centerY, centerZ, scaleX,
                scaleY, scaleZ, rotationDegrees, value, curvature, repeat,
                inverted, warpAmount, warpFrequency, warpSalt);
    }

    public SpatialField withCurvature(double value) {
        return copy(shape, coordinate, centerX, centerY, centerZ, scaleX,
                scaleY, scaleZ, rotationDegrees, polygonSides, value, repeat,
                inverted, warpAmount, warpFrequency, warpSalt);
    }

    public SpatialField withRepeat(int value) {
        return copy(shape, coordinate, centerX, centerY, centerZ, scaleX,
                scaleY, scaleZ, rotationDegrees, polygonSides, curvature,
                value, inverted, warpAmount, warpFrequency, warpSalt);
    }

    public SpatialField withInverted(boolean value) {
        return copy(shape, coordinate, centerX, centerY, centerZ, scaleX,
                scaleY, scaleZ, rotationDegrees, polygonSides, curvature,
                repeat, value, warpAmount, warpFrequency, warpSalt);
    }

    public SpatialField withWarp(
            double amount,
            double frequency,
            long salt
    ) {
        return copy(shape, coordinate, centerX, centerY, centerZ, scaleX,
                scaleY, scaleZ, rotationDegrees, polygonSides, curvature,
                repeat, inverted, amount, frequency, salt);
    }

    public double sample(GenerationContext<?> context) {
        double x = centered(
                context.bounds().normalizedX(context.position()),
                centerX,
                scaleX
        );
        double y = centered(
                context.bounds().normalizedY(context.position()),
                centerY,
                scaleY
        );
        double z = centered(
                context.bounds().normalizedZ(context.position()),
                centerZ,
                scaleZ
        );
        var rotated = rotate(
                primary(x, y, z),
                secondary(context, x, y, z)
        );
        double value = switch (shape) {
            case LINEAR -> linear(context, rotated.first());
            case CURVE -> clamp01(
                    linear(context, rotated.first())
                            + curvature * (rotated.second() * rotated.second()
                            - 0.125)
            );
            case WAVE -> clamp01(
                    linear(context, rotated.first())
                            + curvature * 0.25
                            * Math.sin(rotated.second() * Math.PI * 2.0)
            );
            case RADIAL -> radial(context, x, y, z);
            case SQUARE -> square(context, x, y, z);
            case DIAMOND -> diamond(context, x, y, z);
            case POLYGON -> polygon(rotated.first(), rotated.second());
        };
        if (warpAmount > 0.0) {
            var position = context.position();
            double noise = StableRandom.valueNoise(
                    context.seed(),
                    position.x() * warpFrequency,
                    position.y() * warpFrequency,
                    position.z() * warpFrequency,
                    warpSalt
            );
            value = clamp01(value + (noise - 0.5) * warpAmount);
        }
        if (repeat > 1) {
            value = fraction(value * repeat);
        }
        return inverted ? 1.0 - value : value;
    }

    public List<String> validate() {
        var errors = new ArrayList<String>();
        if (shape == null) {
            errors.add("Gradient field shape must be selected");
        }
        if (coordinate == null) {
            errors.add("Gradient field coordinate must be selected");
        }
        validateFiniteRange(errors, "center X", centerX, -4.0, 4.0);
        validateFiniteRange(errors, "center Y", centerY, -4.0, 4.0);
        validateFiniteRange(errors, "center Z", centerZ, -4.0, 4.0);
        validatePositive(errors, "scale X", scaleX);
        validatePositive(errors, "scale Y", scaleY);
        validatePositive(errors, "scale Z", scaleZ);
        if (!Double.isFinite(rotationDegrees)) {
            errors.add("Gradient field rotation must be finite");
        }
        if (polygonSides < 3 || polygonSides > 32) {
            errors.add("Gradient polygon sides must be between 3 and 32");
        }
        validateFiniteRange(errors, "curvature", curvature, -4.0, 4.0);
        if (repeat < 1 || repeat > 64) {
            errors.add("Gradient repeat must be between 1 and 64");
        }
        validateFiniteRange(errors, "warp amount", warpAmount, 0.0, 2.0);
        if (!Double.isFinite(warpFrequency) || warpFrequency <= 0.0) {
            errors.add("Gradient warp frequency must be greater than zero");
        }
        return List.copyOf(errors);
    }

    private double linear(GenerationContext<?> context, double rotatedPrimary) {
        if (coordinate == Coordinate.TRAVERSAL) {
            return clamp01(
                    centered(coordinate.sample(context), centerX, scaleX) + 0.5
            );
        }
        if (coordinate == Coordinate.DISTANCE) {
            return clamp01(
                    centered(
                            context.bounds().normalizedDistance(
                                    context.position()
                            ),
                            centerX,
                            scaleX
                    ) + 0.5
            );
        }
        return clamp01(rotatedPrimary + 0.5);
    }

    private double radial(
            GenerationContext<?> context,
            double x,
            double y,
            double z
    ) {
        var values = activeValues(context, x, y, z);
        double sum = 0.0;
        for (double value : values) {
            sum += value * value;
        }
        return clamp01(Math.sqrt(sum) / (0.5 * Math.sqrt(values.length)));
    }

    private double square(
            GenerationContext<?> context,
            double x,
            double y,
            double z
    ) {
        double maximum = 0.0;
        for (double value : activeValues(context, x, y, z)) {
            maximum = Math.max(maximum, Math.abs(value));
        }
        return clamp01(maximum / 0.5);
    }

    private double diamond(
            GenerationContext<?> context,
            double x,
            double y,
            double z
    ) {
        var values = activeValues(context, x, y, z);
        double sum = 0.0;
        for (double value : values) {
            sum += Math.abs(value);
        }
        return clamp01(sum / (0.5 * values.length));
    }

    private double polygon(double first, double second) {
        double x = first / 0.5;
        double y = second / 0.5;
        double radius = Math.sqrt(x * x + y * y);
        if (radius == 0.0) {
            return 0.0;
        }
        double sector = Math.PI * 2.0 / polygonSides;
        double angle = Math.atan2(y, x) + Math.PI / polygonSides;
        double local = Math.floorMod(
                (long) Math.floor(angle / sector),
                polygonSides
        ) * sector;
        double boundary = Math.cos(Math.PI / polygonSides)
                / Math.cos(angle - local - Math.PI / polygonSides);
        return clamp01(radius / Math.max(0.000001, boundary));
    }

    private double[] activeValues(
            GenerationContext<?> context,
            double x,
            double y,
            double z
    ) {
        var values = new ArrayList<Double>(3);
        if (context.bounds().minX() != context.bounds().maxX()) {
            values.add(x);
        }
        if (context.bounds().minY() != context.bounds().maxY()) {
            values.add(y);
        }
        if (context.bounds().minZ() != context.bounds().maxZ()) {
            values.add(z);
        }
        if (values.isEmpty()) {
            values.add(primary(x, y, z));
        }
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private double primary(double x, double y, double z) {
        return switch (coordinate) {
            case X -> x;
            case Y -> y;
            case Z -> z;
            case DISTANCE, TRAVERSAL -> x;
        };
    }

    private double secondary(
            GenerationContext<?> context,
            double x,
            double y,
            double z
    ) {
        var bounds = context.bounds();
        return switch (coordinate) {
            case X -> bounds.minY() != bounds.maxY() ? y : z;
            case Y -> bounds.minX() != bounds.maxX() ? x : z;
            case Z -> bounds.minY() != bounds.maxY() ? y : x;
            case DISTANCE, TRAVERSAL ->
                    bounds.minY() != bounds.maxY() ? y : z;
        };
    }

    private Pair rotate(double first, double second) {
        double radians = Math.toRadians(rotationDegrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Pair(
                first * cosine - second * sine,
                first * sine + second * cosine
        );
    }

    private SpatialField copy(
            Shape shape,
            Coordinate coordinate,
            double centerX,
            double centerY,
            double centerZ,
            double scaleX,
            double scaleY,
            double scaleZ,
            double rotationDegrees,
            int polygonSides,
            double curvature,
            int repeat,
            boolean inverted,
            double warpAmount,
            double warpFrequency,
            long warpSalt
    ) {
        return new SpatialField(
                shape, coordinate, centerX, centerY, centerZ, scaleX, scaleY,
                scaleZ, rotationDegrees, polygonSides, curvature, repeat,
                inverted, warpAmount, warpFrequency, warpSalt
        );
    }

    private static double centered(
            double value,
            double center,
            double scale
    ) {
        return (value - center) / scale;
    }

    private static void validatePositive(
            List<String> errors,
            String name,
            double value
    ) {
        if (!Double.isFinite(value) || value <= 0.0 || value > 64.0) {
            errors.add("Gradient field " + name
                    + " must be greater than zero and at most 64");
        }
    }

    private static void validateFiniteRange(
            List<String> errors,
            String name,
            double value,
            double minimum,
            double maximum
    ) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            errors.add("Gradient field " + name + " must be between "
                    + minimum + " and " + maximum);
        }
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record Pair(double first, double second) {
    }

    public enum Shape {
        LINEAR,
        CURVE,
        WAVE,
        RADIAL,
        SQUARE,
        DIAMOND,
        POLYGON
    }
}
