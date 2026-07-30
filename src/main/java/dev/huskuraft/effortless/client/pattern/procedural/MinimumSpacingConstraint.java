package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;
import java.util.Set;

/**
 * Prevents any selected candidate from appearing within a bounded
 * neighborhood of another selected candidate.
 */
public final class MinimumSpacingConstraint<T> implements PlacementConstraint<T> {

    public static final int MAXIMUM_RADIUS = 16;
    private static final java.util.concurrent.ConcurrentMap<
            OffsetKey,
            List<GridPosition>
    > OFFSET_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private final Set<String> candidateIds;
    private final int radius;
    private final DistanceMetric metric;
    private final NeighborScope scope;
    private final List<GridPosition> offsets;

    public MinimumSpacingConstraint(
            Set<String> candidateIds,
            int radius,
            DistanceMetric metric,
            NeighborScope scope
    ) {
        this.candidateIds = Set.copyOf(candidateIds);
        this.radius = radius;
        this.metric = metric;
        this.scope = scope;
        this.offsets = createOffsets(radius, metric);
    }

    @Override
    public ConstraintResult evaluate(
            Candidate<T> candidate,
            GenerationContext<T> context
    ) {
        if (!candidateIds.isEmpty() && !candidateIds.contains(candidate.id())) {
            return ConstraintResult.allow();
        }
        for (var offset : offsets) {
            var neighbor = context.neighborId(
                    context.position().offset(
                            offset.x(),
                            offset.y(),
                            offset.z()
                    ),
                    scope
            );
            if (neighbor.isPresent()
                    && candidateIds.contains(neighbor.get())) {
                return ConstraintResult.reject(
                        "'" + candidate.id()
                                + "' violates minimum "
                                + metric.name().toLowerCase()
                                + " spacing " + radius
                );
            }
        }
        return ConstraintResult.allow();
    }

    @Override
    public List<String> validate(Set<String> availableCandidateIds) {
        var errors = new java.util.ArrayList<String>();
        if (radius < 1 || radius > MAXIMUM_RADIUS) {
            errors.add(
                    "Minimum spacing radius must be between 1 and "
                            + MAXIMUM_RADIUS
            );
        }
        if (metric == null) {
            errors.add("Minimum spacing metric must be selected");
        }
        if (scope == null) {
            errors.add("Minimum spacing neighbor scope must be selected");
        }
        for (var candidateId : candidateIds) {
            if (!availableCandidateIds.contains(candidateId)) {
                errors.add(
                        "Minimum spacing references unknown candidate '"
                                + candidateId + "'"
                );
            }
        }
        return List.copyOf(errors);
    }

    @Override
    public int estimatedEvaluationCost() {
        return Math.max(1, offsets.size());
    }

    private static List<GridPosition> createOffsets(
            int radius,
            DistanceMetric metric
    ) {
        if (radius < 1 || radius > MAXIMUM_RADIUS || metric == null) {
            return List.of();
        }
        return OFFSET_CACHE.computeIfAbsent(
                new OffsetKey(radius, metric),
                MinimumSpacingConstraint::buildOffsets
        );
    }

    private static List<GridPosition> buildOffsets(OffsetKey key) {
        var offsets = new java.util.ArrayList<GridPosition>();
        for (int dy = -key.radius(); dy <= key.radius(); dy++) {
            for (int dz = -key.radius(); dz <= key.radius(); dz++) {
                for (int dx = -key.radius(); dx <= key.radius(); dx++) {
                    if ((dx != 0 || dy != 0 || dz != 0)
                            && key.metric().within(
                                    dx,
                                    dy,
                                    dz,
                                    key.radius()
                            )) {
                        offsets.add(new GridPosition(dx, dy, dz));
                    }
                }
            }
        }
        return List.copyOf(offsets);
    }

    private record OffsetKey(int radius, DistanceMetric metric) {
    }

    public enum DistanceMetric {
        MANHATTAN {
            @Override
            boolean within(int dx, int dy, int dz, int radius) {
                return Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius;
            }
        },
        CHEBYSHEV {
            @Override
            boolean within(int dx, int dy, int dz, int radius) {
                return Math.max(
                        Math.abs(dx),
                        Math.max(Math.abs(dy), Math.abs(dz))
                ) <= radius;
            }
        };

        abstract boolean within(int dx, int dy, int dz, int radius);
    }
}
