package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Replaces selected candidates when the number of matching neighbors lies in
 * a configured inclusive range. This supports removing isolated accents,
 * filling holes, and simple deterministic smoothing.
 */
public final class NeighborhoodReplacementRule<T>
        implements PostGenerationRule<T> {

    private final Set<String> sourceCandidateIds;
    private final Set<String> matchingNeighborIds;
    private final String replacementCandidateId;
    private final int minimumMatches;
    private final int maximumMatches;
    private final NeighborTopology topology;
    private final NeighborScope scope;

    public NeighborhoodReplacementRule(
            Set<String> sourceCandidateIds,
            Set<String> matchingNeighborIds,
            String replacementCandidateId,
            int minimumMatches,
            int maximumMatches,
            NeighborTopology topology,
            NeighborScope scope
    ) {
        this.sourceCandidateIds = Set.copyOf(sourceCandidateIds);
        this.matchingNeighborIds = Set.copyOf(matchingNeighborIds);
        this.replacementCandidateId = replacementCandidateId;
        this.minimumMatches = minimumMatches;
        this.maximumMatches = maximumMatches;
        this.topology = topology;
        this.scope = scope;
    }

    @Override
    public Optional<String> replacementCandidateId(
            GenerationContext<T> context
    ) {
        var current = context.generated().get(context.position());
        if (current == null || !sourceCandidateIds.contains(current.id())) {
            return Optional.empty();
        }
        int matches = 0;
        for (var direction : topology.directions()) {
            var neighbor = context.neighborId(
                    context.position().offset(direction),
                    scope
            );
            if (neighbor.isPresent()
                    && matchingNeighborIds.contains(neighbor.get())) {
                matches++;
            }
        }
        return matches >= minimumMatches && matches <= maximumMatches
                ? Optional.of(replacementCandidateId)
                : Optional.empty();
    }

    @Override
    public List<String> validate(Set<String> candidateIds) {
        var errors = new ArrayList<String>();
        if (sourceCandidateIds.isEmpty()) {
            errors.add("Cleanup rule requires at least one source candidate");
        }
        if (matchingNeighborIds.isEmpty()) {
            errors.add("Cleanup rule requires at least one matching neighbor");
        }
        for (var id : sourceCandidateIds) {
            if (!candidateIds.contains(id)) {
                errors.add("Cleanup rule references unknown source '" + id + "'");
            }
        }
        for (var id : matchingNeighborIds) {
            if (!candidateIds.contains(id)
                    && scope != NeighborScope.GENERATED_AND_EXISTING) {
                errors.add("Cleanup rule references unknown neighbor '" + id + "'");
            }
        }
        if (!candidateIds.contains(replacementCandidateId)) {
            errors.add(
                    "Cleanup rule references unknown replacement '"
                            + replacementCandidateId + "'"
            );
        }
        if (topology == null) {
            errors.add("Cleanup topology must be selected");
        } else if (minimumMatches < 0
                || maximumMatches < minimumMatches
                || maximumMatches > topology.directions().size()) {
            errors.add(
                    "Cleanup match range must satisfy 0 <= minimum <= maximum <= "
                            + topology.directions().size()
            );
        }
        if (scope == null) {
            errors.add("Cleanup neighbor scope must be selected");
        }
        return List.copyOf(errors);
    }

    @Override
    public int estimatedCostPerPosition() {
        return topology == null ? 1 : topology.directions().size();
    }
}
