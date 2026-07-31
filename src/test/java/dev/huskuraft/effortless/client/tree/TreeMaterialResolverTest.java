package dev.huskuraft.effortless.client.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralAdvancedConfig;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;

class TreeMaterialResolverTest {

    @Test
    void blankReferencesUseTheTreePatternForEveryRole() {
        var tree = ProceduralPatternPreset.DEFAULT;
        var library = new ProceduralPatternLibrary(
                true, tree.id(), List.of(tree)
        );

        var result = TreeMaterialResolver.resolve(library, tree);

        assertTrue(result.isSuccess());
        for (var role : TreeCell.Role.values()) {
            assertEquals(tree, result.recipes().get(role));
        }
    }

    @Test
    void componentRecipeResolvesButItsTreeGeometryIsNotFollowed() {
        var component = ProceduralPatternPreset.DEFAULT
                .withName("Sparse willow leaves")
                .withAdvanced(ProceduralAdvancedConfig.DEFAULT
                        .withTreeGeneration(TreeGenerationConfig
                                .forArchetype(TreeArchetype.GIANT_FANTASY)));
        component = new ProceduralPatternPreset(
                UUID.fromString("3028d9bb-e2c0-47d7-b090-a3f57dc79117"),
                component.name(), component.seed(), component.retryLimit(),
                component.fallbackItemId(), component.blocks(),
                component.sequenceEnabled(), component.sequenceOffset(),
                component.sequenceAlternateWeight(),
                component.gradientEnabled(), component.gradientCoordinate(),
                component.noiseEnabled(), component.noiseFrequency(),
                component.noiseSalt(), component.inspectExistingWorld(),
                component.forbiddenAdjacency(),
                component.preferredAdjacency(), component.maximumRunLength(),
                component.verticalRules(), component.advanced(),
                component.materialSource(), component.stockTransformers()
        );
        var generation = TreeGenerationConfig
                .forArchetype(TreeArchetype.OAK)
                .withRecipes("", "", component.id().toString(), "");
        var tree = ProceduralPatternPreset.DEFAULT.withAdvanced(
                ProceduralAdvancedConfig.DEFAULT
                        .withTreeGeneration(generation)
        );
        var library = new ProceduralPatternLibrary(
                true, tree.id(), List.of(tree, component)
        );

        var result = TreeMaterialResolver.resolve(library, tree);

        assertTrue(result.isSuccess());
        assertEquals(component, result.recipes().get(TreeCell.Role.CANOPY));
        assertEquals(
                TreeArchetype.OAK,
                tree.advanced().treeGeneration().archetype()
        );
    }

    @Test
    void missingComponentFailsBeforeGeometryAllocation() {
        var generation = TreeGenerationConfig.DEFAULT.withRecipes(
                "", "", UUID.randomUUID().toString(), ""
        );
        var tree = ProceduralPatternPreset.DEFAULT.withAdvanced(
                ProceduralAdvancedConfig.DEFAULT
                        .withTreeGeneration(generation)
        );
        var library = new ProceduralPatternLibrary(
                true, tree.id(), List.of(tree)
        );

        var result = TreeMaterialResolver.resolve(library, tree);

        assertFalse(result.isSuccess());
        assertTrue(result.errors().get(0).contains("foliage"));
    }
}
