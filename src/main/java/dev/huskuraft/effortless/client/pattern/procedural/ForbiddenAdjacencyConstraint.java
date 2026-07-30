package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ForbiddenAdjacencyConstraint<T> implements PlacementConstraint<T> {

    private final Set<Pair> forbiddenPairs;
    private final NeighborScope scope;
    private final NeighborTopology topology;

    public ForbiddenAdjacencyConstraint(Set<Pair> forbiddenPairs, NeighborScope scope) {
        this(forbiddenPairs, scope, NeighborTopology.ORTHOGONAL_6);
    }

    public ForbiddenAdjacencyConstraint(
            Set<Pair> forbiddenPairs,
            NeighborScope scope,
            NeighborTopology topology
    ) {
        this.forbiddenPairs = Set.copyOf(new LinkedHashSet<>(forbiddenPairs));
        this.scope = scope;
        this.topology = topology;
    }

    @Override
    public ConstraintResult evaluate(Candidate<T> candidate, GenerationContext<T> context) {
        for (var direction : topology.directions()) {
            var neighborId = context.neighborId(context.position().offset(direction), scope);
            if (neighborId.isPresent() && isForbidden(candidate.id(), neighborId.get())) {
                return ConstraintResult.reject(
                        "'" + candidate.id() + "' cannot touch '" + neighborId.get()
                                + "' at " + direction.name().toLowerCase()
                );
            }
        }
        return ConstraintResult.allow();
    }

    @Override
    public List<String> validate(Set<String> candidateIds) {
        var errors = new ArrayList<String>();
        if (scope == null) {
            errors.add("Forbidden adjacency neighbor scope must be selected");
        }
        if (topology == null) {
            errors.add("Forbidden adjacency topology must be selected");
        }
        for (var pair : forbiddenPairs) {
            boolean firstGenerated = candidateIds.contains(pair.first());
            boolean secondGenerated = candidateIds.contains(pair.second());
            if (!firstGenerated && !secondGenerated) {
                errors.add(
                        "Forbidden adjacency pair must contain at least one "
                                + "generated candidate ('" + pair.first()
                                + "', '" + pair.second() + "')"
                );
                continue;
            }
            if (!firstGenerated
                    && scope != NeighborScope.GENERATED_AND_EXISTING) {
                errors.add("Forbidden adjacency references unknown candidate '" + pair.first() + "'");
            }
            if (!secondGenerated
                    && scope != NeighborScope.GENERATED_AND_EXISTING) {
                errors.add("Forbidden adjacency references unknown candidate '" + pair.second() + "'");
            }
        }
        return List.copyOf(errors);
    }

    @Override
    public int estimatedEvaluationCost() {
        return topology == null ? 1 : topology.directions().size();
    }

    private boolean isForbidden(String first, String second) {
        return forbiddenPairs.contains(new Pair(first, second))
                || forbiddenPairs.contains(new Pair(second, first));
    }

    public record Pair(String first, String second) {

        public Pair {
            if (first == null || first.isBlank() || second == null || second.isBlank()) {
                throw new IllegalArgumentException("Forbidden candidate ids must not be blank");
            }
        }
    }
}
