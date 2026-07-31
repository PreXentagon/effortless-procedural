package dev.huskuraft.effortless.client.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralAdvancedConfig;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;

class SplineMaterialResolverTest {

    @Test
    void blankLinksUseMainPatternAndNoDamageOverlay() {
        var spline = ProceduralPatternPreset.DEFAULT;
        var library = new ProceduralPatternLibrary(
                true, spline.id(), List.of(spline)
        );

        var result = SplineMaterialResolver.resolve(library, spline);

        assertTrue(result.isSuccess());
        for (var role : List.of(
                RoadCell.Role.SURFACE, RoadCell.Role.SHOULDER,
                RoadCell.Role.FOUNDATION, RoadCell.Role.CURB,
                RoadCell.Role.MARKING
        )) {
            assertEquals(spline, result.recipes().get(role));
        }
        assertTrue(result.damageRecipe().isEmpty());
    }

    @Test
    void allLinkedRegionsResolveFromLargeLibraryStyleIds() {
        var linked = ProceduralPatternPreset.DEFAULT.duplicate()
                .withName("Linked road recipe");
        String id = linked.id().toString();
        var links = new SplineMaterialLinks(id, id, id, id, id, id);
        var spline = ProceduralPatternPreset.DEFAULT.withAdvanced(
                ProceduralAdvancedConfig.DEFAULT.withRoadProfile(
                        RoadProfile.DEFAULT.withMaterialLinks(links)
                )
        );
        var library = new ProceduralPatternLibrary(
                true, spline.id(), List.of(spline, linked)
        );

        var result = SplineMaterialResolver.resolve(library, spline);

        assertTrue(result.isSuccess(), () -> result.errors().toString());
        for (var role : List.of(
                RoadCell.Role.SURFACE, RoadCell.Role.SHOULDER,
                RoadCell.Role.FOUNDATION, RoadCell.Role.CURB,
                RoadCell.Role.MARKING
        )) {
            assertEquals(linked, result.recipes().get(role));
        }
        assertEquals(linked, result.damageRecipe().orElseThrow());
    }

    @Test
    void missingDamageRecipeFailsBeforeVoxelAllocation() {
        var links = new SplineMaterialLinks(
                "", "", "", "", "", java.util.UUID.randomUUID().toString()
        );
        var spline = ProceduralPatternPreset.DEFAULT.withAdvanced(
                ProceduralAdvancedConfig.DEFAULT.withRoadProfile(
                        RoadProfile.DEFAULT.withMaterialLinks(links)
                )
        );
        var library = new ProceduralPatternLibrary(
                true, spline.id(), List.of(spline)
        );

        var result = SplineMaterialResolver.resolve(library, spline);

        assertFalse(result.isSuccess());
        assertTrue(result.errors().get(0).contains("damage"));
    }
}
