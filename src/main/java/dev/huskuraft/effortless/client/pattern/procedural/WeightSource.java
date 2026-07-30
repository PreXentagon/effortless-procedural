package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;
import java.util.Set;

public interface WeightSource<T> {

    /**
     * Applies this source to the current weights. Sources are evaluated in list
     * order, which makes composition explicit and deterministic.
     */
    void apply(GenerationContext<T> context, List<Candidate<T>> candidates, double[] weights);

    default List<String> validate(Set<String> candidateIds) {
        return List.of();
    }

    default int estimatedCostPerCandidate() {
        return 1;
    }
}
