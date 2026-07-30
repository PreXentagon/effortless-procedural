package dev.huskuraft.effortless.client.pattern.procedural;

/**
 * Defines how a gradient converts its normalized coordinate into candidate
 * weights. This is client-local configuration and is never serialized to the
 * server.
 */
public enum GradientDistributionMode {
    /**
     * Interpolates every block entry's configured start/end weight.
     */
    WEIGHTED_ENDPOINTS,

    /**
     * Treats the ordered block list as evenly spaced stops and blends only
     * between the two neighboring entries.
     */
    ORDERED_BLEND,

    /**
     * Divides the coordinate into one deterministic band per ordered block
     * entry.
     */
    ORDERED_BANDS
}
