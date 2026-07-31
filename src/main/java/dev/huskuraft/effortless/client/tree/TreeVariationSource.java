package dev.huskuraft.effortless.client.tree;

/** Inputs used to derive a deterministic generated-tree variation. */
public enum TreeVariationSource {
    /** The visible variant produces the same tree at every position. */
    LOCKED,
    /** World position and the visible variant both influence the tree. */
    WORLD_POSITION,
    /** The visible variant advances after each successful placement. */
    PLACEMENT_SEQUENCE
}
