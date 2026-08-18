package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralBlockEntry;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;

class ProceduralRecipeEngineTest {

    @Test
    void recipeBoundaryIsDeterministic() {
        var preset = ProceduralPatternPreset.DEFAULT
                .withBlocks(List.of(
                        ProceduralBlockEntry.weighted(
                                ProceduralMaterial.SKIP_ID, 1.0
                        ),
                        ProceduralBlockEntry.weighted(
                                ProceduralMaterial.ERASER_ID, 1.0
                        )
                ))
                .withFallbackItemId(ProceduralMaterial.SKIP_ID);
        var positions = List.of(
                new GridPosition(3, 0, 0),
                new GridPosition(0, 0, 0),
                new GridPosition(2, 0, 0),
                new GridPosition(1, 0, 0)
        );
        var request = ProceduralRecipeEngine.Request.preview(
                preset,
                "Test",
                918273L,
                positions,
                () -> false,
                GenerationProgress.NONE,
                CoordinateLookup.NONE
        );

        var first = ProceduralRecipeEngine.generate(request);
        var second = ProceduralRecipeEngine.generate(request);

        assertTrue(first.isSuccess(), first::formattedError);
        assertEquals(first, second);
        assertEquals(positions.size(), first.placements().size());
        assertEquals(
                positions.stream().sorted(GridPosition.TRAVERSAL_ORDER)
                        .toList(),
                first.traversal()
        );
    }

    @Test
    void recipeBoundaryPreservesSafetyFailureDetails() {
        var preset = ProceduralPatternPreset.DEFAULT
                .withBlocks(List.of(ProceduralBlockEntry.weighted(
                        ProceduralMaterial.SKIP_ID, 1.0
                )))
                .withFallbackItemId(ProceduralMaterial.SKIP_ID);
        var result = ProceduralRecipeEngine.generate(
                new ProceduralRecipeEngine.Request(
                        preset,
                        "Limited",
                        1L,
                        List.of(
                                new GridPosition(0, 0, 0),
                                new GridPosition(1, 0, 0)
                        ),
                        ExistingNeighborLookup.NONE,
                        1,
                        ProceduralGenerator.MAX_ESTIMATED_WORK,
                        () -> false,
                        GenerationProgress.NONE,
                        CoordinateLookup.NONE
                )
        );

        assertFalse(result.isSuccess());
        assertTrue(result.message().contains("Limited"));
        assertTrue(result.formattedError().contains("exceeding the limit"));
    }
}
