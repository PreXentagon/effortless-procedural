package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class StructuralPlacementWeightSourceTest {

    @Test
    void coreAndJunctionsKeepFullStructuralBlocks() {
        double[] weights = {1.0, 1.0, 1.0, 1.0};

        StructuralPlacementWeightSource.adjustWeights(
                List.of(
                        StructuralMaterialClassifier.Kind.PILLAR,
                        StructuralMaterialClassifier.Kind.STAIR,
                        StructuralMaterialClassifier.Kind.SLAB,
                        StructuralMaterialClassifier.Kind.CONNECTOR
                ),
                weights,
                0.15,
                0.8,
                0.4,
                0.2,
                0.8
        );

        assertTrue(weights[0] > 1.0);
        assertEquals(0.0, weights[1]);
        assertEquals(0.0, weights[2]);
        assertEquals(0.0, weights[3]);
    }

    @Test
    void slopedShellFavorsStairsAndSlabs() {
        double[] weights = {1.0, 1.0, 1.0};

        StructuralPlacementWeightSource.adjustWeights(
                List.of(
                        StructuralMaterialClassifier.Kind.PILLAR,
                        StructuralMaterialClassifier.Kind.STAIR,
                        StructuralMaterialClassifier.Kind.SLAB
                ),
                weights,
                0.92,
                0.7,
                0.8,
                0.25,
                0.0
        );

        assertEquals(1.0, weights[0]);
        assertTrue(weights[1] > weights[0]);
        assertTrue(weights[2] > weights[0]);
    }

    @Test
    void connectorsOnlySurviveOnThinSupportedTips() {
        double[] tipWeights = {1.0, 1.0};
        StructuralPlacementWeightSource.adjustWeights(
                List.of(
                        StructuralMaterialClassifier.Kind.PILLAR,
                        StructuralMaterialClassifier.Kind.CONNECTOR
                ),
                tipWeights,
                0.1,
                0.15,
                0.1,
                0.95,
                0.0
        );

        double[] thickWeights = {1.0, 1.0};
        StructuralPlacementWeightSource.adjustWeights(
                List.of(
                        StructuralMaterialClassifier.Kind.PILLAR,
                        StructuralMaterialClassifier.Kind.CONNECTOR
                ),
                thickWeights,
                0.1,
                0.8,
                0.1,
                0.95,
                0.0
        );

        assertTrue(tipWeights[1] > tipWeights[0]);
        assertEquals(0.0, thickWeights[1]);
    }
}
