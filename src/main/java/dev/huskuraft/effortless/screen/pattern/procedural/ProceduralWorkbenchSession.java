package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import dev.huskuraft.effortless.building.pattern.Pattern;
import dev.huskuraft.effortless.building.pattern.Transformer;
import dev.huskuraft.effortless.building.pattern.Transformers;
import dev.huskuraft.effortless.client.pattern.procedural.config.PatternMaterialSource;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralBlockEntry;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralFieldAsset;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralNoiseConfig;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.pattern.procedural.SpatialField;

/**
 * One in-memory draft shared by every pane in the procedural workbench.
 *
 * <p>The session deliberately has no persistence or networking dependencies.
 * The workbench writes its materialized library only when the user presses its
 * final Save button.</p>
 */
final class ProceduralWorkbenchSession {

    private static final int MAX_HISTORY = 100;

    private ProceduralPatternLibrary library;
    private UUID selectedPresetId;
    private final Map<UUID, TextDraft> textDrafts = new HashMap<>();
    private final ArrayDeque<Snapshot> undoHistory = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redoHistory = new ArrayDeque<>();
    private String activeToolIdBaseline;
    private DirtySnapshot savedSnapshot;
    private Snapshot mutationStart;
    private int mutationDepth;

    ProceduralWorkbenchSession(ProceduralPatternLibrary library) {
        this.library = library;
        this.activeToolIdBaseline = library.activeToolId();
        this.selectedPresetId = library.activePreset()
                .or(() -> library.presets().stream().findFirst())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Procedural library must contain a preset"
                ))
                .id();
        savedSnapshot = dirtySnapshot();
    }

    ProceduralPatternLibrary library() {
        return library;
    }

    UUID selectedPresetId() {
        return selectedPresetId;
    }

    ProceduralPatternPreset selectedPreset() {
        return library.presets().stream()
                .filter(preset -> preset.id().equals(selectedPresetId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Selected procedural preset is missing"
                ));
    }

    TextDraft selectedTextDraft() {
        return textDrafts.computeIfAbsent(
                selectedPresetId,
                ignored -> TextDraft.from(selectedPreset())
        );
    }

    void select(UUID presetId) {
        if (library.presets().stream().noneMatch(
                preset -> preset.id().equals(presetId)
        )) {
            throw new IllegalArgumentException("Unknown procedural preset " + presetId);
        }
        selectedPresetId = presetId;
    }

    void setEnabled(boolean enabled) {
        mutate(() -> library = library.withEnabled(enabled));
    }

    void useSelected() {
        mutate(() -> library = library.withActivePreset(selectedPresetId));
    }

    void replaceSelected(UnaryOperator<ProceduralPatternPreset> operation) {
        mutate(() -> putSelected(operation.apply(selectedPreset())));
    }

    void replaceSelected(ProceduralPatternPreset preset) {
        if (!preset.id().equals(selectedPresetId)) {
            throw new IllegalArgumentException(
                    "Replacement preset id does not match the selection"
            );
        }
        mutate(() -> putSelected(preset));
    }

    void setNameDraft(String value) {
        mutate(() -> {
            var draft = selectedTextDraft().withName(value);
            textDrafts.put(selectedPresetId, draft);
            if (!value.trim().isEmpty()) {
                putSelected(selectedPreset().withName(value.trim()));
            }
        });
    }

    void setSeedDraft(String value) {
        mutate(() -> {
            var draft = selectedTextDraft().withSeed(value);
            textDrafts.put(selectedPresetId, draft);
            parseLong(value).ifPresent(parsed ->
                    putSelected(selectedPreset().withSeed(parsed))
            );
        });
    }

    void setNoiseSaltDraft(String value) {
        mutate(() -> {
            var draft = selectedTextDraft().withNoiseSalt(value);
            textDrafts.put(selectedPresetId, draft);
            parseLong(value).ifPresent(parsed -> {
                var preset = selectedPreset();
                putSelected(preset.withNoise(
                        preset.noiseEnabled(),
                        preset.noiseFrequency(),
                        parsed
                ));
            });
        });
    }

    void addPreset() {
        mutate(() -> {
            var added = ProceduralPatternPreset.DEFAULT.duplicate()
                    .withName("New pattern");
            library = library.put(added);
            selectedPresetId = added.id();
        });
    }

    void duplicateSelected() {
        mutate(() -> {
            var duplicate = selectedPreset().duplicate();
            library = library.put(duplicate);
            selectedPresetId = duplicate.id();
        });
    }

    void deleteSelected() {
        mutate(() -> {
            if (library.presets().size() <= 1) {
                return;
            }
            int oldIndex = selectedIndex();
            textDrafts.remove(selectedPresetId);
            library = library.remove(selectedPresetId);
            int next = Math.min(oldIndex, library.presets().size() - 1);
            selectedPresetId = library.presets().get(next).id();
        });
    }

    void moveSelected(int offset) {
        mutate(() -> {
            int current = selectedIndex();
            int target = current + offset;
            if (target < 0 || target >= library.presets().size()) {
                return;
            }
            var reordered = new ArrayList<>(library.presets());
            var value = reordered.remove(current);
            reordered.add(target, value);
            library = new ProceduralPatternLibrary(
                    library.enabled(),
                    library.activePresetId(),
                    reordered,
                    library.fieldAssets(),
                    library.activeToolId()
            );
        });
    }

    int selectedIndex() {
        for (int index = 0; index < library.presets().size(); index++) {
            if (library.presets().get(index).id().equals(selectedPresetId)) {
                return index;
            }
        }
        return -1;
    }

    void replaceLibrary(ProceduralPatternLibrary imported) {
        mutate(() -> {
            library = imported.withActiveToolId(activeToolIdBaseline);
            textDrafts.clear();
            selectedPresetId = imported.activePreset()
                    .or(() -> imported.presets().stream().findFirst())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Imported pattern library has no presets"
                    ))
                    .id();
        });
    }

    void setActiveToolId(String value) {
        activeToolIdBaseline = value;
        library = library.withActiveToolId(value);
        if (savedSnapshot != null) {
            savedSnapshot = new DirtySnapshot(
                    savedSnapshot.library().withActiveToolId(value),
                    savedSnapshot.changedTextDrafts()
            );
        }
    }

    ProceduralPatternLibrary materialize() {
        var result = library;
        for (var preset : library.presets()) {
            var draft = textDrafts.getOrDefault(
                    preset.id(),
                    TextDraft.from(preset)
            );
            String name = draft.name().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException(
                        "Recipe name cannot be empty"
                );
            }
            long seed = parseRequiredLong(draft.seed(), "Seed");
            long noiseSalt = parseRequiredLong(
                    draft.noiseSalt(),
                    "Noise salt"
            );
            result = result.put(
                    preset.withName(name)
                            .withSeed(seed)
                            .withNoise(
                                    preset.noiseEnabled(),
                                    preset.noiseFrequency(),
                                    noiseSalt
                            )
            );
        }
        library = result;
        return result;
    }

    void updateBlock(int index, UnaryOperator<ProceduralBlockEntry> operation) {
        mutate(() -> {
            var preset = selectedPreset();
            if (index < 0 || index >= preset.blocks().size()) {
                return;
            }
            var blocks = new ArrayList<>(preset.blocks());
            blocks.set(index, operation.apply(blocks.get(index)));
            putSelected(preset.withBlocks(blocks));
        });
    }

    int addBlock() {
        var addedIndex = new int[1];
        mutate(() -> {
            var preset = selectedPreset();
            var blocks = new ArrayList<>(preset.blocks());
            String itemId = uniqueDefaultBlockId(blocks);
            blocks.add(ProceduralBlockEntry.weighted(itemId, 1.0));
            putSelected(preset.withBlocks(blocks));
            addedIndex[0] = blocks.size() - 1;
        });
        return addedIndex[0];
    }

    int addSpecialBlock(String itemId) {
        var addedIndex = new int[] {0};
        mutate(() -> {
            var preset = selectedPreset();
            var blocks = new ArrayList<>(preset.blocks());
            for (int index = 0; index < blocks.size(); index++) {
                if (blocks.get(index).itemId().equals(itemId)) {
                    addedIndex[0] = index;
                    return;
                }
            }
            blocks.add(ProceduralBlockEntry.weighted(itemId, 1.0));
            putSelected(preset.withBlocks(blocks));
            addedIndex[0] = blocks.size() - 1;
        });
        return addedIndex[0];
    }

    int deleteBlock(int index) {
        var result = new int[1];
        mutate(() -> {
            var preset = selectedPreset();
            var blocks = new ArrayList<>(preset.blocks());
            if (blocks.size() <= 1 || index < 0 || index >= blocks.size()) {
                result[0] = Math.max(
                        0,
                        Math.min(index, blocks.size() - 1)
                );
                return;
            }
            blocks.remove(index);
            putSelected(preset.withBlocks(blocks));
            result[0] = Math.min(index, blocks.size() - 1);
        });
        return result[0];
    }

    int moveBlock(int index, int offset) {
        var result = new int[] {index};
        mutate(() -> {
            var preset = selectedPreset();
            var blocks = new ArrayList<>(preset.blocks());
            int target = index + offset;
            if (index < 0 || index >= blocks.size()
                    || target < 0 || target >= blocks.size()) {
                return;
            }
            var entry = blocks.remove(index);
            blocks.add(target, entry);
            putSelected(preset.withBlocks(blocks));
            result[0] = target;
        });
        return result[0];
    }

    void setMaterialSource(PatternMaterialSource source) {
        replaceSelected(preset -> preset.withMaterialSource(source));
    }

    UUID createFieldAsset(String name) {
        var result = new UUID[1];
        mutate(() -> {
            var preset = selectedPreset();
            var asset = new ProceduralFieldAsset(
                    UUID.randomUUID(),
                    name,
                    preset.advanced().gradientField(),
                    preset.advanced().noiseConfig()
            );
            library = library.putFieldAsset(asset);
            result[0] = asset.id();
        });
        return result[0];
    }

    void linkGradientFieldAsset(String id) {
        mutate(() -> {
            var preset = selectedPreset();
            var advanced = preset.advanced();
            if (id == null || id.isBlank()) {
                putSelected(preset.withAdvanced(
                        advanced.withGradientFieldAsset("")
                ));
                return;
            }
            var asset = library.fieldAsset(id).orElseThrow(() ->
                    new IllegalArgumentException(
                            "Unknown gradient field asset " + id
                    )
            );
            putSelected(preset.withAdvanced(
                    advanced.withGradientField(asset.gradientField())
                            .withGradientFieldAsset(id)
            ));
        });
    }

    void linkNoiseFieldAsset(String id) {
        mutate(() -> {
            var preset = selectedPreset();
            var advanced = preset.advanced();
            if (id == null || id.isBlank()) {
                putSelected(preset.withAdvanced(
                        advanced.withNoiseFieldAsset("")
                ));
                return;
            }
            var asset = library.fieldAsset(id).orElseThrow(() ->
                    new IllegalArgumentException(
                            "Unknown noise field asset " + id
                    )
            );
            putSelected(preset.withAdvanced(
                    advanced.withNoiseConfig(asset.noiseConfig())
                            .withNoiseFieldAsset(id)
            ));
        });
    }

    void updateGradientField(SpatialField field) {
        mutate(() -> {
            var preset = selectedPreset();
            String assetId = preset.advanced().gradientFieldAssetId();
            putSelected(
                    preset.withGradient(
                                    preset.gradientEnabled(),
                                    field.coordinate()
                            )
                            .withAdvanced(
                                    preset.advanced().withGradientField(field)
                            )
            );
            if (assetId.isBlank()) {
                return;
            }
            var asset = library.fieldAsset(assetId).orElse(null);
            if (asset == null) {
                return;
            }
            library = library.putFieldAsset(asset.withGradientField(field));
            propagateGradientAsset(assetId, field);
        });
    }

    void updateNoiseConfig(ProceduralNoiseConfig config) {
        mutate(() -> {
            var preset = selectedPreset();
            String assetId = preset.advanced().noiseFieldAssetId();
            putSelected(preset.withAdvanced(
                    preset.advanced().withNoiseConfig(config)
            ));
            if (assetId.isBlank()) {
                return;
            }
            var asset = library.fieldAsset(assetId).orElse(null);
            if (asset == null) {
                return;
            }
            library = library.putFieldAsset(asset.withNoiseConfig(config));
            propagateNoiseAsset(assetId, config);
        });
    }

    void deleteFieldAsset(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        mutate(() -> library = library.removeFieldAsset(UUID.fromString(id)));
    }

    private void propagateGradientAsset(String id, SpatialField field) {
        for (var candidate : List.copyOf(library.presets())) {
            if (candidate.advanced().gradientFieldAssetId().equals(id)) {
                library = library.put(candidate.withAdvanced(
                        candidate.advanced().withGradientField(field)
                ));
            }
        }
    }

    private void propagateNoiseAsset(
            String id,
            ProceduralNoiseConfig config
    ) {
        for (var candidate : List.copyOf(library.presets())) {
            if (candidate.advanced().noiseFieldAssetId().equals(id)) {
                library = library.put(candidate.withAdvanced(
                        candidate.advanced().withNoiseConfig(config)
                ));
            }
        }
    }

    void importStockPattern(Pattern pattern) {
        replaceSelected(preset -> preset.withImportedStockPattern(pattern));
    }

    void importStockGeometry(Pattern pattern) {
        var geometry = pattern.transformers().stream()
                .filter(transformer ->
                        transformer.getType() != Transformers.RANDOMIZER
                )
                .toList();
        if (!geometry.isEmpty()) {
            replaceSelected(preset -> preset.withStockTransformers(geometry));
        }
    }

    void addTransformer(Transformer transformer) {
        mutate(() -> {
            var preset = selectedPreset();
            var values = new ArrayList<Transformer>(
                    preset.stockTransformers()
            );
            values.add(transformer);
            putSelected(preset.withStockTransformers(values));
        });
    }

    void updateTransformer(
            int index,
            UnaryOperator<Transformer> operation
    ) {
        mutate(() -> {
            var preset = selectedPreset();
            if (index < 0 || index >= preset.stockTransformers().size()) {
                return;
            }
            var values = new ArrayList<Transformer>(
                    preset.stockTransformers()
            );
            var updated = operation.apply(values.get(index));
            if (updated.getType() == Transformers.RANDOMIZER) {
                throw new IllegalArgumentException(
                        "Material randomizers belong in the Materials stage"
                );
            }
            values.set(index, updated);
            putSelected(preset.withStockTransformers(values));
        });
    }

    int deleteTransformer(int index) {
        var result = new int[] {index};
        mutate(() -> {
            var preset = selectedPreset();
            var values = new ArrayList<Transformer>(
                    preset.stockTransformers()
            );
            if (index < 0 || index >= values.size()) {
                result[0] = Math.max(
                        0,
                        Math.min(index, values.size() - 1)
                );
                return;
            }
            values.remove(index);
            putSelected(preset.withStockTransformers(values));
            result[0] = Math.max(0, Math.min(index, values.size() - 1));
        });
        return result[0];
    }

    int moveTransformer(int index, int offset) {
        var result = new int[] {index};
        mutate(() -> {
            var preset = selectedPreset();
            var values = new ArrayList<Transformer>(
                    preset.stockTransformers()
            );
            int target = index + offset;
            if (index < 0 || index >= values.size()
                    || target < 0 || target >= values.size()) {
                return;
            }
            var transformer = values.remove(index);
            values.add(target, transformer);
            putSelected(preset.withStockTransformers(values));
            result[0] = target;
        });
        return result[0];
    }

    boolean canUndo() {
        return !undoHistory.isEmpty();
    }

    boolean canRedo() {
        return !redoHistory.isEmpty();
    }

    void undo() {
        if (undoHistory.isEmpty()) {
            return;
        }
        redoHistory.addLast(snapshot());
        restore(undoHistory.removeLast());
    }

    void redo() {
        if (redoHistory.isEmpty()) {
            return;
        }
        undoHistory.addLast(snapshot());
        restore(redoHistory.removeLast());
    }

    boolean isDirty() {
        return !dirtySnapshot().equals(savedSnapshot);
    }

    void markSaved() {
        savedSnapshot = dirtySnapshot();
    }

    private void putSelected(ProceduralPatternPreset preset) {
        if (!preset.id().equals(selectedPresetId)) {
            throw new IllegalArgumentException(
                    "Replacement preset id does not match the selection"
            );
        }
        library = library.put(preset);
    }

    private void mutate(Runnable operation) {
        boolean outermost = mutationDepth == 0;
        if (outermost) {
            mutationStart = snapshot();
        }
        mutationDepth++;
        try {
            operation.run();
        } finally {
            mutationDepth--;
            if (outermost) {
                var after = snapshot();
                if (!after.equals(mutationStart)) {
                    undoHistory.addLast(mutationStart);
                    while (undoHistory.size() > MAX_HISTORY) {
                        undoHistory.removeFirst();
                    }
                    redoHistory.clear();
                }
                mutationStart = null;
            }
        }
    }

    private Snapshot snapshot() {
        return new Snapshot(
                library,
                selectedPresetId,
                Map.copyOf(textDrafts)
        );
    }

    private DirtySnapshot dirtySnapshot() {
        var changedDrafts = new HashMap<UUID, TextDraft>();
        for (var entry : textDrafts.entrySet()) {
            var preset = library.presets().stream()
                    .filter(value -> value.id().equals(entry.getKey()))
                    .findFirst();
            if (preset.isEmpty()
                    || !entry.getValue().equals(TextDraft.from(preset.get()))) {
                changedDrafts.put(entry.getKey(), entry.getValue());
            }
        }
        return new DirtySnapshot(
                library.withActiveToolId(activeToolIdBaseline),
                changedDrafts
        );
    }

    private void restore(Snapshot value) {
        library = value.library().withActiveToolId(activeToolIdBaseline);
        selectedPresetId = value.selectedPresetId();
        textDrafts.clear();
        textDrafts.putAll(value.textDrafts());
    }

    private static String uniqueDefaultBlockId(List<ProceduralBlockEntry> blocks) {
        var choices = List.of(
                "minecraft:stone",
                "minecraft:cobblestone",
                "minecraft:andesite",
                "minecraft:diorite",
                "minecraft:granite",
                "minecraft:deepslate"
        );
        for (var choice : choices) {
            if (blocks.stream().noneMatch(entry -> entry.itemId().equals(choice))) {
                return choice;
            }
        }
        return "minecraft:stone";
    }

    private static java.util.OptionalLong parseLong(String value) {
        try {
            return java.util.OptionalLong.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return java.util.OptionalLong.empty();
        }
    }

    private static long parseRequiredLong(String value, String label) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a whole number");
        }
    }

    record TextDraft(String name, String seed, String noiseSalt) {

        static TextDraft from(ProceduralPatternPreset preset) {
            return new TextDraft(
                    preset.name(),
                    Long.toString(preset.seed()),
                    Long.toString(preset.noiseSalt())
            );
        }

        TextDraft withName(String value) {
            return new TextDraft(value, seed, noiseSalt);
        }

        TextDraft withSeed(String value) {
            return new TextDraft(name, value, noiseSalt);
        }

        TextDraft withNoiseSalt(String value) {
            return new TextDraft(name, seed, value);
        }
    }

    private record Snapshot(
            ProceduralPatternLibrary library,
            UUID selectedPresetId,
            Map<UUID, TextDraft> textDrafts
    ) {

        private Snapshot {
            textDrafts = Map.copyOf(textDrafts);
        }
    }

    private record DirtySnapshot(
            ProceduralPatternLibrary library,
            Map<UUID, TextDraft> changedTextDrafts
    ) {

        private DirtySnapshot {
            changedTextDrafts = Map.copyOf(changedTextDrafts);
        }
    }
}
