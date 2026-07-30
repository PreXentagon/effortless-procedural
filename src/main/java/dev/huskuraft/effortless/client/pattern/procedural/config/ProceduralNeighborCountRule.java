package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.List;

public record ProceduralNeighborCountRule(
        String itemId,
        List<String> neighborItemIds,
        int minimum,
        int maximum
) {

    public ProceduralNeighborCountRule {
        neighborItemIds = List.copyOf(neighborItemIds);
    }
}
