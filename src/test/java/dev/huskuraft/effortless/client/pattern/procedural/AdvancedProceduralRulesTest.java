package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AdvancedProceduralRulesTest {

    private static final Candidate<String> A = new Candidate<>("a", "A");
    private static final Candidate<String> B = new Candidate<>("b", "B");

    @Test
    void quotaProducesExactDeterministicCount() {
        var quota = new CandidateQuotaRule<String>(
                "a",
                0.25,
                0.25,
                CandidateQuotaRule.Unit.FRACTION
        );
        var rules = new ProceduralRuleSet<>(
                List.of(A, B),
                List.of(
                        new WeightedSource<>(Map.of("a", 1.0, "b", 1.0)),
                        quota
                ),
                List.of(quota),
                2,
                Optional.of("b"),
                2
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(77L, line(100), rules)
        );

        assertTrue(result.isSuccess(), () -> result.failure().toString());
        assertEquals(
                25,
                result.placements().values().stream().filter("A"::equals).count()
        );
    }

    @Test
    void exactFractionQuotaRoundsDeterministicallyForInteractiveSizes() {
        var quota = new CandidateQuotaRule<String>(
                "a",
                0.25,
                0.25,
                CandidateQuotaRule.Unit.FRACTION
        );
        var rules = new ProceduralRuleSet<>(
                List.of(A, B),
                List.of(
                        new WeightedSource<>(Map.of("a", 1.0, "b", 1.0)),
                        quota
                ),
                List.of(quota),
                2,
                Optional.of("b"),
                2
        );

        for (int positionCount = 1; positionCount <= 20; positionCount++) {
            int count = positionCount;
            var result = ProceduralGenerator.generate(
                    GenerationRequest.create(
                            77L,
                            line(count),
                            rules
                    )
            );

            int expected = (int) Math.round(count * 0.25);
            assertTrue(
                    result.isSuccess(),
                    () -> count + ": " + result.failure()
            );
            assertEquals(
                    expected,
                    result.placements().values().stream()
                            .filter("A"::equals)
                            .count(),
                    "position count " + count
            );
        }
    }

    @Test
    void mutuallyImpossibleMinimumQuotasFailAtomically() {
        var quotaA = new CandidateQuotaRule<String>(
                "a", 0.75, 1.0, CandidateQuotaRule.Unit.FRACTION
        );
        var quotaB = new CandidateQuotaRule<String>(
                "b", 0.75, 1.0, CandidateQuotaRule.Unit.FRACTION
        );
        var rules = new ProceduralRuleSet<>(
                List.of(A, B),
                List.of(
                        new WeightedSource<>(Map.of("a", 1.0, "b", 1.0)),
                        quotaA,
                        quotaB
                ),
                List.of(quotaA, quotaB),
                2,
                Optional.empty(),
                2
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(77L, line(20), rules)
        );

        assertFalse(result.isSuccess());
        assertTrue(result.placements().isEmpty());
    }

    @Test
    void countQuotaWithoutAnyWholeCountIsRejectedBeforeGeneration() {
        var quota = new CandidateQuotaRule<String>(
                "a",
                0.2,
                0.8,
                CandidateQuotaRule.Unit.COUNT
        );

        var errors = quota.validate(Set.of("a"));

        assertTrue(errors.stream().anyMatch(
                error -> error.contains("whole block count")
        ));
    }

    @Test
    void minimumSpacingKeepsSelectedCandidatesApart() {
        var rules = new ProceduralRuleSet<>(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 100.0, "b", 1.0))),
                List.of(new MinimumSpacingConstraint<>(
                        Set.of("a"),
                        2,
                        MinimumSpacingConstraint.DistanceMetric.MANHATTAN,
                        NeighborScope.GENERATED_ONLY
                )),
                2,
                Optional.of("b")
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(92L, line(1_000), rules)
        );

        assertTrue(result.isSuccess());
        int previousA = -10;
        for (int x = 0; x < 1_000; x++) {
            if ("A".equals(result.placements().get(new GridPosition(x, 0, 0)))) {
                assertTrue(x - previousA > 2);
                previousA = x;
            }
        }
    }

    @Test
    void minimumSpacingTreatsSeveralSelectedTypesAsOneSpacedGroup() {
        var positions = List.of(
                new GridPosition(0, 0, 0),
                new GridPosition(1, 0, 0)
        );
        var candidates = List.of(
                new Candidate<>("a", "a"),
                new Candidate<>("b", "b"),
                new Candidate<>("safe", "safe")
        );
        var spacing = new MinimumSpacingConstraint<String>(
                Set.of("a", "b"),
                1,
                MinimumSpacingConstraint.DistanceMetric.MANHATTAN,
                NeighborScope.GENERATED_ONLY
        );
        var rules = new ProceduralRuleSet<>(
                candidates,
                List.of(new SequenceSource<String>(0, 1.0, 0.0)),
                List.of(spacing),
                3,
                Optional.of("safe")
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(1L, positions, rules)
        );

        assertTrue(result.isSuccess(), () -> result.failure().toString());
        assertEquals("a", result.placements().get(positions.get(0)));
        assertEquals("safe", result.placements().get(positions.get(1)));
    }

    @Test
    void diagonalTopologyDetectsEdgeNeighbor() {
        var origin = new GridPosition(0, 0, 0);
        var diagonal = new GridPosition(1, 1, 0);
        var generated = Map.of(diagonal, B);
        var context = new GenerationContext<>(
                0L,
                origin,
                0,
                2,
                GenerationBounds.enclosing(List.of(origin, diagonal)),
                Set.of(origin, diagonal),
                generated,
                ExistingNeighborLookup.NONE
        );
        var pair = Set.of(new ForbiddenAdjacencyConstraint.Pair("a", "b"));

        assertTrue(new ForbiddenAdjacencyConstraint<String>(
                pair,
                NeighborScope.GENERATED_ONLY,
                NeighborTopology.ORTHOGONAL_6
        ).evaluate(A, context).allowed());
        assertFalse(new ForbiddenAdjacencyConstraint<String>(
                pair,
                NeighborScope.GENERATED_ONLY,
                NeighborTopology.FACES_AND_EDGES_18
        ).evaluate(A, context).allowed());
    }

    @Test
    void existingWorldRulesMayReferenceBlocksOutsideGeneratedPalette() {
        var constraint = new ForbiddenAdjacencyConstraint<String>(
                Set.of(new ForbiddenAdjacencyConstraint.Pair("a", "world")),
                NeighborScope.GENERATED_AND_EXISTING,
                NeighborTopology.ORTHOGONAL_6
        );
        var rules = new ProceduralRuleSet<>(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 100.0, "b", 1.0))),
                List.of(constraint),
                2,
                Optional.of("b")
        );
        var request = new GenerationRequest<>(
                1L,
                line(1),
                rules,
                position -> Optional.of("world"),
                10,
                () -> false
        );

        var result = ProceduralGenerator.generate(request);

        assertTrue(result.isSuccess(), () -> result.failure().toString());
        assertEquals("B", result.placements().get(new GridPosition(0, 0, 0)));
        assertFalse(new ForbiddenAdjacencyConstraint<String>(
                Set.of(new ForbiddenAdjacencyConstraint.Pair("a", "world")),
                NeighborScope.GENERATED_ONLY,
                NeighborTopology.ORTHOGONAL_6
        ).validate(Set.of("a", "b")).isEmpty());
    }

    @Test
    void repairPassResolvesConstraintAgainstFutureNeighbor() {
        var constraint = new AllowedDirectionalNeighborConstraint<String>(
                "b",
                NeighborDirection.EAST,
                Set.of("a"),
                NeighborScope.GENERATED_ONLY,
                AllowedDirectionalNeighborConstraint.UnresolvedBehavior.ALLOW
        );
        var rules = new ProceduralRuleSet<>(
                List.of(B, A),
                List.of(new WeightedSource<>(Map.of("b", 1.0, "a", 0.001))),
                List.of(constraint),
                2,
                Optional.of("a"),
                3
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(19L, line(20), rules)
        );

        assertTrue(result.isSuccess(), () -> result.failure().toString());
        for (int x = 0; x < 19; x++) {
            if ("B".equals(result.placements().get(new GridPosition(x, 0, 0)))) {
                assertEquals(
                        "A",
                        result.placements().get(new GridPosition(x + 1, 0, 0))
                );
            }
        }
        assertEquals(
                "A",
                result.placements().get(new GridPosition(19, 0, 0)),
                "The final cell has no east neighbor and may not contain B"
        );
    }

    @Test
    void directionalRuleDoesNotTreatMissingNeighborAsUnresolvedFuture() {
        var constraint = new AllowedDirectionalNeighborConstraint<String>(
                "b",
                NeighborDirection.EAST,
                Set.of("a"),
                NeighborScope.GENERATED_ONLY,
                AllowedDirectionalNeighborConstraint.UnresolvedBehavior.ALLOW
        );
        var rules = new ProceduralRuleSet<>(
                List.of(B, A),
                List.of(new WeightedSource<>(Map.of("b", 100.0, "a", 1.0))),
                List.of(constraint),
                2,
                Optional.of("a"),
                2
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(19L, line(1), rules)
        );

        assertTrue(result.isSuccess(), () -> result.failure().toString());
        assertEquals("A", result.placements().get(new GridPosition(0, 0, 0)));
    }

    @Test
    void checkerMaskRestrictsSelectedCells() {
        var mask = new MaskedWeightSource<String>(
                "Checker accents",
                Coordinate.X,
                0.0,
                1.0,
                false,
                Set.of("a"),
                MaskedWeightSource.Mode.ONLY,
                1.0,
                MaskedWeightSource.Shape.CHECKER,
                1,
                1
        );
        var rules = new ProceduralRuleSet<>(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 1.0, "b", 1.0)), mask),
                List.of(),
                2,
                Optional.of("a")
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(3L, line(100), rules)
        );

        assertTrue(result.isSuccess());
        for (int x = 0; x < 100; x += 2) {
            assertEquals(
                    "A",
                    result.placements().get(new GridPosition(x, 0, 0))
            );
        }
    }

    @Test
    void worldAnchoredBandPhaseDoesNotResetAtSelectionMinimum() {
        var worldBand = new MaskedWeightSource<String>(
                "World bands",
                Coordinate.X,
                0.0,
                0.1,
                false,
                Set.of("a"),
                MaskedWeightSource.Mode.EXCLUDE,
                1.0,
                MaskedWeightSource.Shape.BANDS,
                4,
                1,
                true
        );
        var relativeBand = new MaskedWeightSource<String>(
                "Relative bands",
                Coordinate.X,
                0.0,
                0.1,
                false,
                Set.of("a"),
                MaskedWeightSource.Mode.EXCLUDE,
                1.0,
                MaskedWeightSource.Shape.BANDS,
                4,
                1,
                false
        );
        var candidates = List.of(A, B);
        var worldAtFour = new double[]{1.0, 1.0};
        var worldAtFive = new double[]{1.0, 1.0};
        var relativeAtFour = new double[]{1.0, 1.0};
        var relativeAtFive = new double[]{1.0, 1.0};

        worldBand.apply(selectionStartContext(4), candidates, worldAtFour);
        worldBand.apply(selectionStartContext(5), candidates, worldAtFive);
        relativeBand.apply(
                selectionStartContext(4),
                candidates,
                relativeAtFour
        );
        relativeBand.apply(
                selectionStartContext(5),
                candidates,
                relativeAtFive
        );

        assertEquals(0.0, worldAtFour[0]);
        assertEquals(1.0, worldAtFive[0]);
        assertEquals(0.0, relativeAtFour[0]);
        assertEquals(0.0, relativeAtFive[0]);
    }

    @Test
    void cleanupUsesCompletedSnapshotAndAppliesSimultaneously() {
        var cleanup = new NeighborhoodReplacementRule<String>(
                Set.of("b"),
                Set.of("a"),
                "a",
                2,
                2,
                NeighborTopology.ORTHOGONAL_6,
                NeighborScope.GENERATED_ONLY
        );
        var rules = new ProceduralRuleSet<>(
                List.of(A, B),
                List.of(new SequenceSource<>(0, 1.0, 0.0)),
                List.of(),
                2,
                Optional.of("a"),
                0,
                List.of(cleanup),
                1
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(0L, line(3), rules)
        );

        assertTrue(result.isSuccess());
        assertEquals(List.of("A", "A", "A"), result.traversal().stream()
                .map(result.placements()::get)
                .toList());
    }

    @Test
    void neighborhoodMinimumIsCheckedAfterAllCellsResolve() {
        var rule = new NeighborhoodCountConstraint<String>(
                "a",
                Set.of("b"),
                1,
                2,
                NeighborTopology.ORTHOGONAL_6,
                NeighborScope.GENERATED_ONLY
        );
        var rules = new ProceduralRuleSet<>(
                List.of(A, B),
                List.of(new WeightedSource<>(Map.of("a", 100.0, "b", 1.0))),
                List.of(rule),
                2,
                Optional.of("b"),
                3
        );

        var result = ProceduralGenerator.generate(
                GenerationRequest.create(999L, line(30), rules)
        );

        assertTrue(result.isSuccess(), () -> result.failure().toString());
        for (int x = 0; x < 30; x++) {
            if (!"A".equals(result.placements().get(new GridPosition(x, 0, 0)))) {
                continue;
            }
            boolean hasB = (x > 0 && "B".equals(
                    result.placements().get(new GridPosition(x - 1, 0, 0))
            )) || (x < 29 && "B".equals(
                    result.placements().get(new GridPosition(x + 1, 0, 0))
            ));
            assertTrue(hasB);
        }
    }

    private static List<GridPosition> line(int length) {
        var result = new ArrayList<GridPosition>(length);
        for (int x = 0; x < length; x++) {
            result.add(new GridPosition(x, 0, 0));
        }
        return result;
    }

    private static GenerationContext<String> selectionStartContext(int minX) {
        var start = new GridPosition(minX, 0, 0);
        var end = new GridPosition(minX + 1, 0, 0);
        return new GenerationContext<>(
                0L,
                start,
                0,
                2,
                GenerationBounds.enclosing(List.of(start, end)),
                Set.of(start, end),
                Map.of(),
                ExistingNeighborLookup.NONE
        );
    }
}
