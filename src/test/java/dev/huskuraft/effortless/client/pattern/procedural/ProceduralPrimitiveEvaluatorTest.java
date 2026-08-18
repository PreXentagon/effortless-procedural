package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralCompositionLayer;

class ProceduralPrimitiveEvaluatorTest {

    @Test
    void archHasVerticalSidesAndRoundedCrown() {
        assertTrue(contains(
                ProceduralCompositionLayer.Primitive.ARCH,
                0.0, 1.0, 0.0
        ));
        assertTrue(contains(
                ProceduralCompositionLayer.Primitive.ARCH,
                0.0, 6.0, 0.0
        ));
        assertFalse(contains(
                ProceduralCompositionLayer.Primitive.ARCH,
                4.4, 6.0, 0.0
        ));
    }

    @Test
    void domeAndWedgeHavePredictableProfiles() {
        assertTrue(contains(
                ProceduralCompositionLayer.Primitive.DOME,
                0.0, 0.5, 0.0
        ));
        assertFalse(contains(
                ProceduralCompositionLayer.Primitive.DOME,
                4.0, 6.5, 0.0
        ));
        assertFalse(contains(
                ProceduralCompositionLayer.Primitive.WEDGE,
                0.0, 5.0, -3.5
        ));
        assertTrue(contains(
                ProceduralCompositionLayer.Primitive.WEDGE,
                0.0, 5.0, 3.5
        ));
    }

    @Test
    void insetMembershipSupportsHollowShells() {
        assertTrue(ProceduralPrimitiveEvaluator.contains(
                ProceduralCompositionLayer.Primitive.BOX,
                0.0, 2.5, 0.0,
                7, 7, 7, 1
        ));
        assertFalse(ProceduralPrimitiveEvaluator.contains(
                ProceduralCompositionLayer.Primitive.BOX,
                3.0, 0.5, 0.0,
                7, 7, 7, 1
        ));
    }

    @Test
    void pyramidTapersAndTorusKeepsItsCenterOpen() {
        assertTrue(contains(
                ProceduralCompositionLayer.Primitive.PYRAMID,
                3.0, 0.5, 3.0
        ));
        assertFalse(contains(
                ProceduralCompositionLayer.Primitive.PYRAMID,
                3.0, 6.5, 3.0
        ));
        assertFalse(contains(
                ProceduralCompositionLayer.Primitive.TORUS,
                0.0, 3.5, 0.0
        ));
        assertTrue(contains(
                ProceduralCompositionLayer.Primitive.TORUS,
                3.0, 3.5, 0.0
        ));
    }

    private static boolean contains(
            ProceduralCompositionLayer.Primitive primitive,
            double x,
            double y,
            double z
    ) {
        return ProceduralPrimitiveEvaluator.contains(
                primitive, x, y, z, 9, 7, 9, 0
        );
    }
}
