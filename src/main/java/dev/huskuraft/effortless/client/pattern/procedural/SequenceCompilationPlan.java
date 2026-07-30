package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Converts explicit position choices to the stock SEQUENCE randomizer order.
 */
public final class SequenceCompilationPlan {

    private SequenceCompilationPlan() {
    }

    public static <T> Result<T> create(
            List<GridPosition> operationOrder,
            Map<GridPosition, T> placements
    ) {
        if (operationOrder.size() != placements.size()) {
            return Result.failure(
                    "Operation/placement size mismatch: " + operationOrder.size()
                            + " operations and " + placements.size() + " placements"
            );
        }
        if (new HashSet<>(operationOrder).size() != operationOrder.size()) {
            return Result.failure("Stock operation order contains duplicate positions");
        }
        var sequence = new ArrayList<T>(operationOrder.size());
        for (var position : operationOrder) {
            var value = placements.get(position);
            if (value == null) {
                return Result.failure("No generated value for stock operation at " + position);
            }
            sequence.add(value);
        }
        return Result.success(sequence);
    }

    public record Result<T>(List<T> sequence, String error) {

        public Result {
            sequence = List.copyOf(sequence);
        }

        public static <T> Result<T> success(List<T> sequence) {
            return new Result<>(sequence, "");
        }

        public static <T> Result<T> failure(String error) {
            return new Result<>(List.of(), error);
        }

        public boolean isSuccess() {
            return error.isEmpty();
        }
    }
}
