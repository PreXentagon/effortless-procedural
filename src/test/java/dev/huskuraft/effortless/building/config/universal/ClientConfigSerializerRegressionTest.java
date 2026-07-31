package dev.huskuraft.effortless.building.config.universal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.TestPlatformSupport;
import dev.huskuraft.effortless.building.config.BuilderConfig;
import dev.huskuraft.effortless.building.config.ClientConfig;
import dev.huskuraft.effortless.building.config.ClipboardConfig;
import dev.huskuraft.effortless.building.config.PatternConfig;
import dev.huskuraft.effortless.building.config.ProceduralSafetyConfig;
import dev.huskuraft.effortless.building.config.RenderConfig;
import dev.huskuraft.effortless.building.pattern.array.ArrayTransformer;
import dev.huskuraft.effortless.building.pattern.mirror.MirrorTransformer;
import dev.huskuraft.effortless.building.pattern.raidal.RadialTransformer;
import dev.huskuraft.effortless.building.pattern.randomize.ItemRandomizer;
import dev.huskuraft.universal.api.core.Axis;
import dev.huskuraft.universal.api.math.Vector3d;
import dev.huskuraft.universal.api.math.Vector3i;
import dev.huskuraft.universal.api.nightconfig.core.Config;
import dev.huskuraft.universal.api.text.Text;

class ClientConfigSerializerRegressionTest {

    @BeforeAll
    static void installPlainJvmTextFactory() throws ReflectiveOperationException {
        TestPlatformSupport.installPlainContentFactory();
    }

    @Test
    void roundTripPreservesAllStockTransformerPresetTypesAndBuilderSettings() {
        var array = new ArrayTransformer(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Text.empty(),
                new Vector3i(2, 3, 4),
                5
        );
        var mirror = new MirrorTransformer(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                Text.empty(),
                new Vector3d(1.5, 2.5, 3.5),
                Axis.Z,
                16
        );
        var radial = new RadialTransformer(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                Text.empty(),
                new Vector3d(4.5, 5.5, 6.5),
                Axis.X,
                7,
                23,
                41
        );
        var randomizer = new ItemRandomizer(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                Text.empty(),
                ItemRandomizer.Order.SEQUENCE,
                ItemRandomizer.Target.SINGLE,
                ItemRandomizer.Source.HOTBAR,
                List.of()
        );
        var original = new ClientConfig(
                new BuilderConfig(7, false),
                new RenderConfig(true, false, false, 2048, 128),
                new ProceduralSafetyConfig(
                        false,
                        8_000,
                        400_000,
                        256,
                        12,
                        900,
                        96
                ),
                new PatternConfig(List.of(array, mirror, radial, randomizer)),
                ClipboardConfig.DEFAULT,
                ClientConfig.DEFAULT.structureMap()
        );

        var serializer = new ClientConfigConfigSerializer();
        var restored = serializer.deserialize(serializer.serialize(original));

        assertEquals(7, restored.builderConfig().reservedToolDurability());
        assertEquals(
                original.proceduralSafetyConfig(),
                restored.proceduralSafetyConfig()
        );
        assertEquals(4, restored.patternConfig().transformerPreset().size());
        assertInstanceOf(ArrayTransformer.class, restored.patternConfig().transformerPreset().get(0));
        assertInstanceOf(MirrorTransformer.class, restored.patternConfig().transformerPreset().get(1));
        var restoredRadial = assertInstanceOf(
                RadialTransformer.class,
                restored.patternConfig().transformerPreset().get(2)
        );
        assertEquals(Axis.X, restoredRadial.axis());
        assertEquals(23, restoredRadial.radius());
        assertEquals(41, restoredRadial.length());
        var restoredRandomizer = assertInstanceOf(
                ItemRandomizer.class,
                restored.patternConfig().transformerPreset().get(3)
        );
        assertEquals(ItemRandomizer.Source.HOTBAR, restoredRandomizer.source());
    }

    @Test
    void legacyRadialPresetWithoutAxisUsesAValidDefault() {
        var legacy = Config.inMemory();
        legacy.set("id", "00000000-0000-0000-0000-000000000005");
        legacy.set("type", "radial");
        legacy.set("position", List.of(0.0, 0.0, 0.0));
        legacy.set("slices", 4);
        legacy.set("radius", 12);
        legacy.set("length", 64);

        var restored = assertInstanceOf(
                RadialTransformer.class,
                TransformerConfigSerializer.INSTANCE.deserialize(legacy)
        );

        assertEquals(Axis.Y, restored.axis());
        assertEquals(12, restored.radius());
    }

    @Test
    void legacyRandomizerWithoutSourceUsesCustomizeDefault() {
        var legacy = Config.inMemory();
        legacy.set("id", "00000000-0000-0000-0000-000000000006");
        legacy.set("type", "randomizer");
        legacy.set("order", "sequence");
        legacy.set("target", "single");
        legacy.set("chances", List.of());

        var restored = assertInstanceOf(
                ItemRandomizer.class,
                TransformerConfigSerializer.INSTANCE.deserialize(legacy)
        );

        assertEquals(ItemRandomizer.Source.CUSTOMIZE, restored.source());
    }

}
