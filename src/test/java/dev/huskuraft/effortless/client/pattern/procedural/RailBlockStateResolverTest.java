package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

class RailBlockStateResolverTest {

    private static final Set<String> CREATE = Set.of(
            "xo", "zo", "pd", "nd", "ae", "aw", "an", "as"
    );
    private static final Set<String> VANILLA = Set.of(
            "east_west", "north_south", "ascending_east",
            "ascending_west", "ascending_north", "ascending_south"
    );

    @Test
    void createTracksFollowOrthogonalAndDiagonalTangents() {
        assertEquals("xo", shape(geometry(1, 0, 0), CREATE));
        assertEquals("zo", shape(geometry(0, 0, 1), CREATE));
        assertEquals("pd", shape(geometry(1, 0, 1), CREATE));
        assertEquals("nd", shape(geometry(1, 0, -1), CREATE));
    }

    @Test
    void risingTracksUseTheDirectionInWhichHeightIncreases() {
        assertEquals("ae", shape(geometry(1, 0.6, 0), CREATE));
        assertEquals("aw", shape(geometry(1, -0.6, 0), CREATE));
        assertEquals(
                "ascending_south",
                shape(geometry(0, 0.6, 1), VANILLA)
        );
    }

    private static String shape(
            StructuralGeometry geometry,
            Set<String> available
    ) {
        return RailBlockStateResolver.shape(geometry, available)
                .orElseThrow();
    }

    private static StructuralGeometry geometry(
            double tangentX,
            double tangentY,
            double tangentZ
    ) {
        return new StructuralGeometry(
                0.5, 0.5, 0.0, 0.5, Math.abs(tangentY),
                0.0, 0.0,
                tangentX, tangentY, tangentZ,
                0.0, 1.0, 0.0
        );
    }
}
