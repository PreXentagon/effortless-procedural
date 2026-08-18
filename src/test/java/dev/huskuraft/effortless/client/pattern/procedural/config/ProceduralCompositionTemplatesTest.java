package dev.huskuraft.effortless.client.pattern.procedural.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProceduralCompositionTemplatesTest {

    @Test
    void tunnelUsesOrderedShellThenCutoutOnOriginalPath() {
        var layers = ProceduralCompositionTemplates.tunnelShell();

        assertEquals(2, layers.size());
        assertEquals(
                ProceduralCompositionLayer.Operation.UNION,
                layers.get(0).operation()
        );
        assertTrue(layers.get(0).hollow());
        assertEquals(
                ProceduralCompositionLayer.Operation.SUBTRACT,
                layers.get(1).operation()
        );
        assertEquals(
                ProceduralCompositionLayer.Anchor.PATH,
                layers.get(1).anchor()
        );
        assertTrue(layers.get(0).validate().isEmpty());
        assertTrue(layers.get(1).validate().isEmpty());
    }

    @Test
    void roadsideTreesRemainOnOriginalPathAndUseIndependentSeeds() {
        var layers = ProceduralCompositionTemplates.roadsideTrees();

        assertEquals(2, layers.size());
        assertTrue(layers.stream().allMatch(layer ->
                layer.generator() == ProceduralCompositionLayer.Generator.TREE
                        && layer.anchor()
                        == ProceduralCompositionLayer.Anchor.PATH
                        && layer.safeVariation()
        ));
        assertEquals(-layers.get(0).offsetX(), layers.get(1).offsetX());
        assertTrue(layers.get(0).seedSalt() != layers.get(1).seedSalt());
    }

    @Test
    void roadDamageSeparatesMaterializedRimFromDeeperCutout() {
        var layers = ProceduralCompositionTemplates.roadDamage();

        assertEquals(2, layers.size());
        assertEquals(
                ProceduralCompositionLayer.Operation.UNION,
                layers.get(0).operation()
        );
        assertTrue(layers.get(0).hollow());
        assertEquals(
                ProceduralCompositionLayer.Operation.SUBTRACT,
                layers.get(1).operation()
        );
        assertTrue(layers.get(1).sizeY() > layers.get(0).sizeY());
        assertEquals(layers.get(0).spacing(), layers.get(1).spacing());
        assertTrue(layers.stream().allMatch(layer ->
                layer.anchor() == ProceduralCompositionLayer.Anchor.PATH
                        && layer.validate().isEmpty()
        ));
    }
}
