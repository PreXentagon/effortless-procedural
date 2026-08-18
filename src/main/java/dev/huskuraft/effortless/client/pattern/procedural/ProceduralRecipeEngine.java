package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;

/**
 * Runs one client-local recipe over explicit positions. This is the common
 * material-evaluation boundary used by previews and compatibility compilers:
 * adaptation, deterministic generation, cancellation, work limits and error
 * formatting therefore cannot drift between those callers.
 */
public final class ProceduralRecipeEngine {

    private ProceduralRecipeEngine() {
    }

    public static Result generate(Request request) {
        var adaptation = ProceduralPresetAdapter.adapt(request.preset());
        if (!adaptation.isSuccess()) {
            return Result.failure(
                    request.label() + " pattern is invalid",
                    adaptation.errors()
            );
        }
        var generation = new GenerationRequest<>(
                request.seed(),
                request.positions(),
                adaptation.ruleSet().orElseThrow(),
                request.existingNeighbors(),
                request.maximumPositions(),
                request.cancelled(),
                request.progress(),
                request.coordinates()
        );
        var generated = ProceduralGenerator.generate(
                generation, request.maximumEstimatedWork()
        );
        if (!generated.isSuccess()) {
            var failure = generated.failure().orElseThrow();
            return Result.failure(
                    request.label() + ": " + failure.message(),
                    failure.details()
            );
        }
        return Result.success(
                generated.placements(), generated.traversal()
        );
    }

    public record Request(
            ProceduralPatternPreset preset,
            String label,
            long seed,
            Collection<GridPosition> positions,
            ExistingNeighborLookup existingNeighbors,
            int maximumPositions,
            long maximumEstimatedWork,
            BooleanSupplier cancelled,
            GenerationProgress progress,
            CoordinateLookup coordinates
    ) {
        public Request {
            Objects.requireNonNull(preset, "Preset");
            label = Objects.requireNonNullElse(label, "Recipe");
            positions = List.copyOf(positions);
            Objects.requireNonNull(existingNeighbors, "Existing neighbors");
            Objects.requireNonNull(cancelled, "Cancellation supplier");
            Objects.requireNonNull(progress, "Progress listener");
            Objects.requireNonNull(coordinates, "Coordinate lookup");
            if (maximumPositions < 1) {
                throw new IllegalArgumentException(
                        "Maximum position count must be positive"
                );
            }
            if (maximumEstimatedWork < 1L) {
                throw new IllegalArgumentException(
                        "Maximum estimated work must be positive"
                );
            }
        }

        public static Request preview(
                ProceduralPatternPreset preset,
                String label,
                long seed,
                Collection<GridPosition> positions,
                BooleanSupplier cancelled,
                GenerationProgress progress,
                CoordinateLookup coordinates
        ) {
            return new Request(
                    preset,
                    label,
                    seed,
                    positions,
                    ExistingNeighborLookup.NONE,
                    Math.max(1, positions.size()),
                    ProceduralGenerator.MAX_ESTIMATED_WORK,
                    cancelled,
                    progress,
                    coordinates
            );
        }
    }

    public record Result(
            Map<GridPosition, ProceduralMaterial> placements,
            List<GridPosition> traversal,
            String message,
            List<String> details
    ) {
        public Result {
            placements = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(placements)
            );
            traversal = List.copyOf(traversal);
            message = Objects.requireNonNullElse(message, "");
            details = List.copyOf(details);
        }

        public static Result success(
                Map<GridPosition, ProceduralMaterial> placements,
                List<GridPosition> traversal
        ) {
            return new Result(placements, traversal, "", List.of());
        }

        public static Result failure(String message, List<String> details) {
            return new Result(Map.of(), List.of(), message, details);
        }

        public boolean isSuccess() {
            return message.isEmpty();
        }

        public String formattedError() {
            return details.isEmpty()
                    ? message
                    : message + ": " + String.join("; ", details);
        }
    }
}
