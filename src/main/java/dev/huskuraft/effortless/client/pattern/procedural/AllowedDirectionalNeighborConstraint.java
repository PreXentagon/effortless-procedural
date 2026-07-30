package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;
import java.util.Set;

public final class AllowedDirectionalNeighborConstraint<T>
        implements PlacementConstraint<T> {

    private final String candidateId;
    private final NeighborDirection direction;
    private final Set<String> allowedNeighborIds;
    private final NeighborScope scope;
    private final UnresolvedBehavior unresolvedBehavior;

    public AllowedDirectionalNeighborConstraint(
            String candidateId,
            NeighborDirection direction,
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
    public ConstraintResult evaluate(
            Candidate<T> candidate,
            GenerationContext<T> context
    ) {
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
                "'" + candidate.id() + "' is not allowed next to '"
                        + neighbor.get() + "' at "
                        + direction.name().toLowerCase()
        );
    }

    @Override
    public List<String> validate(Set<String> candidateIds) {
        var errors = new java.util.ArrayList<String>();
        if (!candidateIds.contains(candidateId)) {
            errors.add(
                    "Directional rule references unknown candidate '"
                            + candidateId + "'"
            );
        }
        for (var allowed : allowedNeighborIds) {
            if (!candidateIds.contains(allowed)
                    && scope != NeighborScope.GENERATED_AND_EXISTING) {
                errors.add(
                        "Directional rule references unknown allowed candidate '"
                                + allowed + "'"
                );
            }
        }
        if (allowedNeighborIds.isEmpty()) {
            errors.add("Directional rule requires at least one allowed candidate");
        }
        if (direction == null) {
            errors.add("Directional rule direction must be selected");
        }
        if (scope == null) {
            errors.add("Directional rule neighbor scope must be selected");
        }
        if (unresolvedBehavior == null) {
            errors.add("Directional rule unresolved behavior must be selected");
        }
        return List.copyOf(errors);
    }

    public enum UnresolvedBehavior {
        ALLOW,
        REJECT
    }
}
