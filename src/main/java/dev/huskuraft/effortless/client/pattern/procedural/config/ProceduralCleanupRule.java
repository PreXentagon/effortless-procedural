package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.List;

public record ProceduralCleanupRule(
        List<String> sourceItemIds,
        List<String> matchingNeighborItemIds,
        String replacementItemId,
        int minimumMatches,
        int maximumMatches
) {

    public ProceduralCleanupRule {
        sourceItemIds = List.copyOf(sourceItemIds);
        matchingNeighborItemIds = List.copyOf(matchingNeighborItemIds);
    }
}
