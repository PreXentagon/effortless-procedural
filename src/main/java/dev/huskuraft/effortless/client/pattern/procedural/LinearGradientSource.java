package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LinearGradientSource<T> implements WeightSource<T> {

    private final SpatialField field;
    private final Map<String, EndpointWeights> endpoints;
    private final GradientCurve curve;
    private final int steps;

    public LinearGradientSource(Coordinate coordinate, Map<String, EndpointWeights> endpoints) {
        this(coordinate, endpoints, GradientCurve.LINEAR, 8);
    }

    public LinearGradientSource(
            Coordinate coordinate,
            Map<String, EndpointWeights> endpoints,
            GradientCurve curve,
            int steps
    ) {
        this(SpatialField.linear(coordinate), endpoints, curve, steps);
    }

    public LinearGradientSource(
            SpatialField field,
            Map<String, EndpointWeights> endpoints,
            GradientCurve curve,
            int steps
    ) {
        this.field = field;
        this.endpoints = Map.copyOf(new LinkedHashMap<>(endpoints));
        this.curve = curve;
        this.steps = steps;
    }

    @Override
    public void apply(GenerationContext<T> context, List<Candidate<T>> candidates, double[] weights) {
        double amount = curve.apply(field.sample(context), steps);
        for (int i = 0; i < candidates.size(); i++) {
            var endpoint = endpoints.get(candidates.get(i).id());
            if (endpoint != null) {
                weights[i] *= endpoint.start() + (endpoint.end() - endpoint.start()) * amount;
            }
        }
    }

    @Override
    public List<String> validate(Set<String> candidateIds) {
        var errors = new java.util.ArrayList<String>();
        if (field == null) {
            errors.add("Gradient field must be selected");
        } else {
            errors.addAll(field.validate());
        }
        if (curve == null) {
            errors.add("Gradient curve must be selected");
        }
        if (steps < 2 || steps > 256) {
            errors.add("Gradient steps must be between 2 and 256");
        }
        for (var entry : endpoints.entrySet()) {
            if (!candidateIds.contains(entry.getKey())) {
                errors.add("Gradient references unknown candidate '" + entry.getKey() + "'");
            }
            if (!entry.getValue().isValid()) {
                errors.add("Gradient weights for '" + entry.getKey() + "' must be finite and non-negative");
            }
        }
        return List.copyOf(errors);
    }

    public record EndpointWeights(double start, double end) {

        private boolean isValid() {
            return Double.isFinite(start) && start >= 0.0
                    && Double.isFinite(end) && end >= 0.0;
        }
    }
}
