package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;
import java.util.function.Consumer;

import dev.huskuraft.effortless.client.pattern.procedural.CandidateQuotaRule;
import dev.huskuraft.effortless.client.pattern.procedural.MinimumSpacingConstraint;
import dev.huskuraft.effortless.client.pattern.procedural.NeighborDirection;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralCleanupRule;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralDirectionalRule;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralMaskLayer;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralNeighborCountRule;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralQuotaRule;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralSpacingRule;
import dev.huskuraft.universal.api.platform.Entrance;

/**
 * Opens the focused rule dialogs used by both the legacy advanced page and the
 * workbench inspector. Keeping these launch recipes outside the workbench
 * prevents the root screen from owning rule construction details.
 */
final class ProceduralRuleEditorLauncher {

    private ProceduralRuleEditorLauncher() {
    }

    static void masks(
            Entrance entrance,
            List<ProceduralMaskLayer> original,
            Consumer<List<ProceduralMaskLayer>> consumer
    ) {
        new EffortlessAdvancedRuleListScreen<>(
                entrance,
                "Named mask layers",
                consumer,
                original,
                layer -> layer.name() + (layer.enabled() ? "" : " (disabled)"),
                layer -> layer.shape().name().toLowerCase(),
                ProceduralMaskLayer::defaultLayer,
                request -> EffortlessAdvancedRuleEditScreen.editMask(
                        entrance,
                        request.consumer(),
                        request.value()
                )
        ).attach();
    }

    static void directional(
            Entrance entrance,
            List<ProceduralDirectionalRule> original,
            Consumer<List<ProceduralDirectionalRule>> consumer
    ) {
        new EffortlessAdvancedRuleListScreen<>(
                entrance,
                "Directional neighbor rules",
                consumer,
                original,
                rule -> rule.itemId() + " requires",
                rule -> rule.direction().name().toLowerCase(),
                () -> new ProceduralDirectionalRule(
                        "minecraft:stone",
                        NeighborDirection.DOWN,
                        List.of("minecraft:stone"),
                        false
                ),
                request -> EffortlessAdvancedRuleEditScreen.editDirectional(
                        entrance,
                        request.consumer(),
                        request.value()
                )
        ).attach();
    }

    static void spacing(
            Entrance entrance,
            List<ProceduralSpacingRule> original,
            Consumer<List<ProceduralSpacingRule>> consumer
    ) {
        new EffortlessAdvancedRuleListScreen<>(
                entrance,
                "Minimum spacing rules",
                consumer,
                original,
                rule -> rule.itemIds().size() + " blocks",
                rule -> "radius " + rule.radius(),
                () -> new ProceduralSpacingRule(
                        List.of("minecraft:stone"),
                        1,
                        MinimumSpacingConstraint.DistanceMetric.MANHATTAN
                ),
                request -> EffortlessAdvancedRuleEditScreen.editSpacing(
                        entrance,
                        request.consumer(),
                        request.value()
                )
        ).attach();
    }

    static void neighborCounts(
            Entrance entrance,
            List<ProceduralNeighborCountRule> original,
            Consumer<List<ProceduralNeighborCountRule>> consumer
    ) {
        new EffortlessAdvancedRuleListScreen<>(
                entrance,
                "Neighbor-count rules",
                consumer,
                original,
                ProceduralNeighborCountRule::itemId,
                rule -> rule.minimum() + ".." + rule.maximum(),
                () -> new ProceduralNeighborCountRule(
                        "minecraft:stone",
                        List.of("minecraft:stone"),
                        0,
                        6
                ),
                request -> EffortlessAdvancedRuleEditScreen.editNeighborCount(
                        entrance,
                        request.consumer(),
                        request.value()
                )
        ).attach();
    }

    static void quotas(
            Entrance entrance,
            List<ProceduralQuotaRule> original,
            Consumer<List<ProceduralQuotaRule>> consumer
    ) {
        new EffortlessAdvancedRuleListScreen<>(
                entrance,
                "Block density / quotas",
                consumer,
                original,
                ProceduralQuotaRule::itemId,
                rule -> rule.minimum() + ".." + rule.maximum()
                        + " " + rule.unit().name().toLowerCase(),
                () -> new ProceduralQuotaRule(
                        "minecraft:stone",
                        0.0,
                        1.0,
                        CandidateQuotaRule.Unit.FRACTION
                ),
                request -> EffortlessAdvancedRuleEditScreen.editQuota(
                        entrance,
                        request.consumer(),
                        request.value()
                )
        ).attach();
    }

    static void cleanup(
            Entrance entrance,
            List<ProceduralCleanupRule> original,
            Consumer<List<ProceduralCleanupRule>> consumer
    ) {
        new EffortlessAdvancedRuleListScreen<>(
                entrance,
                "Post-generation cleanup",
                consumer,
                original,
                rule -> rule.sourceItemIds().size() + " source blocks",
                rule -> "replace with " + rule.replacementItemId(),
                () -> new ProceduralCleanupRule(
                        List.of("minecraft:cobblestone"),
                        List.of("minecraft:cobblestone"),
                        "minecraft:stone",
                        0,
                        1
                ),
                request -> EffortlessAdvancedRuleEditScreen.editCleanup(
                        entrance,
                        request.consumer(),
                        request.value()
                )
        ).attach();
    }
}
