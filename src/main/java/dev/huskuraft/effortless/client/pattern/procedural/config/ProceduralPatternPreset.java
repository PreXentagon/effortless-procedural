package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import dev.huskuraft.effortless.building.pattern.Transformer;
import dev.huskuraft.effortless.building.pattern.Pattern;
import dev.huskuraft.effortless.building.pattern.Transformers;
import dev.huskuraft.effortless.building.pattern.randomize.ItemRandomizer;
import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;

public record ProceduralPatternPreset(
        UUID id,
        String name,
        long seed,
        int retryLimit,
        String fallbackItemId,
        List<ProceduralBlockEntry> blocks,
        boolean sequenceEnabled,
        int sequenceOffset,
        double sequenceAlternateWeight,
        boolean gradientEnabled,
        Coordinate gradientCoordinate,
        boolean noiseEnabled,
        double noiseFrequency,
        long noiseSalt,
        boolean inspectExistingWorld,
        List<ProceduralForbiddenPair> forbiddenAdjacency,
        List<ProceduralPreferredPair> preferredAdjacency,
        int maximumRunLength,
        List<ProceduralVerticalRule> verticalRules,
        ProceduralAdvancedConfig advanced,
        PatternMaterialSource materialSource,
        List<Transformer> stockTransformers
) {

    public static final UUID DEFAULT_ID = UUID.nameUUIDFromBytes(
            "effortless:default-procedural-pattern".getBytes(StandardCharsets.UTF_8)
    );

    public static final ProceduralPatternPreset DEFAULT = new ProceduralPatternPreset(
            DEFAULT_ID,
            "Default procedural pattern",
            0L,
            8,
            "minecraft:stone",
            List.of(
                    ProceduralBlockEntry.weighted("minecraft:stone", 3.0),
                    ProceduralBlockEntry.weighted("minecraft:cobblestone", 1.0)
            ),
            false,
            0,
            0.05,
            false,
            Coordinate.Y,
            false,
            0.1,
            0L,
            false,
            List.of(),
            List.of(),
            0,
            List.of(),
            ProceduralAdvancedConfig.DEFAULT,
            PatternMaterialSource.CUSTOM_PALETTE,
            List.of()
    );

    public ProceduralPatternPreset(
            UUID id,
            String name,
            long seed,
            int retryLimit,
            String fallbackItemId,
            List<ProceduralBlockEntry> blocks,
            boolean sequenceEnabled,
            int sequenceOffset,
            double sequenceAlternateWeight,
            boolean gradientEnabled,
            Coordinate gradientCoordinate,
            boolean noiseEnabled,
            double noiseFrequency,
            long noiseSalt,
            boolean inspectExistingWorld,
            List<ProceduralForbiddenPair> forbiddenAdjacency,
            List<ProceduralPreferredPair> preferredAdjacency,
            int maximumRunLength,
            List<ProceduralVerticalRule> verticalRules
    ) {
        this(
                id,
                name,
                seed,
                retryLimit,
                fallbackItemId,
                blocks,
                sequenceEnabled,
                sequenceOffset,
                sequenceAlternateWeight,
                gradientEnabled,
                gradientCoordinate,
                noiseEnabled,
                noiseFrequency,
                noiseSalt,
                inspectExistingWorld,
                forbiddenAdjacency,
                preferredAdjacency,
                maximumRunLength,
                verticalRules,
                ProceduralAdvancedConfig.DEFAULT,
                PatternMaterialSource.CUSTOM_PALETTE,
                List.of()
        );
    }

    public ProceduralPatternPreset(
            UUID id,
            String name,
            long seed,
            int retryLimit,
            String fallbackItemId,
            List<ProceduralBlockEntry> blocks,
            boolean sequenceEnabled,
            int sequenceOffset,
            double sequenceAlternateWeight,
            boolean gradientEnabled,
            Coordinate gradientCoordinate,
            boolean noiseEnabled,
            double noiseFrequency,
            long noiseSalt,
            boolean inspectExistingWorld,
            List<ProceduralForbiddenPair> forbiddenAdjacency,
            List<ProceduralPreferredPair> preferredAdjacency,
            int maximumRunLength,
            List<ProceduralVerticalRule> verticalRules,
            ProceduralAdvancedConfig advanced
    ) {
        this(
                id,
                name,
                seed,
                retryLimit,
                fallbackItemId,
                blocks,
                sequenceEnabled,
                sequenceOffset,
                sequenceAlternateWeight,
                gradientEnabled,
                gradientCoordinate,
                noiseEnabled,
                noiseFrequency,
                noiseSalt,
                inspectExistingWorld,
                forbiddenAdjacency,
                preferredAdjacency,
                maximumRunLength,
                verticalRules,
                advanced,
                PatternMaterialSource.CUSTOM_PALETTE,
                List.of()
        );
    }

    public ProceduralPatternPreset {
        blocks = List.copyOf(blocks);
        forbiddenAdjacency = List.copyOf(forbiddenAdjacency);
        preferredAdjacency = List.copyOf(preferredAdjacency);
        verticalRules = List.copyOf(verticalRules);
        stockTransformers = List.copyOf(stockTransformers);
    }

    public ProceduralPatternPreset withName(String value) {
        return copy(value, seed, retryLimit, fallbackItemId, blocks, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, gradientEnabled,
                gradientCoordinate, noiseEnabled, noiseFrequency, noiseSalt,
                inspectExistingWorld, forbiddenAdjacency, preferredAdjacency,
                maximumRunLength, verticalRules);
    }

    public ProceduralPatternPreset withSeed(long value) {
        return copy(name, value, retryLimit, fallbackItemId, blocks, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, gradientEnabled,
                gradientCoordinate, noiseEnabled, noiseFrequency, noiseSalt,
                inspectExistingWorld, forbiddenAdjacency, preferredAdjacency,
                maximumRunLength, verticalRules);
    }

    public ProceduralPatternPreset withRetryLimit(int value) {
        return copy(name, seed, value, fallbackItemId, blocks, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, gradientEnabled,
                gradientCoordinate, noiseEnabled, noiseFrequency, noiseSalt,
                inspectExistingWorld, forbiddenAdjacency, preferredAdjacency,
                maximumRunLength, verticalRules);
    }

    public ProceduralPatternPreset withFallbackItemId(String value) {
        return copy(name, seed, retryLimit, value, blocks, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, gradientEnabled,
                gradientCoordinate, noiseEnabled, noiseFrequency, noiseSalt,
                inspectExistingWorld, forbiddenAdjacency, preferredAdjacency,
                maximumRunLength, verticalRules);
    }

    public ProceduralPatternPreset withBlocks(List<ProceduralBlockEntry> value) {
        String fallback = value.stream()
                .anyMatch(entry -> entry.itemId().equals(fallbackItemId))
                ? fallbackItemId
                : value.isEmpty() ? fallbackItemId : value.get(0).itemId();
        return copy(name, seed, retryLimit, fallback, value, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, gradientEnabled,
                gradientCoordinate, noiseEnabled, noiseFrequency, noiseSalt,
                inspectExistingWorld, forbiddenAdjacency, preferredAdjacency,
                maximumRunLength, verticalRules);
    }

    public ProceduralPatternPreset withGradient(
            boolean enabled,
            Coordinate coordinate
    ) {
        var changed = copy(name, seed, retryLimit, fallbackItemId, blocks, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, enabled, coordinate,
                noiseEnabled, noiseFrequency, noiseSalt, inspectExistingWorld,
                forbiddenAdjacency, preferredAdjacency, maximumRunLength,
                verticalRules);
        return changed.withAdvanced(
                changed.advanced().withGradientField(
                        changed.advanced().gradientField()
                                .withCoordinate(coordinate)
                )
        );
    }

    public ProceduralPatternPreset withNoise(
            boolean enabled,
            double frequency,
            long salt
    ) {
        return copy(name, seed, retryLimit, fallbackItemId, blocks, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, gradientEnabled,
                gradientCoordinate, enabled, frequency, salt, inspectExistingWorld,
                forbiddenAdjacency, preferredAdjacency, maximumRunLength,
                verticalRules);
    }

    public ProceduralPatternPreset withSequence(
            boolean enabled,
            int offset,
            double alternateWeight
    ) {
        return copy(name, seed, retryLimit, fallbackItemId, blocks, enabled, offset,
                alternateWeight, gradientEnabled, gradientCoordinate, noiseEnabled,
                noiseFrequency, noiseSalt, inspectExistingWorld, forbiddenAdjacency,
                preferredAdjacency, maximumRunLength, verticalRules);
    }

    public ProceduralPatternPreset withInspectExistingWorld(boolean value) {
        return copy(name, seed, retryLimit, fallbackItemId, blocks, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, gradientEnabled,
                gradientCoordinate, noiseEnabled, noiseFrequency, noiseSalt, value,
                forbiddenAdjacency, preferredAdjacency, maximumRunLength,
                verticalRules);
    }

    public ProceduralPatternPreset withForbiddenAdjacency(
            List<ProceduralForbiddenPair> value
    ) {
        return copy(name, seed, retryLimit, fallbackItemId, blocks, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, gradientEnabled,
                gradientCoordinate, noiseEnabled, noiseFrequency, noiseSalt,
                inspectExistingWorld, value, preferredAdjacency, maximumRunLength,
                verticalRules);
    }

    public ProceduralPatternPreset withPreferredAdjacency(
            List<ProceduralPreferredPair> value
    ) {
        return copy(name, seed, retryLimit, fallbackItemId, blocks, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, gradientEnabled,
                gradientCoordinate, noiseEnabled, noiseFrequency, noiseSalt,
                inspectExistingWorld, forbiddenAdjacency, value, maximumRunLength,
                verticalRules);
    }

    public ProceduralPatternPreset withMaximumRunLength(int value) {
        return copy(name, seed, retryLimit, fallbackItemId, blocks, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, gradientEnabled,
                gradientCoordinate, noiseEnabled, noiseFrequency, noiseSalt,
                inspectExistingWorld, forbiddenAdjacency, preferredAdjacency, value,
                verticalRules);
    }

    public ProceduralPatternPreset withVerticalRules(
            List<ProceduralVerticalRule> value
    ) {
        return copy(name, seed, retryLimit, fallbackItemId, blocks, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, gradientEnabled,
                gradientCoordinate, noiseEnabled, noiseFrequency, noiseSalt,
                inspectExistingWorld, forbiddenAdjacency, preferredAdjacency,
                maximumRunLength, value);
    }

    public ProceduralPatternPreset duplicate() {
        return new ProceduralPatternPreset(
                UUID.randomUUID(),
                name + " copy",
                seed,
                retryLimit,
                fallbackItemId,
                blocks,
                sequenceEnabled,
                sequenceOffset,
                sequenceAlternateWeight,
                gradientEnabled,
                gradientCoordinate,
                noiseEnabled,
                noiseFrequency,
                noiseSalt,
                inspectExistingWorld,
                forbiddenAdjacency,
                preferredAdjacency,
                maximumRunLength,
                verticalRules,
                advanced,
                materialSource,
                stockTransformers
        );
    }

    public ProceduralPatternPreset withAdvanced(ProceduralAdvancedConfig value) {
        return new ProceduralPatternPreset(
                id, name, seed, retryLimit, fallbackItemId, blocks,
                sequenceEnabled, sequenceOffset, sequenceAlternateWeight,
                gradientEnabled, gradientCoordinate, noiseEnabled,
                noiseFrequency, noiseSalt, inspectExistingWorld,
                forbiddenAdjacency, preferredAdjacency, maximumRunLength,
                verticalRules, value, materialSource, stockTransformers
        );
    }

    public ProceduralPatternPreset withMaterialSource(
            PatternMaterialSource value
    ) {
        return new ProceduralPatternPreset(
                id, name, seed, retryLimit, fallbackItemId, blocks,
                sequenceEnabled, sequenceOffset, sequenceAlternateWeight,
                gradientEnabled, gradientCoordinate, noiseEnabled,
                noiseFrequency, noiseSalt, inspectExistingWorld,
                forbiddenAdjacency, preferredAdjacency, maximumRunLength,
                verticalRules, advanced, value, stockTransformers
        );
    }

    public ProceduralPatternPreset withStockTransformers(
            List<? extends Transformer> value
    ) {
        return new ProceduralPatternPreset(
                id, name, seed, retryLimit, fallbackItemId, blocks,
                sequenceEnabled, sequenceOffset, sequenceAlternateWeight,
                gradientEnabled, gradientCoordinate, noiseEnabled,
                noiseFrequency, noiseSalt, inspectExistingWorld,
                forbiddenAdjacency, preferredAdjacency, maximumRunLength,
                verticalRules, advanced, materialSource, List.copyOf(value)
        );
    }

    public ProceduralPatternPreset withImportedStockPattern(Pattern pattern) {
        var imported = withStockTransformers(pattern.transformers().stream()
                .filter(transformer ->
                        transformer.getType() != Transformers.RANDOMIZER
                )
                .toList());
        var randomizer = pattern.transformers().stream()
                .filter(ItemRandomizer.class::isInstance)
                .map(ItemRandomizer.class::cast)
                .findFirst();
        if (randomizer.isEmpty()) {
            return imported;
        }
        var value = randomizer.get();
        imported = imported.withMaterialSource(switch (value.getSource()) {
            case INVENTORY -> PatternMaterialSource.INVENTORY;
            case HOTBAR -> PatternMaterialSource.HOTBAR;
            case HANDS -> PatternMaterialSource.HANDS;
            case CUSTOMIZE -> PatternMaterialSource.CUSTOM_PALETTE;
        }).withSequence(
                value.getOrder() == ItemRandomizer.Order.SEQUENCE,
                imported.sequenceOffset(),
                imported.sequenceAlternateWeight()
        );
        if (value.getSource() == ItemRandomizer.Source.CUSTOMIZE
                && !value.getChances().isEmpty()) {
            imported = imported.withBlocks(value.getChances().stream()
                    .map(chance -> ProceduralBlockEntry.weighted(
                            chance.content().getId().getString(),
                            chance.chance()
                    ))
                    .toList());
        }
        return imported;
    }

    private ProceduralPatternPreset copy(
            String name,
            long seed,
            int retryLimit,
            String fallbackItemId,
            List<ProceduralBlockEntry> blocks,
            boolean sequenceEnabled,
            int sequenceOffset,
            double sequenceAlternateWeight,
            boolean gradientEnabled,
            Coordinate gradientCoordinate,
            boolean noiseEnabled,
            double noiseFrequency,
            long noiseSalt,
            boolean inspectExistingWorld,
            List<ProceduralForbiddenPair> forbiddenAdjacency,
            List<ProceduralPreferredPair> preferredAdjacency,
            int maximumRunLength,
            List<ProceduralVerticalRule> verticalRules
    ) {
        return new ProceduralPatternPreset(
                id, name, seed, retryLimit, fallbackItemId, blocks, sequenceEnabled,
                sequenceOffset, sequenceAlternateWeight, gradientEnabled,
                gradientCoordinate, noiseEnabled, noiseFrequency, noiseSalt,
                inspectExistingWorld, forbiddenAdjacency, preferredAdjacency,
                maximumRunLength, verticalRules, advanced, materialSource,
                stockTransformers
        );
    }
}
