package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.Optional;

@FunctionalInterface
public interface ExistingNeighborLookup {

    ExistingNeighborLookup NONE = position -> Optional.empty();

    /**
     * Returns a stable candidate/block id for an existing-world position.
     */
    Optional<String> candidateIdAt(GridPosition position);
}
