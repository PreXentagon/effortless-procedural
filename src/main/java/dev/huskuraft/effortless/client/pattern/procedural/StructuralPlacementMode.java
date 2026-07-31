package dev.huskuraft.effortless.client.pattern.procedural;

/** Determines how generator-provided structural geometry affects materials. */
public enum StructuralPlacementMode {
    /** Preserve the palette's existing weighted/random behavior. */
    RANDOM,
    /** Apply conservative core, shell, slope, tip, and junction heuristics. */
    SMART,
    /** Use structural mask coordinates without automatic material weighting. */
    RULE_DRIVEN
}
