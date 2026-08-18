package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralAdvancedConfig;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralCompositionLayer;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.road.RoadPoint;
import dev.huskuraft.effortless.client.road.RoadSpline;

class ProceduralCompositionEngineTest {

    @Test
    void orderedBooleanOperationsProduceExplicitEraseAndSkip() {
        var union = ProceduralCompositionLayer.defaultLayer()
                .withName("Wide base")
                .withSizes(3, 1, 1);
        var subtract = ProceduralCompositionLayer.defaultLayer()
                .withName("Center cut")
                .withOperation(ProceduralCompositionLayer.Operation.SUBTRACT)
                .withSizes(1, 1, 1);
        var skip = ProceduralCompositionLayer.defaultLayer()
                .withName("Preserved edge")
                .withOperation(ProceduralCompositionLayer.Operation.SKIP)
                .withOffsetX(-1.0)
                .withSizes(1, 1, 1);
        var preset = ProceduralPatternPreset.DEFAULT.withAdvanced(
                ProceduralAdvancedConfig.DEFAULT.withCompositionLayers(
                        List.of(union, subtract, skip)
                )
        );
        var library = new ProceduralPatternLibrary(
                true, preset.id(), List.of(preset)
        );

        var result = ProceduralCompositionEngine.compose(
                new LinkedHashSet<>(List.of(new GridPosition(0, 0, 0))),
                List.of(), library, preset, 100
        );

        assertTrue(result.isSuccess(), () -> String.join("; ", result.errors()));
        assertEquals(
                List.of(new GridPosition(0, 0, 0), new GridPosition(1, 0, 0)),
                result.positions()
        );
        assertEquals(
                java.util.Set.of(new GridPosition(0, 0, 0)),
                result.erasers()
        );
        assertEquals(
                java.util.Set.of(new GridPosition(-1, 0, 0)),
                result.skipped()
        );
    }

    @Test
    void generatedTreePathsCanAnchorLaterDeterministicLayers() {
        var treeRecipe = ProceduralPatternPreset.DEFAULT.duplicate()
                .withName("Tree recipe");
        var markerRecipe = ProceduralPatternPreset.DEFAULT.duplicate()
                .withName("Branch marker");
        var tree = ProceduralCompositionLayer.defaultLayer()
                .withName("Tree")
                .withGenerator(ProceduralCompositionLayer.Generator.TREE)
                .withAnchor(ProceduralCompositionLayer.Anchor.BOUNDS_TOP)
                .withPresetId(treeRecipe.id().toString())
                .withMaximumInstances(1);
        var markers = ProceduralCompositionLayer.defaultLayer()
                .withName("Along generated limbs")
                .withAnchor(
                        ProceduralCompositionLayer.Anchor.GENERATED_PATH
                )
                .withPresetId(markerRecipe.id().toString())
                .withSizes(1, 1, 1)
                .withSpacing(3.0)
                .withMaximumInstances(64);
        var root = ProceduralPatternPreset.DEFAULT.duplicate()
                .withName("Composed tree")
                .withAdvanced(
                        ProceduralAdvancedConfig.DEFAULT
                                .withCompositionLayers(List.of(tree, markers))
                );
        var library = new ProceduralPatternLibrary(
                true,
                root.id(),
                List.of(root, treeRecipe, markerRecipe)
        );
        var base = new LinkedHashSet<>(
                List.of(new GridPosition(0, 0, 0))
        );

        var first = ProceduralCompositionEngine.compose(
                base, List.of(), library, root, 20_000
        );
        var second = ProceduralCompositionEngine.compose(
                base, List.of(), library, root, 20_000
        );

        assertTrue(first.isSuccess(), () -> String.join("; ", first.errors()));
        assertEquals(first.positions(), second.positions());
        assertEquals(first.erasers(), second.erasers());
        assertEquals(
                first.additions().entrySet().stream()
                        .map(entry -> entry.getKey() + ":"
                                + entry.getValue().preset().id())
                        .toList(),
                second.additions().entrySet().stream()
                        .map(entry -> entry.getKey() + ":"
                                + entry.getValue().preset().id())
                        .toList()
        );
        assertTrue(first.additions().values().stream().anyMatch(cell ->
                cell.preset().id().equals(markerRecipe.id())
        ));
    }

    @Test
    void originalPathAnchorDoesNotRecurseOntoGeneratedTreeLimbs() {
        var treeRecipe = ProceduralPatternPreset.DEFAULT.duplicate()
                .withName("Tree recipe");
        var markerRecipe = ProceduralPatternPreset.DEFAULT.duplicate()
                .withName("Road marker");
        var tree = ProceduralCompositionLayer.defaultLayer()
                .withName("Roadside tree")
                .withGenerator(ProceduralCompositionLayer.Generator.TREE)
                .withAnchor(ProceduralCompositionLayer.Anchor.PATH)
                .withPresetId(treeRecipe.id().toString())
                .withMaximumInstances(1);
        var markers = ProceduralCompositionLayer.defaultLayer()
                .withName("Original road only")
                .withAnchor(ProceduralCompositionLayer.Anchor.PATH)
                .withPresetId(markerRecipe.id().toString())
                .withSizes(1, 1, 1)
                .withSpacing(1.0)
                .withMaximumInstances(16);
        var root = ProceduralPatternPreset.DEFAULT.duplicate()
                .withAdvanced(ProceduralAdvancedConfig.DEFAULT
                        .withCompositionLayers(List.of(tree, markers)));
        var library = new ProceduralPatternLibrary(
                true, root.id(), List.of(root, treeRecipe, markerRecipe)
        );
        var tangent = new RoadPoint(1.0, 0.0, 0.0);
        var path = List.of(
                new RoadSpline.Sample(
                        new RoadPoint(0.0, 0.0, 0.0), tangent,
                        0.0, 0, 0.0
                ),
                new RoadSpline.Sample(
                        new RoadPoint(8.0, 0.0, 0.0), tangent,
                        1.0, 0, 1.0
                )
        );

        var result = ProceduralCompositionEngine.compose(
                new LinkedHashSet<>(List.of(new GridPosition(0, 0, 0))),
                path, library, root, 20_000
        );

        assertTrue(result.isSuccess(), () -> String.join("; ", result.errors()));
        var markerPositions = result.additions().entrySet().stream()
                .filter(entry -> entry.getValue().preset().id()
                        .equals(markerRecipe.id()))
                .map(java.util.Map.Entry::getKey)
                .toList();
        assertFalse(markerPositions.isEmpty());
        assertTrue(markerPositions.stream().allMatch(position ->
                position.y() == 0
        ));
    }

    @Test
    void pathSpacingIsIndependentOfInputSampleDensity() {
        var tangent = new RoadPoint(1.0, 0.0, 0.0);
        var sparse = List.of(
                new RoadSpline.Sample(
                        new RoadPoint(0.0, 0.0, 0.0), tangent,
                        0.0, 0, 0.0
                ),
                new RoadSpline.Sample(
                        new RoadPoint(10.0, 0.0, 0.0), tangent,
                        1.0, 0, 1.0
                )
        );

        var samples = ProceduralCompositionEngine.resamplePath(sparse, 2.0);

        assertEquals(
                List.of(0.0, 2.0, 4.0, 6.0, 8.0, 10.0),
                samples.stream().map(sample -> sample.point().x()).toList()
        );
    }

    @Test
    void positionLimitFailsBeforeReturningPartialOutput() {
        var huge = ProceduralCompositionLayer.defaultLayer()
                .withSizes(20, 20, 20);
        var preset = ProceduralPatternPreset.DEFAULT.withAdvanced(
                ProceduralAdvancedConfig.DEFAULT
                        .withCompositionLayers(List.of(huge))
        );
        var library = new ProceduralPatternLibrary(
                true, preset.id(), List.of(preset)
        );

        var result = ProceduralCompositionEngine.compose(
                new LinkedHashSet<>(List.of(new GridPosition(0, 0, 0))),
                List.of(), library, preset, 100
        );

        assertFalse(result.isSuccess());
        assertTrue(result.positions().isEmpty());
        assertTrue(result.errors().stream().anyMatch(error ->
                error.contains("limit")
        ));
    }

    @Test
    void extremeHollowPrimitiveFailsBeforeScanningItsBoundingVolume() {
        var huge = ProceduralCompositionLayer.defaultLayer()
                .withSizes(4096, 4096, 4096)
                .withHollow(true);
        var preset = ProceduralPatternPreset.DEFAULT.withAdvanced(
                ProceduralAdvancedConfig.DEFAULT
                        .withCompositionLayers(List.of(huge))
        );
        var library = new ProceduralPatternLibrary(
                true, preset.id(), List.of(preset)
        );

        var result = ProceduralCompositionEngine.compose(
                new LinkedHashSet<>(List.of(new GridPosition(0, 0, 0))),
                List.of(), library, preset, 1_000
        );

        assertFalse(result.isSuccess());
        assertTrue(result.errors().stream().anyMatch(error ->
                error.contains("scan work")
        ));
    }

    @Test
    void cancelledCompositionReturnsNoPartialOutput() {
        var layer = ProceduralCompositionLayer.defaultLayer()
                .withSizes(20, 20, 20);
        var preset = ProceduralPatternPreset.DEFAULT.withAdvanced(
                ProceduralAdvancedConfig.DEFAULT
                        .withCompositionLayers(List.of(layer))
        );
        var library = new ProceduralPatternLibrary(
                true, preset.id(), List.of(preset)
        );

        var result = ProceduralCompositionEngine.compose(
                new LinkedHashSet<>(List.of(new GridPosition(0, 0, 0))),
                List.of(), library, preset, 100_000, () -> true
        );

        assertFalse(result.isSuccess());
        assertTrue(result.positions().isEmpty());
        assertTrue(result.errors().getFirst().contains("cancelled"));
    }

    @Test
    void excessiveLayerCountIsRejectedBeforeGeneration() {
        var layers = java.util.stream.IntStream.rangeClosed(
                        0, ProceduralCompositionEngine.MAXIMUM_LAYERS
                )
                .mapToObj(index -> ProceduralCompositionLayer.defaultLayer()
                        .withName("Layer " + index))
                .toList();
        var preset = ProceduralPatternPreset.DEFAULT.withAdvanced(
                ProceduralAdvancedConfig.DEFAULT
                        .withCompositionLayers(layers)
        );
        var library = new ProceduralPatternLibrary(
                true, preset.id(), List.of(preset)
        );

        var result = ProceduralCompositionEngine.compose(
                new LinkedHashSet<>(List.of(new GridPosition(0, 0, 0))),
                List.of(), library, preset, 100_000
        );

        assertFalse(result.isSuccess());
        assertTrue(result.errors().getFirst().contains("above the limit"));
    }

    @Test
    void fiftyOrderedLayersRemainSupportedAndDeterministic() {
        var layers = java.util.stream.IntStream.range(0, 50)
                .mapToObj(index -> ProceduralCompositionLayer.defaultLayer()
                        .withName("Layer " + index)
                        .withOperation(index % 2 == 0
                                ? ProceduralCompositionLayer.Operation.UNION
                                : ProceduralCompositionLayer.Operation.REPLACE))
                .toList();
        var preset = ProceduralPatternPreset.DEFAULT.withAdvanced(
                ProceduralAdvancedConfig.DEFAULT
                        .withCompositionLayers(layers)
        );
        var library = new ProceduralPatternLibrary(
                true, preset.id(), List.of(preset)
        );
        var base = new LinkedHashSet<>(
                List.of(new GridPosition(0, 0, 0))
        );

        var first = ProceduralCompositionEngine.compose(
                base, List.of(), library, preset, 10_000
        );
        var second = ProceduralCompositionEngine.compose(
                base, List.of(), library, preset, 10_000
        );

        assertTrue(first.isSuccess(), () -> String.join("; ", first.errors()));
        assertEquals(first.positions(), second.positions());
        assertEquals(first.additions(), second.additions());
        assertEquals(first.erasers(), second.erasers());
        assertEquals(first.skipped(), second.skipped());
    }
}
