package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.List;

/** Deterministic, allocation-bounded sampling for dense workbench previews. */
final class PreviewSampling {

    private PreviewSampling() {
    }

    static <T> List<T> evenlySpaced(List<T> values, int maximum) {
        if (maximum <= 0 || values.isEmpty()) {
            return List.of();
        }
        if (values.size() <= maximum) {
            return List.copyOf(values);
        }
        if (maximum == 1) {
            return List.of(values.get(values.size() / 2));
        }
        var sampled = new ArrayList<T>(maximum);
        long last = values.size() - 1L;
        long denominator = maximum - 1L;
        for (int index = 0; index < maximum; index++) {
            int source = (int) ((index * last + denominator / 2L)
                    / denominator);
            sampled.add(values.get(source));
        }
        return List.copyOf(sampled);
    }
}
