package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;
import java.util.Set;

/**
 * A named coordinate mask that composes with all other distribution sources.
 */
public final class MaskedWeightSource<T> implements WeightSource<T> {

    private final String name;
    private final Coordinate coordinate;
    private final double minimum;
    private final double maximum;
    private final boolean inverted;
    private final Set<String> candidateIds;
    private final Mode mode;
    private final double multiplier;
    private final Shape shape;
    private final int period;
    private final int thickness;
    private final boolean worldAnchored;
    private final SpatialField spatialField;

    public MaskedWeightSource(
            String name,
            Coordinate coordinate,
            double minimum,
            double maximum,
            boolean inverted,
            Set<String> candidateIds,
            Mode mode,
            double multiplier
    ) {
        this(
                name,
                coordinate,
                minimum,
                maximum,
                inverted,
                candidateIds,
                mode,
                multiplier,
                Shape.RANGE,
                4,
                1,
                false,
                null
        );
    }

    public MaskedWeightSource(
            String name,
            Coordinate coordinate,
            double minimum,
            double maximum,
            boolean inverted,
            Set<String> candidateIds,
            Mode mode,
            double multiplier,
            Shape shape,
            int period,
            int thickness
    ) {
        this(
                name,
                coordinate,
                minimum,
                maximum,
                inverted,
                candidateIds,
                mode,
                multiplier,
                shape,
                period,
                thickness,
                false,
                null
        );
    }

    public MaskedWeightSource(
            String name,
            Coordinate coordinate,
            double minimum,
            double maximum,
            boolean inverted,
            Set<String> candidateIds,
            Mode mode,
            double multiplier,
            Shape shape,
            int period,
            int thickness,
            boolean worldAnchored
    ) {
        this(
                name, coordinate, minimum, maximum, inverted, candidateIds,
                mode, multiplier, shape, period, thickness, worldAnchored,
                null
        );
    }

    public MaskedWeightSource(
            String name,
            Coordinate coordinate,
            double minimum,
            double maximum,
            boolean inverted,
            Set<String> candidateIds,
            Mode mode,
            double multiplier,
            Shape shape,
            int period,
            int thickness,
            boolean worldAnchored,
            SpatialField spatialField
    ) {
        this.name = name;
        this.coordinate = coordinate;
        this.minimum = minimum;
        this.maximum = maximum;
        this.inverted = inverted;
        this.candidateIds = Set.copyOf(candidateIds);
        this.mode = mode;
        this.multiplier = multiplier;
        this.shape = shape;
        this.period = period;
        this.thickness = thickness;
        this.worldAnchored = worldAnchored;
        this.spatialField = spatialField;
    }

    @Override
    public void apply(
            GenerationContext<T> context,
            List<Candidate<T>> candidates,
            double[] weights
    ) {
        boolean inside = matches(context);
        if (inside == inverted) {
            return;
        }
        for (int index = 0; index < candidates.size(); index++) {
            boolean selected = candidateIds.contains(candidates.get(index).id());
            switch (mode) {
                case MULTIPLY -> {
                    if (selected) {
                        weights[index] *= multiplier;
                    }
                }
                case EXCLUDE -> {
                    if (selected) {
                        weights[index] = 0.0;
                    }
                }
                case ONLY -> {
                    if (!selected) {
                        weights[index] = 0.0;
                    }
                }
            }
        }
    }

    @Override
    public List<String> validate(Set<String> availableCandidateIds) {
        var errors = new java.util.ArrayList<String>();
        if (name == null || name.isBlank()) {
            errors.add("Mask layer name must not be blank");
        }
        if (coordinate == null) {
            errors.add("Mask layer coordinate must be selected");
        }
        if (shape == null) {
            errors.add("Mask layer shape must be selected");
        }
        if (shape == Shape.FIELD) {
            if (spatialField == null) {
                errors.add("Field mask requires a spatial field");
            } else {
                errors.addAll(spatialField.validate());
            }
        }
        if ((shape == Shape.RANGE
                || shape == Shape.BANDS
                || shape == Shape.RINGS
                || shape == Shape.FIELD)
                && (!Double.isFinite(minimum)
                || !Double.isFinite(maximum)
                || minimum < 0.0
                || maximum > 1.0
                || minimum > maximum)) {
            errors.add(
                    "Mask layer range must satisfy 0 <= minimum <= maximum <= 1"
            );
        }
        if (period < 1 || period > 1024) {
            errors.add("Mask layer period must be between 1 and 1024");
        }
        if (thickness < 1 || thickness > 1024) {
            errors.add("Mask layer thickness must be between 1 and 1024");
        }
        if (candidateIds.isEmpty()) {
            errors.add("Mask layer requires at least one candidate");
        }
        for (var candidateId : candidateIds) {
            if (!availableCandidateIds.contains(candidateId)) {
                errors.add(
                        "Mask layer references unknown candidate '"
                                + candidateId + "'"
                );
            }
        }
        if (mode == null) {
            errors.add("Mask layer mode must be selected");
        }
        if (!Double.isFinite(multiplier) || multiplier < 0.0) {
            errors.add("Mask layer multiplier must be finite and non-negative");
        }
        if (mode == Mode.MULTIPLY && multiplier == 0.0) {
            errors.add(
                    "Use EXCLUDE instead of a zero mask-layer multiplier"
            );
        }
        return List.copyOf(errors);
    }

    private boolean matches(GenerationContext<T> context) {
        return switch (shape) {
            case RANGE -> inRange(coordinate.sample(context));
            case BANDS -> inRange(bandPhase(context));
            case RINGS -> inRange(ringPhase(context));
            case CHECKER -> Math.floorMod(
                    Math.floorDiv(context.position().x(), period)
                            + Math.floorDiv(context.position().y(), period)
                            + Math.floorDiv(context.position().z(), period),
                    2
            ) == 0;
            case SURFACE -> distanceFromBoundary(context) < thickness;
            case INTERIOR -> distanceFromBoundary(context) >= thickness;
            case FIELD -> inRange(spatialField.sample(context));
        };
    }

    private double bandPhase(GenerationContext<T> context) {
        if (!worldAnchored || coordinate == Coordinate.TRAVERSAL
                || coordinate == Coordinate.PATH
                || coordinate == Coordinate.LATERAL
                || coordinate == Coordinate.DEPTH
                || coordinate == Coordinate.THICKNESS
                || coordinate == Coordinate.SLOPE
                || coordinate == Coordinate.TIP
                || coordinate == Coordinate.JUNCTION) {
            return fraction(coordinate.sample(context) * period);
        }
        double raw = switch (coordinate) {
            case X -> context.position().x();
            case Y -> context.position().y();
            case Z -> context.position().z();
            case DISTANCE -> worldDistance(context.position());
            case TRAVERSAL -> throw new IllegalStateException(
                    "Traversal is handled before raw world sampling"
            );
            case PATH, LATERAL, DEPTH, THICKNESS, SLOPE, TIP, JUNCTION ->
                    throw new IllegalStateException(
                    "Generator-local coordinates are handled before raw world sampling"
            );
        };
        return fraction(raw / period);
    }

    private double ringPhase(GenerationContext<T> context) {
        if (worldAnchored) {
            return fraction(worldDistance(context.position()) / period);
        }
        return fraction(
                context.bounds().normalizedDistance(context.position()) * period
        );
    }

    private static double worldDistance(GridPosition position) {
        double x = position.x();
        double y = position.y();
        double z = position.z();
        return Math.sqrt(x * x + y * y + z * z);
    }

    private boolean inRange(double value) {
        return value >= minimum && value <= maximum;
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }

    private static int distanceFromBoundary(GenerationContext<?> context) {
        var position = context.position();
        var bounds = context.bounds();
        return Math.min(
                Math.min(
                        Math.min(
                                position.x() - bounds.minX(),
                                bounds.maxX() - position.x()
                        ),
                        Math.min(
                                position.y() - bounds.minY(),
                                bounds.maxY() - position.y()
                        )
                ),
                Math.min(
                        position.z() - bounds.minZ(),
                        bounds.maxZ() - position.z()
                )
        );
    }

    public enum Mode {
        MULTIPLY,
        EXCLUDE,
        ONLY
    }

    public enum Shape {
        RANGE,
        BANDS,
        RINGS,
        CHECKER,
        SURFACE,
        INTERIOR,
        FIELD
    }
}
