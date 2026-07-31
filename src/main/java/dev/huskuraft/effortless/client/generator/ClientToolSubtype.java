package dev.huskuraft.effortless.client.generator;

import dev.huskuraft.effortless.building.Option;

/**
 * Client-only subtype exposed by an explicit-geometry authoring tool.
 *
 * <p>The wheel and workbench preview both consume this interface. Implementing
 * it does not add a build-mode enum or any server-visible serializer value.</p>
 */
public interface ClientToolSubtype extends Option {

    /** Stable client-local identifier used by persistence and UI selection. */
    String id();

    @Override
    default String getName() {
        return id();
    }
}
