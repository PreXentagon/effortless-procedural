package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.List;

/**
 * Reusable scene recipes for common compound builds. Keeping these outside the
 * screen makes their ordered boolean semantics testable and available to
 * future importers or command-driven tooling.
 */
public final class ProceduralCompositionTemplates {

    private ProceduralCompositionTemplates() {
    }

    public static List<ProceduralCompositionLayer> tunnelShell() {
        return List.of(
                ProceduralCompositionLayer.defaultLayer()
                        .withName("Tunnel lining")
                        .withAnchor(ProceduralCompositionLayer.Anchor.PATH)
                        .withPrimitive(
                                ProceduralCompositionLayer.Primitive.ARCH
                        )
                        .withSizes(9, 7, 3)
                        .withHollow(true)
                        .withShellThickness(1)
                        .withSpacing(0.5)
                        .withMaximumInstances(4096),
                ProceduralCompositionLayer.defaultLayer()
                        .withName("Tunnel opening")
                        .withOperation(
                                ProceduralCompositionLayer.Operation.SUBTRACT
                        )
                        .withAnchor(ProceduralCompositionLayer.Anchor.PATH)
                        .withPrimitive(
                                ProceduralCompositionLayer.Primitive.ARCH
                        )
                        .withSizes(7, 6, 3)
                        .withSpacing(0.5)
                        .withMaximumInstances(4096)
        );
    }

    public static List<ProceduralCompositionLayer> roadsideTrees() {
        return List.of(
                roadsideTree("Left roadside trees", -7.0, 17L),
                roadsideTree("Right roadside trees", 7.0, 31L)
        );
    }

    /**
     * A sparse, two-layer road defect: one independently materialized rim and
     * one deeper destructive cutout. Road thickness does not need to match the
     * void depth; both layers can be resized or assigned separate recipes.
     */
    public static List<ProceduralCompositionLayer> roadDamage() {
        return List.of(
                ProceduralCompositionLayer.defaultLayer()
                        .withName("Damage lining")
                        .withAnchor(ProceduralCompositionLayer.Anchor.PATH)
                        .withPrimitive(
                                ProceduralCompositionLayer.Primitive.ELLIPSOID
                        )
                        .withOffsets(0.0, -2.0, 0.0)
                        .withSizes(9, 4, 9)
                        .withHollow(true)
                        .withShellThickness(1)
                        .withSpacing(24.0)
                        .withMaximumInstances(64),
                ProceduralCompositionLayer.defaultLayer()
                        .withName("Damage void")
                        .withOperation(
                                ProceduralCompositionLayer.Operation.SUBTRACT
                        )
                        .withAnchor(ProceduralCompositionLayer.Anchor.PATH)
                        .withPrimitive(
                                ProceduralCompositionLayer.Primitive.ELLIPSOID
                        )
                        .withOffsets(0.0, -3.0, 0.0)
                        .withSizes(7, 7, 7)
                        .withSpacing(24.0)
                        .withMaximumInstances(64)
        );
    }

    private static ProceduralCompositionLayer roadsideTree(
            String name,
            double lateralOffset,
            long seedSalt
    ) {
        return ProceduralCompositionLayer.defaultLayer()
                .withName(name)
                .withGenerator(ProceduralCompositionLayer.Generator.TREE)
                .withAnchor(ProceduralCompositionLayer.Anchor.PATH)
                .withOffsets(lateralOffset, 0.0, 0.0)
                .withSpacing(12.0)
                .withMaximumInstances(256)
                .withSeedSalt(seedSalt)
                .withSafeVariation(true);
    }
}
