package dev.huskuraft.effortless.client.pattern.procedural;

/**
 * Stable ways to derive the effective seed. None depend on mutable RNG state.
 */
public enum SeedMode {
    FIXED,
    SHAPE,
    WORLD_ANCHORED
}
