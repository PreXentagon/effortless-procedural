package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;
import java.util.Set;

public interface PlacementConstraint<T> {

    ConstraintResult evaluate(Candidate<T> candidate, GenerationContext<T> context);

    /**
     * Soft preference multiplier evaluated before candidate selection.
     */
    default double preferenceMultiplier(Candidate<T> candidate, GenerationContext<T> context) {
        return 1.0;
    }

    default List<String> validate(Set<String> candidateIds) {
        return List.of();
    }

    default int estimatedEvaluationCost() {
        return 1;
    }

    default int estimatedPreferenceCost() {
        return 0;
    }
}
