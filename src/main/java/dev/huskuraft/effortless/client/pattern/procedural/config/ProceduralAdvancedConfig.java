package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.ArrayList;
import java.util.List;

import dev.huskuraft.effortless.client.pattern.procedural.CoordinateSpace;
import dev.huskuraft.effortless.client.pattern.procedural.GradientCurve;
import dev.huskuraft.effortless.client.pattern.procedural.GradientDistributionMode;
import dev.huskuraft.effortless.client.pattern.procedural.NeighborTopology;
import dev.huskuraft.effortless.client.pattern.procedural.SeedMode;
import dev.huskuraft.effortless.client.pattern.procedural.SpatialField;

public record ProceduralAdvancedConfig(
        int repairPasses,
        NeighborTopology adjacencyTopology,
        CoordinateSpace coordinateSpace,
        SeedMode seedMode,
        GradientDistributionMode gradientDistributionMode,
        GradientCurve gradientCurve,
        int gradientSteps,
        List<ProceduralMaskLayer> maskLayers,
        List<ProceduralDirectionalRule> directionalRules,
        List<ProceduralSpacingRule> spacingRules,
        List<ProceduralNeighborCountRule> neighborCountRules,
        List<ProceduralQuotaRule> quotaRules,
        int cleanupPasses,
        List<ProceduralCleanupRule> cleanupRules,
        String parentPresetId,
        boolean inheritBlocks,
        boolean inheritRules,
        SpatialField gradientField,
        ProceduralNoiseConfig noiseConfig,
        String gradientFieldAssetId,
        String noiseFieldAssetId
) {

    public static final ProceduralAdvancedConfig DEFAULT =
            new ProceduralAdvancedConfig(
                    2,
                    NeighborTopology.ORTHOGONAL_6,
                    CoordinateSpace.RELATIVE,
                    SeedMode.FIXED,
                    GradientDistributionMode.WEIGHTED_ENDPOINTS,
                    GradientCurve.LINEAR,
                    8,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    0,
                    List.of(),
                    "",
                    false,
                    false,
                    SpatialField.DEFAULT,
                    ProceduralNoiseConfig.DEFAULT,
                    "",
                    ""
            );

    public ProceduralAdvancedConfig {
        maskLayers = List.copyOf(maskLayers);
        directionalRules = List.copyOf(directionalRules);
        spacingRules = List.copyOf(spacingRules);
        neighborCountRules = List.copyOf(neighborCountRules);
        quotaRules = List.copyOf(quotaRules);
        cleanupRules = List.copyOf(cleanupRules);
        parentPresetId = parentPresetId == null ? "" : parentPresetId;
        gradientField = gradientField == null
                ? SpatialField.DEFAULT
                : gradientField;
        noiseConfig = noiseConfig == null
                ? ProceduralNoiseConfig.DEFAULT
                : noiseConfig;
        gradientFieldAssetId = gradientFieldAssetId == null
                ? ""
                : gradientFieldAssetId;
        noiseFieldAssetId = noiseFieldAssetId == null
                ? ""
                : noiseFieldAssetId;
    }

    public ProceduralAdvancedConfig(
            int repairPasses,
            NeighborTopology adjacencyTopology,
            CoordinateSpace coordinateSpace,
            SeedMode seedMode,
            GradientDistributionMode gradientDistributionMode,
            GradientCurve gradientCurve,
            int gradientSteps,
            List<ProceduralMaskLayer> maskLayers,
            List<ProceduralDirectionalRule> directionalRules,
            List<ProceduralSpacingRule> spacingRules,
            List<ProceduralNeighborCountRule> neighborCountRules,
            List<ProceduralQuotaRule> quotaRules,
            int cleanupPasses,
            List<ProceduralCleanupRule> cleanupRules,
            String parentPresetId,
            boolean inheritBlocks,
            boolean inheritRules
    ) {
        this(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientDistributionMode, gradientCurve, gradientSteps,
                maskLayers, directionalRules, spacingRules, neighborCountRules,
                quotaRules, cleanupPasses, cleanupRules, parentPresetId,
                inheritBlocks, inheritRules, SpatialField.DEFAULT,
                ProceduralNoiseConfig.DEFAULT, "", ""
        );
    }

    public ProceduralAdvancedConfig(
            int repairPasses,
            NeighborTopology adjacencyTopology,
            CoordinateSpace coordinateSpace,
            SeedMode seedMode,
            GradientDistributionMode gradientDistributionMode,
            GradientCurve gradientCurve,
            int gradientSteps,
            List<ProceduralMaskLayer> maskLayers,
            List<ProceduralDirectionalRule> directionalRules,
            List<ProceduralSpacingRule> spacingRules,
            List<ProceduralNeighborCountRule> neighborCountRules,
            List<ProceduralQuotaRule> quotaRules,
            int cleanupPasses,
            List<ProceduralCleanupRule> cleanupRules,
            String parentPresetId,
            boolean inheritBlocks,
            boolean inheritRules,
            SpatialField gradientField,
            ProceduralNoiseConfig noiseConfig
    ) {
        this(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientDistributionMode, gradientCurve, gradientSteps,
                maskLayers, directionalRules, spacingRules, neighborCountRules,
                quotaRules, cleanupPasses, cleanupRules, parentPresetId,
                inheritBlocks, inheritRules, gradientField, noiseConfig,
                "", ""
        );
    }

    public ProceduralAdvancedConfig withInheritedRules(
            ProceduralAdvancedConfig parent
    ) {
        return new ProceduralAdvancedConfig(
                repairPasses,
                adjacencyTopology,
                coordinateSpace,
                seedMode,
                gradientDistributionMode,
                gradientCurve,
                gradientSteps,
                concat(parent.maskLayers, maskLayers),
                concat(parent.directionalRules, directionalRules),
                concat(parent.spacingRules, spacingRules),
                concat(parent.neighborCountRules, neighborCountRules),
                concat(parent.quotaRules, quotaRules),
                cleanupPasses,
                concat(parent.cleanupRules, cleanupRules),
                "",
                false,
                false,
                gradientField,
                noiseConfig,
                gradientFieldAssetId,
                noiseFieldAssetId
        );
    }

    public ProceduralAdvancedConfig withoutParent() {
        return new ProceduralAdvancedConfig(
                repairPasses,
                adjacencyTopology,
                coordinateSpace,
                seedMode,
                gradientDistributionMode,
                gradientCurve,
                gradientSteps,
                maskLayers,
                directionalRules,
                spacingRules,
                neighborCountRules,
                quotaRules,
                cleanupPasses,
                cleanupRules,
                "",
                false,
                false,
                gradientField,
                noiseConfig,
                gradientFieldAssetId,
                noiseFieldAssetId
        );
    }

    public ProceduralAdvancedConfig withRepairPasses(int value) {
        return copy(
                value, adjacencyTopology, coordinateSpace, seedMode,
                gradientCurve, gradientSteps, maskLayers, directionalRules,
                spacingRules, neighborCountRules, quotaRules, cleanupPasses,
                cleanupRules, parentPresetId, inheritBlocks, inheritRules
        );
    }

    public ProceduralAdvancedConfig withAdjacencyTopology(
            NeighborTopology value
    ) {
        return copy(
                repairPasses, value, coordinateSpace, seedMode, gradientCurve,
                gradientSteps, maskLayers, directionalRules, spacingRules,
                neighborCountRules, quotaRules, cleanupPasses, cleanupRules,
                parentPresetId, inheritBlocks, inheritRules
        );
    }

    public ProceduralAdvancedConfig withCoordinateSpace(CoordinateSpace value) {
        return copy(
                repairPasses, adjacencyTopology, value, seedMode, gradientCurve,
                gradientSteps, maskLayers, directionalRules, spacingRules,
                neighborCountRules, quotaRules, cleanupPasses, cleanupRules,
                parentPresetId, inheritBlocks, inheritRules
        );
    }

    public ProceduralAdvancedConfig withSeedMode(SeedMode value) {
        return copy(
                repairPasses, adjacencyTopology, coordinateSpace, value,
                gradientCurve, gradientSteps, maskLayers, directionalRules,
                spacingRules, neighborCountRules, quotaRules, cleanupPasses,
                cleanupRules, parentPresetId, inheritBlocks, inheritRules
        );
    }

    public ProceduralAdvancedConfig withGradientDistributionMode(
            GradientDistributionMode value
    ) {
        return new ProceduralAdvancedConfig(
                repairPasses,
                adjacencyTopology,
                coordinateSpace,
                seedMode,
                value,
                gradientCurve,
                gradientSteps,
                maskLayers,
                directionalRules,
                spacingRules,
                neighborCountRules,
                quotaRules,
                cleanupPasses,
                cleanupRules,
                parentPresetId,
                inheritBlocks,
                inheritRules,
                gradientField,
                noiseConfig,
                gradientFieldAssetId,
                noiseFieldAssetId
        );
    }

    public ProceduralAdvancedConfig withGradientField(SpatialField value) {
        return new ProceduralAdvancedConfig(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientDistributionMode, gradientCurve, gradientSteps,
                maskLayers, directionalRules, spacingRules, neighborCountRules,
                quotaRules, cleanupPasses, cleanupRules, parentPresetId,
                inheritBlocks, inheritRules, value, noiseConfig,
                gradientFieldAssetId, noiseFieldAssetId
        );
    }

    public ProceduralAdvancedConfig withNoiseConfig(
            ProceduralNoiseConfig value
    ) {
        return new ProceduralAdvancedConfig(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientDistributionMode, gradientCurve, gradientSteps,
                maskLayers, directionalRules, spacingRules, neighborCountRules,
                quotaRules, cleanupPasses, cleanupRules, parentPresetId,
                inheritBlocks, inheritRules, gradientField, value,
                gradientFieldAssetId, noiseFieldAssetId
        );
    }

    public ProceduralAdvancedConfig withGradientFieldAsset(String id) {
        return new ProceduralAdvancedConfig(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientDistributionMode, gradientCurve, gradientSteps,
                maskLayers, directionalRules, spacingRules, neighborCountRules,
                quotaRules, cleanupPasses, cleanupRules, parentPresetId,
                inheritBlocks, inheritRules, gradientField, noiseConfig,
                id, noiseFieldAssetId
        );
    }

    public ProceduralAdvancedConfig withNoiseFieldAsset(String id) {
        return new ProceduralAdvancedConfig(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientDistributionMode, gradientCurve, gradientSteps,
                maskLayers, directionalRules, spacingRules, neighborCountRules,
                quotaRules, cleanupPasses, cleanupRules, parentPresetId,
                inheritBlocks, inheritRules, gradientField, noiseConfig,
                gradientFieldAssetId, id
        );
    }

    public ProceduralAdvancedConfig withGradientCurve(
            GradientCurve curve,
            int steps
    ) {
        return copy(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                curve, steps, maskLayers, directionalRules, spacingRules,
                neighborCountRules, quotaRules, cleanupPasses, cleanupRules,
                parentPresetId, inheritBlocks, inheritRules
        );
    }

    public ProceduralAdvancedConfig withMaskLayers(
            List<ProceduralMaskLayer> value
    ) {
        return copy(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientCurve, gradientSteps, value, directionalRules,
                spacingRules, neighborCountRules, quotaRules, cleanupPasses,
                cleanupRules, parentPresetId, inheritBlocks, inheritRules
        );
    }

    public ProceduralAdvancedConfig withDirectionalRules(
            List<ProceduralDirectionalRule> value
    ) {
        return copy(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientCurve, gradientSteps, maskLayers, value, spacingRules,
                neighborCountRules, quotaRules, cleanupPasses, cleanupRules,
                parentPresetId, inheritBlocks, inheritRules
        );
    }

    public ProceduralAdvancedConfig withSpacingRules(
            List<ProceduralSpacingRule> value
    ) {
        return copy(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientCurve, gradientSteps, maskLayers, directionalRules,
                value, neighborCountRules, quotaRules, cleanupPasses,
                cleanupRules, parentPresetId, inheritBlocks, inheritRules
        );
    }

    public ProceduralAdvancedConfig withNeighborCountRules(
            List<ProceduralNeighborCountRule> value
    ) {
        return copy(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientCurve, gradientSteps, maskLayers, directionalRules,
                spacingRules, value, quotaRules, cleanupPasses, cleanupRules,
                parentPresetId, inheritBlocks, inheritRules
        );
    }

    public ProceduralAdvancedConfig withQuotaRules(
            List<ProceduralQuotaRule> value
    ) {
        return copy(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientCurve, gradientSteps, maskLayers, directionalRules,
                spacingRules, neighborCountRules, value, cleanupPasses,
                cleanupRules, parentPresetId, inheritBlocks, inheritRules
        );
    }

    public ProceduralAdvancedConfig withCleanup(
            int passes,
            List<ProceduralCleanupRule> rules
    ) {
        return copy(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientCurve, gradientSteps, maskLayers, directionalRules,
                spacingRules, neighborCountRules, quotaRules, passes, rules,
                parentPresetId, inheritBlocks, inheritRules
        );
    }

    public ProceduralAdvancedConfig withParent(
            String id,
            boolean blocks,
            boolean rules
    ) {
        return copy(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientCurve, gradientSteps, maskLayers, directionalRules,
                spacingRules, neighborCountRules, quotaRules, cleanupPasses,
                cleanupRules, id, blocks, rules
        );
    }

    private ProceduralAdvancedConfig copy(
            int repairPasses,
            NeighborTopology adjacencyTopology,
            CoordinateSpace coordinateSpace,
            SeedMode seedMode,
            GradientCurve gradientCurve,
            int gradientSteps,
            List<ProceduralMaskLayer> maskLayers,
            List<ProceduralDirectionalRule> directionalRules,
            List<ProceduralSpacingRule> spacingRules,
            List<ProceduralNeighborCountRule> neighborCountRules,
            List<ProceduralQuotaRule> quotaRules,
            int cleanupPasses,
            List<ProceduralCleanupRule> cleanupRules,
            String parentPresetId,
            boolean inheritBlocks,
            boolean inheritRules
    ) {
        return new ProceduralAdvancedConfig(
                repairPasses, adjacencyTopology, coordinateSpace, seedMode,
                gradientDistributionMode, gradientCurve, gradientSteps,
                maskLayers, directionalRules,
                spacingRules, neighborCountRules, quotaRules, cleanupPasses,
                cleanupRules, parentPresetId, inheritBlocks, inheritRules,
                gradientField, noiseConfig, gradientFieldAssetId,
                noiseFieldAssetId
        );
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        var result = new ArrayList<T>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return List.copyOf(result);
    }
}
