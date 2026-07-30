package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralVerticalRule;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.core.Item;
import dev.huskuraft.universal.api.core.ResourceLocation;

public final class ProceduralPresetAdapter {

    private ProceduralPresetAdapter() {
    }

    public static Adaptation adapt(ProceduralPatternPreset preset) {
        var errors = new ArrayList<String>();
        var candidates = new ArrayList<Candidate<Item>>();
        var weighted = new LinkedHashMap<String, Double>();
        var gradient = new LinkedHashMap<String, LinearGradientSource.EndpointWeights>();
        var noise = new LinkedHashMap<String, SeededNoiseSource.MultiplierRange>();
        var gradientStops = new LinkedHashMap<String, Double>();

        for (var entry : preset.blocks()) {
            Item item;
            try {
                var location = ResourceLocation.decompose(entry.itemId());
                var resolved = Item.fromIdOptional(location);
                if (resolved.isEmpty()) {
                    errors.add("Unknown block item '" + entry.itemId() + "'");
                    continue;
                }
                item = resolved.get();
            } catch (RuntimeException exception) {
                errors.add("Invalid block item id '" + entry.itemId() + "'");
                continue;
            }
            if (!(item instanceof BlockItem)) {
                errors.add("'" + entry.itemId() + "' is not a placeable block item");
                continue;
            }
            candidates.add(new Candidate<>(entry.itemId(), item));
            weighted.put(entry.itemId(), entry.weight());
            gradient.put(
                    entry.itemId(),
                    new LinearGradientSource.EndpointWeights(
                            entry.gradientStart(),
                            entry.gradientEnd()
                    )
            );
            noise.put(
                    entry.itemId(),
                    new SeededNoiseSource.MultiplierRange(
                            entry.noiseMinimum(),
                            entry.noiseMaximum()
                    )
            );
            gradientStops.put(entry.itemId(), entry.gradientPosition());
        }
        if (preset.inspectExistingWorld()) {
            validateExistingWorldReferences(
                    preset,
                    weighted.keySet(),
                    errors
            );
        }

        var sources = new ArrayList<WeightSource<Item>>();
        sources.add(new WeightedSource<>(weighted));
        if (preset.sequenceEnabled()) {
            sources.add(new SequenceSource<>(
                    preset.sequenceOffset(),
                    1.0,
                    preset.sequenceAlternateWeight()
            ));
        }
        if (preset.gradientEnabled()) {
            switch (preset.advanced().gradientDistributionMode()) {
                case WEIGHTED_ENDPOINTS ->
                        sources.add(new LinearGradientSource<>(
                                preset.advanced().gradientField(),
                                gradient,
                                preset.advanced().gradientCurve(),
                                preset.advanced().gradientSteps()
                        ));
                case ORDERED_BLEND, ORDERED_BANDS ->
                        sources.add(new OrderedPaletteGradientSource<>(
                                preset.advanced().gradientField(),
                                preset.advanced().gradientDistributionMode(),
                                preset.advanced().gradientCurve(),
                                preset.advanced().gradientSteps(),
                                gradientStops
                        ));
            }
        }
        if (preset.noiseEnabled()) {
            sources.add(new SeededNoiseSource<>(
                    preset.noiseFrequency(),
                    preset.noiseSalt(),
                    noise,
                    preset.advanced().noiseConfig()
            ));
        }

        var constraints = new ArrayList<PlacementConstraint<Item>>();
        var scope = preset.inspectExistingWorld()
                ? NeighborScope.GENERATED_AND_EXISTING
                : NeighborScope.GENERATED_ONLY;
        var topology = preset.advanced().adjacencyTopology();
        if (!preset.forbiddenAdjacency().isEmpty()) {
            var pairs = new LinkedHashSet<ForbiddenAdjacencyConstraint.Pair>();
            for (var pair : preset.forbiddenAdjacency()) {
                pairs.add(new ForbiddenAdjacencyConstraint.Pair(
                        pair.firstItemId(),
                        pair.secondItemId()
                ));
            }
            constraints.add(new ForbiddenAdjacencyConstraint<>(
                    pairs,
                    scope,
                    topology
            ));
        }
        for (var preference : preset.preferredAdjacency()) {
            constraints.add(new PreferredAdjacencyConstraint<>(
                    preference.itemId(),
                    Set.of(preference.preferredNeighborItemId()),
                    preference.multiplier(),
                    scope,
                    topology
            ));
        }
        if (preset.maximumRunLength() > 0) {
            constraints.add(new MaxRunLengthConstraint<>(
                    Set.of(),
                    preset.maximumRunLength(),
                    scope
            ));
        }
        for (var vertical : preset.verticalRules()) {
            constraints.add(new AllowedVerticalNeighborConstraint<>(
                    vertical.itemId(),
                    vertical.direction() == ProceduralVerticalRule.Direction.BELOW
                            ? GridPosition.Direction.DOWN
                            : GridPosition.Direction.UP,
                    Set.copyOf(vertical.allowedItemIds()),
                    scope,
                    vertical.rejectUnresolved()
                            ? AllowedVerticalNeighborConstraint.UnresolvedBehavior.REJECT
                            : AllowedVerticalNeighborConstraint.UnresolvedBehavior.ALLOW
            ));
        }
        for (var layer : preset.advanced().maskLayers()) {
            if (!layer.enabled()) {
                continue;
            }
            sources.add(new MaskedWeightSource<>(
                    layer.name(),
                    layer.coordinate(),
                    layer.minimum(),
                    layer.maximum(),
                    layer.inverted(),
                    Set.copyOf(layer.itemIds()),
                    layer.mode(),
                    layer.multiplier(),
                    layer.shape(),
                    layer.period(),
                    layer.thickness(),
                    preset.advanced().coordinateSpace()
                            == CoordinateSpace.WORLD,
                    layer.spatialField()
            ));
        }
        for (var directional : preset.advanced().directionalRules()) {
            constraints.add(new AllowedDirectionalNeighborConstraint<>(
                    directional.itemId(),
                    directional.direction(),
                    Set.copyOf(directional.allowedItemIds()),
                    scope,
                    directional.rejectUnresolved()
                            ? AllowedDirectionalNeighborConstraint.UnresolvedBehavior.REJECT
                            : AllowedDirectionalNeighborConstraint.UnresolvedBehavior.ALLOW
            ));
        }
        for (var spacing : preset.advanced().spacingRules()) {
            constraints.add(new MinimumSpacingConstraint<>(
                    Set.copyOf(spacing.itemIds()),
                    spacing.radius(),
                    spacing.metric(),
                    scope
            ));
        }
        for (var neighborCount : preset.advanced().neighborCountRules()) {
            constraints.add(new NeighborhoodCountConstraint<>(
                    neighborCount.itemId(),
                    Set.copyOf(neighborCount.neighborItemIds()),
                    neighborCount.minimum(),
                    neighborCount.maximum(),
                    topology,
                    scope
            ));
        }
        for (var quotaConfig : preset.advanced().quotaRules()) {
            var quota = new CandidateQuotaRule<Item>(
                    quotaConfig.itemId(),
                    quotaConfig.minimum(),
                    quotaConfig.maximum(),
                    quotaConfig.unit()
            );
            sources.add(quota);
            constraints.add(quota);
        }
        var cleanupRules = new ArrayList<PostGenerationRule<Item>>();
        for (var cleanup : preset.advanced().cleanupRules()) {
            cleanupRules.add(new NeighborhoodReplacementRule<>(
                    Set.copyOf(cleanup.sourceItemIds()),
                    Set.copyOf(cleanup.matchingNeighborItemIds()),
                    cleanup.replacementItemId(),
                    cleanup.minimumMatches(),
                    cleanup.maximumMatches(),
                    topology,
                    scope
            ));
        }

        var ruleSet = new ProceduralRuleSet<>(
                candidates,
                sources,
                constraints,
                preset.retryLimit(),
                Optional.ofNullable(preset.fallbackItemId()).filter(id -> !id.isBlank()),
                preset.advanced().repairPasses(),
                cleanupRules,
                preset.advanced().cleanupPasses()
        );
        errors.addAll(ruleSet.validate());
        if (!errors.isEmpty()) {
            return Adaptation.failure(errors);
        }
        return Adaptation.success(ruleSet);
    }

    private static void validateExistingWorldReferences(
            ProceduralPatternPreset preset,
            Set<String> generatedCandidateIds,
            List<String> errors
    ) {
        var references = new LinkedHashSet<String>();
        for (var pair : preset.forbiddenAdjacency()) {
            references.add(pair.firstItemId());
            references.add(pair.secondItemId());
        }
        for (var pair : preset.preferredAdjacency()) {
            references.add(pair.preferredNeighborItemId());
        }
        for (var rule : preset.verticalRules()) {
            references.addAll(rule.allowedItemIds());
        }
        for (var rule : preset.advanced().directionalRules()) {
            references.addAll(rule.allowedItemIds());
        }
        for (var rule : preset.advanced().neighborCountRules()) {
            references.addAll(rule.neighborItemIds());
        }
        for (var rule : preset.advanced().cleanupRules()) {
            references.addAll(rule.matchingNeighborItemIds());
        }
        references.removeAll(generatedCandidateIds);

        for (var reference : references) {
            try {
                if (Item.fromIdOptional(ResourceLocation.decompose(reference))
                        .isEmpty()) {
                    errors.add(
                            "Unknown existing-world block item '"
                                    + reference + "'"
                    );
                }
            } catch (RuntimeException exception) {
                errors.add(
                        "Invalid existing-world block item id '"
                                + reference + "'"
                );
            }
        }
    }

    public record Adaptation(
            Optional<ProceduralRuleSet<Item>> ruleSet,
            List<String> errors
    ) {

        public Adaptation {
            errors = List.copyOf(errors);
        }

        public static Adaptation success(ProceduralRuleSet<Item> ruleSet) {
            return new Adaptation(Optional.of(ruleSet), List.of());
        }

        public static Adaptation failure(List<String> errors) {
            return new Adaptation(Optional.empty(), errors);
        }

        public boolean isSuccess() {
            return ruleSet.isPresent();
        }
    }
}
