package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ProceduralGenerator {

    private static final long SELECTION_STREAM = 0x6570662d73656c65L;
    private static final long REPAIR_STREAM = 0x6570662d72657061L;
    public static final long MAX_ESTIMATED_WORK = 250_000_000L;

    private ProceduralGenerator() {
    }

    public static <T> GenerationResult<T> generate(GenerationRequest<T> request) {
        var validationErrors = request.ruleSet().validate();
        if (!validationErrors.isEmpty()) {
            return failure(
                    GenerationResult.GenerationFailure.Code.INVALID_RULE_SET,
                    "Procedural rule set is invalid",
                    Optional.empty(),
                    validationErrors
            );
        }
        if (request.positions().size() > request.maximumPositions()) {
            return failure(
                    GenerationResult.GenerationFailure.Code.TOO_MANY_POSITIONS,
                    "Generation contains " + request.positions().size()
                            + " positions, exceeding the limit of " + request.maximumPositions(),
                    Optional.empty(),
                    List.of()
            );
        }
        if (request.positions().isEmpty()) {
            return GenerationResult.success(Map.of(), List.of());
        }

        long estimatedWork = estimateWork(
                request.positions().size(),
                request.ruleSet()
        );
        if (estimatedWork > MAX_ESTIMATED_WORK) {
            return failure(
                    GenerationResult.GenerationFailure.Code.TOO_EXPENSIVE,
                    "Procedural generation is estimated to require too much work",
                    Optional.empty(),
                    List.of(
                            "Estimated rule checks exceed "
                                    + MAX_ESTIMATED_WORK,
                            "Reduce the selection size, spacing/run radius, "
                                    + "retry count, repair passes, or cleanup passes"
                    )
            );
        }

        var traversal = new ArrayList<>(request.positions());
        var unique = new HashSet<>(traversal);
        if (unique.size() != traversal.size()) {
            return failure(
                    GenerationResult.GenerationFailure.Code.DUPLICATE_POSITION,
                    "Generation positions must be unique",
                    Optional.empty(),
                    List.of()
            );
        }
        traversal.sort(GridPosition.TRAVERSAL_ORDER);

        var candidates = request.ruleSet().candidates();
        var candidatesById = new HashMap<String, Candidate<T>>();
        for (var candidate : candidates) {
            candidatesById.put(candidate.id(), candidate);
        }
        var bounds = GenerationBounds.enclosing(traversal);
        var generated = new LinkedHashMap<GridPosition, Candidate<T>>();
        var generatedCounts = new HashMap<String, Integer>();

        report(
                request.progress(),
                GenerationProgress.Stage.GENERATING,
                0,
                traversal.size()
        );
        for (int ordinal = 0; ordinal < traversal.size(); ordinal++) {
            if (request.cancelled().getAsBoolean()) {
                return failure(
                        GenerationResult.GenerationFailure.Code.CANCELLED,
                        "Procedural generation was cancelled",
                        Optional.of(traversal.get(ordinal)),
                        List.of()
                );
            }

            var position = traversal.get(ordinal);
            var context = new GenerationContext<>(
                    request.seed(),
                    position,
                    ordinal,
                    traversal.size(),
                    bounds,
                    unique,
                    generated,
                    request.existingNeighbors(),
                    generatedCounts
            );
            var weights = new double[candidates.size()];
            java.util.Arrays.fill(weights, 1.0);
            for (var source : request.ruleSet().weightSources()) {
                source.apply(context, candidates, weights);
            }
            for (int i = 0; i < candidates.size(); i++) {
                for (var constraint : request.ruleSet().constraints()) {
                    weights[i] *= constraint.preferenceMultiplier(candidates.get(i), context);
                }
                if (!Double.isFinite(weights[i]) || weights[i] < 0.0) {
                    return failure(
                            GenerationResult.GenerationFailure.Code.INVALID_WEIGHT,
                            "Distribution produced an invalid weight for '" + candidates.get(i).id() + "'",
                            Optional.of(position),
                            List.of("Weight was " + weights[i])
                    );
                }
            }

            var rejected = new boolean[candidates.size()];
            var rejectionReasons = new ArrayList<String>();
            Candidate<T> selected = null;
            int attempts = Math.min(request.ruleSet().retryLimit(), candidates.size());
            for (int attempt = 0; attempt < attempts; attempt++) {
                int candidateIndex = selectIndex(
                        weights,
                        rejected,
                        StableRandom.positionUnit(
                                request.seed(),
                                position,
                                ordinal,
                                attempt,
                                SELECTION_STREAM
                        )
                );
                if (candidateIndex < 0) {
                    break;
                }
                var candidate = candidates.get(candidateIndex);
                var evaluation = evaluate(candidate, context, request.ruleSet().constraints());
                if (evaluation.allowed()) {
                    selected = candidate;
                    break;
                }
                rejected[candidateIndex] = true;
                rejectionReasons.add(candidate.id() + ": " + evaluation.reason());
            }

            if (selected == null && request.ruleSet().fallbackCandidateId().isPresent()) {
                var fallback = candidatesById.get(request.ruleSet().fallbackCandidateId().get());
                var evaluation = evaluate(fallback, context, request.ruleSet().constraints());
                if (evaluation.allowed()) {
                    selected = fallback;
                } else {
                    rejectionReasons.add("Fallback " + fallback.id() + ": " + evaluation.reason());
                }
            }

            if (selected == null) {
                return failure(
                        GenerationResult.GenerationFailure.Code.NO_VALID_CANDIDATE,
                        "No valid candidate could be selected at " + position,
                        Optional.of(position),
                        rejectionReasons
                );
            }
            generated.put(position, selected);
            generatedCounts.merge(selected.id(), 1, Integer::sum);
            report(
                    request.progress(),
                    GenerationProgress.Stage.GENERATING,
                    ordinal + 1,
                    traversal.size()
            );
        }

        if (request.ruleSet().repairPasses() > 0) {
            var repairFailure = repair(
                    request,
                    traversal,
                    unique,
                    bounds,
                    candidates,
                    candidatesById,
                    generated,
                    generatedCounts
            );
            if (repairFailure.isPresent()) {
                return repairFailure.get();
            }
        } else {
            var constraintFailure = validateGeneratedConstraints(
                    request,
                    traversal,
                    unique,
                    bounds,
                    generated,
                    generatedCounts
            );
            if (constraintFailure.isPresent()) {
                return constraintFailure.get();
            }
        }

        if (request.ruleSet().cleanupPasses() > 0
                && !request.ruleSet().cleanupRules().isEmpty()) {
            var cleanupFailure = cleanup(
                    request,
                    traversal,
                    unique,
                    bounds,
                    candidatesById,
                    generated,
                    generatedCounts
            );
            if (cleanupFailure.isPresent()) {
                return cleanupFailure.get();
            }
        }

        var postCleanupConstraintFailure = validateGeneratedConstraints(
                request,
                traversal,
                unique,
                bounds,
                generated,
                generatedCounts
        );
        if (postCleanupConstraintFailure.isPresent()) {
            return postCleanupConstraintFailure.get();
        }

        var finalValidationErrors = validateFinal(
                request.ruleSet(),
                generated,
                traversal.size()
        );
        if (!finalValidationErrors.isEmpty()) {
            return failure(
                    GenerationResult.GenerationFailure.Code.GLOBAL_RULE_UNSATISFIED,
                    "Generated output does not satisfy its global rules",
                    Optional.empty(),
                    finalValidationErrors
            );
        }

        var placements = new LinkedHashMap<GridPosition, T>();
        for (var entry : generated.entrySet()) {
            placements.put(entry.getKey(), entry.getValue().value());
        }
        return GenerationResult.success(placements, traversal);
    }

    private static <T> Optional<GenerationResult<T>> cleanup(
            GenerationRequest<T> request,
            List<GridPosition> traversal,
            Set<GridPosition> targets,
            GenerationBounds bounds,
            Map<String, Candidate<T>> candidatesById,
            LinkedHashMap<GridPosition, Candidate<T>> generated,
            Map<String, Integer> generatedCounts
    ) {
        int totalProgress = traversal.size() * request.ruleSet().cleanupPasses();
        report(
                request.progress(),
                GenerationProgress.Stage.CLEANING,
                0,
                totalProgress
        );
        for (int pass = 0; pass < request.ruleSet().cleanupPasses(); pass++) {
            var snapshot = new LinkedHashMap<>(generated);
            var snapshotCounts = new HashMap<String, Integer>();
            for (var candidate : snapshot.values()) {
                snapshotCounts.merge(candidate.id(), 1, Integer::sum);
            }
            var replacements =
                    new LinkedHashMap<GridPosition, Candidate<T>>();
            for (int ordinal = 0; ordinal < traversal.size(); ordinal++) {
                if (request.cancelled().getAsBoolean()) {
                    return Optional.of(failure(
                            GenerationResult.GenerationFailure.Code.CANCELLED,
                            "Procedural generation was cancelled during cleanup",
                            Optional.of(traversal.get(ordinal)),
                            List.of()
                    ));
                }
                var position = traversal.get(ordinal);
                var context = new GenerationContext<>(
                        request.seed(),
                        position,
                        ordinal,
                        traversal.size(),
                        bounds,
                        targets,
                        snapshot,
                        request.existingNeighbors(),
                        snapshotCounts
                );
                for (var rule : request.ruleSet().cleanupRules()) {
                    var replacementId = rule.replacementCandidateId(context);
                    if (replacementId.isEmpty()) {
                        continue;
                    }
                    var replacement = candidatesById.get(replacementId.get());
                    if (replacement != null
                            && !replacement.equals(snapshot.get(position))) {
                        replacements.put(position, replacement);
                    }
                    break;
                }
                report(
                        request.progress(),
                        GenerationProgress.Stage.CLEANING,
                        pass * traversal.size() + ordinal + 1,
                        totalProgress
                );
            }
            if (replacements.isEmpty()) {
                break;
            }
            generated.putAll(replacements);
            generatedCounts.clear();
            for (var candidate : generated.values()) {
                generatedCounts.merge(candidate.id(), 1, Integer::sum);
            }
        }
        return Optional.empty();
    }

    private static <T> Optional<GenerationResult<T>> repair(
            GenerationRequest<T> request,
            List<GridPosition> traversal,
            Set<GridPosition> targets,
            GenerationBounds bounds,
            List<Candidate<T>> candidates,
            Map<String, Candidate<T>> candidatesById,
            LinkedHashMap<GridPosition, Candidate<T>> generated,
            Map<String, Integer> generatedCounts
    ) {
        int totalProgress = traversal.size() * request.ruleSet().repairPasses();
        report(
                request.progress(),
                GenerationProgress.Stage.REPAIRING,
                0,
                totalProgress
        );
        for (int pass = 0; pass < request.ruleSet().repairPasses(); pass++) {
            int repairs = 0;
            for (int ordinal = 0; ordinal < traversal.size(); ordinal++) {
                if (request.cancelled().getAsBoolean()) {
                    return Optional.of(failure(
                            GenerationResult.GenerationFailure.Code.CANCELLED,
                            "Procedural generation was cancelled during repair",
                            Optional.of(traversal.get(ordinal)),
                            List.of()
                    ));
                }

                var position = traversal.get(ordinal);
                var context = new GenerationContext<>(
                        request.seed(),
                        position,
                        ordinal,
                        traversal.size(),
                        bounds,
                        targets,
                        generated,
                        request.existingNeighbors(),
                        generatedCounts
                );
                var current = generated.get(position);
                var currentEvaluation = evaluate(
                        current,
                        context,
                        request.ruleSet().constraints()
                );
                if (!currentEvaluation.allowed()) {
                    var weights = new double[candidates.size()];
                    java.util.Arrays.fill(weights, 1.0);
                    for (var source : request.ruleSet().weightSources()) {
                        source.apply(context, candidates, weights);
                    }
                    for (int index = 0; index < candidates.size(); index++) {
                        for (var constraint : request.ruleSet().constraints()) {
                            weights[index] *= constraint.preferenceMultiplier(
                                    candidates.get(index),
                                    context
                            );
                        }
                        if (!Double.isFinite(weights[index])
                                || weights[index] < 0.0) {
                            return Optional.of(failure(
                                    GenerationResult.GenerationFailure.Code.INVALID_WEIGHT,
                                    "Distribution produced an invalid repair weight for '"
                                            + candidates.get(index).id() + "'",
                                    Optional.of(position),
                                    List.of("Weight was " + weights[index])
                            ));
                        }
                    }

                    var rejected = new boolean[candidates.size()];
                    var rejectionReasons = new ArrayList<String>();
                    rejectionReasons.add(
                            current.id() + ": " + currentEvaluation.reason()
                    );
                    int currentIndex = candidates.indexOf(current);
                    if (currentIndex >= 0) {
                        rejected[currentIndex] = true;
                    }
                    Candidate<T> replacement = null;
                    int attempts = Math.min(
                            request.ruleSet().retryLimit(),
                            candidates.size()
                    );
                    for (int attempt = 0; attempt < attempts; attempt++) {
                        int candidateIndex = selectIndex(
                                weights,
                                rejected,
                                StableRandom.positionUnit(
                                        request.seed(),
                                        position,
                                        ordinal,
                                        attempt,
                                        REPAIR_STREAM
                                                ^ (0x9E3779B97F4A7C15L * pass)
                                )
                        );
                        if (candidateIndex < 0) {
                            break;
                        }
                        var candidate = candidates.get(candidateIndex);
                        var evaluation = evaluate(
                                candidate,
                                context,
                                request.ruleSet().constraints()
                        );
                        if (evaluation.allowed()) {
                            replacement = candidate;
                            break;
                        }
                        rejected[candidateIndex] = true;
                        rejectionReasons.add(
                                candidate.id() + ": " + evaluation.reason()
                        );
                    }

                    if (replacement == null
                            && request.ruleSet().fallbackCandidateId().isPresent()) {
                        var fallback = candidatesById.get(
                                request.ruleSet().fallbackCandidateId().get()
                        );
                        var evaluation = evaluate(
                                fallback,
                                context,
                                request.ruleSet().constraints()
                        );
                        if (evaluation.allowed()) {
                            replacement = fallback;
                        } else {
                            rejectionReasons.add(
                                    "Fallback " + fallback.id() + ": "
                                            + evaluation.reason()
                            );
                        }
                    }
                    if (replacement != null) {
                        generatedCounts.computeIfPresent(
                                current.id(),
                                (id, count) -> count <= 1 ? null : count - 1
                        );
                        generated.put(position, replacement);
                        generatedCounts.merge(
                                replacement.id(),
                                1,
                                Integer::sum
                        );
                        repairs++;
                    }
                }
                report(
                        request.progress(),
                        GenerationProgress.Stage.REPAIRING,
                        pass * traversal.size() + ordinal + 1,
                        totalProgress
                );
            }
            if (repairs == 0) {
                break;
            }
        }

        return validateGeneratedConstraints(
                request,
                traversal,
                targets,
                bounds,
                generated,
                generatedCounts
        );
    }

    private static <T> Optional<GenerationResult<T>> validateGeneratedConstraints(
            GenerationRequest<T> request,
            List<GridPosition> traversal,
            Set<GridPosition> targets,
            GenerationBounds bounds,
            LinkedHashMap<GridPosition, Candidate<T>> generated,
            Map<String, Integer> generatedCounts
    ) {
        for (int ordinal = 0; ordinal < traversal.size(); ordinal++) {
            var position = traversal.get(ordinal);
            var context = new GenerationContext<>(
                    request.seed(),
                    position,
                    ordinal,
                    traversal.size(),
                    bounds,
                    targets,
                    generated,
                    request.existingNeighbors(),
                    generatedCounts
            );
            var evaluation = evaluate(
                    generated.get(position),
                    context,
                    request.ruleSet().constraints()
            );
            if (!evaluation.allowed()) {
                return Optional.of(failure(
                        GenerationResult.GenerationFailure.Code.NO_VALID_CANDIDATE,
                        "Deterministic repair passes were exhausted at "
                                + position,
                        Optional.of(position),
                        List.of(evaluation.reason())
                ));
            }
        }
        return Optional.empty();
    }

    private static <T> ConstraintResult evaluate(
            Candidate<T> candidate,
            GenerationContext<T> context,
            List<PlacementConstraint<T>> constraints
    ) {
        for (var constraint : constraints) {
            var result = constraint.evaluate(candidate, context);
            if (!result.allowed()) {
                return result;
            }
        }
        return ConstraintResult.allow();
    }

    private static <T> List<String> validateFinal(
            ProceduralRuleSet<T> ruleSet,
            Map<GridPosition, Candidate<T>> generated,
            int positionCount
    ) {
        var errors = new ArrayList<String>();
        var visited = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<FinalGenerationValidator<T>, Boolean>()
        );
        for (var source : ruleSet.weightSources()) {
            if (source instanceof FinalGenerationValidator<?> rawValidator) {
                @SuppressWarnings("unchecked")
                var validator = (FinalGenerationValidator<T>) rawValidator;
                if (visited.add(validator)) {
                    errors.addAll(
                            validator.validateFinal(generated, positionCount)
                    );
                }
            }
        }
        for (var constraint : ruleSet.constraints()) {
            if (constraint instanceof FinalGenerationValidator<?> rawValidator) {
                @SuppressWarnings("unchecked")
                var validator = (FinalGenerationValidator<T>) rawValidator;
                if (visited.add(validator)) {
                    errors.addAll(
                            validator.validateFinal(generated, positionCount)
                    );
                }
            }
        }
        return List.copyOf(errors);
    }

    private static int selectIndex(double[] weights, boolean[] rejected, double unit) {
        double maximum = 0.0;
        for (int i = 0; i < weights.length; i++) {
            if (!rejected[i]) {
                maximum = Math.max(maximum, weights[i]);
            }
        }
        if (!(maximum > 0.0) || !Double.isFinite(maximum)) {
            return -1;
        }

        double total = 0.0;
        for (int i = 0; i < weights.length; i++) {
            if (!rejected[i] && weights[i] > 0.0) {
                total += weights[i] / maximum;
            }
        }
        double target = unit * total;
        double cumulative = 0.0;
        int lastPositive = -1;
        for (int i = 0; i < weights.length; i++) {
            if (rejected[i] || weights[i] <= 0.0) {
                continue;
            }
            lastPositive = i;
            cumulative += weights[i] / maximum;
            if (target < cumulative) {
                return i;
            }
        }
        return lastPositive;
    }

    private static long estimateWork(
            int positionCount,
            ProceduralRuleSet<?> ruleSet
    ) {
        long candidateCount = ruleSet.candidates().size();
        long attempts = Math.min(ruleSet.retryLimit(), candidateCount);

        long sourceCost = 0;
        for (var source : ruleSet.weightSources()) {
            sourceCost = cappedAdd(
                    sourceCost,
                    Math.max(0, source.estimatedCostPerCandidate())
            );
        }
        long evaluationCost = 0;
        long preferenceCost = 0;
        for (var constraint : ruleSet.constraints()) {
            evaluationCost = cappedAdd(
                    evaluationCost,
                    Math.max(0, constraint.estimatedEvaluationCost())
            );
            preferenceCost = cappedAdd(
                    preferenceCost,
                    Math.max(0, constraint.estimatedPreferenceCost())
            );
        }

        long weightAndPreference = cappedMultiply(
                candidateCount,
                cappedAdd(sourceCost, preferenceCost)
        );
        long candidateEvaluations = cappedMultiply(
                attempts,
                evaluationCost
        );
        if (ruleSet.fallbackCandidateId().isPresent()) {
            candidateEvaluations = cappedAdd(
                    candidateEvaluations,
                    evaluationCost
            );
        }
        long initialPerPosition = cappedAdd(
                weightAndPreference,
                candidateEvaluations
        );

        long repairPerPosition = cappedAdd(
                evaluationCost,
                cappedAdd(weightAndPreference, candidateEvaluations)
        );
        long repairWork = cappedMultiply(
                positionCount,
                cappedMultiply(ruleSet.repairPasses(), repairPerPosition)
        );

        long cleanupCost = 0;
        for (var cleanupRule : ruleSet.cleanupRules()) {
            cleanupCost = cappedAdd(
                    cleanupCost,
                    Math.max(0, cleanupRule.estimatedCostPerPosition())
            );
        }
        long cleanupWork = cappedMultiply(
                positionCount,
                cappedMultiply(ruleSet.cleanupPasses(), cleanupCost)
        );

        // One validation follows generation/repair and another follows cleanup.
        long validationWork = cappedMultiply(
                positionCount,
                cappedMultiply(2, evaluationCost)
        );
        long generationWork = cappedMultiply(
                positionCount,
                initialPerPosition
        );
        return cappedAdd(
                cappedAdd(generationWork, repairWork),
                cappedAdd(cleanupWork, validationWork)
        );
    }

    private static long cappedAdd(long left, long right) {
        long cap = MAX_ESTIMATED_WORK + 1;
        if (left >= cap || right >= cap || left > cap - right) {
            return cap;
        }
        return left + right;
    }

    private static long cappedMultiply(long left, long right) {
        long cap = MAX_ESTIMATED_WORK + 1;
        if (left <= 0 || right <= 0) {
            return 0;
        }
        if (left >= cap || right >= cap || left > cap / right) {
            return cap;
        }
        return left * right;
    }

    private static <T> GenerationResult<T> failure(
            GenerationResult.GenerationFailure.Code code,
            String message,
            Optional<GridPosition> position,
            List<String> details
    ) {
        return GenerationResult.failure(
                new GenerationResult.GenerationFailure(code, message, position, details)
        );
    }

    private static void report(
            GenerationProgress progress,
            GenerationProgress.Stage stage,
            int completed,
            int total
    ) {
        try {
            progress.update(stage, completed, total);
        } catch (RuntimeException ignored) {
            // Progress reporting must never make deterministic generation fail.
        }
    }
}
