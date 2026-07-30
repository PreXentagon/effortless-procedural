package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enforces a deterministic minimum/maximum occurrence quota for one
 * candidate. It participates in weighting, local rejection, and final
 * validation so malformed or mutually impossible quotas fail atomically.
 */
public final class CandidateQuotaRule<T>
        implements WeightSource<T>, PlacementConstraint<T>,
        FinalGenerationValidator<T> {

    private final String candidateId;
    private final double minimum;
    private final double maximum;
    private final Unit unit;

    public CandidateQuotaRule(
            String candidateId,
            double minimum,
            double maximum,
            Unit unit
    ) {
        this.candidateId = candidateId;
        this.minimum = minimum;
        this.maximum = maximum;
        this.unit = unit;
    }

    @Override
    public void apply(
            GenerationContext<T> context,
            List<Candidate<T>> candidates,
            double[] weights
    ) {
        var countRange = countRange(context.positionCount());
        int minimumCount = countRange.minimum();
        int maximumCount = countRange.maximum();
        for (int index = 0; index < candidates.size(); index++) {
            var proposed = candidates.get(index);
            int countAfter = context.candidateCountAfterPlacement(
                    candidateId,
                    proposed.id()
            );
            if (countAfter > maximumCount
                    || countAfter + context.unresolvedPositionsAfterPlacement()
                    < minimumCount) {
                weights[index] = 0.0;
            }
        }
    }

    @Override
    public ConstraintResult evaluate(
            Candidate<T> candidate,
            GenerationContext<T> context
    ) {
        int countAfter = context.candidateCountAfterPlacement(
                candidateId,
                candidate.id()
        );
        var countRange = countRange(context.positionCount());
        int maximumCount = countRange.maximum();
        if (countAfter > maximumCount) {
            return ConstraintResult.reject(
                    "'" + candidateId + "' exceeds its maximum quota of "
                            + maximumCount
            );
        }
        int minimumCount = countRange.minimum();
        if (countAfter + context.unresolvedPositionsAfterPlacement()
                < minimumCount) {
            return ConstraintResult.reject(
                    "Choosing '" + candidate.id() + "' makes the minimum quota "
                            + minimumCount + " for '" + candidateId
                            + "' unreachable"
            );
        }
        return ConstraintResult.allow();
    }

    @Override
    public List<String> validate(Set<String> candidateIds) {
        var errors = new ArrayList<String>();
        if (!candidateIds.contains(candidateId)) {
            errors.add("Quota references unknown candidate '" + candidateId + "'");
        }
        if (unit == null) {
            errors.add("Quota unit must be selected");
            return List.copyOf(errors);
        }
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)
                || minimum < 0.0 || maximum < minimum) {
            errors.add("Quota must satisfy 0 <= minimum <= maximum");
        }
        if (unit == Unit.COUNT
                && (minimum > Integer.MAX_VALUE || maximum > Integer.MAX_VALUE)) {
            errors.add("Count quota exceeds the supported integer range");
        }
        if (unit == Unit.COUNT
                && Double.isFinite(minimum)
                && Double.isFinite(maximum)
                && minimum >= 0.0
                && maximum >= minimum
                && Math.ceil(minimum) > Math.floor(maximum)) {
            errors.add("Count quota range must contain a whole block count");
        }
        if (unit == Unit.FRACTION && maximum > 1.0) {
            errors.add("Fraction quota must satisfy maximum <= 1");
        }
        return List.copyOf(errors);
    }

    @Override
    public List<String> validateFinal(
            Map<GridPosition, Candidate<T>> generated,
            int positionCount
    ) {
        int count = 0;
        for (var candidate : generated.values()) {
            if (candidate.id().equals(candidateId)) {
                count++;
            }
        }
        var countRange = countRange(positionCount);
        int minimumCount = countRange.minimum();
        int maximumCount = countRange.maximum();
        if (count < minimumCount || count > maximumCount) {
            return List.of(
                    "Candidate '" + candidateId + "' occurred " + count
                            + " times; required " + minimumCount + ".."
                            + maximumCount
            );
        }
        return List.of();
    }

    private CountRange countRange(int positionCount) {
        int minimumCount = switch (unit) {
            case COUNT -> (int) Math.ceil(minimum);
            case FRACTION ->
                    (int) Math.ceil(minimum * positionCount - 1.0e-12);
        };
        int maximumCount = switch (unit) {
            case COUNT -> (int) Math.floor(maximum);
            case FRACTION ->
                    (int) Math.floor(maximum * positionCount + 1.0e-12);
        };
        if (unit == Unit.FRACTION && minimumCount > maximumCount) {
            int nearest = (int) Math.round(
                    (minimum + maximum) * 0.5 * positionCount
            );
            return new CountRange(nearest, nearest);
        }
        return new CountRange(minimumCount, maximumCount);
    }

    private record CountRange(int minimum, int maximum) {
    }

    public enum Unit {
        COUNT,
        FRACTION
    }
}
