package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;

/**
 * Position-dependent deterministic sequence. A zero alternate weight makes the
 * sequence exact; a positive alternate weight allows bounded retries to use
 * other candidates when the primary choice violates a hard rule.
 */
public final class SequenceSource<T> implements WeightSource<T> {

    private final int offset;
    private final double primaryWeight;
    private final double alternateWeight;

    public SequenceSource(int offset, double primaryWeight, double alternateWeight) {
        this.offset = offset;
        this.primaryWeight = primaryWeight;
        this.alternateWeight = alternateWeight;
    }

    @Override
    public void apply(GenerationContext<T> context, List<Candidate<T>> candidates, double[] weights) {
        int primary = Math.floorMod(context.ordinal() + offset, candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] *= i == primary ? primaryWeight : alternateWeight;
        }
    }

    @Override
    public List<String> validate(java.util.Set<String> candidateIds) {
        if (!valid(primaryWeight) || !valid(alternateWeight)) {
            return List.of("Sequence weights must be finite and non-negative");
        }
        if (primaryWeight == 0.0 && alternateWeight == 0.0) {
            return List.of("Sequence must provide at least one positive weight");
        }
        return List.of();
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
