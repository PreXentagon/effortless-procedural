package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Multiplies candidate weights without expanding them into repeated entries.
 */
public final class WeightedSource<T> implements WeightSource<T> {

    private final Map<String, Double> weights;
    private final double defaultWeight;

    public WeightedSource(Map<String, Double> weights) {
        this(weights, 0.0);
    }

    public WeightedSource(Map<String, Double> weights, double defaultWeight) {
        this.weights = Map.copyOf(new LinkedHashMap<>(weights));
        this.defaultWeight = defaultWeight;
    }

    @Override
    public void apply(GenerationContext<T> context, List<Candidate<T>> candidates, double[] currentWeights) {
        for (int i = 0; i < candidates.size(); i++) {
            currentWeights[i] *= weights.getOrDefault(candidates.get(i).id(), defaultWeight);
        }
    }

    @Override
    public List<String> validate(Set<String> candidateIds) {
        var errors = new java.util.ArrayList<String>();
        if (!isWeightValid(defaultWeight)) {
            errors.add("Default weight must be finite and non-negative");
        }
        for (var entry : weights.entrySet()) {
            if (!candidateIds.contains(entry.getKey())) {
                errors.add("Weighted source references unknown candidate '" + entry.getKey() + "'");
            }
            if (!isWeightValid(entry.getValue())) {
                errors.add("Weight for '" + entry.getKey() + "' must be finite and non-negative");
            }
        }
        return List.copyOf(errors);
    }

    private static boolean isWeightValid(double weight) {
        return Double.isFinite(weight) && weight >= 0.0;
    }
}
