package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.Arrays;
import java.util.List;

public enum NeighborTopology {
    ORTHOGONAL_6(1),
    FACES_AND_EDGES_18(2),
    ALL_26(3);

    private final List<NeighborDirection> directions;

    NeighborTopology(int maximumChangedAxes) {
        directions = Arrays.stream(NeighborDirection.values())
                .filter(direction -> direction.changedAxes() <= maximumChangedAxes)
                .toList();
    }

    public List<NeighborDirection> directions() {
        return directions;
    }
}
