package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Requires a candidate to have a bounded number of selected neighboring
 * candidates. Future target positions are treated optimistically during the
 * ordered pass and resolved by the bounded repair pass.
 */
public final class NeighborhoodCountConstraint<T>
        implements PlacementConstraint<T> {

    private final String candidateId;
    private final Set<String> countedNeighborIds;
    private final int minimum;
    private final int maximum;
    private final NeighborTopology topology;
    private final NeighborScope scope;

    public NeighborhoodCountConstraint(
            String candidateId,
            Set<String> countedNeighborIds,
            int minimum,
            int maximum,
            NeighborTopology topology,
            NeighborScope scope
    ) {
        this.candidateId = candidateId;
        this.countedNeighborIds = Set.copyOf(countedNeighborIds);
        this.minimum = minimum;
        this.maximum = maximum;
        this.topology = topology;
        this.scope = scope;
    }

    @Override
    public ConstraintResult evaluate(
            Candidate<T> candidate,
            GenerationContext<T> context
    ) {
        if (!candidate.id().equals(candidateId)) {
            return ConstraintResult.allow();
        }

        int resolvedMatches = 0;
        int unresolved = 0;
        for (var direction : topology.directions()) {
            var neighborPosition = context.position().offset(direction);
            var neighbor = context.neighborId(neighborPosition, scope);
            if (neighbor.isPresent()) {
                if (countedNeighborIds.contains(neighbor.get())) {
                    resolvedMatches++;
                }
            } else if (context.isUnresolvedTarget(neighborPosition)) {
                unresolved++;
            }
        }

        if (resolvedMatches > maximum) {
            return ConstraintResult.reject(
                    "'" + candidateId + "' has " + resolvedMatches
                            + " matching neighbors; maximum is " + maximum
            );
        }
        if (resolvedMatches + unresolved < minimum) {
            return ConstraintResult.reject(
                    "'" + candidateId + "' can have at most "
                            + (resolvedMatches + unresolved)
                            + " matching neighbors; minimum is " + minimum
            );
        }
        return ConstraintResult.allow();
    }

    @Override
    public List<String> validate(Set<String> candidateIds) {
        var errors = new ArrayList<String>();
        if (!candidateIds.contains(candidateId)) {
            errors.add(
                    "Neighbor-count rule references unknown candidate '"
                            + candidateId + "'"
            );
        }
        if (countedNeighborIds.isEmpty()) {
            errors.add("Neighbor-count rule requires at least one neighbor candidate");
        }
        for (var neighborId : countedNeighborIds) {
            if (!candidateIds.contains(neighborId)
                    && scope != NeighborScope.GENERATED_AND_EXISTING) {
                errors.add(
                        "Neighbor-count rule references unknown neighbor candidate '"
                                + neighborId + "'"
                );
            }
        }
        if (topology == null) {
            errors.add("Neighbor-count topology must be selected");
        } else if (minimum < 0 || maximum < minimum
                || maximum > topology.directions().size()) {
            errors.add(
                    "Neighbor-count range must satisfy 0 <= minimum <= maximum <= "
                            + topology.directions().size()
            );
        }
        if (scope == null) {
            errors.add("Neighbor-count scope must be selected");
        }
        return List.copyOf(errors);
    }

    @Override
    public int estimatedEvaluationCost() {
        return topology == null ? 1 : topology.directions().size();
    }
}
