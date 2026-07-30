package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.List;

import dev.huskuraft.effortless.client.pattern.procedural.MinimumSpacingConstraint;

public record ProceduralSpacingRule(
        List<String> itemIds,
        int radius,
        MinimumSpacingConstraint.DistanceMetric metric
) {

    public ProceduralSpacingRule {
        itemIds = List.copyOf(itemIds);
    }
}
