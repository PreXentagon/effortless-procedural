package dev.huskuraft.effortless.client.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.Candidate;
import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;
import dev.huskuraft.effortless.client.pattern.procedural.ExistingNeighborLookup;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationProgress;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationRequest;
import dev.huskuraft.effortless.client.pattern.procedural.GradientCurve;
import dev.huskuraft.effortless.client.pattern.procedural.GradientDistributionMode;
import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.OrderedPaletteGradientSource;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralGenerator;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralRuleSet;

class RoadCoordinateGenerationTest {

    @Test
    void orderedGradientUsesRoadPathInsteadOfWorldAxis() {
        var positions = List.of(
                new GridPosition(0, 0, 0),
                new GridPosition(0, 0, 10),
                new GridPosition(-10, 0, 10)
        );
        var path = new LinkedHashMap<GridPosition, Double>();
        path.put(positions.get(0), 0.0);
        path.put(positions.get(1), 0.5);
        path.put(positions.get(2), 1.0);
        var candidates = List.of(
                new Candidate<>("start", "start"),
                new Candidate<>("middle", "middle"),
                new Candidate<>("end", "end")
        );
        var ruleSet = new ProceduralRuleSet<>(
                candidates,
                List.of(new OrderedPaletteGradientSource<>(
                        Coordinate.PATH,
                        GradientDistributionMode.ORDERED_BANDS,
                        GradientCurve.LINEAR,
                        8
                )),
                List.of(),
                3,
                Optional.of("start")
        );
        var request = new GenerationRequest<>(
                1234L,
                positions,
                ruleSet,
                ExistingNeighborLookup.NONE,
                100,
                () -> false,
                GenerationProgress.NONE,
                (coordinate, position) -> coordinate == Coordinate.PATH
                        ? OptionalDouble.of(path.get(position))
                        : OptionalDouble.empty()
        );

        var result = ProceduralGenerator.generate(request);

        assertTrue(result.isSuccess());
        assertEquals("start", result.placements().get(positions.get(0)));
        assertEquals("middle", result.placements().get(positions.get(1)));
        assertEquals("end", result.placements().get(positions.get(2)));
    }

    @Test
    void roadCoordinatesHavePredictableFallbacksOutsideRoads() {
        var position = new GridPosition(4, 2, 8);
        var context = new dev.huskuraft.effortless.client.pattern.procedural
                .GenerationContext<String>(
                0L,
                position,
                1,
                3,
                new dev.huskuraft.effortless.client.pattern.procedural
                        .GenerationBounds(0, 0, 0, 8, 4, 8),
                java.util.Set.of(position),
                Map.of(),
                ExistingNeighborLookup.NONE
        );

        assertEquals(0.5, Coordinate.PATH.sample(context));
        assertEquals(0.5, Coordinate.LATERAL.sample(context));
        assertEquals(0.5, Coordinate.DEPTH.sample(context));
    }
}
