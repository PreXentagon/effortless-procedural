package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralBlockEntry;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralAdvancedConfig;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralMaskLayer;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;

class ProceduralMaterialTest {

    @Test
    void adapterKeepsSkipAndEraserClientOnly() {
        var preset = ProceduralPatternPreset.DEFAULT
                .withBlocks(List.of(
                        ProceduralBlockEntry.weighted(
                                ProceduralMaterial.SKIP_ID,
                                1.0
                        ),
                        ProceduralBlockEntry.weighted(
                                ProceduralMaterial.ERASER_ID,
                                1.0
                        )
                ))
                .withFallbackItemId(ProceduralMaterial.SKIP_ID);

        var adaptation = ProceduralPresetAdapter.adapt(preset);

        assertTrue(adaptation.isSuccess(), () -> adaptation.errors().toString());
        var byId = adaptation.ruleSet().orElseThrow().candidates().stream()
                .collect(Collectors.toMap(
                        Candidate::id,
                        Candidate::value
                ));
        assertEquals(
                ProceduralMaterial.Kind.SKIP,
                byId.get(ProceduralMaterial.SKIP_ID).kind()
        );
        assertEquals(
                ProceduralMaterial.Kind.ERASER,
                byId.get(ProceduralMaterial.ERASER_ID).kind()
        );
        assertTrue(byId.values().stream()
                .allMatch(material -> material.blockItem() == null));
        assertEquals(
                Map.of(
                        ProceduralMaterial.SKIP_ID,
                        ProceduralMaterial.Kind.SKIP,
                        ProceduralMaterial.ERASER_ID,
                        ProceduralMaterial.Kind.ERASER
                ),
                byId.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().kind()
                ))
        );
    }

    @Test
    void ruleDrivenStructuralPlacementRequiresAnExplicitStructuralMask() {
        var preset = specialMaterialPreset().withAdvanced(
                ProceduralAdvancedConfig.DEFAULT
                        .withStructuralPlacementMode(
                                StructuralPlacementMode.RULE_DRIVEN
                        )
        );

        var adaptation = ProceduralPresetAdapter.adapt(preset);

        assertFalse(adaptation.isSuccess());
        assertTrue(adaptation.errors().stream().anyMatch(
                error -> error.contains("structural placement requires")
        ));
    }

    @Test
    void ruleDrivenStructuralPlacementAcceptsGeneratorIndependentTipMask() {
        var mask = new ProceduralMaskLayer(
                UUID.fromString("30150dd4-9c4c-4f2b-8c24-5ca72c39240a"),
                "Detail at limb tips",
                true,
                MaskedWeightSource.Shape.RANGE,
                Coordinate.TIP,
                0.65,
                1.0,
                false,
                List.of(ProceduralMaterial.ERASER_ID),
                MaskedWeightSource.Mode.MULTIPLY,
                3.0,
                4,
                1
        );
        var preset = specialMaterialPreset().withAdvanced(
                ProceduralAdvancedConfig.DEFAULT
                        .withStructuralPlacementMode(
                                StructuralPlacementMode.RULE_DRIVEN
                        )
                        .withMaskLayers(List.of(mask))
        );

        var adaptation = ProceduralPresetAdapter.adapt(preset);

        assertTrue(adaptation.isSuccess(), () -> adaptation.errors().toString());
        assertTrue(adaptation.ruleSet().orElseThrow().weightSources().stream()
                .anyMatch(MaskedWeightSource.class::isInstance));
    }

    private static ProceduralPatternPreset specialMaterialPreset() {
        return ProceduralPatternPreset.DEFAULT
                .withBlocks(List.of(
                        ProceduralBlockEntry.weighted(
                                ProceduralMaterial.SKIP_ID,
                                1.0
                        ),
                        ProceduralBlockEntry.weighted(
                                ProceduralMaterial.ERASER_ID,
                                1.0
                        )
                ))
                .withFallbackItemId(ProceduralMaterial.SKIP_ID);
    }
}
