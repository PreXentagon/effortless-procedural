package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;
import java.util.Set;

/**
 * Limits contiguous identical candidates on X, Y and Z. Only neighbors already
 * resolved by traversal (and optionally outside-target world neighbors) count.
 */
public final class MaxRunLengthConstraint<T> implements PlacementConstraint<T> {

    public static final int MAXIMUM_RUN_LENGTH = 1_024;

    private final Set<String> candidateIds;
    private final int maximum;
    private final NeighborScope scope;

    public MaxRunLengthConstraint(Set<String> candidateIds, int maximum, NeighborScope scope) {
        this.candidateIds = Set.copyOf(candidateIds);
        this.maximum = maximum;
        this.scope = scope;
    }

    @Override
    public ConstraintResult evaluate(Candidate<T> candidate, GenerationContext<T> context) {
        if (!candidateIds.isEmpty() && !candidateIds.contains(candidate.id())) {
            return ConstraintResult.allow();
        }
        for (var axis : Axis.values()) {
            int run = 1
                    + count(candidate.id(), context, axis.negative())
                    + count(candidate.id(), context, axis.positive());
            if (run > maximum) {
                return ConstraintResult.reject(
                        "'" + candidate.id() + "' would create a run of " + run
                                + " on " + axis.name() + " (maximum " + maximum + ")"
                );
            }
        }
        return ConstraintResult.allow();
    }

    @Override
    public List<String> validate(Set<String> availableCandidateIds) {
        var errors = new java.util.ArrayList<String>();
        if (maximum < 1) {
            errors.add("Maximum run length must be at least one");
        } else if (maximum > MAXIMUM_RUN_LENGTH) {
            errors.add(
                    "Maximum run length must not exceed "
                            + MAXIMUM_RUN_LENGTH
            );
        }
        if (scope == null) {
            errors.add("Maximum run neighbor scope must be selected");
        }
        for (var candidateId : candidateIds) {
            if (!availableCandidateIds.contains(candidateId)) {
                errors.add("Maximum run references unknown candidate '" + candidateId + "'");
            }
        }
        return List.copyOf(errors);
    }

    @Override
    public int estimatedEvaluationCost() {
        return Math.max(1, 6 * Math.min(maximum, MAXIMUM_RUN_LENGTH));
    }

    private int count(
            String candidateId,
            GenerationContext<T> context,
            GridPosition.Direction direction
    ) {
        int count = 0;
        var cursor = context.position().offset(direction);
        while (count < maximum) {
            var neighbor = context.neighborId(cursor, scope);
            if (neighbor.isEmpty() || !neighbor.get().equals(candidateId)) {
                break;
            }
            count++;
            cursor = cursor.offset(direction);
        }
        return count;
    }

    private enum Axis {
        X(GridPosition.Direction.WEST, GridPosition.Direction.EAST),
        Y(GridPosition.Direction.DOWN, GridPosition.Direction.UP),
        Z(GridPosition.Direction.NORTH, GridPosition.Direction.SOUTH);

        private final GridPosition.Direction negative;
        private final GridPosition.Direction positive;

        Axis(GridPosition.Direction negative, GridPosition.Direction positive) {
            this.negative = negative;
            this.positive = positive;
        }

        private GridPosition.Direction negative() {
            return negative;
        }

        private GridPosition.Direction positive() {
            return positive;
        }
    }
}
