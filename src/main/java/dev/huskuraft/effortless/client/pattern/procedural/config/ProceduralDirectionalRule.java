package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.List;

import dev.huskuraft.effortless.client.pattern.procedural.NeighborDirection;

public record ProceduralDirectionalRule(
        String itemId,
        NeighborDirection direction,
        List<String> allowedItemIds,
        boolean rejectUnresolved
) {

    public ProceduralDirectionalRule {
        allowedItemIds = List.copyOf(allowedItemIds);
    }
}
