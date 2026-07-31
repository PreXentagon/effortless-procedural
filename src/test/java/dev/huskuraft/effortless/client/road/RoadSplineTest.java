package dev.huskuraft.effortless.client.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RoadSplineTest {

    @Test
    void straightSplineIncludesExactEndpoints() {
        var first = new RoadPoint(0.5, 2.5, 4.5);
        var last = new RoadPoint(10.5, 2.5, 4.5);
        var result = RoadSpline.sample(List.of(first, last), 0.0, 0.25);

        assertTrue(result.isSuccess());
        assertEquals(first, result.samples().get(0).point());
        assertEquals(last, result.samples()
                .get(result.samples().size() - 1).point());
        assertEquals(0.0, result.samples().get(0).progress());
        assertEquals(
                1.0,
                result.samples().get(result.samples().size() - 1).progress()
        );
        assertTrue(result.samples().stream().allMatch(sample ->
                Math.abs(sample.point().y() - 2.5) < 1.0e-9
                        && Math.abs(sample.point().z() - 4.5) < 1.0e-9
        ));
    }

    @Test
    void curvePassesThroughEveryControlPointDeterministically() {
        var points = List.of(
                new RoadPoint(0.5, 0.5, 0.5),
                new RoadPoint(6.5, 0.5, 5.5),
                new RoadPoint(12.5, 2.5, 0.5),
                new RoadPoint(18.5, 2.5, -3.5)
        );
        var first = RoadSpline.sample(points, 0.2, 0.2);
        var second = RoadSpline.sample(points, 0.2, 0.2);

        assertEquals(first, second);
        for (var point : points) {
            assertTrue(first.samples().stream().anyMatch(sample ->
                    sample.point().distance(point) < 1.0e-9
            ));
        }
        assertTrue(first.length() > points.get(0)
                .distance(points.get(points.size() - 1)));
    }

    @Test
    void closestSampleReportsOwningControlSegment() {
        var result = RoadSpline.sample(List.of(
                new RoadPoint(0.5, 0.5, 0.5),
                new RoadPoint(5.5, 0.5, 3.5),
                new RoadPoint(10.5, 0.5, 0.5)
        ), 0.0, 0.2);

        var closest = RoadSpline.closest(
                result.samples(),
                new RoadPoint(2.5, 0.5, 2.0)
        ).orElseThrow();
        assertEquals(0, closest.segmentIndex());
        assertTrue(closest.progress() > 0.0 && closest.progress() < 1.0);
    }
}
