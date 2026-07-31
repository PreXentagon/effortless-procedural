package dev.huskuraft.effortless.client.road;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RoadInteractionMathTest {

    @Test
    void crossingSegmentsHaveZeroDistance() {
        assertEquals(
                0.0,
                RoadInteractionMath.segmentDistanceSquared(
                        new RoadPoint(0.0, 0.0, 0.0),
                        new RoadPoint(4.0, 0.0, 0.0),
                        new RoadPoint(2.0, -2.0, 0.0),
                        new RoadPoint(2.0, 2.0, 0.0)
                ),
                1.0e-9
        );
    }

    @Test
    void separatedParallelSegmentsUseClosestPoints() {
        assertEquals(
                4.0,
                RoadInteractionMath.segmentDistanceSquared(
                        new RoadPoint(0.0, 0.0, 0.0),
                        new RoadPoint(4.0, 0.0, 0.0),
                        new RoadPoint(0.0, 2.0, 0.0),
                        new RoadPoint(4.0, 2.0, 0.0)
                ),
                1.0e-9
        );
    }
}
