package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.HashSet;
import java.util.Set;

import dev.huskuraft.universal.api.core.BlockState;

/** Classifies blocks by stable state properties rather than registry names. */
public final class StructuralMaterialClassifier {

    private StructuralMaterialClassifier() {
    }

    public static Kind classify(BlockState state) {
        if (state == null || state.isAir()) {
            return Kind.SPECIAL;
        }
        var properties = propertyNames(state);
        if (properties.contains("persistent")
                || properties.contains("distance")) {
            return Kind.FOLIAGE;
        }
        if (properties.contains("shape")
                && properties.contains("half")
                && properties.contains("facing")) {
            return Kind.STAIR;
        }
        if (properties.contains("type")) {
            return Kind.SLAB;
        }
        if (properties.containsAll(Set.of(
                "north", "south", "east", "west"
        ))) {
            return Kind.CONNECTOR;
        }
        if (properties.contains("axis")
                || properties.contains("horizontal_axis")) {
            return Kind.PILLAR;
        }
        return Kind.SOLID;
    }

    public static boolean canSupportConnector(BlockState state) {
        return switch (classify(state)) {
            case SOLID, PILLAR, CONNECTOR -> true;
            case STAIR, SLAB, FOLIAGE, SPECIAL -> false;
        };
    }

    private static Set<String> propertyNames(BlockState state) {
        var names = new HashSet<String>();
        state.getPropertiesMap().keySet().forEach(property ->
                names.add(property.getName())
        );
        return names;
    }

    public enum Kind {
        SOLID,
        PILLAR,
        STAIR,
        SLAB,
        CONNECTOR,
        FOLIAGE,
        SPECIAL
    }
}
