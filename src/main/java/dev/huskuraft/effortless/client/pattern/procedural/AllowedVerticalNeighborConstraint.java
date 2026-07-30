package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;
import java.util.Set;

public final class AllowedVerticalNeighborConstraint<T> implements PlacementConstraint<T> {

    private final String candidateId;
    private final GridPosition.Direction direction;
    private final Set<String> allowedNeighborIds;
    private final NeighborScope scope;
    private final UnresolvedBehavior unresolvedBehavior;

    public AllowedVerticalNeighborConstraint(
            String candidateId,
            GridPosition.Direction direction,
            Set<String> allowedNeighborIds,
            NeighborScope scope,
            UnresolvedBehavior unresolvedBehavior
    ) {
        this.candidateId = candidateId;
        this.direction = direction;
        this.allowedNeighborIds = Set.copyOf(allowedNeighborIds);
        this.scope = scope;
        this.unresolvedBehavior = unresolvedBehavior;
    }

    @Override
    public ConstraintResult evaluate(Candidate<T> candidate, GenerationContext<T> context) {
        if (!candidate.id().equals(candidateId)) {
            return ConstraintResult.allow();
        }
        var neighborPosition = context.position().offset(direction);
        var neighbor = context.neighborId(neighborPosition, scope);
        if (neighbor.isEmpty()) {
            if (context.isUnresolvedTarget(neighborPosition)
                    && unresolvedBehavior == UnresolvedBehavior.ALLOW) {
                return ConstraintResult.allow();
            }
            return ConstraintResult.reject(
                    "'" + candidate.id() + "' requires an allowed "
                            + direction.name().toLowerCase() + " neighbor"
            );
        }
        if (allowedNeighborIds.contains(neighbor.get())) {
            return ConstraintResult.allow();
        }
        return ConstraintResult.reject(
                "'" + candidate.id() + "' is not allowed next to '" + neighbor.get()
                        + "' at " + direction.name().toLowerCase()
        );
    }

    @Override
    public List<String> validate(Set<String> candidateIds) {
        var errors = new java.util.ArrayList<String>();
        if (!candidateIds.contains(candidateId)) {
            errors.add("Vertical neighbor rule references unknown candidate '" + candidateId + "'");
        }
        for (var allowed : allowedNeighborIds) {
            if (!candidateIds.contains(allowed)
                    && scope != NeighborScope.GENERATED_AND_EXISTING) {
                errors.add("Vertical neighbor rule references unknown allowed candidate '" + allowed + "'");
            }
        }
        if (direction != GridPosition.Direction.UP && direction != GridPosition.Direction.DOWN) {
            errors.add("Vertical neighbor direction must be UP or DOWN");
        }
        if (scope == null) {
            errors.add("Vertical neighbor scope must be selected");
        }
        if (unresolvedBehavior == null) {
            errors.add("Vertical neighbor unresolved behavior must be selected");
        }
        return List.copyOf(errors);
    }

    public enum UnresolvedBehavior {
        ALLOW,
        REJECT
    }
}
