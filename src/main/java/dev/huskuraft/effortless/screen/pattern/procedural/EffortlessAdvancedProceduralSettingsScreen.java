package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.CandidateQuotaRule;
import dev.huskuraft.effortless.client.pattern.procedural.CoordinateSpace;
import dev.huskuraft.effortless.client.pattern.procedural.GradientCurve;
import dev.huskuraft.effortless.client.pattern.procedural.MinimumSpacingConstraint;
import dev.huskuraft.effortless.client.pattern.procedural.NeighborDirection;
import dev.huskuraft.effortless.client.pattern.procedural.NeighborTopology;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralRuleSet;
import dev.huskuraft.effortless.client.pattern.procedural.SeedMode;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralAdvancedConfig;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralCleanupRule;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralDirectionalRule;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralMaskLayer;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralNeighborCountRule;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralQuotaRule;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralSpacingRule;
import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

final class EffortlessAdvancedProceduralSettingsScreen
        extends EffortlessProceduralScreen {

    private final Consumer<ProceduralAdvancedConfig> consumer;
    private final List<ProceduralPatternPreset> presets;
    private final UUID currentPresetId;
    private ProceduralAdvancedConfig config;

    EffortlessAdvancedProceduralSettingsScreen(
            Entrance entrance,
            Consumer<ProceduralAdvancedConfig> consumer,
            ProceduralAdvancedConfig config,
            List<ProceduralPatternPreset> presets,
            UUID currentPresetId
    ) {
        super(
                entrance,
                Text.text("Advanced pattern rules"),
                PANEL_WIDTH_60,
                PANEL_HEIGHT_FULL
        );
        this.consumer = consumer;
        this.config = config;
        this.presets = List.copyOf(presets);
        this.currentPresetId = currentPresetId;
        setDraftCommit(() -> {
            consumer.accept(this.config);
            return true;
        });
    }

    @Override
    protected EffortlessClient getEntrance() {
        return (EffortlessClient) super.getEntrance();
    }

    @Override
    public void onCreate() {
        addWidget(new TextWidget(
                getEntrance(),
                getLeft() + getWidth() / 2,
                getTop() + PANEL_TITLE_HEIGHT_1 - 10,
                workbenchTitle(),
                TextWidget.Gravity.CENTER
        ));
        var options = addWidget(new ProceduralSettingOptionsList(
                getEntrance(),
                getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - PANEL_TITLE_HEIGHT_1 - PANEL_BUTTON_ROW_HEIGHT_1,
                false,
                false
        ));
        options.setAlwaysShowScrollbar(true);

        options.addIntegerEntry(
                Text.text("Deterministic repair passes"),
                Text.empty(),
                config.repairPasses(),
                0,
                ProceduralRuleSet.MAX_REPAIR_PASSES,
                value -> config = config.withRepairPasses(value)
        );
        options.addSelectorEntry(
                Text.text("Adjacency neighbors"),
                Text.empty(),
                labels(NeighborTopology.values()),
                List.of(NeighborTopology.values()),
                config.adjacencyTopology(),
                value -> config = config.withAdjacencyTopology(value)
        );
        options.addSelectorEntry(
                Text.text("Coordinate space"),
                Text.empty(),
                labels(CoordinateSpace.values()),
                List.of(CoordinateSpace.values()),
                config.coordinateSpace(),
                value -> config = config.withCoordinateSpace(value)
        );
        options.addSelectorEntry(
                Text.text("Seed mode"),
                Text.empty(),
                labels(SeedMode.values()),
                List.of(SeedMode.values()),
                config.seedMode(),
                value -> config = config.withSeedMode(value)
        );
        options.addSelectorEntry(
                Text.text("Gradient curve"),
                Text.empty(),
                labels(GradientCurve.values()),
                List.of(GradientCurve.values()),
                config.gradientCurve(),
                value -> config = config.withGradientCurve(
                        value, config.gradientSteps()
                )
        );
        options.addIntegerEntry(
                Text.text("Stepped-gradient steps"),
                Text.empty(),
                config.gradientSteps(),
                2,
                256,
                value -> config = config.withGradientCurve(
                        config.gradientCurve(), value
                )
        );
        options.addIntegerEntry(
                Text.text("Cleanup passes"),
                Text.empty(),
                config.cleanupPasses(),
                0,
                ProceduralRuleSet.MAX_CLEANUP_PASSES,
                value -> config = config.withCleanup(
                        value, config.cleanupRules()
                )
        );

        var parentValues = new ArrayList<String>();
        var parentLabels = new ArrayList<Text>();
        parentValues.add("");
        parentLabels.add(Text.text("none"));
        for (var preset : presets) {
            if (!preset.id().equals(currentPresetId)) {
                parentValues.add(preset.id().toString());
                parentLabels.add(Text.text(preset.name()));
            }
        }
        String selectedParent = parentValues.contains(config.parentPresetId())
                ? config.parentPresetId()
                : "";
        options.addSelectorEntry(
                Text.text("Parent preset"),
                Text.empty(),
                parentLabels,
                parentValues,
                selectedParent,
                value -> config = config.withParent(
                        value, config.inheritBlocks(), config.inheritRules()
                )
        );
        options.addSwitchEntry(
                Text.text("Inherit parent blocks"),
                Text.empty(),
                config.inheritBlocks(),
                value -> config = config.withParent(
                        config.parentPresetId(), value, config.inheritRules()
                )
        );
        options.addSwitchEntry(
                Text.text("Inherit parent rules"),
                Text.empty(),
                config.inheritRules(),
                value -> config = config.withParent(
                        config.parentPresetId(), config.inheritBlocks(), value
                )
        );

        addMaskTab(options);
        addDirectionalTab(options);
        addSpacingTab(options);
        addNeighborCountTab(options);
        addQuotaTab(options);
        addCleanupTab(options);

        addWidget(Button.builder(
                getEntrance(),
                Text.text("Discard"),
                button -> discardAndDetach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(), 0f, 0f, 0.5f
        ).build());
        addWidget(Button.builder(
                getEntrance(),
                Text.text("Back"),
                button -> detach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(), 0f, 0.5f, 0.5f
        ).build());
    }

    private void addMaskTab(SettingOptionsList options) {
        options.addTab(
                Text.text("Named masks / bands / rings"),
                Text.empty(),
                config.maskLayers(),
                value -> config = config.withMaskLayers(value),
                (entry, value) -> configureListButton(entry, value.size(), () ->
                        new EffortlessAdvancedRuleListScreen<>(
                                getEntrance(),
                                "Named mask layers",
                                entry::setItem,
                                value,
                                layer -> layer.name()
                                        + (layer.enabled() ? "" : " (disabled)"),
                                layer -> layer.shape().name().toLowerCase(),
                                ProceduralMaskLayer::defaultLayer,
                                request -> EffortlessAdvancedRuleEditScreen.editMask(
                                        getEntrance(),
                                        request.consumer(),
                                        request.value()
                                )
                        ).attach()
                )
        );
    }

    private void addDirectionalTab(SettingOptionsList options) {
        options.addTab(
                Text.text("Directional neighbor rules"),
                Text.empty(),
                config.directionalRules(),
                value -> config = config.withDirectionalRules(value),
                (entry, value) -> configureListButton(entry, value.size(), () ->
                        new EffortlessAdvancedRuleListScreen<>(
                                getEntrance(),
                                "Directional neighbor rules",
                                entry::setItem,
                                value,
                                rule -> rule.itemId() + " requires",
                                rule -> rule.direction().name().toLowerCase(),
                                () -> new ProceduralDirectionalRule(
                                        "minecraft:stone",
                                        NeighborDirection.DOWN,
                                        List.of("minecraft:stone"),
                                        false
                                ),
                                request -> EffortlessAdvancedRuleEditScreen.editDirectional(
                                        getEntrance(),
                                        request.consumer(),
                                        request.value()
                                )
                        ).attach()
                )
        );
    }

    private void addSpacingTab(SettingOptionsList options) {
        options.addTab(
                Text.text("Minimum spacing rules"),
                Text.empty(),
                config.spacingRules(),
                value -> config = config.withSpacingRules(value),
                (entry, value) -> configureListButton(entry, value.size(), () ->
                        new EffortlessAdvancedRuleListScreen<>(
                                getEntrance(),
                                "Minimum spacing rules",
                                entry::setItem,
                                value,
                                rule -> rule.itemIds().size() + " blocks",
                                rule -> "radius " + rule.radius(),
                                () -> new ProceduralSpacingRule(
                                        List.of("minecraft:stone"),
                                        1,
                                        MinimumSpacingConstraint.DistanceMetric.MANHATTAN
                                ),
                                request -> EffortlessAdvancedRuleEditScreen.editSpacing(
                                        getEntrance(),
                                        request.consumer(),
                                        request.value()
                                )
                        ).attach()
                )
        );
    }

    private void addNeighborCountTab(SettingOptionsList options) {
        options.addTab(
                Text.text("Neighbor-count rules"),
                Text.empty(),
                config.neighborCountRules(),
                value -> config = config.withNeighborCountRules(value),
                (entry, value) -> configureListButton(entry, value.size(), () ->
                        new EffortlessAdvancedRuleListScreen<>(
                                getEntrance(),
                                "Neighbor-count rules",
                                entry::setItem,
                                value,
                                rule -> rule.itemId(),
                                rule -> rule.minimum() + ".." + rule.maximum(),
                                () -> new ProceduralNeighborCountRule(
                                        "minecraft:stone",
                                        List.of("minecraft:stone"),
                                        0,
                                        6
                                ),
                                request -> EffortlessAdvancedRuleEditScreen.editNeighborCount(
                                        getEntrance(),
                                        request.consumer(),
                                        request.value()
                                )
                        ).attach()
                )
        );
    }

    private void addQuotaTab(SettingOptionsList options) {
        options.addTab(
                Text.text("Block density / quota rules"),
                Text.empty(),
                config.quotaRules(),
                value -> config = config.withQuotaRules(value),
                (entry, value) -> configureListButton(entry, value.size(), () ->
                        new EffortlessAdvancedRuleListScreen<>(
                                getEntrance(),
                                "Block density / quotas",
                                entry::setItem,
                                value,
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
                                        getEntrance(),
                                        request.consumer(),
                                        request.value()
                                )
                        ).attach()
                )
        );
    }

    private void addCleanupTab(SettingOptionsList options) {
        options.addTab(
                Text.text("Post-generation cleanup rules"),
                Text.empty(),
                config.cleanupRules(),
                value -> config = config.withCleanup(
                        config.cleanupPasses(), value
                ),
                (entry, value) -> configureListButton(entry, value.size(), () ->
                        new EffortlessAdvancedRuleListScreen<>(
                                getEntrance(),
                                "Post-generation cleanup",
                                entry::setItem,
                                value,
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
                                        getEntrance(),
                                        request.consumer(),
                                        request.value()
                                )
                        ).attach()
                )
        );
    }

    private static void configureListButton(
            SettingOptionsList.ButtonEntry<?> entry,
            int count,
            Runnable open
    ) {
        entry.getButton().setMessage(Text.text(count + " rules"));
        entry.getButton().setOnPressListener(button -> open.run());
    }

    private static List<Text> labels(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(value -> Text.text(
                        value.name().toLowerCase().replace('_', ' ')
                ))
                .toList();
    }
}
