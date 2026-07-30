package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.List;

public record ProceduralVerticalRule(
        String itemId,
        Direction direction,
        List<String> allowedItemIds,
        boolean rejectUnresolved
) {

    public ProceduralVerticalRule {
        allowedItemIds = List.copyOf(allowedItemIds);
    }

    public enum Direction {
        ABOVE,
        BELOW
    }
}
