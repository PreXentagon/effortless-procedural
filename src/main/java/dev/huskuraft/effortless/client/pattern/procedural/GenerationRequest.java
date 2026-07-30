package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public record GenerationRequest<T>(
        long seed,
        Collection<GridPosition> positions,
        ProceduralRuleSet<T> ruleSet,
        ExistingNeighborLookup existingNeighbors,
        int maximumPositions,
        BooleanSupplier cancelled,
        GenerationProgress progress
) {

    public static final int DEFAULT_MAXIMUM_POSITIONS = 1_000_000;

    public GenerationRequest {
        positions = java.util.List.copyOf(positions);
        Objects.requireNonNull(ruleSet, "Rule set");
        Objects.requireNonNull(existingNeighbors, "Existing neighbor lookup");
        Objects.requireNonNull(cancelled, "Cancellation supplier");
        Objects.requireNonNull(progress, "Progress listener");
        if (maximumPositions < 1) {
            throw new IllegalArgumentException("Maximum position count must be positive");
        }
    }

    public GenerationRequest(
            long seed,
            Collection<GridPosition> positions,
            ProceduralRuleSet<T> ruleSet,
            ExistingNeighborLookup existingNeighbors,
            int maximumPositions,
            BooleanSupplier cancelled
    ) {
        this(
                seed,
                positions,
                ruleSet,
                existingNeighbors,
                maximumPositions,
                cancelled,
                GenerationProgress.NONE
        );
    }

    public static <T> GenerationRequest<T> create(
            long seed,
            Collection<GridPosition> positions,
            ProceduralRuleSet<T> ruleSet
    ) {
        return new GenerationRequest<>(
                seed,
                positions,
                ruleSet,
                ExistingNeighborLookup.NONE,
                DEFAULT_MAXIMUM_POSITIONS,
                () -> false,
                GenerationProgress.NONE
        );
    }
}
