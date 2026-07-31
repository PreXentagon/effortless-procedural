package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ProceduralGeneratorTest {

    private static final Candidate<String> A = new Candidate<>("a", "A");
    private static final Candidate<String> B = new Candidate<>("b", "B");
    private static final Candidate<String> C = new Candidate<>("c", "C");
    private static final Candidate<String> D = new Candidate<>("d", "D");
    private static final Candidate<String> E = new Candidate<>("e", "E");

    @Test
    void reversedNoiseMultiplierRangeIsRejected() {
        var source = new SeededNoiseSource<String>(
                0.1,
                0L,
                Map.of(
                        "a",
                        new SeededNoiseSource.MultiplierRange(2.0, 1.0)
                )
        );

        var errors = source.validate(Set.of("a"));

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("minimum <= maximum"));
    }

    @Test
    void sameSeedAndInputProduceIdenticalOutput() {
        var positions = line(2_000);
        var rules = rules(
                List.of(A, B, C),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "b", 2.0, "c", 3.0))),
                List.of(),
                3,
                Optional.of("a")
        );

        var first = ProceduralGenerator.generate(GenerationRequest.create(938475L, positions, rules));
        var second = ProceduralGenerator.generate(GenerationRequest.create(938475L, positions, rules));

        assertTrue(first.isSuccess());
        assertEquals(first.placements(), second.placements());
        assertEquals(first.traversal(), second.traversal());
    }

    @Test
    void differentSeedsCanProduceDifferentOutput() {
        var positions = line(500);
        var rules = rules(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "b", 1.0))),
                List.of(),
                2,
                Optional.of("a")
        );

        var first = ProceduralGenerator.generate(GenerationRequest.create(10L, positions, rules));
        var second = ProceduralGenerator.generate(GenerationRequest.create(11L, positions, rules));

        assertNotEquals(first.placements(), second.placements());
    }

    @Test
    void noiseRemainsFiniteAtWorldBorderAndMaximumFrequency() {
        double coordinate = 30_000_000.0 * 1_024.0;

        double positive = StableRandom.valueNoise(
                123L,
                coordinate,
                coordinate,
                coordinate,
                456L
        );
        double negative = StableRandom.valueNoise(
                123L,
                -coordinate,
                -coordinate,
                -coordinate,
                456L
        );

        assertTrue(Double.isFinite(positive));
        assertTrue(Double.isFinite(negative));
        assertTrue(positive >= 0.0 && positive < 1.0);
        assertTrue(negative >= 0.0 && negative < 1.0);
    }

    @Test
    void weightedSelectionStaysWithinStatisticalTolerance() {
        var positions = line(50_000);
        var rules = rules(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "b", 3.0))),
                List.of(),
                2,
                Optional.of("a")
        );

        var result = ProceduralGenerator.generate(GenerationRequest.create(1234567L, positions, rules));
        long bCount = result.placements().values().stream().filter("B"::equals).count();
        double fraction = (double) bCount / positions.size();

        assertTrue(result.isSuccess());
        assertTrue(fraction > 0.735 && fraction < 0.765, "B fraction was " + fraction);
    }

    @Test
    void finiteWeightsDoNotOverflowDuringSelection() {
        var rules = rules(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of(
                        "a", Double.MAX_VALUE,
                        "b", Double.MAX_VALUE
                ))),
                List.of(),
                2,
                Optional.of("a")
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(123L, line(100), rules)
        );

        assertTrue(result.isSuccess(), () -> result.failure().toString());
    }

    @Test
    void gradientEndpointsSelectIntendedCandidates() {
        var positions = List.of(new GridPosition(0, 0, 0), new GridPosition(10, 0, 0));
        var gradient = new LinearGradientSource<String>(
                Coordinate.X,
                Map.of(
                        "a", new LinearGradientSource.EndpointWeights(1.0, 0.0),
                        "b", new LinearGradientSource.EndpointWeights(0.0, 1.0)
                )
        );
        var rules = rules(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "b", 1.0)), gradient),
                List.of(),
                2,
                Optional.of("a")
        );

        var result = ProceduralGenerator.generate(GenerationRequest.create(55L, positions, rules));

        assertTrue(result.isSuccess());
        assertEquals("A", result.placements().get(positions.get(0)));
        assertEquals("B", result.placements().get(positions.get(1)));
    }

    @Test
    void orderedGradientBlendSelectsEveryExactPaletteStop() {
        var positions = List.of(
                new GridPosition(0, 0, 0),
                new GridPosition(5, 0, 0),
                new GridPosition(10, 0, 0)
        );
        var gradient = new OrderedPaletteGradientSource<String>(
                Coordinate.X,
                GradientDistributionMode.ORDERED_BLEND,
                GradientCurve.LINEAR,
                8
        );
        var rules = rules(
                List.of(A, B, C),
                List.of(
                        new WeightedSource<>(Map.of(
                                "a", 1.0,
                                "b", 1.0,
                                "c", 1.0
                        )),
                        gradient
                ),
                List.of(),
                3,
                Optional.of("a")
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(55L, positions, rules)
        );

        assertTrue(result.isSuccess());
        assertEquals(
                List.of("A", "B", "C"),
                result.traversal().stream()
                        .map(result.placements()::get)
                        .toList()
        );
    }

    @Test
    void orderedGradientBandsProduceContiguousEqualPaletteSections() {
        var positions = line(12);
        var gradient = new OrderedPaletteGradientSource<String>(
                Coordinate.X,
                GradientDistributionMode.ORDERED_BANDS,
                GradientCurve.LINEAR,
                8
        );
        var rules = rules(
                List.of(A, B, C),
                List.of(
                        new WeightedSource<>(Map.of(
                                "a", 1.0,
                                "b", 1.0,
                                "c", 1.0
                        )),
                        gradient
                ),
                List.of(),
                3,
                Optional.of("a")
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(55L, positions, rules)
        );

        assertTrue(result.isSuccess());
        assertEquals(
                List.of(
                        "A", "A", "A", "A",
                        "B", "B", "B", "B",
                        "C", "C", "C", "C"
                ),
                result.traversal().stream()
                        .map(result.placements()::get)
                        .toList()
        );
    }

    @Test
    void fiveBlockOrderedGradientBandsProduceFourBlocksPerSection() {
        var positions = line(20);
        var gradient = new OrderedPaletteGradientSource<String>(
                Coordinate.X,
                GradientDistributionMode.ORDERED_BANDS,
                GradientCurve.LINEAR,
                8
        );
        var rules = rules(
                List.of(A, B, C, D, E),
                List.of(
                        new WeightedSource<>(Map.of(
                                "a", 1.0,
                                "b", 1.0,
                                "c", 1.0,
                                "d", 1.0,
                                "e", 1.0
                        )),
                        gradient
                ),
                List.of(),
                5,
                Optional.of("a")
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(55L, positions, rules)
        );

        assertTrue(result.isSuccess());
        assertEquals(
                List.of(
                        "A", "A", "A", "A",
                        "B", "B", "B", "B",
                        "C", "C", "C", "C",
                        "D", "D", "D", "D",
                        "E", "E", "E", "E"
                ),
                result.traversal().stream()
                        .map(result.placements()::get)
                        .toList()
        );
    }

    @Test
    void configuredGradientStopsValidateInPaletteOrder() {
        var stops = new LinkedHashMap<String, Double>();
        stops.put("red", 0.10);
        stops.put("orange", 0.25);
        stops.put("yellow", 0.50);
        stops.put("lime", 0.75);
        stops.put("green", 1.00);
        var gradient = new OrderedPaletteGradientSource<String>(
                SpatialField.linear(Coordinate.X),
                GradientDistributionMode.ORDERED_BANDS,
                GradientCurve.LINEAR,
                4,
                stops
        );

        assertTrue(gradient.validate(Set.of(
                "green",
                "red",
                "lime",
                "yellow",
                "orange"
        )).isEmpty());
    }

    @Test
    void noiseValuesAreStableAndBounded() {
        double first = StableRandom.valueNoise(887766L, -1.25, 4.5, 19.75, 42L);
        double second = StableRandom.valueNoise(887766L, -1.25, 4.5, 19.75, 42L);
        double differentSeed = StableRandom.valueNoise(887767L, -1.25, 4.5, 19.75, 42L);

        assertEquals(first, second);
        assertTrue(first >= 0.0 && first < 1.0);
        assertNotEquals(first, differentSeed);
    }

    @Test
    void noiseDistributionIsDeterministic() {
        var noise = new SeededNoiseSource<String>(
                0.15,
                7L,
                Map.of(
                        "a", new SeededNoiseSource.MultiplierRange(0.0, 3.0),
                        "b", new SeededNoiseSource.MultiplierRange(0.0, 3.0)
                )
        );
        var rules = rules(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "b", 1.0)), noise),
                List.of(),
                2,
                Optional.of("a")
        );

        var first = ProceduralGenerator.generate(GenerationRequest.create(9191L, cube(12), rules));
        var second = ProceduralGenerator.generate(GenerationRequest.create(9191L, cube(12), rules));

        assertTrue(first.isSuccess());
        assertEquals(first.placements(), second.placements());
    }

    @Test
    void forbiddenAdjacencyIsNeverProducedWhenAlternativeExists() {
        var forbidden = new ForbiddenAdjacencyConstraint<String>(
                Set.of(new ForbiddenAdjacencyConstraint.Pair("a", "c")),
                NeighborScope.GENERATED_ONLY
        );
        var rules = rules(
                List.of(A, C),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "c", 1.0))),
                List.of(forbidden),
                2,
                Optional.of("a")
        );

        var result = ProceduralGenerator.generate(GenerationRequest.create(444L, line(2_000), rules));
        var ordered = result.traversal().stream().map(result.placements()::get).toList();

        assertTrue(result.isSuccess());
        for (int i = 1; i < ordered.size(); i++) {
            assertEquals(ordered.get(i - 1), ordered.get(i));
        }
    }

    @Test
    void retryExhaustionUsesConfiguredValidFallback() {
        var position = new GridPosition(0, 0, 0);
        var forbidden = new ForbiddenAdjacencyConstraint<String>(
                Set.of(new ForbiddenAdjacencyConstraint.Pair("a", "c")),
                NeighborScope.GENERATED_AND_EXISTING
        );
        var rules = rules(
                List.of(A, B, C),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "b", 0.0, "c", 0.0))),
                List.of(forbidden),
                1,
                Optional.of("b")
        );
        var request = new GenerationRequest<>(
                1L,
                List.of(position),
                rules,
                neighbor -> neighbor.equals(new GridPosition(-1, 0, 0))
                        ? Optional.of("c")
                        : Optional.empty(),
                10,
                () -> false
        );

        var result = ProceduralGenerator.generate(request);

        assertTrue(result.isSuccess());
        assertEquals("B", result.placements().get(position));
    }

    @Test
    void impossibleRulesReturnUsefulFailureAndNoPartialOutput() {
        var position = new GridPosition(0, 0, 0);
        var forbidden = new ForbiddenAdjacencyConstraint<String>(
                Set.of(new ForbiddenAdjacencyConstraint.Pair("a", "c")),
                NeighborScope.GENERATED_AND_EXISTING
        );
        var rules = rules(
                List.of(A, C),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "c", 0.0))),
                List.of(forbidden),
                1,
                Optional.of("a")
        );
        var request = new GenerationRequest<>(
                1L,
                List.of(position),
                rules,
                neighbor -> Optional.of("c"),
                10,
                () -> false
        );

        var result = ProceduralGenerator.generate(request);

        assertFalse(result.isSuccess());
        assertTrue(result.placements().isEmpty());
        assertEquals(
                GenerationResult.GenerationFailure.Code.NO_VALID_CANDIDATE,
                result.failure().orElseThrow().code()
        );
        assertFalse(result.failure().orElseThrow().details().isEmpty());
    }

    @Test
    void outputOrderIsStableYThenZThenX() {
        var positions = List.of(
                new GridPosition(9, 3, 0),
                new GridPosition(1, -1, 8),
                new GridPosition(5, 3, -4),
                new GridPosition(-2, 3, -4)
        );
        var rules = rules(
                List.of(A),
                List.of(new WeightedSource<>(Map.of("a", 1.0))),
                List.of(),
                1,
                Optional.of("a")
        );

        var result = ProceduralGenerator.generate(GenerationRequest.create(1L, positions, rules));

        assertEquals(
                List.of(
                        new GridPosition(1, -1, 8),
                        new GridPosition(-2, 3, -4),
                        new GridPosition(5, 3, -4),
                        new GridPosition(9, 3, 0)
                ),
                result.traversal()
        );
        assertEquals(result.traversal(), new ArrayList<>(result.placements().keySet()));
    }

    @Test
    void maximumRunLengthUsesAnotherCandidate() {
        var rules = rules(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 100.0, "b", 1.0))),
                List.of(new MaxRunLengthConstraint<>(Set.of("a"), 3, NeighborScope.GENERATED_ONLY)),
                2,
                Optional.of("b")
        );

        var result = ProceduralGenerator.generate(GenerationRequest.create(71L, line(1_000), rules));
        int run = 0;
        for (var value : result.traversal().stream().map(result.placements()::get).toList()) {
            run = value.equals("A") ? run + 1 : 0;
            assertTrue(run <= 3);
        }
    }

    @Test
    void verticalRuleCanRequireAllowedBlockBelow() {
        var rules = rules(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "b", 0.0))),
                List.of(new AllowedVerticalNeighborConstraint<>(
                        "a",
                        GridPosition.Direction.DOWN,
                        Set.of("b"),
                        NeighborScope.GENERATED_AND_EXISTING,
                        AllowedVerticalNeighborConstraint.UnresolvedBehavior.REJECT
                )),
                1,
                Optional.of("b")
        );
        var target = new GridPosition(0, 5, 0);
        var request = new GenerationRequest<>(
                3L,
                List.of(target),
                rules,
                position -> position.equals(new GridPosition(0, 4, 0))
                        ? Optional.of("b")
                        : Optional.empty(),
                100,
                () -> false
        );

        var result = ProceduralGenerator.generate(request);

        assertTrue(result.isSuccess());
        assertEquals("A", result.placements().get(target));
    }

    @Test
    void verticalRuleRejectsMissingNonTargetNeighborEvenWhenFutureIsAllowed() {
        var rules = rules(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 100.0, "b", 1.0))),
                List.of(new AllowedVerticalNeighborConstraint<>(
                        "a",
                        GridPosition.Direction.DOWN,
                        Set.of("b"),
                        NeighborScope.GENERATED_ONLY,
                        AllowedVerticalNeighborConstraint.UnresolvedBehavior.ALLOW
                )),
                2,
                Optional.of("b")
        );
        var target = new GridPosition(0, 5, 0);

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(3L, List.of(target), rules)
        );

        assertTrue(result.isSuccess());
        assertEquals("B", result.placements().get(target));
    }

    @Test
    void malformedRulesAreRejectedBeforeGeneration() {
        var rules = rules(
                List.of(A, A),
                List.of(new WeightedSource<>(Map.of("missing", -1.0))),
                List.of(),
                0,
                Optional.of("missing")
        );

        var result = ProceduralGenerator.generate(GenerationRequest.create(0L, line(1), rules));

        assertFalse(result.isSuccess());
        assertEquals(
                GenerationResult.GenerationFailure.Code.INVALID_RULE_SET,
                result.failure().orElseThrow().code()
        );
        assertTrue(result.failure().orElseThrow().details().size() >= 4);
    }

    @Test
    void cancellationReturnsNoPartialOutput() {
        var rules = rules(
                List.of(A),
                List.of(new WeightedSource<>(Map.of("a", 1.0))),
                List.of(),
                1,
                Optional.of("a")
        );
        var calls = new AtomicInteger();
        var request = new GenerationRequest<>(
                0L,
                line(100),
                rules,
                ExistingNeighborLookup.NONE,
                100,
                () -> calls.incrementAndGet() > 10
        );

        var result = ProceduralGenerator.generate(request);

        assertFalse(result.isSuccess());
        assertTrue(result.placements().isEmpty());
        assertEquals(
                GenerationResult.GenerationFailure.Code.CANCELLED,
                result.failure().orElseThrow().code()
        );
    }

    @Test
    void positionBudgetFailsBeforeAllocatingOutput() {
        var rules = rules(
                List.of(A),
                List.of(new WeightedSource<>(Map.of("a", 1.0))),
                List.of(),
                1,
                Optional.of("a")
        );
        var request = new GenerationRequest<>(
                0L,
                line(101),
                rules,
                ExistingNeighborLookup.NONE,
                100,
                () -> false
        );

        var result = ProceduralGenerator.generate(request);

        assertFalse(result.isSuccess());
        assertEquals(
                GenerationResult.GenerationFailure.Code.TOO_MANY_POSITIONS,
                result.failure().orElseThrow().code()
        );
    }

    @Test
    void workBudgetRejectsExpensiveRulesWithoutPartialOutput() {
        var rules = rules(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "b", 1.0))),
                List.of(new MinimumSpacingConstraint<>(
                        Set.of("a"),
                        MinimumSpacingConstraint.MAXIMUM_RADIUS,
                        MinimumSpacingConstraint.DistanceMetric.CHEBYSHEV,
                        NeighborScope.GENERATED_ONLY
                )),
                2,
                Optional.of("b")
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(0L, line(2_000), rules)
        );

        assertFalse(result.isSuccess());
        assertTrue(result.placements().isEmpty());
        assertEquals(
                GenerationResult.GenerationFailure.Code.TOO_EXPENSIVE,
                result.failure().orElseThrow().code()
        );
    }

    @Test
    void callerCanRaiseExecutionWorkBudgetWithoutChangingRules() {
        var configuredRules = rules(
                List.of(A, B),
                List.of(new WeightedSource<>(
                        Map.of("a", 1.0, "b", 1.0)
                )),
                List.of(),
                2,
                Optional.of("a")
        );
        var request = GenerationRequest.create(
                12L,
                line(20),
                configuredRules
        );

        var rejected = ProceduralGenerator.generate(request, 1L);
        var accepted = ProceduralGenerator.generate(request, 10_000L);

        assertFalse(rejected.isSuccess());
        assertEquals(
                GenerationResult.GenerationFailure.Code.TOO_EXPENSIVE,
                rejected.failure().orElseThrow().code()
        );
        assertTrue(accepted.isSuccess());
    }

    @Test
    void futureTargetsDoNotLeakExistingWorldState() {
        var generated = new HashMap<GridPosition, Candidate<String>>();
        var current = new GridPosition(0, 0, 0);
        var future = new GridPosition(1, 0, 0);
        var context = new GenerationContext<>(
                1L,
                current,
                0,
                2,
                GenerationBounds.enclosing(List.of(current, future)),
                Set.of(current, future),
                generated,
                position -> Optional.of("world")
        );

        assertTrue(context.neighborId(future, NeighborScope.GENERATED_AND_EXISTING).isEmpty());
    }

    @Test
    void sequenceSourceUsesStableTraversalOrdinal() {
        var rules = rules(
                List.of(A, B, C),
                List.of(new SequenceSource<>(0, 1.0, 0.0)),
                List.of(),
                1,
                Optional.of("a")
        );

        var result = ProceduralGenerator.generate(GenerationRequest.create(
                123L,
                line(9),
                rules
        ));

        assertTrue(result.isSuccess());
        assertEquals(
                List.of("A", "B", "C", "A", "B", "C", "A", "B", "C"),
                result.traversal().stream().map(result.placements()::get).toList()
        );
    }

    @Test
    void preferredAdjacencyIncreasesPreferredCandidateFrequency() {
        var positions = line(20_000);
        var baseRules = rules(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "b", 1.0))),
                List.of(),
                2,
                Optional.of("a")
        );
        var preferredRules = rules(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "b", 1.0))),
                List.of(new PreferredAdjacencyConstraint<>(
                        "b",
                        Set.of("a"),
                        8.0,
                        NeighborScope.GENERATED_AND_EXISTING
                )),
                2,
                Optional.of("a")
        );
        ExistingNeighborLookup world = position -> position.y() == -1
                ? Optional.of("a")
                : Optional.empty();
        var baseRequest = new GenerationRequest<>(
                99L, positions, baseRules, world, 30_000, () -> false
        );
        var preferredRequest = new GenerationRequest<>(
                99L, positions, preferredRules, world, 30_000, () -> false
        );

        var base = ProceduralGenerator.generate(baseRequest);
        var preferred = ProceduralGenerator.generate(preferredRequest);
        long baseB = base.placements().values().stream().filter("B"::equals).count();
        long preferredB = preferred.placements().values().stream().filter("B"::equals).count();

        assertTrue(preferredB > baseB + 5_000);
    }

    private static ProceduralRuleSet<String> rules(
            List<Candidate<String>> candidates,
            List<WeightSource<String>> sources,
            List<PlacementConstraint<String>> constraints,
            int retryLimit,
            Optional<String> fallback
    ) {
        return new ProceduralRuleSet<>(candidates, sources, constraints, retryLimit, fallback);
    }

    private static List<GridPosition> line(int length) {
        var positions = new ArrayList<GridPosition>(length);
        for (int x = 0; x < length; x++) {
            positions.add(new GridPosition(x, 0, 0));
        }
        return positions;
    }

    private static List<GridPosition> cube(int side) {
        var positions = new ArrayList<GridPosition>(side * side * side);
        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    positions.add(new GridPosition(x, y, z));
                }
            }
        }
        return positions;
    }
}
