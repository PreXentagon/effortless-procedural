package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import dev.huskuraft.effortless.building.config.universal.TransformerConfigSerializer;
import dev.huskuraft.effortless.building.pattern.Transformers;
import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;
import dev.huskuraft.effortless.client.pattern.procedural.CandidateQuotaRule;
import dev.huskuraft.effortless.client.pattern.procedural.CoordinateSpace;
import dev.huskuraft.effortless.client.pattern.procedural.GradientCurve;
import dev.huskuraft.effortless.client.pattern.procedural.GradientDistributionMode;
import dev.huskuraft.effortless.client.pattern.procedural.MaskedWeightSource;
import dev.huskuraft.effortless.client.pattern.procedural.MaxRunLengthConstraint;
import dev.huskuraft.effortless.client.pattern.procedural.MinimumSpacingConstraint;
import dev.huskuraft.effortless.client.pattern.procedural.NeighborDirection;
import dev.huskuraft.effortless.client.pattern.procedural.NeighborTopology;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralRuleSet;
import dev.huskuraft.effortless.client.pattern.procedural.SeedMode;
import dev.huskuraft.effortless.client.pattern.procedural.SpatialField;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralPlacementMode;
import dev.huskuraft.effortless.client.road.RoadProfile;
import dev.huskuraft.effortless.client.road.SplineCrossSectionBand;
import dev.huskuraft.effortless.client.road.SplineCutoutConfig;
import dev.huskuraft.effortless.client.road.SplineSubtype;
import dev.huskuraft.effortless.client.road.SplineMaterialLinks;
import dev.huskuraft.effortless.client.tree.TreeArchetype;
import dev.huskuraft.effortless.client.tree.TreeGenerationConfig;
import dev.huskuraft.effortless.client.tree.TreeMode;
import dev.huskuraft.effortless.client.tree.TreeProfile;
import dev.huskuraft.effortless.client.tree.TreeVariationSource;
import dev.huskuraft.effortless.client.tree.TreeVariationStrength;
import dev.huskuraft.universal.api.config.ConfigSerializer;
import dev.huskuraft.universal.api.nightconfig.core.CommentedConfig;
import dev.huskuraft.universal.api.nightconfig.core.Config;
import dev.huskuraft.universal.api.nightconfig.core.ConfigSpec;

/**
 * Client-local persistence only. This serializer is intentionally unrelated to
 * the network Context/Pattern/Transformer serializers.
 */
public final class ProceduralPatternConfigSerializer
        implements ConfigSerializer<ProceduralPatternLibrary> {

    public static final int FORMAT_VERSION = 12;

    private static final String KEY_FORMAT_VERSION = "formatVersion";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_ACTIVE_PRESET = "activePresetId";
    private static final String KEY_PRESETS = "presets";
    private static final String KEY_FIELD_ASSETS = "fieldAssets";
    private static final String KEY_ACTIVE_TOOL = "activeTool";

    @Override
    public ConfigSpec getSpec(Config config) {
        var spec = new ConfigSpec();
        spec.defineInRange(KEY_FORMAT_VERSION, FORMAT_VERSION, 1, FORMAT_VERSION);
        spec.define(KEY_ENABLED, getDefault().enabled(), Boolean.class::isInstance);
        spec.define(
                KEY_ACTIVE_PRESET,
                getDefault().activePresetId().toString(),
                ProceduralPatternConfigSerializer::isUuid
        );
        spec.defineList(
                KEY_PRESETS,
                () -> getDefault().presets().stream().map(this::serializePreset).toList(),
                Config.class::isInstance
        );
        spec.defineList(
                KEY_FIELD_ASSETS,
                () -> getDefault().fieldAssets().stream()
                        .map(this::serializeFieldAsset)
                        .toList(),
                Config.class::isInstance
        );
        spec.define(KEY_ACTIVE_TOOL, "", String.class::isInstance);
        return spec;
    }

    @Override
    public ProceduralPatternLibrary getDefault() {
        return ProceduralPatternLibrary.DEFAULT;
    }

    @Override
    public ProceduralPatternLibrary deserialize(Config config) {
        validate(config);
        var presets = config.<List<Config>>get(KEY_PRESETS).stream()
                .map(this::deserializePreset)
                .toList();
        if (presets.isEmpty()) {
            presets = getDefault().presets();
        }
        var requestedActiveId = UUID.fromString(config.get(KEY_ACTIVE_PRESET));
        var activeId = presets.stream()
                .anyMatch(preset -> preset.id().equals(requestedActiveId))
                ? requestedActiveId
                : presets.get(0).id();
        var fieldAssets = config.<List<Config>>get(KEY_FIELD_ASSETS).stream()
                .map(this::deserializeFieldAsset)
                .toList();
        return new ProceduralPatternLibrary(
                config.get(KEY_ENABLED),
                activeId,
                presets,
                fieldAssets,
                config.get(KEY_ACTIVE_TOOL)
        );
    }

    @Override
    public Config serialize(ProceduralPatternLibrary library) {
        var config = CommentedConfig.inMemory();
        config.set(KEY_FORMAT_VERSION, FORMAT_VERSION);
        config.set(KEY_ENABLED, library.enabled());
        config.set(KEY_ACTIVE_PRESET, library.activePresetId().toString());
        config.set(KEY_ACTIVE_TOOL, library.activeToolId());
        config.set(KEY_PRESETS, library.presets().stream().map(this::serializePreset).toList());
        config.set(
                KEY_FIELD_ASSETS,
                library.fieldAssets().stream()
                        .map(this::serializeFieldAsset)
                        .toList()
        );
        validate(config);
        return config;
    }

    private Config serializePreset(ProceduralPatternPreset preset) {
        var config = Config.inMemory();
        config.set("id", preset.id().toString());
        config.set("name", preset.name());
        config.set("seed", preset.seed());
        config.set("retryLimit", preset.retryLimit());
        config.set("fallbackItem", preset.fallbackItemId());
        config.set("sequenceEnabled", preset.sequenceEnabled());
        config.set("sequenceOffset", preset.sequenceOffset());
        config.set("sequenceAlternateWeight", preset.sequenceAlternateWeight());
        config.set("gradientEnabled", preset.gradientEnabled());
        config.set("gradientCoordinate", enumName(preset.gradientCoordinate()));
        config.set("noiseEnabled", preset.noiseEnabled());
        config.set("noiseFrequency", preset.noiseFrequency());
        config.set("noiseSalt", preset.noiseSalt());
        config.set("inspectExistingWorld", preset.inspectExistingWorld());
        config.set("maximumRunLength", preset.maximumRunLength());
        config.set("materialSource", enumName(preset.materialSource()));
        config.set(
                "stockTransformers",
                preset.stockTransformers().stream()
                        .map(TransformerConfigSerializer.INSTANCE::serialize)
                        .toList()
        );
        config.set("blocks", preset.blocks().stream().map(this::serializeBlock).toList());
        config.set(
                "forbiddenAdjacency",
                preset.forbiddenAdjacency().stream().map(this::serializeForbiddenPair).toList()
        );
        config.set(
                "preferredAdjacency",
                preset.preferredAdjacency().stream().map(this::serializePreferredPair).toList()
        );
        config.set(
                "verticalRules",
                preset.verticalRules().stream().map(this::serializeVerticalRule).toList()
        );
        config.set("advanced", serializeAdvanced(preset.advanced()));
        presetSpec(preset).correct(config);
        return config;
    }

    private ProceduralPatternPreset deserializePreset(Config config) {
        Config originalAdvanced = config.get("advanced");
        boolean hadGradientField = originalAdvanced != null
                && originalAdvanced.contains("gradientField");
        boolean hadTreeGeneration = originalAdvanced != null
                && originalAdvanced.contains("treeGeneration");
        presetSpec(ProceduralPatternPreset.DEFAULT).correct(config);
        Coordinate gradientCoordinate = Coordinate.valueOf(
                config.<String>get("gradientCoordinate")
                        .toUpperCase(Locale.ROOT)
        );
        var advanced = deserializeAdvanced(
                config.get("advanced"),
                originalAdvanced != null,
                hadTreeGeneration
        );
        if (!hadGradientField) {
            advanced = advanced.withGradientField(
                    SpatialField.linear(gradientCoordinate)
            );
        }
        return new ProceduralPatternPreset(
                UUID.fromString(config.get("id")),
                config.get("name"),
                config.<Number>get("seed").longValue(),
                config.getInt("retryLimit"),
                config.get("fallbackItem"),
                config.<List<Config>>get("blocks").stream().map(this::deserializeBlock).toList(),
                config.get("sequenceEnabled"),
                config.getInt("sequenceOffset"),
                config.<Number>get("sequenceAlternateWeight").doubleValue(),
                config.get("gradientEnabled"),
                gradientCoordinate,
                config.get("noiseEnabled"),
                config.<Number>get("noiseFrequency").doubleValue(),
                config.<Number>get("noiseSalt").longValue(),
                config.get("inspectExistingWorld"),
                config.<List<Config>>get("forbiddenAdjacency").stream()
                        .map(this::deserializeForbiddenPair)
                        .toList(),
                config.<List<Config>>get("preferredAdjacency").stream()
                        .map(this::deserializePreferredPair)
                        .toList(),
                config.getInt("maximumRunLength"),
                config.<List<Config>>get("verticalRules").stream()
                        .map(this::deserializeVerticalRule)
                        .toList(),
                advanced,
                parseEnum(
                        config,
                        "materialSource",
                        PatternMaterialSource.class
                ),
                config.<List<Config>>get("stockTransformers").stream()
                        .map(TransformerConfigSerializer.INSTANCE::deserialize)
                        .filter(java.util.Objects::nonNull)
                        .filter(transformer ->
                                transformer.getType() != Transformers.RANDOMIZER
                        )
                        .toList()
        );
    }

    private ConfigSpec presetSpec(ProceduralPatternPreset defaults) {
        var spec = new ConfigSpec();
        spec.define("id", defaults.id().toString(), ProceduralPatternConfigSerializer::isUuid);
        spec.define("name", defaults.name(), value -> value instanceof String);
        spec.define("seed", defaults.seed(), value -> value instanceof Number);
        spec.defineInRange(
                "retryLimit",
                defaults.retryLimit(),
                1,
                ProceduralRuleSet.MAX_RETRY_LIMIT
        );
        spec.define(
                "fallbackItem",
                defaults.fallbackItemId(),
                ProceduralPatternConfigSerializer::isNonBlankString
        );
        spec.define("sequenceEnabled", defaults.sequenceEnabled(), Boolean.class::isInstance);
        spec.define("sequenceOffset", defaults.sequenceOffset(), value -> value instanceof Number);
        spec.defineInRange(
                "sequenceAlternateWeight",
                defaults.sequenceAlternateWeight(),
                0.0,
                Double.MAX_VALUE
        );
        spec.define("gradientEnabled", defaults.gradientEnabled(), Boolean.class::isInstance);
        spec.define(
                "gradientCoordinate",
                enumName(defaults.gradientCoordinate()),
                value -> isEnum(value, Coordinate.class)
        );
        spec.define("noiseEnabled", defaults.noiseEnabled(), Boolean.class::isInstance);
        spec.defineInRange("noiseFrequency", defaults.noiseFrequency(), 0.000001, 1024.0);
        spec.define("noiseSalt", defaults.noiseSalt(), value -> value instanceof Number);
        spec.define(
                "inspectExistingWorld",
                defaults.inspectExistingWorld(),
                Boolean.class::isInstance
        );
        defineEnum(spec, "materialSource", defaults.materialSource());
        spec.defineList(
                "stockTransformers",
                () -> defaults.stockTransformers().stream()
                        .map(TransformerConfigSerializer.INSTANCE::serialize)
                        .toList(),
                Config.class::isInstance
        );
        spec.defineInRange(
                "maximumRunLength",
                defaults.maximumRunLength(),
                0,
                MaxRunLengthConstraint.MAXIMUM_RUN_LENGTH
        );
        spec.defineList(
                "blocks",
                () -> defaults.blocks().stream().map(this::serializeBlock).toList(),
                Config.class::isInstance
        );
        spec.defineList(
                "forbiddenAdjacency",
                () -> defaults.forbiddenAdjacency().stream()
                        .map(this::serializeForbiddenPair)
                        .toList(),
                Config.class::isInstance
        );
        spec.defineList(
                "preferredAdjacency",
                () -> defaults.preferredAdjacency().stream()
                        .map(this::serializePreferredPair)
                        .toList(),
                Config.class::isInstance
        );
        spec.defineList(
                "verticalRules",
                () -> defaults.verticalRules().stream()
                        .map(this::serializeVerticalRule)
                        .toList(),
                Config.class::isInstance
        );
        spec.define(
                "advanced",
                serializeAdvanced(defaults.advanced()),
                Config.class::isInstance
        );
        return spec;
    }

    private Config serializeAdvanced(ProceduralAdvancedConfig advanced) {
        var config = Config.inMemory();
        config.set("repairPasses", advanced.repairPasses());
        config.set("adjacencyTopology", enumName(advanced.adjacencyTopology()));
        config.set("coordinateSpace", enumName(advanced.coordinateSpace()));
        config.set("seedMode", enumName(advanced.seedMode()));
        config.set(
                "gradientDistributionMode",
                enumName(advanced.gradientDistributionMode())
        );
        config.set("gradientCurve", enumName(advanced.gradientCurve()));
        config.set("gradientSteps", advanced.gradientSteps());
        config.set("gradientField", serializeSpatialField(
                advanced.gradientField()
        ));
        config.set("noiseConfig", serializeNoiseConfig(
                advanced.noiseConfig()
        ));
        config.set(
                "gradientFieldAssetId",
                advanced.gradientFieldAssetId()
        );
        config.set(
                "noiseFieldAssetId",
                advanced.noiseFieldAssetId()
        );
        config.set("roadProfile", serializeRoadProfile(
                advanced.roadProfile()
        ));
        config.set("treeProfile", serializeTreeProfile(
                advanced.treeProfile()
        ));
        config.set("treeGeneration", serializeTreeGeneration(
                advanced.treeGeneration()
        ));
        config.set(
                "structuralPlacementMode",
                enumName(advanced.structuralPlacementMode())
        );
        config.set(
                "maskLayers",
                advanced.maskLayers().stream().map(this::serializeMaskLayer).toList()
        );
        config.set(
                "directionalRules",
                advanced.directionalRules().stream()
                        .map(this::serializeDirectionalRule)
                        .toList()
        );
        config.set(
                "spacingRules",
                advanced.spacingRules().stream().map(this::serializeSpacingRule).toList()
        );
        config.set(
                "neighborCountRules",
                advanced.neighborCountRules().stream()
                        .map(this::serializeNeighborCountRule)
                        .toList()
        );
        config.set(
                "quotaRules",
                advanced.quotaRules().stream().map(this::serializeQuotaRule).toList()
        );
        config.set("cleanupPasses", advanced.cleanupPasses());
        config.set(
                "cleanupRules",
                advanced.cleanupRules().stream().map(this::serializeCleanupRule).toList()
        );
        config.set(
                "compositionLayers",
                advanced.compositionLayers().stream()
                        .map(this::serializeCompositionLayer)
                        .toList()
        );
        config.set("parentPresetId", advanced.parentPresetId());
        config.set("inheritBlocks", advanced.inheritBlocks());
        config.set("inheritRules", advanced.inheritRules());
        advancedSpec(advanced).correct(config);
        return config;
    }

    private ProceduralAdvancedConfig deserializeAdvanced(
            Config config,
            boolean hadAdvanced,
            boolean hadTreeGeneration
    ) {
        advancedSpec(ProceduralAdvancedConfig.DEFAULT).correct(config);
        var treeGeneration = hadTreeGeneration
                ? deserializeTreeGeneration(config.get("treeGeneration"))
                : hadAdvanced ? TreeGenerationConfig.legacySkeleton(
                        deserializeTreeProfile(config.get("treeProfile"))
                ) : TreeGenerationConfig.DEFAULT;
        return new ProceduralAdvancedConfig(
                config.getInt("repairPasses"),
                parseEnum(config, "adjacencyTopology", NeighborTopology.class),
                parseEnum(config, "coordinateSpace", CoordinateSpace.class),
                parseEnum(config, "seedMode", SeedMode.class),
                parseEnum(
                        config,
                        "gradientDistributionMode",
                        GradientDistributionMode.class
                ),
                parseEnum(config, "gradientCurve", GradientCurve.class),
                config.getInt("gradientSteps"),
                config.<List<Config>>get("maskLayers").stream()
                        .map(this::deserializeMaskLayer)
                        .toList(),
                config.<List<Config>>get("directionalRules").stream()
                        .map(this::deserializeDirectionalRule)
                        .toList(),
                config.<List<Config>>get("spacingRules").stream()
                        .map(this::deserializeSpacingRule)
                        .toList(),
                config.<List<Config>>get("neighborCountRules").stream()
                        .map(this::deserializeNeighborCountRule)
                        .toList(),
                config.<List<Config>>get("quotaRules").stream()
                        .map(this::deserializeQuotaRule)
                        .toList(),
                config.getInt("cleanupPasses"),
                config.<List<Config>>get("cleanupRules").stream()
                        .map(this::deserializeCleanupRule)
                        .toList(),
                config.get("parentPresetId"),
                config.get("inheritBlocks"),
                config.get("inheritRules"),
                deserializeSpatialField(config.get("gradientField")),
                deserializeNoiseConfig(config.get("noiseConfig")),
                config.get("gradientFieldAssetId"),
                config.get("noiseFieldAssetId"),
                deserializeRoadProfile(config.get("roadProfile")),
                treeGeneration,
                parseEnum(
                        config,
                        "structuralPlacementMode",
                        StructuralPlacementMode.class
                ),
                config.<List<Config>>get("compositionLayers").stream()
                        .map(this::deserializeCompositionLayer)
                        .toList()
        );
    }

    private ConfigSpec advancedSpec(ProceduralAdvancedConfig defaults) {
        var spec = new ConfigSpec();
        spec.defineInRange(
                "repairPasses",
                defaults.repairPasses(),
                0,
                ProceduralRuleSet.MAX_REPAIR_PASSES
        );
        defineEnum(spec, "adjacencyTopology", defaults.adjacencyTopology());
        defineEnum(spec, "coordinateSpace", defaults.coordinateSpace());
        defineEnum(spec, "seedMode", defaults.seedMode());
        defineEnum(
                spec,
                "gradientDistributionMode",
                defaults.gradientDistributionMode()
        );
        defineEnum(spec, "gradientCurve", defaults.gradientCurve());
        spec.defineInRange("gradientSteps", defaults.gradientSteps(), 2, 256);
        spec.define(
                "gradientField",
                serializeSpatialField(defaults.gradientField()),
                Config.class::isInstance
        );
        spec.define(
                "noiseConfig",
                serializeNoiseConfig(defaults.noiseConfig()),
                Config.class::isInstance
        );
        spec.define(
                "gradientFieldAssetId",
                defaults.gradientFieldAssetId(),
                ProceduralPatternConfigSerializer::isBlankOrUuid
        );
        spec.define(
                "noiseFieldAssetId",
                defaults.noiseFieldAssetId(),
                ProceduralPatternConfigSerializer::isBlankOrUuid
        );
        spec.define(
                "roadProfile",
                serializeRoadProfile(defaults.roadProfile()),
                Config.class::isInstance
        );
        spec.define(
                "treeProfile",
                serializeTreeProfile(defaults.treeProfile()),
                Config.class::isInstance
        );
        spec.define(
                "treeGeneration",
                serializeTreeGeneration(defaults.treeGeneration()),
                Config.class::isInstance
        );
        defineEnum(
                spec,
                "structuralPlacementMode",
                defaults.structuralPlacementMode()
        );
        spec.defineList(
                "maskLayers",
                () -> defaults.maskLayers().stream().map(this::serializeMaskLayer).toList(),
                Config.class::isInstance
        );
        spec.defineList(
                "directionalRules",
                () -> defaults.directionalRules().stream()
                        .map(this::serializeDirectionalRule)
                        .toList(),
                Config.class::isInstance
        );
        spec.defineList(
                "spacingRules",
                () -> defaults.spacingRules().stream()
                        .map(this::serializeSpacingRule)
                        .toList(),
                Config.class::isInstance
        );
        spec.defineList(
                "neighborCountRules",
                () -> defaults.neighborCountRules().stream()
                        .map(this::serializeNeighborCountRule)
                        .toList(),
                Config.class::isInstance
        );
        spec.defineList(
                "quotaRules",
                () -> defaults.quotaRules().stream()
                        .map(this::serializeQuotaRule)
                        .toList(),
                Config.class::isInstance
        );
        spec.defineInRange(
                "cleanupPasses",
                defaults.cleanupPasses(),
                0,
                ProceduralRuleSet.MAX_CLEANUP_PASSES
        );
        spec.defineList(
                "cleanupRules",
                () -> defaults.cleanupRules().stream()
                        .map(this::serializeCleanupRule)
                        .toList(),
                Config.class::isInstance
        );
        spec.defineList(
                "compositionLayers",
                () -> defaults.compositionLayers().stream()
                        .map(this::serializeCompositionLayer)
                        .toList(),
                Config.class::isInstance
        );
        spec.define(
                "parentPresetId",
                defaults.parentPresetId(),
                ProceduralPatternConfigSerializer::isBlankOrUuid
        );
        spec.define("inheritBlocks", defaults.inheritBlocks(), Boolean.class::isInstance);
        spec.define("inheritRules", defaults.inheritRules(), Boolean.class::isInstance);
        return spec;
    }

    private Config serializeCompositionLayer(
            ProceduralCompositionLayer layer
    ) {
        var config = Config.inMemory();
        config.set("name", layer.name());
        config.set("enabled", layer.enabled());
        config.set("operation", enumName(layer.operation()));
        config.set("generator", enumName(layer.generator()));
        config.set("anchor", enumName(layer.anchor()));
        config.set("primitive", enumName(layer.primitive()));
        config.set("presetId", layer.presetId());
        config.set("offsetX", layer.offsetX());
        config.set("offsetY", layer.offsetY());
        config.set("offsetZ", layer.offsetZ());
        config.set("sizeX", layer.sizeX());
        config.set("sizeY", layer.sizeY());
        config.set("sizeZ", layer.sizeZ());
        config.set("rotationDegrees", layer.rotationDegrees());
        config.set("hollow", layer.hollow());
        config.set("shellThickness", layer.shellThickness());
        config.set("spacing", layer.spacing());
        config.set("maximumInstances", layer.maximumInstances());
        config.set("seedSalt", layer.seedSalt());
        config.set("safeVariation", layer.safeVariation());
        compositionLayerSpec(layer).correct(config);
        return config;
    }

    private ProceduralCompositionLayer deserializeCompositionLayer(
            Config config
    ) {
        var defaults = ProceduralCompositionLayer.defaultLayer();
        compositionLayerSpec(defaults).correct(config);
        return new ProceduralCompositionLayer(
                config.get("name"),
                config.get("enabled"),
                parseEnum(
                        config, "operation",
                        ProceduralCompositionLayer.Operation.class
                ),
                parseEnum(
                        config, "generator",
                        ProceduralCompositionLayer.Generator.class
                ),
                parseEnum(
                        config, "anchor",
                        ProceduralCompositionLayer.Anchor.class
                ),
                parseEnum(
                        config, "primitive",
                        ProceduralCompositionLayer.Primitive.class
                ),
                config.get("presetId"),
                number(config, "offsetX"),
                number(config, "offsetY"),
                number(config, "offsetZ"),
                config.getInt("sizeX"),
                config.getInt("sizeY"),
                config.getInt("sizeZ"),
                number(config, "rotationDegrees"),
                config.get("hollow"),
                config.getInt("shellThickness"),
                number(config, "spacing"),
                config.getInt("maximumInstances"),
                config.getLong("seedSalt"),
                config.get("safeVariation")
        );
    }

    private ConfigSpec compositionLayerSpec(
            ProceduralCompositionLayer defaults
    ) {
        var spec = new ConfigSpec();
        spec.define("name", defaults.name(), String.class::isInstance);
        spec.define("enabled", defaults.enabled(), Boolean.class::isInstance);
        defineEnum(spec, "operation", defaults.operation());
        defineEnum(spec, "generator", defaults.generator());
        defineEnum(spec, "anchor", defaults.anchor());
        defineEnum(spec, "primitive", defaults.primitive());
        spec.define(
                "presetId", defaults.presetId(),
                ProceduralPatternConfigSerializer::isBlankOrUuid
        );
        spec.defineInRange("offsetX", defaults.offsetX(), -30_000_000.0, 30_000_000.0);
        spec.defineInRange("offsetY", defaults.offsetY(), -30_000_000.0, 30_000_000.0);
        spec.defineInRange("offsetZ", defaults.offsetZ(), -30_000_000.0, 30_000_000.0);
        spec.defineInRange("sizeX", defaults.sizeX(), 1, 4096);
        spec.defineInRange("sizeY", defaults.sizeY(), 1, 4096);
        spec.defineInRange("sizeZ", defaults.sizeZ(), 1, 4096);
        spec.defineInRange(
                "rotationDegrees", defaults.rotationDegrees(),
                -1_000_000.0, 1_000_000.0
        );
        spec.define("hollow", defaults.hollow(), Boolean.class::isInstance);
        spec.defineInRange("shellThickness", defaults.shellThickness(), 1, 4096);
        spec.defineInRange("spacing", defaults.spacing(), 0.5, 4096.0);
        spec.defineInRange("maximumInstances", defaults.maximumInstances(), 1, 4096);
        spec.define("seedSalt", defaults.seedSalt(), Number.class::isInstance);
        spec.define("safeVariation", defaults.safeVariation(), Boolean.class::isInstance);
        return spec;
    }

    private Config serializeSpatialField(SpatialField field) {
        var config = Config.inMemory();
        config.set("shape", enumName(field.shape()));
        config.set("coordinate", enumName(field.coordinate()));
        config.set("centerX", field.centerX());
        config.set("centerY", field.centerY());
        config.set("centerZ", field.centerZ());
        config.set("scaleX", field.scaleX());
        config.set("scaleY", field.scaleY());
        config.set("scaleZ", field.scaleZ());
        config.set("rotationDegrees", field.rotationDegrees());
        config.set("polygonSides", field.polygonSides());
        config.set("curvature", field.curvature());
        config.set("repeat", field.repeat());
        config.set("inverted", field.inverted());
        config.set("warpAmount", field.warpAmount());
        config.set("warpFrequency", field.warpFrequency());
        config.set("warpSalt", field.warpSalt());
        spatialFieldSpec(field).correct(config);
        return config;
    }

    private Config serializeFieldAsset(ProceduralFieldAsset asset) {
        var config = Config.inMemory();
        config.set("id", asset.id().toString());
        config.set("name", asset.name());
        config.set("gradientField", serializeSpatialField(
                asset.gradientField()
        ));
        config.set("noiseConfig", serializeNoiseConfig(asset.noiseConfig()));
        fieldAssetSpec(asset).correct(config);
        return config;
    }

    private ProceduralFieldAsset deserializeFieldAsset(Config config) {
        var defaults = new ProceduralFieldAsset(
                new UUID(0L, 2L),
                "Spatial field",
                SpatialField.DEFAULT,
                ProceduralNoiseConfig.DEFAULT
        );
        fieldAssetSpec(defaults).correct(config);
        return new ProceduralFieldAsset(
                UUID.fromString(config.get("id")),
                config.get("name"),
                deserializeSpatialField(config.get("gradientField")),
                deserializeNoiseConfig(config.get("noiseConfig"))
        );
    }

    private ConfigSpec fieldAssetSpec(ProceduralFieldAsset defaults) {
        var spec = new ConfigSpec();
        spec.define(
                "id",
                defaults.id().toString(),
                ProceduralPatternConfigSerializer::isUuid
        );
        spec.define(
                "name",
                defaults.name(),
                ProceduralPatternConfigSerializer::isNonBlankString
        );
        spec.define(
                "gradientField",
                serializeSpatialField(defaults.gradientField()),
                Config.class::isInstance
        );
        spec.define(
                "noiseConfig",
                serializeNoiseConfig(defaults.noiseConfig()),
                Config.class::isInstance
        );
        return spec;
    }

    private SpatialField deserializeSpatialField(Config config) {
        spatialFieldSpec(SpatialField.DEFAULT).correct(config);
        return new SpatialField(
                parseEnum(config, "shape", SpatialField.Shape.class),
                parseEnum(config, "coordinate", Coordinate.class),
                number(config, "centerX"),
                number(config, "centerY"),
                number(config, "centerZ"),
                number(config, "scaleX"),
                number(config, "scaleY"),
                number(config, "scaleZ"),
                number(config, "rotationDegrees"),
                config.getInt("polygonSides"),
                number(config, "curvature"),
                config.getInt("repeat"),
                config.get("inverted"),
                number(config, "warpAmount"),
                number(config, "warpFrequency"),
                config.<Number>get("warpSalt").longValue()
        );
    }

    private ConfigSpec spatialFieldSpec(SpatialField defaults) {
        var spec = new ConfigSpec();
        defineEnum(spec, "shape", defaults.shape());
        defineEnum(spec, "coordinate", defaults.coordinate());
        spec.defineInRange("centerX", defaults.centerX(), -4.0, 4.0);
        spec.defineInRange("centerY", defaults.centerY(), -4.0, 4.0);
        spec.defineInRange("centerZ", defaults.centerZ(), -4.0, 4.0);
        spec.defineInRange("scaleX", defaults.scaleX(), 0.000001, 64.0);
        spec.defineInRange("scaleY", defaults.scaleY(), 0.000001, 64.0);
        spec.defineInRange("scaleZ", defaults.scaleZ(), 0.000001, 64.0);
        spec.define(
                "rotationDegrees",
                defaults.rotationDegrees(),
                value -> value instanceof Number
        );
        spec.defineInRange(
                "polygonSides",
                defaults.polygonSides(),
                3,
                32
        );
        spec.defineInRange("curvature", defaults.curvature(), -4.0, 4.0);
        spec.defineInRange("repeat", defaults.repeat(), 1, 64);
        spec.define("inverted", defaults.inverted(), Boolean.class::isInstance);
        spec.defineInRange("warpAmount", defaults.warpAmount(), 0.0, 2.0);
        spec.defineInRange(
                "warpFrequency",
                defaults.warpFrequency(),
                0.000001,
                1024.0
        );
        spec.define("warpSalt", defaults.warpSalt(), value -> value instanceof Number);
        return spec;
    }

    private Config serializeNoiseConfig(ProceduralNoiseConfig value) {
        var config = Config.inMemory();
        config.set("scaleX", value.scaleX());
        config.set("scaleY", value.scaleY());
        config.set("scaleZ", value.scaleZ());
        config.set("offsetX", value.offsetX());
        config.set("offsetY", value.offsetY());
        config.set("offsetZ", value.offsetZ());
        config.set("rotationDegrees", value.rotationDegrees());
        config.set("octaves", value.octaves());
        config.set("persistence", value.persistence());
        config.set("lacunarity", value.lacunarity());
        config.set("warpStrength", value.warpStrength());
        config.set("warpFrequency", value.warpFrequency());
        config.set("warpSalt", value.warpSalt());
        noiseConfigSpec(value).correct(config);
        return config;
    }

    private ProceduralNoiseConfig deserializeNoiseConfig(Config config) {
        noiseConfigSpec(ProceduralNoiseConfig.DEFAULT).correct(config);
        return new ProceduralNoiseConfig(
                number(config, "scaleX"),
                number(config, "scaleY"),
                number(config, "scaleZ"),
                number(config, "offsetX"),
                number(config, "offsetY"),
                number(config, "offsetZ"),
                number(config, "rotationDegrees"),
                config.getInt("octaves"),
                number(config, "persistence"),
                number(config, "lacunarity"),
                number(config, "warpStrength"),
                number(config, "warpFrequency"),
                config.<Number>get("warpSalt").longValue()
        );
    }

    private ConfigSpec noiseConfigSpec(ProceduralNoiseConfig defaults) {
        var spec = new ConfigSpec();
        spec.defineInRange("scaleX", defaults.scaleX(), 0.000001, 64.0);
        spec.defineInRange("scaleY", defaults.scaleY(), 0.000001, 64.0);
        spec.defineInRange("scaleZ", defaults.scaleZ(), 0.000001, 64.0);
        spec.define("offsetX", defaults.offsetX(), value -> value instanceof Number);
        spec.define("offsetY", defaults.offsetY(), value -> value instanceof Number);
        spec.define("offsetZ", defaults.offsetZ(), value -> value instanceof Number);
        spec.define(
                "rotationDegrees",
                defaults.rotationDegrees(),
                value -> value instanceof Number
        );
        spec.defineInRange("octaves", defaults.octaves(), 1, 8);
        spec.defineInRange("persistence", defaults.persistence(), 0.0, 1.0);
        spec.defineInRange("lacunarity", defaults.lacunarity(), 1.0, 8.0);
        spec.defineInRange(
                "warpStrength",
                defaults.warpStrength(),
                0.0,
                64.0
        );
        spec.defineInRange(
                "warpFrequency",
                defaults.warpFrequency(),
                0.000001,
                1024.0
        );
        spec.define("warpSalt", defaults.warpSalt(), value -> value instanceof Number);
        return spec;
    }

    private Config serializeMaskLayer(ProceduralMaskLayer layer) {
        var config = Config.inMemory();
        config.set("id", layer.id().toString());
        config.set("name", layer.name());
        config.set("enabled", layer.enabled());
        config.set("shape", enumName(layer.shape()));
        config.set("coordinate", enumName(layer.coordinate()));
        config.set("minimum", layer.minimum());
        config.set("maximum", layer.maximum());
        config.set("inverted", layer.inverted());
        config.set("items", layer.itemIds());
        config.set("mode", enumName(layer.mode()));
        config.set("multiplier", layer.multiplier());
        config.set("period", layer.period());
        config.set("thickness", layer.thickness());
        config.set("spatialField", serializeSpatialField(
                layer.spatialField()
        ));
        maskSpec(layer).correct(config);
        return config;
    }

    private ProceduralMaskLayer deserializeMaskLayer(Config config) {
        boolean hadSpatialField = config.contains("spatialField");
        maskSpec(defaultMask()).correct(config);
        Coordinate coordinate = parseEnum(
                config,
                "coordinate",
                Coordinate.class
        );
        return new ProceduralMaskLayer(
                UUID.fromString(config.get("id")),
                config.get("name"),
                config.get("enabled"),
                parseEnum(config, "shape", MaskedWeightSource.Shape.class),
                coordinate,
                number(config, "minimum"),
                number(config, "maximum"),
                config.get("inverted"),
                config.get("items"),
                parseEnum(config, "mode", MaskedWeightSource.Mode.class),
                number(config, "multiplier"),
                config.getInt("period"),
                config.getInt("thickness"),
                hadSpatialField
                        ? deserializeSpatialField(config.get("spatialField"))
                        : SpatialField.linear(coordinate)
        );
    }

    private ConfigSpec maskSpec(ProceduralMaskLayer defaults) {
        var spec = new ConfigSpec();
        spec.define("id", defaults.id().toString(), ProceduralPatternConfigSerializer::isUuid);
        spec.define("name", defaults.name(), ProceduralPatternConfigSerializer::isNonBlankString);
        spec.define("enabled", defaults.enabled(), Boolean.class::isInstance);
        defineEnum(spec, "shape", defaults.shape());
        defineEnum(spec, "coordinate", defaults.coordinate());
        spec.defineInRange("minimum", defaults.minimum(), 0.0, 1.0);
        spec.defineInRange("maximum", defaults.maximum(), 0.0, 1.0);
        spec.define("inverted", defaults.inverted(), Boolean.class::isInstance);
        spec.defineList(
                "items",
                defaults.itemIds(),
                ProceduralPatternConfigSerializer::isNonBlankString
        );
        defineEnum(spec, "mode", defaults.mode());
        defineNonNegative(spec, "multiplier", defaults.multiplier());
        spec.defineInRange("period", defaults.period(), 1, 1024);
        spec.defineInRange("thickness", defaults.thickness(), 1, 1024);
        spec.define(
                "spatialField",
                serializeSpatialField(defaults.spatialField()),
                Config.class::isInstance
        );
        return spec;
    }

    private Config serializeDirectionalRule(ProceduralDirectionalRule rule) {
        var config = Config.inMemory();
        config.set("item", rule.itemId());
        config.set("direction", enumName(rule.direction()));
        config.set("allowedItems", rule.allowedItemIds());
        config.set("rejectUnresolved", rule.rejectUnresolved());
        directionalSpec(rule).correct(config);
        return config;
    }

    private ProceduralDirectionalRule deserializeDirectionalRule(Config config) {
        var defaults = new ProceduralDirectionalRule(
                "minecraft:stone",
                NeighborDirection.DOWN,
                List.of("minecraft:cobblestone"),
                false
        );
        directionalSpec(defaults).correct(config);
        return new ProceduralDirectionalRule(
                config.get("item"),
                parseEnum(config, "direction", NeighborDirection.class),
                config.get("allowedItems"),
                config.get("rejectUnresolved")
        );
    }

    private ConfigSpec directionalSpec(ProceduralDirectionalRule defaults) {
        var spec = new ConfigSpec();
        spec.define("item", defaults.itemId(), ProceduralPatternConfigSerializer::isNonBlankString);
        defineEnum(spec, "direction", defaults.direction());
        spec.defineList(
                "allowedItems",
                defaults.allowedItemIds(),
                ProceduralPatternConfigSerializer::isNonBlankString
        );
        spec.define("rejectUnresolved", defaults.rejectUnresolved(), Boolean.class::isInstance);
        return spec;
    }

    private Config serializeSpacingRule(ProceduralSpacingRule rule) {
        var config = Config.inMemory();
        config.set("items", rule.itemIds());
        config.set("radius", rule.radius());
        config.set("metric", enumName(rule.metric()));
        spacingSpec(rule).correct(config);
        return config;
    }

    private ProceduralSpacingRule deserializeSpacingRule(Config config) {
        var defaults = new ProceduralSpacingRule(
                List.of("minecraft:stone"),
                1,
                MinimumSpacingConstraint.DistanceMetric.MANHATTAN
        );
        spacingSpec(defaults).correct(config);
        return new ProceduralSpacingRule(
                config.get("items"),
                config.getInt("radius"),
                parseEnum(
                        config,
                        "metric",
                        MinimumSpacingConstraint.DistanceMetric.class
                )
        );
    }

    private ConfigSpec spacingSpec(ProceduralSpacingRule defaults) {
        var spec = new ConfigSpec();
        spec.defineList(
                "items",
                defaults.itemIds(),
                ProceduralPatternConfigSerializer::isNonBlankString
        );
        spec.defineInRange(
                "radius",
                defaults.radius(),
                1,
                MinimumSpacingConstraint.MAXIMUM_RADIUS
        );
        defineEnum(spec, "metric", defaults.metric());
        return spec;
    }

    private Config serializeNeighborCountRule(ProceduralNeighborCountRule rule) {
        var config = Config.inMemory();
        config.set("item", rule.itemId());
        config.set("neighborItems", rule.neighborItemIds());
        config.set("minimum", rule.minimum());
        config.set("maximum", rule.maximum());
        neighborCountSpec(rule).correct(config);
        return config;
    }

    private ProceduralNeighborCountRule deserializeNeighborCountRule(Config config) {
        var defaults = new ProceduralNeighborCountRule(
                "minecraft:stone",
                List.of("minecraft:cobblestone"),
                0,
                6
        );
        neighborCountSpec(defaults).correct(config);
        return new ProceduralNeighborCountRule(
                config.get("item"),
                config.get("neighborItems"),
                config.getInt("minimum"),
                config.getInt("maximum")
        );
    }

    private ConfigSpec neighborCountSpec(ProceduralNeighborCountRule defaults) {
        var spec = new ConfigSpec();
        spec.define("item", defaults.itemId(), ProceduralPatternConfigSerializer::isNonBlankString);
        spec.defineList(
                "neighborItems",
                defaults.neighborItemIds(),
                ProceduralPatternConfigSerializer::isNonBlankString
        );
        spec.defineInRange("minimum", defaults.minimum(), 0, 26);
        spec.defineInRange("maximum", defaults.maximum(), 0, 26);
        return spec;
    }

    private Config serializeQuotaRule(ProceduralQuotaRule rule) {
        var config = Config.inMemory();
        config.set("item", rule.itemId());
        config.set("minimum", rule.minimum());
        config.set("maximum", rule.maximum());
        config.set("unit", enumName(rule.unit()));
        quotaSpec(rule).correct(config);
        return config;
    }

    private ProceduralQuotaRule deserializeQuotaRule(Config config) {
        var defaults = new ProceduralQuotaRule(
                "minecraft:stone",
                0.0,
                1.0,
                CandidateQuotaRule.Unit.FRACTION
        );
        quotaSpec(defaults).correct(config);
        return new ProceduralQuotaRule(
                config.get("item"),
                number(config, "minimum"),
                number(config, "maximum"),
                parseEnum(config, "unit", CandidateQuotaRule.Unit.class)
        );
    }

    private ConfigSpec quotaSpec(ProceduralQuotaRule defaults) {
        var spec = new ConfigSpec();
        spec.define("item", defaults.itemId(), ProceduralPatternConfigSerializer::isNonBlankString);
        defineNonNegative(spec, "minimum", defaults.minimum());
        defineNonNegative(spec, "maximum", defaults.maximum());
        defineEnum(spec, "unit", defaults.unit());
        return spec;
    }

    private Config serializeCleanupRule(ProceduralCleanupRule rule) {
        var config = Config.inMemory();
        config.set("sourceItems", rule.sourceItemIds());
        config.set("matchingNeighborItems", rule.matchingNeighborItemIds());
        config.set("replacementItem", rule.replacementItemId());
        config.set("minimumMatches", rule.minimumMatches());
        config.set("maximumMatches", rule.maximumMatches());
        cleanupSpec(rule).correct(config);
        return config;
    }

    private ProceduralCleanupRule deserializeCleanupRule(Config config) {
        var defaults = new ProceduralCleanupRule(
                List.of("minecraft:cobblestone"),
                List.of("minecraft:cobblestone"),
                "minecraft:stone",
                0,
                1
        );
        cleanupSpec(defaults).correct(config);
        return new ProceduralCleanupRule(
                config.get("sourceItems"),
                config.get("matchingNeighborItems"),
                config.get("replacementItem"),
                config.getInt("minimumMatches"),
                config.getInt("maximumMatches")
        );
    }

    private ConfigSpec cleanupSpec(ProceduralCleanupRule defaults) {
        var spec = new ConfigSpec();
        spec.defineList(
                "sourceItems",
                defaults.sourceItemIds(),
                ProceduralPatternConfigSerializer::isNonBlankString
        );
        spec.defineList(
                "matchingNeighborItems",
                defaults.matchingNeighborItemIds(),
                ProceduralPatternConfigSerializer::isNonBlankString
        );
        spec.define(
                "replacementItem",
                defaults.replacementItemId(),
                ProceduralPatternConfigSerializer::isNonBlankString
        );
        spec.defineInRange("minimumMatches", defaults.minimumMatches(), 0, 26);
        spec.defineInRange("maximumMatches", defaults.maximumMatches(), 0, 26);
        return spec;
    }

    private Config serializeRoadProfile(RoadProfile profile) {
        var config = Config.inMemory();
        config.set("surfaceWidth", profile.surfaceWidth());
        config.set("thickness", profile.thickness());
        config.set("shoulderWidth", profile.shoulderWidth());
        config.set("tension", profile.tension());
        config.set("sampleSpacing", profile.sampleSpacing());
        config.set("subtype", enumName(profile.subtype()));
        config.set("surfaceRecipeId", profile.materialLinks().surfaceRecipeId());
        config.set("shoulderRecipeId", profile.materialLinks().shoulderRecipeId());
        config.set("foundationRecipeId", profile.materialLinks().foundationRecipeId());
        config.set("curbRecipeId", profile.materialLinks().curbRecipeId());
        config.set("markingRecipeId", profile.materialLinks().markingRecipeId());
        config.set("damageRecipeId", profile.materialLinks().damageRecipeId());
        config.set(
                "crossSectionBands",
                profile.crossSectionBands().stream()
                        .map(this::serializeCrossSectionBand)
                        .toList()
        );
        config.set("cutout", serializeCutout(profile.cutout()));
        roadProfileSpec(profile).correct(config);
        return config;
    }

    private RoadProfile deserializeRoadProfile(Config config) {
        var hadSubtype = config.contains("subtype");
        roadProfileSpec(RoadProfile.DEFAULT).correct(config);
        return new RoadProfile(
                config.getInt("surfaceWidth"),
                config.getInt("thickness"),
                config.getInt("shoulderWidth"),
                number(config, "tension"),
                number(config, "sampleSpacing"),
                hadSubtype
                        ? parseEnum(config, "subtype", SplineSubtype.class)
                        : SplineSubtype.CUSTOM,
                new SplineMaterialLinks(
                        config.get("surfaceRecipeId"),
                        config.get("shoulderRecipeId"),
                        config.get("foundationRecipeId"),
                        config.get("curbRecipeId"),
                        config.get("markingRecipeId"),
                        config.get("damageRecipeId")
                ),
                config.<List<Config>>get("crossSectionBands").stream()
                        .map(this::deserializeCrossSectionBand)
                        .toList(),
                deserializeCutout(config.get("cutout"))
        );
    }

    private ConfigSpec roadProfileSpec(RoadProfile defaults) {
        var spec = new ConfigSpec();
        spec.defineInRange(
                "surfaceWidth",
                defaults.surfaceWidth(),
                1,
                RoadProfile.MAX_SURFACE_WIDTH
        );
        spec.defineInRange(
                "thickness",
                defaults.thickness(),
                1,
                RoadProfile.MAX_THICKNESS
        );
        spec.defineInRange(
                "shoulderWidth",
                defaults.shoulderWidth(),
                0,
                RoadProfile.MAX_SHOULDER_WIDTH
        );
        spec.defineInRange("tension", defaults.tension(), 0.0, 1.0);
        spec.defineInRange(
                "sampleSpacing",
                defaults.sampleSpacing(),
                RoadProfile.MIN_SAMPLE_SPACING,
                RoadProfile.MAX_SAMPLE_SPACING
        );
        defineEnum(spec, "subtype", defaults.subtype());
        var links = defaults.materialLinks();
        spec.define(
                "surfaceRecipeId", links.surfaceRecipeId(),
                value -> value instanceof String
        );
        spec.define(
                "shoulderRecipeId", links.shoulderRecipeId(),
                value -> value instanceof String
        );
        spec.define(
                "foundationRecipeId", links.foundationRecipeId(),
                value -> value instanceof String
        );
        spec.define(
                "curbRecipeId", links.curbRecipeId(),
                value -> value instanceof String
        );
        spec.define(
                "markingRecipeId", links.markingRecipeId(),
                value -> value instanceof String
        );
        spec.define(
                "damageRecipeId", links.damageRecipeId(),
                value -> value instanceof String
        );
        spec.defineList(
                "crossSectionBands",
                () -> defaults.crossSectionBands().stream()
                        .map(this::serializeCrossSectionBand)
                        .toList(),
                Config.class::isInstance
        );
        spec.define(
                "cutout",
                serializeCutout(defaults.cutout()),
                Config.class::isInstance
        );
        return spec;
    }

    private Config serializeCrossSectionBand(
            SplineCrossSectionBand band
    ) {
        var config = Config.inMemory();
        config.set("name", band.name());
        config.set("width", band.width());
        config.set("heightOffset", band.heightOffset());
        config.set("depth", band.depth());
        config.set("side", enumName(band.side()));
        config.set("placement", enumName(band.placement()));
        config.set("recipeId", band.recipeId());
        crossSectionBandSpec(band).correct(config);
        return config;
    }

    private SplineCrossSectionBand deserializeCrossSectionBand(
            Config config
    ) {
        crossSectionBandSpec(SplineCrossSectionBand.DEFAULT).correct(config);
        return new SplineCrossSectionBand(
                config.get("name"),
                config.getInt("width"),
                config.getInt("heightOffset"),
                config.getInt("depth"),
                parseEnum(config, "side", SplineCrossSectionBand.Side.class),
                parseEnum(
                        config, "placement",
                        SplineCrossSectionBand.Placement.class
                ),
                config.get("recipeId")
        );
    }

    private ConfigSpec crossSectionBandSpec(
            SplineCrossSectionBand defaults
    ) {
        var spec = new ConfigSpec();
        spec.define(
                "name", defaults.name(),
                ProceduralPatternConfigSerializer::isNonBlankString
        );
        spec.defineInRange(
                "width", defaults.width(), 1,
                SplineCrossSectionBand.MAX_WIDTH
        );
        spec.defineInRange(
                "heightOffset", defaults.heightOffset(),
                -SplineCrossSectionBand.MAX_HEIGHT_OFFSET,
                SplineCrossSectionBand.MAX_HEIGHT_OFFSET
        );
        spec.defineInRange(
                "depth", defaults.depth(), 1,
                SplineCrossSectionBand.MAX_DEPTH
        );
        defineEnum(spec, "side", defaults.side());
        defineEnum(spec, "placement", defaults.placement());
        spec.define(
                "recipeId", defaults.recipeId(),
                value -> value instanceof String
        );
        return spec;
    }

    private Config serializeCutout(SplineCutoutConfig cutout) {
        var config = Config.inMemory();
        config.set("enabled", cutout.enabled());
        config.set("depth", cutout.depth());
        config.set("wallThickness", cutout.wallThickness());
        config.set("taperPerLayer", cutout.taperPerLayer());
        config.set("lineWalls", cutout.lineWalls());
        config.set("lineFloor", cutout.lineFloor());
        config.set("wallRecipeId", cutout.wallRecipeId());
        config.set("floorRecipeId", cutout.floorRecipeId());
        cutoutSpec(cutout).correct(config);
        return config;
    }

    private SplineCutoutConfig deserializeCutout(Config config) {
        cutoutSpec(SplineCutoutConfig.DEFAULT).correct(config);
        return new SplineCutoutConfig(
                config.get("enabled"),
                config.getInt("depth"),
                config.getInt("wallThickness"),
                number(config, "taperPerLayer"),
                config.get("lineWalls"),
                config.get("lineFloor"),
                config.get("wallRecipeId"),
                config.get("floorRecipeId")
        );
    }

    private ConfigSpec cutoutSpec(SplineCutoutConfig defaults) {
        var spec = new ConfigSpec();
        spec.define("enabled", defaults.enabled(), Boolean.class::isInstance);
        spec.defineInRange(
                "depth", defaults.depth(), 1, SplineCutoutConfig.MAX_DEPTH
        );
        spec.defineInRange(
                "wallThickness", defaults.wallThickness(), 0,
                SplineCutoutConfig.MAX_WALL_THICKNESS
        );
        spec.defineInRange(
                "taperPerLayer", defaults.taperPerLayer(), 0.0, 1.0
        );
        spec.define(
                "lineWalls", defaults.lineWalls(), Boolean.class::isInstance
        );
        spec.define(
                "lineFloor", defaults.lineFloor(), Boolean.class::isInstance
        );
        spec.define(
                "wallRecipeId", defaults.wallRecipeId(),
                value -> value instanceof String
        );
        spec.define(
                "floorRecipeId", defaults.floorRecipeId(),
                value -> value instanceof String
        );
        return spec;
    }

    private Config serializeTreeGeneration(TreeGenerationConfig value) {
        var config = Config.inMemory();
        config.set("mode", enumName(value.mode()));
        config.set("archetype", enumName(value.archetype()));
        config.set("variationStrength", enumName(value.variationStrength()));
        config.set("variationSource", enumName(value.variationSource()));
        config.set("styleLock", value.styleLock());
        config.set("variant", value.variant());
        config.set("minimumHeight", value.minimumHeight());
        config.set("maximumHeight", value.maximumHeight());
        config.set("minimumBranches", value.minimumBranches());
        config.set("maximumBranches", value.maximumBranches());
        config.set("baseRadius", value.baseRadius());
        config.set("tipRadius", value.tipRadius());
        config.set("trunkBend", value.trunkBend());
        config.set("branchLengthScale", value.branchLengthScale());
        config.set("branchDroop", value.branchDroop());
        config.set("crownRadius", value.crownRadius());
        config.set("crownHeight", value.crownHeight());
        config.set("foliageDensity", value.foliageDensity());
        config.set("foliageNoiseFrequency", value.foliageNoiseFrequency());
        config.set("rootCount", value.rootCount());
        config.set("rootLengthScale", value.rootLengthScale());
        config.set("trunkRecipeId", value.trunkRecipeId());
        config.set("branchRecipeId", value.branchRecipeId());
        config.set("foliageRecipeId", value.foliageRecipeId());
        config.set("rootRecipeId", value.rootRecipeId());
        config.set(
                "skeletonProfile",
                serializeTreeProfile(value.skeletonProfile())
        );
        treeGenerationSpec(value).correct(config);
        return config;
    }

    private TreeGenerationConfig deserializeTreeGeneration(Config config) {
        treeGenerationSpec(TreeGenerationConfig.DEFAULT).correct(config);
        return new TreeGenerationConfig(
                parseEnum(config, "mode", TreeMode.class),
                parseEnum(config, "archetype", TreeArchetype.class),
                parseEnum(
                        config,
                        "variationStrength",
                        TreeVariationStrength.class
                ),
                parseEnum(
                        config,
                        "variationSource",
                        TreeVariationSource.class
                ),
                config.get("styleLock"),
                config.getInt("variant"),
                config.getInt("minimumHeight"),
                config.getInt("maximumHeight"),
                config.getInt("minimumBranches"),
                config.getInt("maximumBranches"),
                config.getInt("baseRadius"),
                config.getInt("tipRadius"),
                number(config, "trunkBend"),
                number(config, "branchLengthScale"),
                number(config, "branchDroop"),
                config.getInt("crownRadius"),
                config.getInt("crownHeight"),
                number(config, "foliageDensity"),
                number(config, "foliageNoiseFrequency"),
                config.getInt("rootCount"),
                number(config, "rootLengthScale"),
                config.get("trunkRecipeId"),
                config.get("branchRecipeId"),
                config.get("foliageRecipeId"),
                config.get("rootRecipeId"),
                deserializeTreeProfile(config.get("skeletonProfile"))
        );
    }

    private ConfigSpec treeGenerationSpec(TreeGenerationConfig defaults) {
        var spec = new ConfigSpec();
        defineEnum(spec, "mode", defaults.mode());
        defineEnum(spec, "archetype", defaults.archetype());
        defineEnum(spec, "variationStrength", defaults.variationStrength());
        defineEnum(spec, "variationSource", defaults.variationSource());
        spec.define("styleLock", defaults.styleLock(), Boolean.class::isInstance);
        spec.defineInRange("variant", defaults.variant(), 0, Integer.MAX_VALUE);
        spec.defineInRange(
                "minimumHeight", defaults.minimumHeight(), 3,
                TreeGenerationConfig.MAX_HEIGHT
        );
        spec.defineInRange(
                "maximumHeight", defaults.maximumHeight(), 3,
                TreeGenerationConfig.MAX_HEIGHT
        );
        spec.defineInRange(
                "minimumBranches", defaults.minimumBranches(), 0,
                TreeGenerationConfig.MAX_BRANCHES
        );
        spec.defineInRange(
                "maximumBranches", defaults.maximumBranches(), 0,
                TreeGenerationConfig.MAX_BRANCHES
        );
        spec.defineInRange(
                "baseRadius", defaults.baseRadius(), 1,
                TreeGenerationConfig.MAX_RADIUS
        );
        spec.defineInRange(
                "tipRadius", defaults.tipRadius(), 1,
                TreeGenerationConfig.MAX_RADIUS
        );
        spec.defineInRange("trunkBend", defaults.trunkBend(), 0.0, 2.0);
        spec.defineInRange(
                "branchLengthScale", defaults.branchLengthScale(), 0.1, 4.0
        );
        spec.defineInRange(
                "branchDroop", defaults.branchDroop(), -1.0, 2.0
        );
        spec.defineInRange(
                "crownRadius", defaults.crownRadius(), 1,
                TreeGenerationConfig.MAX_RADIUS
        );
        spec.defineInRange(
                "crownHeight", defaults.crownHeight(), 1,
                TreeGenerationConfig.MAX_HEIGHT
        );
        spec.defineInRange(
                "foliageDensity", defaults.foliageDensity(), 0.0, 1.0
        );
        spec.defineInRange(
                "foliageNoiseFrequency",
                defaults.foliageNoiseFrequency(),
                0.01,
                2.0
        );
        spec.defineInRange(
                "rootCount", defaults.rootCount(), 0,
                TreeGenerationConfig.MAX_ROOTS
        );
        spec.defineInRange(
                "rootLengthScale", defaults.rootLengthScale(), 0.1, 4.0
        );
        spec.define(
                "trunkRecipeId", defaults.trunkRecipeId(),
                ProceduralPatternConfigSerializer::isBlankOrUuid
        );
        spec.define(
                "branchRecipeId", defaults.branchRecipeId(),
                ProceduralPatternConfigSerializer::isBlankOrUuid
        );
        spec.define(
                "foliageRecipeId", defaults.foliageRecipeId(),
                ProceduralPatternConfigSerializer::isBlankOrUuid
        );
        spec.define(
                "rootRecipeId", defaults.rootRecipeId(),
                ProceduralPatternConfigSerializer::isBlankOrUuid
        );
        spec.define(
                "skeletonProfile",
                serializeTreeProfile(defaults.skeletonProfile()),
                Config.class::isInstance
        );
        return spec;
    }

    private Config serializeTreeProfile(TreeProfile profile) {
        var config = Config.inMemory();
        config.set("trunkRadius", profile.trunkRadius());
        config.set("branchRadius", profile.branchRadius());
        config.set("canopyRadius", profile.canopyRadius());
        config.set("sampleSpacing", profile.sampleSpacing());
        treeProfileSpec(profile).correct(config);
        return config;
    }

    private TreeProfile deserializeTreeProfile(Config config) {
        treeProfileSpec(TreeProfile.DEFAULT).correct(config);
        return new TreeProfile(
                config.getInt("trunkRadius"),
                config.getInt("branchRadius"),
                config.getInt("canopyRadius"),
                number(config, "sampleSpacing")
        );
    }

    private ConfigSpec treeProfileSpec(TreeProfile defaults) {
        var spec = new ConfigSpec();
        spec.defineInRange(
                "trunkRadius",
                defaults.trunkRadius(),
                1,
                TreeProfile.MAX_TRUNK_RADIUS
        );
        spec.defineInRange(
                "branchRadius",
                defaults.branchRadius(),
                1,
                TreeProfile.MAX_BRANCH_RADIUS
        );
        spec.defineInRange(
                "canopyRadius",
                defaults.canopyRadius(),
                0,
                TreeProfile.MAX_CANOPY_RADIUS
        );
        spec.defineInRange(
                "sampleSpacing",
                defaults.sampleSpacing(),
                TreeProfile.MIN_SAMPLE_SPACING,
                TreeProfile.MAX_SAMPLE_SPACING
        );
        return spec;
    }

    private static ProceduralMaskLayer defaultMask() {
        return new ProceduralMaskLayer(
                new UUID(0L, 1L),
                "Layer",
                true,
                MaskedWeightSource.Shape.RANGE,
                Coordinate.Y,
                0.0,
                0.5,
                false,
                List.of("minecraft:stone"),
                MaskedWeightSource.Mode.MULTIPLY,
                2.0,
                4,
                1
        );
    }

    private Config serializeBlock(ProceduralBlockEntry entry) {
        var config = Config.inMemory();
        config.set("item", entry.itemId());
        config.set("weight", entry.weight());
        config.set("gradientStart", entry.gradientStart());
        config.set("gradientEnd", entry.gradientEnd());
        config.set("noiseMinimum", entry.noiseMinimum());
        config.set("noiseMaximum", entry.noiseMaximum());
        config.set("gradientPosition", entry.gradientPosition());
        blockSpec(entry).correct(config);
        return config;
    }

    private ProceduralBlockEntry deserializeBlock(Config config) {
        blockSpec(ProceduralBlockEntry.weighted("minecraft:stone", 1.0)).correct(config);
        return new ProceduralBlockEntry(
                config.get("item"),
                number(config, "weight"),
                number(config, "gradientStart"),
                number(config, "gradientEnd"),
                number(config, "noiseMinimum"),
                number(config, "noiseMaximum"),
                number(config, "gradientPosition")
        );
    }

    private ConfigSpec blockSpec(ProceduralBlockEntry defaults) {
        var spec = new ConfigSpec();
        spec.define("item", defaults.itemId(), ProceduralPatternConfigSerializer::isNonBlankString);
        defineNonNegative(spec, "weight", defaults.weight());
        defineNonNegative(spec, "gradientStart", defaults.gradientStart());
        defineNonNegative(spec, "gradientEnd", defaults.gradientEnd());
        defineNonNegative(spec, "noiseMinimum", defaults.noiseMinimum());
        defineNonNegative(spec, "noiseMaximum", defaults.noiseMaximum());
        spec.defineInRange(
                "gradientPosition",
                defaults.gradientPosition(),
                -1.0,
                1.0
        );
        return spec;
    }

    private Config serializeForbiddenPair(ProceduralForbiddenPair pair) {
        var config = Config.inMemory();
        config.set("first", pair.firstItemId());
        config.set("second", pair.secondItemId());
        pairSpec(pair.firstItemId(), pair.secondItemId()).correct(config);
        return config;
    }

    private ProceduralForbiddenPair deserializeForbiddenPair(Config config) {
        pairSpec("minecraft:stone", "minecraft:cobblestone").correct(config);
        return new ProceduralForbiddenPair(config.get("first"), config.get("second"));
    }

    private Config serializePreferredPair(ProceduralPreferredPair pair) {
        var config = Config.inMemory();
        config.set("item", pair.itemId());
        config.set("neighbor", pair.preferredNeighborItemId());
        config.set("multiplier", pair.multiplier());
        preferredSpec(pair).correct(config);
        return config;
    }

    private ProceduralPreferredPair deserializePreferredPair(Config config) {
        preferredSpec(new ProceduralPreferredPair(
                "minecraft:stone",
                "minecraft:cobblestone",
                2.0
        )).correct(config);
        return new ProceduralPreferredPair(
                config.get("item"),
                config.get("neighbor"),
                number(config, "multiplier")
        );
    }

    private Config serializeVerticalRule(ProceduralVerticalRule rule) {
        var config = Config.inMemory();
        config.set("item", rule.itemId());
        config.set("direction", enumName(rule.direction()));
        config.set("allowedItems", rule.allowedItemIds());
        config.set("rejectUnresolved", rule.rejectUnresolved());
        verticalSpec(rule).correct(config);
        return config;
    }

    private ProceduralVerticalRule deserializeVerticalRule(Config config) {
        verticalSpec(new ProceduralVerticalRule(
                "minecraft:stone",
                ProceduralVerticalRule.Direction.BELOW,
                List.of("minecraft:cobblestone"),
                false
        )).correct(config);
        return new ProceduralVerticalRule(
                config.get("item"),
                ProceduralVerticalRule.Direction.valueOf(
                        config.<String>get("direction").toUpperCase(Locale.ROOT)
                ),
                config.get("allowedItems"),
                config.get("rejectUnresolved")
        );
    }

    private ConfigSpec pairSpec(String first, String second) {
        var spec = new ConfigSpec();
        spec.define("first", first, ProceduralPatternConfigSerializer::isNonBlankString);
        spec.define("second", second, ProceduralPatternConfigSerializer::isNonBlankString);
        return spec;
    }

    private ConfigSpec preferredSpec(ProceduralPreferredPair defaults) {
        var spec = new ConfigSpec();
        spec.define("item", defaults.itemId(), ProceduralPatternConfigSerializer::isNonBlankString);
        spec.define(
                "neighbor",
                defaults.preferredNeighborItemId(),
                ProceduralPatternConfigSerializer::isNonBlankString
        );
        spec.defineInRange("multiplier", defaults.multiplier(), 0.000001, Double.MAX_VALUE);
        return spec;
    }

    private ConfigSpec verticalSpec(ProceduralVerticalRule defaults) {
        var spec = new ConfigSpec();
        spec.define("item", defaults.itemId(), ProceduralPatternConfigSerializer::isNonBlankString);
        spec.define(
                "direction",
                enumName(defaults.direction()),
                value -> isEnum(value, ProceduralVerticalRule.Direction.class)
        );
        spec.defineList(
                "allowedItems",
                defaults.allowedItemIds(),
                ProceduralPatternConfigSerializer::isNonBlankString
        );
        spec.define(
                "rejectUnresolved",
                defaults.rejectUnresolved(),
                Boolean.class::isInstance
        );
        return spec;
    }

    private static void defineNonNegative(ConfigSpec spec, String key, double value) {
        spec.defineInRange(key, value, 0.0, Double.MAX_VALUE);
    }

    private static double number(Config config, String key) {
        return config.<Number>get(key).doubleValue();
    }

    private static String enumName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E parseEnum(
            Config config,
            String key,
            Class<E> enumType
    ) {
        return Enum.valueOf(
                enumType,
                config.<String>get(key).toUpperCase(Locale.ROOT)
        );
    }

    private static void defineEnum(ConfigSpec spec, String key, Enum<?> value) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Class<? extends Enum> enumType = value.getDeclaringClass();
        spec.define(
                key,
                enumName(value),
                candidate -> isEnum(candidate, enumType)
        );
    }

    private static boolean isUuid(Object value) {
        try {
            UUID.fromString((String) value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isNonBlankString(Object value) {
        return value instanceof String string && !string.isBlank();
    }

    private static boolean isBlankOrUuid(Object value) {
        return value instanceof String string
                && (string.isBlank() || isUuid(string));
    }

    private static <E extends Enum<E>> boolean isEnum(Object value, Class<E> enumType) {
        if (!(value instanceof String string)) {
            return false;
        }
        try {
            Enum.valueOf(enumType, string.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
