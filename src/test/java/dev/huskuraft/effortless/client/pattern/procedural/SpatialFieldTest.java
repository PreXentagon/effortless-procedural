package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralNoiseConfig;

class SpatialFieldTest {

    @Test
    void traversalDefaultMappingIsPreservedAndCanBeShifted() {
        var context = contextAt(5, 0, 0, 5, 11);
        var defaults = SpatialField.linear(Coordinate.TRAVERSAL);
        var shifted = defaults.withCenter(0.75, 0.5, 0.5);

        assertEquals(0.5, defaults.sample(context), 0.000001);
        assertEquals(0.25, shifted.sample(context), 0.000001);
    }

    @Test
    void linearFieldAndReverseHaveOppositeEndpoints() {
        var field = SpatialField.linear(Coordinate.X);

        assertEquals(0.0, field.sample(context(0, 5, 0)), 0.000001);
        assertEquals(1.0, field.sample(context(10, 5, 0)), 0.000001);
        assertEquals(
                1.0,
                field.withInverted(true).sample(context(0, 5, 0)),
                0.000001
        );
    }

    @Test
    void radialAndSquareFieldsUseTheActivePlane() {
        var radial = SpatialField.linear(Coordinate.X)
                .withShape(SpatialField.Shape.RADIAL);
        var square = radial.withShape(SpatialField.Shape.SQUARE);

        assertEquals(0.0, radial.sample(context(5, 5, 0)), 0.000001);
        assertEquals(1.0, radial.sample(context(10, 10, 0)), 0.000001);
        assertEquals(1.0, square.sample(context(10, 5, 0)), 0.000001);
        assertTrue(radial.sample(context(10, 5, 0)) < 1.0);
    }

    @Test
    void rotationChangesDirectionalFieldDeterministically() {
        var vertical = SpatialField.linear(Coordinate.X)
                .withRotation(90.0);

        double bottom = vertical.sample(context(5, 0, 0));
        double top = vertical.sample(context(5, 10, 0));

        assertNotEquals(bottom, top);
        assertEquals(bottom, vertical.sample(context(5, 0, 0)));
        assertEquals(top, vertical.sample(context(5, 10, 0)));
    }

    @Test
    void customOrderedStopsControlBlendIntervals() {
        var candidates = List.of(
                new Candidate<>("a", "A"),
                new Candidate<>("b", "B"),
                new Candidate<>("c", "C")
        );
        var source = new OrderedPaletteGradientSource<String>(
                SpatialField.linear(Coordinate.X),
                GradientDistributionMode.ORDERED_BLEND,
                GradientCurve.LINEAR,
                8,
                new LinkedHashMap<>(Map.of(
                        "a", 0.0,
                        "b", 0.8,
                        "c", 1.0
                ))
        );
        var weights = new double[] {1.0, 1.0, 1.0};

        source.apply(context(4, 5, 0), candidates, weights);

        assertArrayEquals(new double[] {0.5, 0.5, 0.0}, weights, 0.000001);
    }

    @Test
    void fractalNoiseAndWarpRemainStable() {
        var config = ProceduralNoiseConfig.DEFAULT
                .withFractal(4, 0.6, 2.2)
                .withRotation(35.0)
                .withWarp(1.5, 0.08, 91L);
        var source = new SeededNoiseSource<String>(
                0.12,
                77L,
                Map.of("stone", new SeededNoiseSource.MultiplierRange(0.0, 1.0)),
                config
        );
        var candidate = List.of(new Candidate<>("stone", "stone"));
        var first = new double[] {1.0};
        var second = new double[] {1.0};

        source.apply(context(7, 3, 0), candidate, first);
        source.apply(context(7, 3, 0), candidate, second);

        assertArrayEquals(first, second, 0.0);
        assertTrue(first[0] >= 0.0 && first[0] <= 1.0);
    }

    private static GenerationContext<String> context(int x, int y, int z) {
        return new GenerationContext<>(
                12345L,
                new GridPosition(x, y, z),
                y * 11 + x,
                121,
                new GenerationBounds(0, 0, 0, 10, 10, 0),
                Set.of(),
                Map.of(),
                ExistingNeighborLookup.NONE
        );
    }

    private static GenerationContext<String> contextAt(
            int x,
            int y,
            int z,
            int ordinal,
            int positionCount
    ) {
        return new GenerationContext<>(
                12345L,
                new GridPosition(x, y, z),
                ordinal,
                positionCount,
                new GenerationBounds(0, 0, 0, 10, 10, 0),
                Set.of(),
                Map.of(),
                ExistingNeighborLookup.NONE
        );
    }
}
