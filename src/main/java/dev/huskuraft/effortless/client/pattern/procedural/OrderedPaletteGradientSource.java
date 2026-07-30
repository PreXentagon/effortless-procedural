package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies a gradient to the stable candidate-list order. Unlike endpoint
 * weights, ordered modes make each candidate occupy a predictable section of
 * the selected coordinate.
 */
public final class OrderedPaletteGradientSource<T>
        implements WeightSource<T> {

    private final SpatialField field;
    private final GradientDistributionMode mode;
    private final GradientCurve curve;
    private final int steps;
    private final Map<String, Double> configuredStops;

    public OrderedPaletteGradientSource(
            Coordinate coordinate,
            GradientDistributionMode mode,
            GradientCurve curve,
            int steps
    ) {
        this(SpatialField.linear(coordinate), mode, curve, steps, Map.of());
    }

    public OrderedPaletteGradientSource(
            SpatialField field,
            GradientDistributionMode mode,
            GradientCurve curve,
            int steps
    ) {
        this(field, mode, curve, steps, Map.of());
    }

    public OrderedPaletteGradientSource(
            SpatialField field,
            GradientDistributionMode mode,
            GradientCurve curve,
            int steps,
            Map<String, Double> configuredStops
    ) {
        this.field = field;
        this.mode = mode;
        this.curve = curve;
        this.steps = steps;
        // Stop order is palette order. Map.copyOf does not promise to preserve
        // the insertion order supplied by ProceduralPresetAdapter.
        this.configuredStops = Collections.unmodifiableMap(
                new LinkedHashMap<>(configuredStops)
        );
    }

    @Override
    public void apply(
            GenerationContext<T> context,
            List<Candidate<T>> candidates,
            double[] weights
    ) {
        if (candidates.isEmpty()) {
            return;
        }
        double amount = Math.clamp(
                curve.apply(field.sample(context), steps),
                0.0,
                1.0
        );
        switch (mode) {
            case ORDERED_BLEND ->
                    applyBlend(amount, stops(candidates), weights);
            case ORDERED_BANDS -> {
                boolean custom = hasCompleteCustomStops(candidates);
                applyBands(amount, stops(candidates), weights, custom);
            }
            case WEIGHTED_ENDPOINTS -> throw new IllegalStateException(
                    "Endpoint weights require LinearGradientSource"
            );
        }
    }

    @Override
    public List<String> validate(Set<String> candidateIds) {
        var errors = new ArrayList<String>();
        if (field == null) {
            errors.add("Gradient field must be selected");
        } else {
            errors.addAll(field.validate());
        }
        if (mode == null || mode == GradientDistributionMode.WEIGHTED_ENDPOINTS) {
            errors.add("An ordered gradient mode must be selected");
        }
        if (curve == null) {
            errors.add("Gradient curve must be selected");
        }
        if (steps < 2 || steps > 256) {
            errors.add("Gradient steps must be between 2 and 256");
        }
        double previous = -1.0;
        for (var configuredStop : configuredStops.entrySet()) {
            if (!candidateIds.contains(configuredStop.getKey())) {
                continue;
            }
            double position = configuredStop.getValue();
            if (position < 0.0) {
                continue;
            }
            if (!Double.isFinite(position) || position > 1.0) {
                errors.add("Gradient stop for '" + configuredStop.getKey()
                        + "' must be between 0 and 1");
            } else if (position <= previous) {
                errors.add("Configured gradient stops must increase in palette order");
                break;
            }
            previous = position;
        }
        return List.copyOf(errors);
    }

    private static void applyBlend(
            double amount,
            double[] stops,
            double[] weights
    ) {
        int candidateCount = stops.length;
        if (candidateCount == 1) {
            return;
        }
        int lower = 0;
        int upper = 0;
        if (amount >= stops[candidateCount - 1]) {
            lower = candidateCount - 1;
            upper = lower;
        } else {
            for (int index = 1; index < candidateCount; index++) {
                if (amount <= stops[index]) {
                    lower = index - 1;
                    upper = index;
                    break;
                }
            }
        }
        double span = stops[upper] - stops[lower];
        double upperWeight = span <= 0.0
                ? 0.0
                : Math.clamp((amount - stops[lower]) / span, 0.0, 1.0);
        double lowerWeight = 1.0 - upperWeight;
        for (int index = 0; index < candidateCount; index++) {
            double multiplier = index == lower
                    ? lowerWeight
                    : index == upper ? upperWeight : 0.0;
            weights[index] *= multiplier;
        }
    }

    private static void applyBands(
            double amount,
            double[] stops,
            double[] weights,
            boolean customStops
    ) {
        int candidateCount = stops.length;
        if (!customStops) {
            int selected = Math.min(
                    candidateCount - 1,
                    (int) Math.floor(amount * candidateCount)
            );
            for (int index = 0; index < candidateCount; index++) {
                if (index != selected) {
                    weights[index] = 0.0;
                }
            }
            return;
        }
        int selected = candidateCount - 1;
        for (int index = 0; index < candidateCount - 1; index++) {
            double boundary = (stops[index] + stops[index + 1]) / 2.0;
            if (amount < boundary) {
                selected = index;
                break;
            }
        }
        for (int index = 0; index < candidateCount; index++) {
            if (index != selected) {
                weights[index] = 0.0;
            }
        }
    }

    private double[] stops(List<Candidate<T>> candidates) {
        int size = candidates.size();
        var result = new double[size];
        boolean complete = true;
        double previous = -1.0;
        for (int index = 0; index < size; index++) {
            double configured = configuredStops.getOrDefault(
                    candidates.get(index).id(),
                    -1.0
            );
            if (!Double.isFinite(configured)
                    || configured < 0.0
                    || configured > 1.0
                    || configured <= previous) {
                complete = false;
                break;
            }
            result[index] = configured;
            previous = configured;
        }
        if (complete) {
            return result;
        }
        for (int index = 0; index < size; index++) {
            result[index] = size <= 1
                    ? 0.0
                    : (double) index / (size - 1);
        }
        return result;
    }

    private boolean hasCompleteCustomStops(
            List<Candidate<T>> candidates
    ) {
        double previous = -1.0;
        for (var candidate : candidates) {
            double configured = configuredStops.getOrDefault(
                    candidate.id(),
                    -1.0
            );
            if (!Double.isFinite(configured)
                    || configured < 0.0
                    || configured > 1.0
                    || configured <= previous) {
                return false;
            }
            previous = configured;
        }
        return true;
    }
}
