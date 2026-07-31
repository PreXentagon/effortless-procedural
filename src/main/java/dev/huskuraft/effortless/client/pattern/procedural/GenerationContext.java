package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record GenerationContext<T>(
        long seed,
        GridPosition position,
        int ordinal,
        int positionCount,
        GenerationBounds bounds,
        Set<GridPosition> targetPositions,
        Map<GridPosition, Candidate<T>> generated,
        ExistingNeighborLookup existingNeighbors,
        Map<String, Integer> generatedCounts,
        CoordinateLookup coordinateLookup
) {

    public GenerationContext(
            long seed,
            GridPosition position,
            int ordinal,
            int positionCount,
            GenerationBounds bounds,
            Set<GridPosition> targetPositions,
            Map<GridPosition, Candidate<T>> generated,
            ExistingNeighborLookup existingNeighbors
    ) {
        this(
                seed,
                position,
                ordinal,
                positionCount,
                bounds,
                targetPositions,
                generated,
                existingNeighbors,
                countCandidates(generated),
                CoordinateLookup.NONE
        );
    }

    public GenerationContext(
            long seed,
            GridPosition position,
            int ordinal,
            int positionCount,
            GenerationBounds bounds,
            Set<GridPosition> targetPositions,
            Map<GridPosition, Candidate<T>> generated,
            ExistingNeighborLookup existingNeighbors,
            Map<String, Integer> generatedCounts
    ) {
        this(
                seed,
                position,
                ordinal,
                positionCount,
                bounds,
                targetPositions,
                generated,
                existingNeighbors,
                generatedCounts,
                CoordinateLookup.NONE
        );
    }

    public GenerationContext {
        coordinateLookup = coordinateLookup == null
                ? CoordinateLookup.NONE
                : coordinateLookup;
    }

    public Optional<String> neighborId(GridPosition neighbor, NeighborScope scope) {
        var generatedCandidate = generated.get(neighbor);
        if (generatedCandidate != null) {
            return Optional.of(generatedCandidate.id());
        }

        // A target that has not been traversed yet is intentionally unresolved.
        // Looking through it at the old world state would make preview/build
        // constraints depend on blocks that the output itself will replace.
        if (targetPositions.contains(neighbor)) {
            return Optional.empty();
        }

        if (scope == NeighborScope.GENERATED_AND_EXISTING) {
            return existingNeighbors.candidateIdAt(neighbor);
        }
        return Optional.empty();
    }

    public boolean isUnresolvedTarget(GridPosition neighbor) {
        return targetPositions.contains(neighbor)
                && !generated.containsKey(neighbor);
    }

    public int candidateCountAfterPlacement(String candidateId) {
        return candidateCountAfterPlacement(candidateId, candidateId);
    }

    public int candidateCountAfterPlacement(
            String countedCandidateId,
            String proposedCandidateId
    ) {
        int count = generatedCounts.getOrDefault(countedCandidateId, 0);
        var current = generated.get(position);
        if (current != null && current.id().equals(countedCandidateId)) {
            count--;
        }
        if (proposedCandidateId.equals(countedCandidateId)) {
            count++;
        }
        return count;
    }

    public int unresolvedPositionsAfterPlacement() {
        return Math.max(
                0,
                positionCount - generated.size()
                        - (generated.containsKey(position) ? 0 : 1)
        );
    }

    private static <T> Map<String, Integer> countCandidates(
            Map<GridPosition, Candidate<T>> generated
    ) {
        var counts = new java.util.HashMap<String, Integer>();
        for (var candidate : generated.values()) {
            counts.merge(candidate.id(), 1, Integer::sum);
        }
        return counts;
    }
}
