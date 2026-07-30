package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Proposes a replacement from a completed-generation snapshot. All proposals
 * in one pass are applied simultaneously, preventing traversal-order bias.
 */
public interface PostGenerationRule<T> {

    Optional<String> replacementCandidateId(GenerationContext<T> context);

    default List<String> validate(Set<String> candidateIds) {
        return List.of();
    }

    default int estimatedCostPerPosition() {
        return 1;
    }
}
