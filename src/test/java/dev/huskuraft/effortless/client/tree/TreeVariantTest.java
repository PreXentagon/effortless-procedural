package dev.huskuraft.effortless.client.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.road.RoadPoint;

class TreeVariantTest {

    private static final RoadPoint ORIGIN = new RoadPoint(0.5, 64.5, 0.5);

    @Test
    void sameInputsResolveExactlyTheSameVariant() {
        var config = TreeGenerationConfig.forArchetype(TreeArchetype.WILLOW);

        assertEquals(
                TreeVariant.resolve(config, 99123L, ORIGIN, 7),
                TreeVariant.resolve(config, 99123L, ORIGIN, 7)
        );
    }

    @Test
    void visibleVariantChangesAPlacementSequenceTreeWithinSavedBounds() {
        var config = TreeGenerationConfig.forArchetype(TreeArchetype.OAK)
                .withVariationSource(TreeVariationSource.PLACEMENT_SEQUENCE);
        var first = TreeVariant.resolve(config, 42L, ORIGIN, 2);
        var second = TreeVariant.resolve(config, 42L, ORIGIN, 3);

        assertNotEquals(first, second);
        assertTrue(first.height() >= config.minimumHeight());
        assertTrue(first.height() <= config.maximumHeight());
        assertTrue(first.branchCount() >= config.minimumBranches());
        assertTrue(first.branchCount() <= config.maximumBranches());
        assertTrue(first.tipRadius() <= first.baseRadius());
    }

    @Test
    void worldPositionOnlyAffectsWorldPositionSource() {
        var shifted = new RoadPoint(128.5, 70.5, -41.5);
        var world = TreeGenerationConfig.forArchetype(TreeArchetype.ACACIA)
                .withVariationSource(TreeVariationSource.WORLD_POSITION);
        var locked = world.withVariationSource(TreeVariationSource.LOCKED);

        assertNotEquals(
                TreeVariant.resolve(world, 91L, ORIGIN, 0),
                TreeVariant.resolve(world, 91L, shifted, 0)
        );
        assertEquals(
                TreeVariant.resolve(locked, 91L, ORIGIN, 0),
                TreeVariant.resolve(locked, 91L, shifted, 0)
        );
    }

    @Test
    void disabledVariationUsesStableMidpointsAndExactSecondaryValues() {
        var config = TreeGenerationConfig.forArchetype(TreeArchetype.PINE)
                .withVariationStrength(TreeVariationStrength.OFF);
        var resolved = TreeVariant.resolve(config, 7L, ORIGIN, 99);

        assertEquals(
                (config.minimumHeight() + config.maximumHeight()) / 2,
                resolved.height()
        );
        assertEquals(
                (config.minimumBranches() + config.maximumBranches()) / 2,
                resolved.branchCount()
        );
        assertEquals(config.trunkBend(), resolved.trunkBend());
        assertEquals(config.foliageDensity(), resolved.foliageDensity());
    }
}
