package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class GenerationBoundsTest {

    @Test
    void boundsProvideOneCanonicalSpatialCalculation() {
        var bounds = GenerationBounds.enclosing(List.of(
                new GridPosition(-3, 5, 9),
                new GridPosition(2, 7, 12)
        ));

        assertEquals(6, bounds.sizeX());
        assertEquals(3, bounds.sizeY());
        assertEquals(4, bounds.sizeZ());
        assertEquals(72L, bounds.volume());
        assertEquals(new GridPosition(0, 0, 0), bounds.normalize(
                new GridPosition(-3, 5, 9)
        ));
        assertEquals(new GridPosition(5, 2, 3), bounds.normalize(
                new GridPosition(2, 7, 12)
        ));
        assertEquals(0.0, bounds.normalizedX(
                new GridPosition(-3, 6, 10)
        ));
        assertEquals(1.0, bounds.normalizedZ(
                new GridPosition(0, 6, 12)
        ));
    }
}
