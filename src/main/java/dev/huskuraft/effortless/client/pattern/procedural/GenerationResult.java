package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;

public record GenerationResult<T>(
        Map<GridPosition, T> placements,
        List<GridPosition> traversal,
        Optional<GenerationFailure> failure
) {

    public GenerationResult {
        placements = Collections.unmodifiableMap(new LinkedHashMap<>(placements));
        traversal = List.copyOf(traversal);
        failure = java.util.Objects.requireNonNull(failure, "Failure");
    }

    public static <T> GenerationResult<T> success(
            Map<GridPosition, T> placements,
            List<GridPosition> traversal
    ) {
        return new GenerationResult<>(placements, traversal, Optional.empty());
    }

    public static <T> GenerationResult<T> failure(GenerationFailure failure) {
        return new GenerationResult<>(Map.of(), List.of(), Optional.of(failure));
    }

    public boolean isSuccess() {
        return failure.isEmpty();
    }

    public record GenerationFailure(
            Code code,
            String message,
            Optional<GridPosition> position,
            List<String> details
    ) {

        public GenerationFailure {
            position = java.util.Objects.requireNonNull(position, "Position");
            details = List.copyOf(details);
        }

        public enum Code {
            INVALID_RULE_SET,
            TOO_MANY_POSITIONS,
            TOO_EXPENSIVE,
            DUPLICATE_POSITION,
            CANCELLED,
            NO_VALID_CANDIDATE,
            INVALID_WEIGHT,
            GLOBAL_RULE_UNSATISFIED
        }
    }
}
