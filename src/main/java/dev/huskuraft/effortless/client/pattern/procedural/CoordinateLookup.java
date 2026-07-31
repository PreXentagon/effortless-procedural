package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.OptionalDouble;

/**
 * Optional client-only coordinate metadata for non-rectilinear generators.
 * Ordinary structure generation uses {@link #NONE}.
 */
@FunctionalInterface
public interface CoordinateLookup {

    CoordinateLookup NONE = (coordinate, position) -> OptionalDouble.empty();

    OptionalDouble sample(Coordinate coordinate, GridPosition position);
}
