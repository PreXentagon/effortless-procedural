package dev.huskuraft.effortless.client.pattern.procedural.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.TestPlatformSupport;
import dev.huskuraft.effortless.building.pattern.Pattern;
import dev.huskuraft.effortless.building.pattern.array.ArrayTransformer;
import dev.huskuraft.effortless.building.pattern.randomize.ItemRandomizer;
import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;
import dev.huskuraft.effortless.client.pattern.procedural.CandidateQuotaRule;
import dev.huskuraft.effortless.client.pattern.procedural.CoordinateSpace;
import dev.huskuraft.effortless.client.pattern.procedural.GradientCurve;
import dev.huskuraft.effortless.client.pattern.procedural.GradientDistributionMode;
import dev.huskuraft.effortless.client.pattern.procedural.MaskedWeightSource;
import dev.huskuraft.effortless.client.pattern.procedural.MinimumSpacingConstraint;
import dev.huskuraft.effortless.client.pattern.procedural.NeighborDirection;
import dev.huskuraft.effortless.client.pattern.procedural.NeighborTopology;
import dev.huskuraft.effortless.client.pattern.procedural.SeedMode;
import dev.huskuraft.effortless.client.pattern.procedural.SpatialField;
import dev.huskuraft.universal.api.math.Vector3i;
import dev.huskuraft.universal.api.nightconfig.core.Config;
import dev.huskuraft.universal.api.text.Text;

class ProceduralPatternConfigSerializerTest {

    @BeforeAll
    static void installPlainJvmTextFactory() throws ReflectiveOperationException {
        TestPlatformSupport.installPlainContentFactory();
    }

    @Test
    void roundTripPreservesActivePresetAndAllRuleFields() {
        var preset = new ProceduralPatternPreset(
                UUID.fromString("d23098d1-bfce-40ac-807e-aec38e83462d"),
                "Layered masonry",
                99887766L,
                17,
                "minecraft:stone",
                List.of(new ProceduralBlockEntry(
                        "minecraft:stone",
                        4.5,
                        8.0,
                        0.25,
                        0.1,
                        2.5
                )),
                true,
                2,
                0.2,
                true,
                Coordinate.DISTANCE,
                true,
                0.075,
                1234L,
                true,
                List.of(new ProceduralForbiddenPair(
                        "minecraft:stone",
                        "minecraft:cobblestone"
                )),
                List.of(new ProceduralPreferredPair(
                        "minecraft:stone",
                        "minecraft:cobblestone",
                        3.0
                )),
                3,
                List.of(new ProceduralVerticalRule(
                        "minecraft:stone",
                        ProceduralVerticalRule.Direction.BELOW,
                        List.of("minecraft:cobblestone"),
                        true
                )),
                new ProceduralAdvancedConfig(
                        4,
                        NeighborTopology.ALL_26,
                        CoordinateSpace.WORLD,
                        SeedMode.WORLD_ANCHORED,
                        GradientDistributionMode.ORDERED_BANDS,
                        GradientCurve.STEPPED,
                        12,
                        List.of(new ProceduralMaskLayer(
                                UUID.fromString("55fc5e76-1ac5-42bd-b730-80b227eab5a4"),
                                "Stone shell",
                                true,
                                MaskedWeightSource.Shape.SURFACE,
                                Coordinate.DISTANCE,
                                0.2,
                                0.8,
                                true,
                                List.of("minecraft:stone"),
                                MaskedWeightSource.Mode.ONLY,
                                5.0,
                                7,
                                2
                        )),
                        List.of(new ProceduralDirectionalRule(
                                "minecraft:stone",
                                NeighborDirection.NORTH_EAST,
                                List.of("minecraft:cobblestone"),
                                true
                        )),
                        List.of(new ProceduralSpacingRule(
                                List.of("minecraft:stone"),
                                3,
                                MinimumSpacingConstraint.DistanceMetric.CHEBYSHEV
                        )),
                        List.of(new ProceduralNeighborCountRule(
                                "minecraft:stone",
                                List.of("minecraft:cobblestone"),
                                1,
                                5
                        )),
                        List.of(new ProceduralQuotaRule(
                                "minecraft:stone",
                                0.25,
                                0.75,
                                CandidateQuotaRule.Unit.FRACTION
                        )),
                        2,
                        List.of(new ProceduralCleanupRule(
                                List.of("minecraft:stone"),
                                List.of("minecraft:cobblestone"),
                                "minecraft:cobblestone",
                                0,
                                2
                        )),
                        "bb198a55-91e7-4565-948d-22a12ea49a35",
                        true,
                        true
                )
        );
        var array = new ArrayTransformer(
                UUID.fromString("00000000-0000-0000-0000-000000000081"),
                Text.text("Wall repeats"),
                new Vector3i(4, 0, 0),
                9
        );
        preset = preset.withMaterialSource(PatternMaterialSource.HOTBAR)
                .withStockTransformers(List.of(array));
        var original = new ProceduralPatternLibrary(true, preset.id(), List.of(preset));
        var serializer = new ProceduralPatternConfigSerializer();

        var restored = serializer.deserialize(serializer.serialize(original));

        assertEquals(original, restored);
    }

    @Test
    void formatOnePresetReceivesFormatTwoDefaults() {
        var serializer = new ProceduralPatternConfigSerializer();
        var legacy = serializer.serialize(ProceduralPatternLibrary.DEFAULT);
        legacy.set("formatVersion", 1);
        legacy.<List<Config>>get("presets").get(0).remove("advanced");

        var restored = serializer.deserialize(legacy);

        assertEquals(
                ProceduralAdvancedConfig.DEFAULT,
                restored.activePreset().orElseThrow().advanced()
        );
    }

    @Test
    void existingPresetWithoutGradientModeKeepsEndpointWeightBehavior() {
        var serializer = new ProceduralPatternConfigSerializer();
        var legacy = serializer.serialize(ProceduralPatternLibrary.DEFAULT);
        legacy.<List<Config>>get("presets").get(0)
                .<Config>get("advanced")
                .remove("gradientDistributionMode");

        var restored = serializer.deserialize(legacy);

        assertEquals(
                GradientDistributionMode.WEIGHTED_ENDPOINTS,
                restored.activePreset().orElseThrow()
                        .advanced()
                        .gradientDistributionMode()
        );
    }

    @Test
    void formatTwoPresetReceivesUnifiedWorkbenchDefaults() {
        var serializer = new ProceduralPatternConfigSerializer();
        var legacy = serializer.serialize(ProceduralPatternLibrary.DEFAULT);
        legacy.set("formatVersion", 2);
        var legacyPreset = legacy.<List<Config>>get("presets").get(0);
        legacyPreset.remove("materialSource");
        legacyPreset.remove("stockTransformers");

        var restored = serializer.deserialize(legacy);
        var preset = restored.activePreset().orElseThrow();

        assertEquals(PatternMaterialSource.CUSTOM_PALETTE, preset.materialSource());
        assertEquals(List.of(), preset.stockTransformers());
    }

    @Test
    void legacyStockPatternMigratesGeometryAndMaterialSource() {
        var array = new ArrayTransformer(
                UUID.fromString("00000000-0000-0000-0000-000000000091"),
                Text.text("Legacy array"),
                new Vector3i(3, 1, 0),
                6
        );
        var randomizer = new ItemRandomizer(
                UUID.fromString("00000000-0000-0000-0000-000000000092"),
                Text.text("Legacy hotbar"),
                ItemRandomizer.Order.RANDOM,
                ItemRandomizer.Target.SINGLE,
                ItemRandomizer.Source.HOTBAR,
                List.of()
        );

        var migrated = ProceduralPatternPreset.DEFAULT
                .withImportedStockPattern(new Pattern(
                        true,
                        List.of(array, randomizer)
                ));

        assertEquals(PatternMaterialSource.HOTBAR, migrated.materialSource());
        assertEquals(List.of(array), migrated.stockTransformers());
        assertEquals(false, migrated.sequenceEnabled());
    }

    @Test
    void unifiedPatternPersistsMoreThanFourOrderedTransforms() {
        var transforms = IntStream.range(0, 7)
                .mapToObj(index -> new ArrayTransformer(
                        new UUID(0L, 200L + index),
                        Text.text("Array " + index),
                        new Vector3i(index + 1, index, 0),
                        index + 2
                ))
                .toList();
        var preset = ProceduralPatternPreset.DEFAULT
                .withStockTransformers(transforms);
        var library = new ProceduralPatternLibrary(
                true,
                preset.id(),
                List.of(preset)
        );
        var serializer = new ProceduralPatternConfigSerializer();

        var restored = serializer.deserialize(serializer.serialize(library));

        assertEquals(
                transforms,
                restored.activePreset().orElseThrow().stockTransformers()
        );
    }

    @Test
    void reusableSpatialAssetRoundTripsAndResolvesForRuntime() {
        var asset = new ProceduralFieldAsset(
                UUID.fromString("c8e2d77d-f110-4e72-ae86-c13ff8f9da94"),
                "Diagonal weathering",
                SpatialField.linear(Coordinate.X)
                        .withShape(SpatialField.Shape.POLYGON)
                        .withRotation(30.0)
                        .withPolygonSides(5)
                        .withWarp(0.4, 0.08, 99L),
                ProceduralNoiseConfig.DEFAULT
                        .withFractal(3, 0.55, 2.1)
                        .withWarp(2.0, 0.05, 123L)
        );
        var advanced = ProceduralAdvancedConfig.DEFAULT
                .withGradientFieldAsset(asset.id().toString())
                .withNoiseFieldAsset(asset.id().toString());
        var preset = ProceduralPatternPreset.DEFAULT.withAdvanced(advanced);
        var original = new ProceduralPatternLibrary(
                true,
                preset.id(),
                List.of(preset),
                List.of(asset)
        );
        var serializer = new ProceduralPatternConfigSerializer();

        var restored = serializer.deserialize(serializer.serialize(original));
        var resolved = restored.resolvedActivePreset().preset().orElseThrow();

        assertEquals(original, restored);
        assertEquals(asset.gradientField(), resolved.advanced().gradientField());
        assertEquals(asset.noiseConfig(), resolved.advanced().noiseConfig());
    }
}
