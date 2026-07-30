package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Losslessly compresses adjacent equal values for the stock sequence
 * randomizer, whose chance count expands each entry into repeated values.
 */
public final class RunLengthSequence {

    private RunLengthSequence() {
    }

    public static <T> List<Run<T>> encode(List<T> values, int maximumRunLength) {
        if (maximumRunLength < 1) {
            throw new IllegalArgumentException("Maximum run length must be positive");
        }
        if (values.isEmpty()) {
            return List.of();
        }

        var runs = new ArrayList<Run<T>>();
        T current = Objects.requireNonNull(values.get(0), "Sequence value");
        int length = 1;
        for (int index = 1; index < values.size(); index++) {
            T value = Objects.requireNonNull(values.get(index), "Sequence value");
            if (Objects.equals(current, value) && length < maximumRunLength) {
                length++;
                continue;
            }
            runs.add(new Run<>(current, length));
            current = value;
            length = 1;
        }
        runs.add(new Run<>(current, length));
        return List.copyOf(runs);
    }

    public record Run<T>(T value, int length) {

        public Run {
            Objects.requireNonNull(value, "Run value");
            if (length < 1) {
                throw new IllegalArgumentException("Run length must be positive");
            }
        }
    }
}
