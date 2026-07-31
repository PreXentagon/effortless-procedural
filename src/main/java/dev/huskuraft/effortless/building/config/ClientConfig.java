package dev.huskuraft.effortless.building.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;

import dev.huskuraft.effortless.building.structure.BuildMode;
import dev.huskuraft.effortless.building.structure.builder.Structure;

public record ClientConfig(
        BuilderConfig builderConfig,
        RenderConfig renderConfig,
        ProceduralSafetyConfig proceduralSafetyConfig,
        PatternConfig patternConfig,
        ClipboardConfig clipboardConfig,
        Map<BuildMode, Structure> structureMap
) {

    public ClientConfig(
            RenderConfig renderConfig,
            PatternConfig patternConfig,
            ClipboardConfig clipboardConfig) {
        this(
                BuilderConfig.DEFAULT,
                renderConfig,
                ProceduralSafetyConfig.DEFAULT,
                patternConfig,
                clipboardConfig,
                DEFAULT.structureMap()
        );
    }

    public ClientConfig(
            BuilderConfig builderConfig,
            RenderConfig renderConfig,
            PatternConfig patternConfig,
            ClipboardConfig clipboardConfig,
            Map<BuildMode, Structure> structureMap
    ) {
        this(
                builderConfig,
                renderConfig,
                ProceduralSafetyConfig.DEFAULT,
                patternConfig,
                clipboardConfig,
                structureMap
        );
    }

    public static ClientConfig DEFAULT = new ClientConfig(
            BuilderConfig.DEFAULT,
            RenderConfig.DEFAULT,
            ProceduralSafetyConfig.DEFAULT,
            PatternConfig.DEFAULT,
            ClipboardConfig.DEFAULT,
            Arrays.stream(BuildMode.values()).collect(Collectors.toMap(Function.identity(), BuildMode::getDefaultStructure, (e1, e2) -> e1, LinkedHashMap::new))
    );

    public ClientConfig withBuilderConfig(BuilderConfig builderConfig) {
        return new ClientConfig(builderConfig, renderConfig, proceduralSafetyConfig, patternConfig, clipboardConfig, structureMap);
    }

    public ClientConfig withRenderConfig(RenderConfig renderConfig) {
        return new ClientConfig(builderConfig, renderConfig, proceduralSafetyConfig, patternConfig, clipboardConfig, structureMap);
    }

    public ClientConfig withProceduralSafetyConfig(
            ProceduralSafetyConfig value
    ) {
        return new ClientConfig(
                builderConfig,
                renderConfig,
                value,
                patternConfig,
                clipboardConfig,
                structureMap
        );
    }

    public ClientConfig withPatternConfig(PatternConfig patternConfig) {
        return new ClientConfig(builderConfig, renderConfig, proceduralSafetyConfig, patternConfig, clipboardConfig, structureMap);
    }

    public ClientConfig withClipboardConfig(ClipboardConfig clipboardConfig) {
        return new ClientConfig(builderConfig, renderConfig, proceduralSafetyConfig, patternConfig, clipboardConfig, structureMap);
    }

    public ClientConfig withPassiveMode(boolean passiveMode) {
        return new ClientConfig(builderConfig.withPassiveMode(passiveMode), renderConfig, proceduralSafetyConfig, patternConfig, clipboardConfig, structureMap);
    }

    public ClientConfig withStructure(Structure structure) {
        var structureMap = Maps.newLinkedHashMap(this.structureMap);
        structureMap.put(structure.getMode(), structure);
        return new ClientConfig(builderConfig, renderConfig, proceduralSafetyConfig, patternConfig, clipboardConfig, Collections.unmodifiableMap(structureMap));
    }

    public Structure getStructure(BuildMode buildMode) {
        return structureMap.get(buildMode);
    }

}
