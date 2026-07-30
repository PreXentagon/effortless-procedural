package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProceduralRuleSet<T>(
        List<Candidate<T>> candidates,
        List<WeightSource<T>> weightSources,
        List<PlacementConstraint<T>> constraints,
        int retryLimit,
        Optional<String> fallbackCandidateId,
        int repairPasses,
        List<PostGenerationRule<T>> cleanupRules,
        int cleanupPasses
) {

    public static final int MAX_RETRY_LIMIT = 256;
    public static final int MAX_REPAIR_PASSES = 8;
    public static final int MAX_CLEANUP_PASSES = 8;

    public ProceduralRuleSet(
            List<Candidate<T>> candidates,
            List<WeightSource<T>> weightSources,
            List<PlacementConstraint<T>> constraints,
            int retryLimit,
            Optional<String> fallbackCandidateId,
            int repairPasses
    ) {
        this(
                candidates,
                weightSources,
                constraints,
                retryLimit,
                fallbackCandidateId,
                repairPasses,
                List.of(),
                0
        );
    }

    public ProceduralRuleSet(
            List<Candidate<T>> candidates,
            List<WeightSource<T>> weightSources,
            List<PlacementConstraint<T>> constraints,
            int retryLimit,
            Optional<String> fallbackCandidateId
    ) {
        this(
                candidates,
                weightSources,
                constraints,
                retryLimit,
                fallbackCandidateId,
                0,
                List.of(),
                0
        );
    }

    public ProceduralRuleSet {
        candidates = List.copyOf(candidates);
        weightSources = List.copyOf(weightSources);
        constraints = List.copyOf(constraints);
        cleanupRules = List.copyOf(cleanupRules);
        fallbackCandidateId = Objects.requireNonNull(fallbackCandidateId, "Fallback candidate");
    }

    public List<String> validate() {
        var errors = new java.util.ArrayList<String>();
        if (candidates.isEmpty()) {
            errors.add("At least one candidate block is required");
        }
        if (retryLimit < 1 || retryLimit > MAX_RETRY_LIMIT) {
            errors.add("Retry limit must be between 1 and " + MAX_RETRY_LIMIT);
        }
        if (repairPasses < 0 || repairPasses > MAX_REPAIR_PASSES) {
            errors.add(
                    "Repair passes must be between 0 and " + MAX_REPAIR_PASSES
            );
        }
        if (cleanupPasses < 0 || cleanupPasses > MAX_CLEANUP_PASSES) {
            errors.add(
                    "Cleanup passes must be between 0 and "
                            + MAX_CLEANUP_PASSES
            );
        }

        var candidateIds = new HashSet<String>();
        for (var candidate : candidates) {
            if (!candidateIds.add(candidate.id())) {
                errors.add("Duplicate candidate id '" + candidate.id() + "'");
            }
        }
        fallbackCandidateId.ifPresent(fallback -> {
            if (!candidateIds.contains(fallback)) {
                errors.add("Fallback references unknown candidate '" + fallback + "'");
            }
        });

        if (weightSources.isEmpty()) {
            errors.add("At least one distribution source is required");
        }
        for (var source : weightSources) {
            errors.addAll(source.validate(candidateIds));
        }
        for (var constraint : constraints) {
            errors.addAll(constraint.validate(candidateIds));
        }
        for (var cleanupRule : cleanupRules) {
            errors.addAll(cleanupRule.validate(candidateIds));
        }
        return List.copyOf(errors);
    }
}
