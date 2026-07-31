package dev.huskuraft.effortless.client.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.road.RoadPoint;
import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;

class TreeVoxelizerTest {

    private static final List<RoadPoint> POINTS = List.of(
            new RoadPoint(0.5, 0.5, 0.5),
            new RoadPoint(0.5, 8.5, 0.5),
            new RoadPoint(5.5, 6.5, 0.5),
            new RoadPoint(-4.5, 5.5, 2.5)
    );

    @Test
    void sameSkeletonProducesStableOrderedCells() {
        var first = TreeVoxelizer.voxelize(
                POINTS,
                TreeProfile.DEFAULT,
                100_000
        );
        var second = TreeVoxelizer.voxelize(
                POINTS,
                TreeProfile.DEFAULT,
                100_000
        );

        assertTrue(first.isSuccess());
        assertEquals(first.cells(), second.cells());
        assertEquals(3, first.limbs().size());
        assertTrue(first.cells().stream().anyMatch(cell ->
                cell.role() == TreeCell.Role.TRUNK
        ));
        assertTrue(first.cells().stream().anyMatch(cell ->
                cell.role() == TreeCell.Role.BRANCH
        ));
        assertTrue(first.cells().stream().anyMatch(cell ->
                cell.role() == TreeCell.Role.CANOPY
        ));
    }

    @Test
    void configuredLimitFailsBeforeReturningPartialGeometry() {
        var result = TreeVoxelizer.voxelize(
                POINTS,
                new TreeProfile(2, 2, 8, 0.1),
                20
        );

        assertFalse(result.isSuccess());
        assertTrue(result.cells().isEmpty());
        assertTrue(result.errors().get(0).contains("configured"));
    }

    @Test
    void branchRootsRemainAttachedWhenTrunkMoves() {
        var moved = TreeVoxelizer.voxelize(
                List.of(
                        new RoadPoint(3.5, 0.5, 1.5),
                        new RoadPoint(3.5, 8.5, 1.5),
                        new RoadPoint(8.5, 6.5, 1.5)
                ),
                TreeProfile.DEFAULT,
                100_000
        );

        assertTrue(moved.isSuccess());
        var branch = moved.limbs().get(1);
        assertEquals(3.5, branch.start().x());
        assertEquals(1.5, branch.start().z());
    }

    @Test
    void generatedTreeIsDeterministicAndContainsAllStructuralRoles() {
        var config = TreeGenerationConfig.forArchetype(TreeArchetype.OAK);
        var anchor = new RoadPoint(10.5, 64.5, -3.5);

        var first = TreeVoxelizer.generate(anchor, config, 12345L, 4, 200_000);
        var second = TreeVoxelizer.generate(anchor, config, 12345L, 4, 200_000);

        assertTrue(first.isSuccess());
        assertEquals(first.cells(), second.cells());
        assertTrue(first.cells().stream().anyMatch(cell ->
                cell.role() == TreeCell.Role.TRUNK));
        assertTrue(first.cells().stream().anyMatch(cell ->
                cell.role() == TreeCell.Role.BRANCH));
        assertTrue(first.cells().stream().anyMatch(cell ->
                cell.role() == TreeCell.Role.ROOT));
        assertTrue(first.cells().stream().anyMatch(cell ->
                cell.role() == TreeCell.Role.CANOPY));
    }

    @Test
    void archetypesProduceDifferentCoherentGeometry() {
        var anchor = new RoadPoint(0.5, 0.5, 0.5);
        var spruce = TreeVoxelizer.generate(
                anchor,
                TreeGenerationConfig.forArchetype(TreeArchetype.SPRUCE),
                99L,
                0,
                300_000
        );
        var palm = TreeVoxelizer.generate(
                anchor,
                TreeGenerationConfig.forArchetype(TreeArchetype.PALM),
                99L,
                0,
                300_000
        );

        assertTrue(spruce.isSuccess());
        assertTrue(palm.isSuccess());
        assertFalse(spruce.cells().equals(palm.cells()));
        assertTrue(spruce.cells().size() > palm.cells().size());
    }

    @Test
    void sparseFoliageIsStableAndActuallyRemovesCanopyCells() {
        var anchor = new RoadPoint(0.5, 0.5, 0.5);
        var denseConfig = TreeGenerationConfig
                .forArchetype(TreeArchetype.WILLOW)
                .withVariationStrength(TreeVariationStrength.OFF)
                .withFoliage(1.0, 0.18);
        var sparseConfig = denseConfig.withFoliage(0.25, 0.18);
        var dense = TreeVoxelizer.generate(
                anchor, denseConfig, 341L, 0, 300_000
        );
        var sparse = TreeVoxelizer.generate(
                anchor, sparseConfig, 341L, 0, 300_000
        );
        var repeated = TreeVoxelizer.generate(
                anchor, sparseConfig, 341L, 0, 300_000
        );

        long denseLeaves = dense.cells().stream().filter(cell ->
                cell.role() == TreeCell.Role.CANOPY).count();
        long sparseLeaves = sparse.cells().stream().filter(cell ->
                cell.role() == TreeCell.Role.CANOPY).count();
        assertTrue(dense.isSuccess());
        assertTrue(sparse.isSuccess());
        assertEquals(sparse.cells(), repeated.cells());
        assertTrue(sparseLeaves < denseLeaves);
    }

    @Test
    void generatedLimitNeverReturnsPartialGeometry() {
        var result = TreeVoxelizer.generate(
                new RoadPoint(0.5, 0.5, 0.5),
                TreeGenerationConfig.forArchetype(
                        TreeArchetype.GIANT_FANTASY
                ),
                1L,
                0,
                30
        );

        assertFalse(result.isSuccess());
        assertTrue(result.cells().isEmpty());
    }

    @Test
    void disablingStyleLockPermitsDeterministicStructuralDrift() {
        var anchor = new RoadPoint(0.5, 0.5, 0.5);
        var locked = TreeGenerationConfig
                .forArchetype(TreeArchetype.SPRUCE)
                .withStyleLock(true);
        var unlocked = locked.withStyleLock(false);

        var stable = TreeVoxelizer.generate(
                anchor, locked, 777L, 5, 300_000
        );
        var drifted = TreeVoxelizer.generate(
                anchor, unlocked, 777L, 5, 300_000
        );

        assertTrue(stable.isSuccess());
        assertTrue(drifted.isSuccess());
        assertFalse(stable.cells().equals(drifted.cells()));
        assertEquals(
                drifted.cells(),
                TreeVoxelizer.generate(
                        anchor, unlocked, 777L, 5, 300_000
                ).cells()
        );
    }

    @Test
    void guidedTreeKeepsArchetypeAndUsesAuthoredEndpoints() {
        var guides = List.of(
                new RoadPoint(0.5, 0.5, 0.5),
                new RoadPoint(2.5, 15.5, 1.5),
                new RoadPoint(8.5, 10.5, 2.5),
                new RoadPoint(-5.5, 12.5, -1.5)
        );
        var config = TreeGenerationConfig
                .forArchetype(TreeArchetype.OAK)
                .withMode(TreeMode.GUIDED);

        var first = TreeVoxelizer.generateGuided(
                guides, config, 913L, 2, 300_000
        );
        var second = TreeVoxelizer.generateGuided(
                guides, config, 913L, 2, 300_000
        );

        assertTrue(first.isSuccess());
        assertEquals(first.cells(), second.cells());
        assertTrue(first.limbs().stream().anyMatch(limb ->
                limb.end().equals(guides.get(2))
        ));
        assertTrue(first.cells().stream().anyMatch(cell ->
                cell.role() == TreeCell.Role.ROOT
        ));
        assertTrue(first.cells().stream().anyMatch(cell ->
                cell.role() == TreeCell.Role.CANOPY
        ));
    }

    @Test
    void everyArchetypeHasConnectedWoodAndSupportedFoliage() {
        for (var archetype : TreeArchetype.values()) {
            var result = TreeVoxelizer.generate(
                    new RoadPoint(0.5, 0.5, 0.5),
                    TreeGenerationConfig.forArchetype(archetype),
                    0x54_52_45_45L,
                    3,
                    500_000
            );

            assertTrue(result.isSuccess(), () -> archetype + ": "
                    + String.join("; ", result.errors()));
            var structural = positions(result, false);
            assertFalse(structural.isEmpty(), archetype.name());
            assertEquals(
                    structural,
                    connected(structural, structural.iterator().next(), false),
                    () -> archetype + " contains a disconnected limb"
            );

            var all = new HashSet<>(structural);
            all.addAll(positions(result, true));
            var reached = connected(
                    all,
                    structural.iterator().next(),
                    true
            );
            assertTrue(
                    reached.containsAll(positions(result, true)),
                    () -> archetype + " contains floating foliage"
            );
        }
    }

    @Test
    void branchCellsCarryHorizontalTangentsForLogOrientation() {
        var result = TreeVoxelizer.generateGuided(
                List.of(
                        new RoadPoint(0.5, 0.5, 0.5),
                        new RoadPoint(1.5, 18.5, 0.5),
                        new RoadPoint(9.5, 12.5, 2.5)
                ),
                TreeGenerationConfig.forArchetype(TreeArchetype.OAK),
                91L,
                0,
                300_000
        );

        assertTrue(result.isSuccess());
        assertTrue(result.cells().stream().anyMatch(cell ->
                cell.role() == TreeCell.Role.BRANCH
                        && Math.max(
                                Math.abs(cell.tangentX()),
                                Math.abs(cell.tangentZ())
                        ) > Math.abs(cell.tangentY())
        ));
    }

    @Test
    void generatedLimbsExposeReusableStructuralCoordinates() {
        var result = TreeVoxelizer.generateGuided(
                List.of(
                        new RoadPoint(0.5, 0.5, 0.5),
                        new RoadPoint(0.5, 18.5, 0.5),
                        new RoadPoint(9.5, 12.5, 2.5)
                ),
                TreeGenerationConfig.forArchetype(TreeArchetype.OAK),
                90210L,
                0,
                300_000
        );

        assertTrue(result.isSuccess());
        var branches = result.cells().stream()
                .filter(cell -> cell.role() == TreeCell.Role.BRANCH)
                .toList();
        assertTrue(branches.stream().anyMatch(cell -> cell.path() < 0.12));
        assertTrue(branches.stream().anyMatch(cell -> cell.path() > 0.88));
        assertTrue(branches.stream().anyMatch(cell ->
                cell.depth() > 0.65
                        && Math.hypot(cell.normalX(), cell.normalZ()) > 0.4
        ));
        assertTrue(result.cells().stream().anyMatch(cell ->
                cell.role() != TreeCell.Role.CANOPY
                        && cell.junction() > 0.5
        ));
    }

    @Test
    void legacyWorkflowsNormalizeToGuided() {
        assertEquals(
                TreeMode.GUIDED,
                TreeGenerationConfig.forArchetype(TreeArchetype.OAK)
                        .withMode(TreeMode.GENERATED).mode()
        );
        assertEquals(
                TreeMode.GUIDED,
                TreeGenerationConfig.legacySkeleton(TreeProfile.DEFAULT)
                        .mode()
        );
    }

    private static Set<GridPosition> positions(
            TreeVoxelizer.Result result,
            boolean canopy
    ) {
        var positions = new HashSet<GridPosition>();
        result.cells().stream()
                .filter(cell -> (cell.role() == TreeCell.Role.CANOPY)
                        == canopy)
                .map(TreeCell::position)
                .forEach(positions::add);
        return positions;
    }

    private static Set<GridPosition> connected(
            Set<GridPosition> available,
            GridPosition start,
            boolean diagonals
    ) {
        var reached = new HashSet<GridPosition>();
        var queue = new ArrayDeque<GridPosition>();
        reached.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            var position = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int distance = Math.abs(dx) + Math.abs(dy)
                                + Math.abs(dz);
                        if (distance == 0
                                || (!diagonals && distance != 1)) {
                            continue;
                        }
                        var neighbor = position.offset(dx, dy, dz);
                        if (available.contains(neighbor)
                                && reached.add(neighbor)) {
                            queue.addLast(neighbor);
                        }
                    }
                }
            }
        }
        return reached;
    }
}
