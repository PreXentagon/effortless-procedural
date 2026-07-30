package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;
import java.util.Set;

public final class PreferredAdjacencyConstraint<T> implements PlacementConstraint<T> {

    private final String candidateId;
    private final Set<String> preferredNeighborIds;
    private final double multiplierPerNeighbor;
    private final NeighborScope scope;
    private final NeighborTopology topology;

    public PreferredAdjacencyConstraint(
            String candidateId,
            Set<String> preferredNeighborIds,
            double multiplierPerNeighbor,
            NeighborScope scope
    ) {
        this(
                candidateId,
                preferredNeighborIds,
                multiplierPerNeighbor,
                scope,
                NeighborTopology.ORTHOGONAL_6
        );
    }

    public PreferredAdjacencyConstraint(
            String candidateId,
            Set<String> preferredNeighborIds,
            double multiplierPerNeighbor,
            NeighborScope scope,
            NeighborTopology topology
    ) {
        this.candidateId = candidateId;
        this.preferredNeighborIds = Set.copyOf(preferredNeighborIds);
        this.multiplierPerNeighbor = multiplierPerNeighbor;
        this.scope = scope;
        this.topology = topology;
    }

    @Override
    public ConstraintResult evaluate(Candidate<T> candidate, GenerationContext<T> context) {
        return ConstraintResult.allow();
    }

    @Override
    public double preferenceMultiplier(Candidate<T> candidate, GenerationContext<T> context) {
        if (!candidate.id().equals(candidateId)) {
            return 1.0;
        }
        int matches = 0;
        for (var direction : topology.directions()) {
            var neighborId = context.neighborId(context.position().offset(direction), scope);
            if (neighborId.isPresent() && preferredNeighborIds.contains(neighborId.get())) {
                matches++;
            }
        }
        return Math.pow(multiplierPerNeighbor, matches);
    }

    @Override
    public List<String> validate(Set<String> candidateIds) {
        var errors = new java.util.ArrayList<String>();
        if (!candidateIds.contains(candidateId)) {
            errors.add("Preferred adjacency references unknown candidate '" + candidateId + "'");
        }
        for (var preferred : preferredNeighborIds) {
            if (!candidateIds.contains(preferred)
                    && scope != NeighborScope.GENERATED_AND_EXISTING) {
                errors.add("Preferred adjacency references unknown neighbor '" + preferred + "'");
            }
        }
        if (!Double.isFinite(multiplierPerNeighbor) || multiplierPerNeighbor <= 0.0) {
            errors.add("Preferred adjacency multiplier must be finite and greater than zero");
        }
        if (scope == null) {
            errors.add("Preferred adjacency neighbor scope must be selected");
        }
        if (topology == null) {
            errors.add("Preferred adjacency topology must be selected");
        }
        return List.copyOf(errors);
    }

    @Override
    public int estimatedEvaluationCost() {
        return 0;
    }

    @Override
    public int estimatedPreferenceCost() {
        return topology == null ? 1 : topology.directions().size();
    }
}
