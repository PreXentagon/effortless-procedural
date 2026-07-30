package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ProceduralPatternLibrary(
        boolean enabled,
        UUID activePresetId,
        List<ProceduralPatternPreset> presets,
        List<ProceduralFieldAsset> fieldAssets
) {

    public static final int MAX_COMPOSITION_DEPTH = 64;
    public static final ProceduralPatternLibrary DEFAULT = new ProceduralPatternLibrary(
            false,
            ProceduralPatternPreset.DEFAULT.id(),
            List.of(ProceduralPatternPreset.DEFAULT),
            List.of()
    );

    public ProceduralPatternLibrary {
        java.util.Objects.requireNonNull(
                activePresetId,
                "Active procedural preset id"
        );
        presets = List.copyOf(presets);
        fieldAssets = List.copyOf(fieldAssets);
        var ids = new java.util.HashSet<UUID>();
        for (var preset : presets) {
            if (!ids.add(preset.id())) {
                throw new IllegalArgumentException(
                        "Duplicate procedural preset id " + preset.id()
                );
            }
        }
        ids.clear();
        for (var asset : fieldAssets) {
            if (!ids.add(asset.id())) {
                throw new IllegalArgumentException(
                        "Duplicate procedural field asset id " + asset.id()
                );
            }
        }
    }

    public ProceduralPatternLibrary(
            boolean enabled,
            UUID activePresetId,
            List<ProceduralPatternPreset> presets
    ) {
        this(enabled, activePresetId, presets, List.of());
    }

    public Optional<ProceduralPatternPreset> activePreset() {
        return presets.stream().filter(preset -> preset.id().equals(activePresetId)).findFirst();
    }

    public PresetResolution resolvedActivePreset() {
        var active = activePreset();
        if (active.isEmpty()) {
            return PresetResolution.failure(
                    "Active procedural preset '" + activePresetId + "' was not found"
            );
        }
        try {
            return PresetResolution.success(resolveAssets(
                    resolve(active.get(), new LinkedHashSet<>())
            ));
        } catch (IllegalArgumentException exception) {
            return PresetResolution.failure(exception.getMessage());
        }
    }

    public ProceduralPatternLibrary cycleActivePreset(int direction) {
        if (presets.isEmpty()) {
            return this;
        }
        int current = 0;
        for (int index = 0; index < presets.size(); index++) {
            if (presets.get(index).id().equals(activePresetId)) {
                current = index;
                break;
            }
        }
        int next = Math.floorMod(current + Integer.signum(direction), presets.size());
        return withActivePreset(presets.get(next).id());
    }

    public ProceduralPatternLibrary withEnabled(boolean value) {
        return new ProceduralPatternLibrary(
                value,
                activePresetId,
                presets,
                fieldAssets
        );
    }

    public ProceduralPatternLibrary withActivePreset(UUID value) {
        if (presets.stream().noneMatch(preset -> preset.id().equals(value))) {
            throw new IllegalArgumentException("Unknown procedural preset " + value);
        }
        return new ProceduralPatternLibrary(enabled, value, presets, fieldAssets);
    }

    public ProceduralPatternLibrary put(ProceduralPatternPreset preset) {
        var result = new ArrayList<>(presets);
        int index = -1;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).id().equals(preset.id())) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            result.set(index, preset);
        } else {
            result.add(preset);
        }
        return new ProceduralPatternLibrary(
                enabled,
                activePresetId,
                result,
                fieldAssets
        );
    }

    public ProceduralPatternLibrary remove(UUID id) {
        var result = presets.stream().filter(preset -> !preset.id().equals(id)).toList();
        if (result.isEmpty()) {
            return DEFAULT.withEnabled(enabled);
        }
        var active = activePresetId.equals(id) ? result.get(0).id() : activePresetId;
        return new ProceduralPatternLibrary(enabled, active, result, fieldAssets);
    }

    public Optional<ProceduralFieldAsset> fieldAsset(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return fieldAssets.stream()
                .filter(asset -> asset.id().toString().equals(id))
                .findFirst();
    }

    public ProceduralPatternLibrary putFieldAsset(
            ProceduralFieldAsset asset
    ) {
        var result = new ArrayList<>(fieldAssets);
        int index = -1;
        for (int current = 0; current < result.size(); current++) {
            if (result.get(current).id().equals(asset.id())) {
                index = current;
                break;
            }
        }
        if (index >= 0) {
            result.set(index, asset);
        } else {
            result.add(asset);
        }
        return new ProceduralPatternLibrary(
                enabled,
                activePresetId,
                presets,
                result
        );
    }

    public ProceduralPatternLibrary removeFieldAsset(UUID id) {
        var assets = fieldAssets.stream()
                .filter(asset -> !asset.id().equals(id))
                .toList();
        String removed = id.toString();
        var changedPresets = presets.stream().map(preset -> {
            var advanced = preset.advanced();
            if (advanced.gradientFieldAssetId().equals(removed)) {
                advanced = advanced.withGradientFieldAsset("");
            }
            if (advanced.noiseFieldAssetId().equals(removed)) {
                advanced = advanced.withNoiseFieldAsset("");
            }
            return preset.withAdvanced(advanced);
        }).toList();
        return new ProceduralPatternLibrary(
                enabled,
                activePresetId,
                changedPresets,
                assets
        );
    }

    private ProceduralPatternPreset resolve(
            ProceduralPatternPreset child,
            Set<UUID> resolving
    ) {
        if (resolving.size() >= MAX_COMPOSITION_DEPTH) {
            throw new IllegalArgumentException(
                    "Preset composition exceeds " + MAX_COMPOSITION_DEPTH
                            + " parent levels at '" + child.name() + "'"
            );
        }
        if (!resolving.add(child.id())) {
            throw new IllegalArgumentException(
                    "Preset composition cycle contains '" + child.name() + "'"
            );
        }
        var advanced = child.advanced();
        if (advanced.parentPresetId().isBlank()) {
            resolving.remove(child.id());
            return child.withAdvanced(advanced.withoutParent());
        }

        UUID parentId;
        try {
            parentId = UUID.fromString(advanced.parentPresetId());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Preset '" + child.name() + "' has an invalid parent id"
            );
        }
        var parent = presets.stream()
                .filter(preset -> preset.id().equals(parentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Preset '" + child.name() + "' references a missing parent"
                ));
        var resolvedParent = resolve(parent, resolving);
        resolving.remove(child.id());

        var blocks = advanced.inheritBlocks()
                ? mergeBlocks(resolvedParent.blocks(), child.blocks())
                : child.blocks();
        var forbidden = advanced.inheritRules()
                ? concat(resolvedParent.forbiddenAdjacency(), child.forbiddenAdjacency())
                : child.forbiddenAdjacency();
        var preferred = advanced.inheritRules()
                ? concat(resolvedParent.preferredAdjacency(), child.preferredAdjacency())
                : child.preferredAdjacency();
        var vertical = advanced.inheritRules()
                ? concat(resolvedParent.verticalRules(), child.verticalRules())
                : child.verticalRules();
        var resolvedAdvanced = advanced.inheritRules()
                ? advanced.withInheritedRules(resolvedParent.advanced())
                : advanced.withoutParent();

        return new ProceduralPatternPreset(
                child.id(),
                child.name(),
                child.seed(),
                child.retryLimit(),
                child.fallbackItemId(),
                blocks,
                child.sequenceEnabled(),
                child.sequenceOffset(),
                child.sequenceAlternateWeight(),
                child.gradientEnabled(),
                child.gradientCoordinate(),
                child.noiseEnabled(),
                child.noiseFrequency(),
                child.noiseSalt(),
                child.inspectExistingWorld(),
                forbidden,
                preferred,
                child.maximumRunLength(),
                vertical,
                resolvedAdvanced,
                child.materialSource(),
                child.stockTransformers()
        );
    }

    private ProceduralPatternPreset resolveAssets(
            ProceduralPatternPreset preset
    ) {
        var advanced = preset.advanced();
        if (!advanced.gradientFieldAssetId().isBlank()) {
            var asset = fieldAsset(advanced.gradientFieldAssetId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Preset '" + preset.name()
                                    + "' references a missing gradient field asset"
                    ));
            advanced = advanced.withGradientField(asset.gradientField());
        }
        if (!advanced.noiseFieldAssetId().isBlank()) {
            var asset = fieldAsset(advanced.noiseFieldAssetId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Preset '" + preset.name()
                                    + "' references a missing noise field asset"
                    ));
            advanced = advanced.withNoiseConfig(asset.noiseConfig());
        }
        return preset.withAdvanced(advanced);
    }

    private static List<ProceduralBlockEntry> mergeBlocks(
            List<ProceduralBlockEntry> parent,
            List<ProceduralBlockEntry> child
    ) {
        var byId = new LinkedHashMap<String, ProceduralBlockEntry>();
        for (var block : parent) {
            byId.put(block.itemId(), block);
        }
        for (var block : child) {
            byId.put(block.itemId(), block);
        }
        return List.copyOf(byId.values());
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        var result = new ArrayList<T>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    public record PresetResolution(
            Optional<ProceduralPatternPreset> preset,
            List<String> errors
    ) {

        public PresetResolution {
            errors = List.copyOf(errors);
        }

        public static PresetResolution success(ProceduralPatternPreset preset) {
            return new PresetResolution(Optional.of(preset), List.of());
        }

        public static PresetResolution failure(String message) {
            return new PresetResolution(Optional.empty(), List.of(message));
        }

        public boolean isSuccess() {
            return preset.isPresent();
        }
    }
}
