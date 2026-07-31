package dev.huskuraft.effortless.screen.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.building.structure.BuildMode;
import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralAdvancedConfig;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.road.RoadProfile;
import dev.huskuraft.effortless.client.road.SplineSubtype;
import dev.huskuraft.effortless.client.tree.TreeArchetype;
import dev.huskuraft.effortless.client.tree.TreeCell;
import dev.huskuraft.effortless.client.tree.TreeGenerationConfig;

class ProceduralRoadPreviewTest {

    @Test
    void toolbarContainsEveryStockModeAndClientOnlyAuthoringShapes() {
        var stockModes = ProceduralPreviewType.ALL.stream()
                .filter(type -> !type.isClientOnly())
                .map(ProceduralPreviewType::stockMode)
                .toList();
        var expected = Arrays.stream(BuildMode.values())
                .filter(BuildMode::isEnabled)
                .toList();

        assertEquals(expected, stockModes);
        assertTrue(ProceduralPreviewType.ALL.contains(
                ProceduralPreviewType.ROAD
        ));
        assertTrue(ProceduralPreviewType.ALL.contains(
                ProceduralPreviewType.TREE
        ));
        assertThrows(
                IllegalStateException.class,
                ProceduralPreviewType.ROAD::stockMode
        );
        assertThrows(
                IllegalStateException.class,
                ProceduralPreviewType.TREE::stockMode
        );
        for (var type : ProceduralPreviewType.ALL) {
            assertFalse(PreviewOrientation.choices(type).isEmpty());
            assertTrue(
                    PreviewOrientation.choices(type)
                            .contains(PreviewOrientation.defaultFor(type))
            );
            assertFalse(PreviewOrientation.kind(type).isBlank());
        }
    }

    @Test
    void roadPreviewIsCurvedSparseGeometryWithRoadCoordinates() {
        var geometry = ProceduralPreviewWidget.roadGeometry(
                RoadProfile.DEFAULT
        );
        var positions = geometry.positions();

        assertFalse(positions.isEmpty());
        int sizeX = positions.stream().mapToInt(position -> position.x())
                .max().orElseThrow() + 1;
        int sizeY = positions.stream().mapToInt(position -> position.y())
                .max().orElseThrow() + 1;
        int sizeZ = positions.stream().mapToInt(position -> position.z())
                .max().orElseThrow() + 1;
        assertTrue(sizeX > RoadProfile.DEFAULT.totalWidth());
        assertTrue(sizeZ > RoadProfile.DEFAULT.totalWidth());
        assertTrue(
                positions.size() < (long) sizeX * sizeY * sizeZ,
                "The road preview must not collapse into a filled rectangle"
        );

        double minimumPath = positions.stream()
                .mapToDouble(position -> geometry.coordinates()
                        .sample(Coordinate.PATH, position)
                        .orElseThrow())
                .min()
                .orElseThrow();
        double maximumPath = positions.stream()
                .mapToDouble(position -> geometry.coordinates()
                        .sample(Coordinate.PATH, position)
                        .orElseThrow())
                .max()
                .orElseThrow();
        assertTrue(minimumPath <= 0.01);
        assertTrue(maximumPath >= 0.99);
    }

    @Test
    void treePreviewExposesAllGuidedComponentCoordinates() {
        var geometry = ProceduralPreviewWidget.treeGeometry(
                TreeGenerationConfig.forArchetype(TreeArchetype.OAK),
                0L
        );

        assertFalse(geometry.positions().isEmpty());
        var lateralValues = geometry.positions().stream()
                .mapToDouble(position -> geometry.coordinates()
                        .sample(Coordinate.LATERAL, position)
                        .orElseThrow())
                .distinct()
                .boxed()
                .toList();
        assertTrue(lateralValues.contains(0.0));
        assertTrue(lateralValues.contains(0.35));
        assertTrue(lateralValues.contains(0.68));
        assertTrue(lateralValues.contains(1.0));
    }

    @Test
    void generatedTreePreviewRetainsComponentRolesForMaterialRecipes() {
        var geometry = ProceduralPreviewWidget.treeGeometry(
                TreeGenerationConfig.forArchetype(TreeArchetype.MANGROVE),
                86420L
        );

        assertEquals(
                geometry.positions().size(),
                geometry.treeRoles().size()
        );
        assertTrue(geometry.treeRoles().containsValue(TreeCell.Role.TRUNK));
        assertTrue(geometry.treeRoles().containsValue(TreeCell.Role.BRANCH));
        assertTrue(geometry.treeRoles().containsValue(TreeCell.Role.CANOPY));
        assertTrue(geometry.treeRoles().containsValue(TreeCell.Role.ROOT));
    }

    @Test
    void previewSplineSubtypeRetainsTunedGeometry() {
        var tuned = new RoadProfile(
                13, 5, 3, 0.67, 0.15, SplineSubtype.CUSTOM
        );
        var preset = ProceduralPatternPreset.DEFAULT.withAdvanced(
                ProceduralAdvancedConfig.DEFAULT.withRoadProfile(tuned)
        );

        var preview = ProceduralPreviewWidget.applyPreviewSubtype(
                preset,
                ProceduralPreviewType.ROAD,
                SplineSubtype.CROWNED_ROAD
        ).advanced().roadProfile();

        assertEquals(SplineSubtype.CROWNED_ROAD, preview.subtype());
        assertEquals(tuned.surfaceWidth(), preview.surfaceWidth());
        assertEquals(tuned.thickness(), preview.thickness());
        assertEquals(tuned.shoulderWidth(), preview.shoulderWidth());
        assertEquals(tuned.tension(), preview.tension());
        assertEquals(tuned.sampleSpacing(), preview.sampleSpacing());
    }

    @Test
    void previewTreeArchetypeRetainsTunedGeometry() {
        var tuned = TreeGenerationConfig
                .forArchetype(TreeArchetype.WILLOW)
                .withHeightRange(39, 43)
                .withBranchRange(31, 37)
                .withRadii(5, 2, 13);
        var preset = ProceduralPatternPreset.DEFAULT.withAdvanced(
                ProceduralAdvancedConfig.DEFAULT.withTreeGeneration(tuned)
        );

        var preview = ProceduralPreviewWidget.applyPreviewSubtype(
                preset,
                ProceduralPreviewType.TREE,
                TreeArchetype.ACACIA
        ).advanced().treeGeneration();

        assertEquals(TreeArchetype.ACACIA, preview.archetype());
        assertEquals(tuned.minimumHeight(), preview.minimumHeight());
        assertEquals(tuned.maximumHeight(), preview.maximumHeight());
        assertEquals(tuned.minimumBranches(), preview.minimumBranches());
        assertEquals(tuned.maximumBranches(), preview.maximumBranches());
        assertEquals(tuned.baseRadius(), preview.baseRadius());
        assertEquals(tuned.tipRadius(), preview.tipRadius());
        assertEquals(tuned.crownRadius(), preview.crownRadius());
    }

    @Test
    void roadAndTreeGeometryRespondToTunedValues() {
        var narrow = ProceduralPreviewWidget.roadGeometry(
                new RoadProfile(3, 1, 0, 0.0, 0.25)
        );
        var wide = ProceduralPreviewWidget.roadGeometry(
                new RoadProfile(13, 4, 2, 0.0, 0.25)
        );
        assertTrue(wide.positions().size() > narrow.positions().size());

        var shortTree = ProceduralPreviewWidget.treeGeometry(
                TreeGenerationConfig.forArchetype(TreeArchetype.OAK)
                        .withHeightRange(10, 10),
                412L
        );
        var tallTree = ProceduralPreviewWidget.treeGeometry(
                TreeGenerationConfig.forArchetype(TreeArchetype.OAK)
                        .withHeightRange(34, 34),
                412L
        );
        assertNotEquals(shortTree.positions(), tallTree.positions());
        assertTrue(maxY(tallTree) > maxY(shortTree));
    }

    @Test
    void everyTreeSubtypeUsesAnExistingVanillaBlockTexturePath() {
        for (var archetype : TreeArchetype.values()) {
            assertTrue(
                    archetype.getIcon().getString().startsWith(
                            "minecraft:textures/block/"
                    ),
                    archetype.name()
            );
        }
    }

    private static int maxY(ProceduralPreviewWidget.PreviewGeometry geometry) {
        return geometry.positions().stream()
                .mapToInt(position -> position.y())
                .max()
                .orElseThrow();
    }
}
