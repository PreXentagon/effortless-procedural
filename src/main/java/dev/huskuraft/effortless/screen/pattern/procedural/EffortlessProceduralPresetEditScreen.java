package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import dev.huskuraft.effortless.Effortless;
import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;
import dev.huskuraft.effortless.client.pattern.procedural.GradientDistributionMode;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralRuleSet;
import dev.huskuraft.effortless.client.pattern.procedural.MaxRunLengthConstraint;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralAdvancedConfig;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.screen.item.EffortlessItemPickerScreen;
import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

final class EffortlessProceduralPresetEditScreen
        extends EffortlessProceduralScreen {

    private final Consumer<ProceduralPatternPreset> consumer;
    private final List<ProceduralPatternPreset> availablePresets;
    private ProceduralPatternPreset preset;
    private ReliableEditBox nameField;
    private ReliableEditBox seedField;
    private ReliableEditBox noiseSaltField;
    private String nameDraft;
    private String seedDraft;
    private String noiseSaltDraft;
    private SettingOptionsList.ButtonEntry<ProceduralAdvancedConfig> advancedEntry;

    EffortlessProceduralPresetEditScreen(
            Entrance entrance,
            Consumer<ProceduralPatternPreset> consumer,
            ProceduralPatternPreset preset,
            List<ProceduralPatternPreset> availablePresets
    ) {
        super(entrance, Text.text("Edit pattern rules"), PANEL_WIDTH_60, PANEL_HEIGHT_FULL);
        this.consumer = consumer;
        this.preset = preset;
        this.availablePresets = List.copyOf(availablePresets);
        this.nameDraft = preset.name();
        this.seedDraft = Long.toString(preset.seed());
        this.noiseSaltDraft = Long.toString(preset.noiseSalt());
        setDraftCommit(this::commitDraft);
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

        int labelWidth = 70;
        int fieldX = getLeft() + PADDINGS_H + labelWidth;
        int fieldWidth = getWidth() - PADDINGS_H * 2 - labelWidth;
        int rowY = getTop() + PANEL_TITLE_HEIGHT_1;
        addWidget(new TextWidget(
                getEntrance(), getLeft() + PADDINGS_H, rowY + 6, Text.text("Name")
        ));
        nameField = addWidget(new ReliableEditBox(
                getEntrance(), fieldX, rowY, fieldWidth, 20, Text.text("Pattern name")
        ));
        nameField.setMaxLength(80);
        nameField.setValue(nameDraft);
        nameField.setChangeListener(value -> nameDraft = value);

        addWidget(new TextWidget(
                getEntrance(), getLeft() + PADDINGS_H, rowY + 28, Text.text("Seed")
        ));
        seedField = addWidget(new ReliableEditBox(
                getEntrance(), fieldX, rowY + 22, fieldWidth, 20, Text.text("Seed")
        ));
        seedField.setMaxLength(20);
        seedField.setFilter(EffortlessProceduralPresetEditScreen::isLongInput);
        seedField.setValue(seedDraft);
        seedField.setChangeListener(value -> seedDraft = value);

        addWidget(new TextWidget(
                getEntrance(), getLeft() + PADDINGS_H, rowY + 50, Text.text("Noise salt")
        ));
        noiseSaltField = addWidget(new ReliableEditBox(
                getEntrance(), fieldX, rowY + 44, fieldWidth, 20, Text.text("Noise salt")
        ));
        noiseSaltField.setMaxLength(20);
        noiseSaltField.setFilter(EffortlessProceduralPresetEditScreen::isLongInput);
        noiseSaltField.setValue(noiseSaltDraft);
        noiseSaltField.setChangeListener(value -> noiseSaltDraft = value);

        var entries = addWidget(new ProceduralSettingOptionsList(
                getEntrance(),
                getLeft() + PADDINGS_H,
                rowY + 68,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - PANEL_TITLE_HEIGHT_1 - 68 - PANEL_BUTTON_ROW_HEIGHT_1,
                false,
                false
        ));
        entries.setAlwaysShowScrollbar(true);

        entries.addIntegerEntry(
                Text.text("Retry limit"),
                Text.empty(),
                preset.retryLimit(),
                1,
                ProceduralRuleSet.MAX_RETRY_LIMIT,
                value -> preset = preset.withRetryLimit(value)
        );
        var fallbackEntry = entries.addTab(
                Text.text("Fallback block"),
                Text.empty(),
                preset.fallbackItemId(),
                value -> preset = preset.withFallbackItemId(value),
                (entry, value) -> {
                    entry.getButton().setMessage(Text.text(shortId(value)));
                    entry.getButton().setOnPressListener(button -> new EffortlessItemPickerScreen(
                            getEntrance(),
                            item -> item instanceof BlockItem
                                    && preset.blocks().stream().anyMatch(
                                            candidate -> candidate.itemId()
                                                    .equals(
                                                            item.getId()
                                                                    .getString()
                                                    )
                                    ),
                            item -> entry.setItem(item.getId().getString())
                    ).attach());
                }
        );
        entries.addSwitchEntry(
                Text.text("Sequence layer"),
                Text.empty(),
                preset.sequenceEnabled(),
                value -> preset = preset.withSequence(
                        value,
                        preset.sequenceOffset(),
                        preset.sequenceAlternateWeight()
                )
        );
        entries.addIntegerEntry(
                Text.text("Sequence offset"),
                Text.empty(),
                preset.sequenceOffset(),
                -1_000_000,
                1_000_000,
                value -> preset = preset.withSequence(
                        preset.sequenceEnabled(),
                        value,
                        preset.sequenceAlternateWeight()
                )
        );
        entries.addNumberEntry(
                Text.text("Sequence alternate weight"),
                Text.empty(),
                preset.sequenceAlternateWeight(),
                0.0,
                1_000_000.0,
                value -> preset = preset.withSequence(
                        preset.sequenceEnabled(),
                        preset.sequenceOffset(),
                        value
                )
        );
        entries.addSwitchEntry(
                Text.text("Gradient layer"),
                Text.empty(),
                preset.gradientEnabled(),
                value -> preset = preset.withGradient(value, preset.gradientCoordinate())
        );
        entries.addSelectorEntry(
                Text.text("Gradient coordinate"),
                Text.empty(),
                Arrays.stream(Coordinate.values())
                        .map(value -> Text.text(value.name().toLowerCase()))
                        .toList(),
                List.of(Coordinate.values()),
                preset.gradientCoordinate(),
                value -> preset = preset.withGradient(preset.gradientEnabled(), value)
        );
        entries.addSelectorEntry(
                Text.text("Gradient behavior"),
                Text.empty(),
                Arrays.stream(GradientDistributionMode.values())
                        .map(value -> Text.text(
                                value.name().toLowerCase().replace('_', ' ')
                        ))
                        .toList(),
                List.of(GradientDistributionMode.values()),
                preset.advanced().gradientDistributionMode(),
                value -> {
                    var advanced = preset.advanced()
                            .withGradientDistributionMode(value);
                    preset = preset.withAdvanced(advanced);
                    if (advancedEntry != null
                            && !advancedEntry.getItem().equals(advanced)) {
                        advancedEntry.setItem(advanced);
                    }
                }
        );
        entries.addSwitchEntry(
                Text.text("Noise layer"),
                Text.empty(),
                preset.noiseEnabled(),
                value -> preset = preset.withNoise(
                        value,
                        preset.noiseFrequency(),
                        preset.noiseSalt()
                )
        );
        entries.addNumberEntry(
                Text.text("Noise frequency"),
                Text.empty(),
                preset.noiseFrequency(),
                0.000001,
                1024.0,
                value -> preset = preset.withNoise(
                        preset.noiseEnabled(),
                        value,
                        preset.noiseSalt()
                )
        );
        entries.addSwitchEntry(
                Text.text("Inspect existing world"),
                Text.empty(),
                preset.inspectExistingWorld(),
                value -> preset = preset.withInspectExistingWorld(value)
        );
        entries.addIntegerEntry(
                Text.text("Maximum identical run (0 = off)"),
                Text.empty(),
                preset.maximumRunLength(),
                0,
                MaxRunLengthConstraint.MAXIMUM_RUN_LENGTH,
                value -> preset = preset.withMaximumRunLength(value)
        );
        entries.addTab(
                Text.text("Block distribution entries"),
                Text.empty(),
                preset.blocks(),
                value -> {
                    preset = preset.withBlocks(value);
                    fallbackEntry.setItem(preset.fallbackItemId());
                },
                (entry, value) -> {
                    entry.getButton().setMessage(Text.text(value.size() + " blocks"));
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessProceduralBlocksScreen(
                                    getEntrance(),
                                    entry::setItem,
                                    value,
                                    preset.advanced()
                                            .gradientDistributionMode()
                            ).attach()
                    );
                }
        );
        entries.addTab(
                Text.text("Forbidden adjacency"),
                Text.empty(),
                preset.forbiddenAdjacency(),
                value -> preset = preset.withForbiddenAdjacency(value),
                (entry, value) -> {
                    entry.getButton().setMessage(Text.text(value.size() + " rules"));
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessForbiddenAdjacencyScreen(
                                    getEntrance(),
                                    entry::setItem,
                                    value
                            ).attach()
                    );
                }
        );
        entries.addTab(
                Text.text("Preferred adjacency"),
                Text.empty(),
                preset.preferredAdjacency(),
                value -> preset = preset.withPreferredAdjacency(value),
                (entry, value) -> {
                    entry.getButton().setMessage(Text.text(value.size() + " rules"));
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessPreferredAdjacencyScreen(
                                    getEntrance(),
                                    entry::setItem,
                                    value
                            ).attach()
                    );
                }
        );
        entries.addTab(
                Text.text("Vertical neighbor rules"),
                Text.empty(),
                preset.verticalRules(),
                value -> preset = preset.withVerticalRules(value),
                (entry, value) -> {
                    entry.getButton().setMessage(Text.text(value.size() + " rules"));
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessVerticalRulesScreen(
                                    getEntrance(),
                                    entry::setItem,
                                    value
                            ).attach()
                    );
                }
        );
        advancedEntry = entries.addTab(
                Text.text("Advanced rules, masks and composition"),
                Text.empty(),
                preset.advanced(),
                value -> preset = preset.withAdvanced(value),
                (entry, value) -> {
                    var current = preset.advanced();
                    int count = current.maskLayers().size()
                            + current.directionalRules().size()
                            + current.spacingRules().size()
                            + current.neighborCountRules().size()
                            + current.quotaRules().size()
                            + current.cleanupRules().size();
                    entry.getButton().setMessage(Text.text(count + " advanced rules"));
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessAdvancedProceduralSettingsScreen(
                                    getEntrance(),
                                    entry::setItem,
                                    preset.advanced(),
                                    availablePresets,
                                    preset.id()
                            ).attach()
                    );
                }
        );

        addWidget(Button.builder(getEntrance(), Text.text("Discard"), button -> {
            discardAndDetach();
        }).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(), 0f, 0f, 0.5f
        ).build());
        addWidget(Button.builder(getEntrance(), Text.text("Back"), button -> {
            detach();
        }).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(), 0f, 0.5f, 0.5f
        ).build());
    }

    private boolean commitDraft() {
        try {
            nameField.commitVisibleValue();
            seedField.commitVisibleValue();
            noiseSaltField.commitVisibleValue();
            var name = nameDraft.trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Pattern name cannot be empty");
            }
            long seed = Long.parseLong(seedDraft);
            long noiseSalt = Long.parseLong(noiseSaltDraft);
            preset = preset.withName(name)
                    .withSeed(seed)
                    .withNoise(preset.noiseEnabled(), preset.noiseFrequency(), noiseSalt);
            consumer.accept(preset);
            return true;
        } catch (IllegalArgumentException exception) {
            getEntrance().getClient().getPlayer().sendMessage(Effortless.getSystemMessage(
                    Text.text("Procedural pattern: " + exception.getMessage())
                            .withStyle(ChatFormatting.RED)
            ));
            return false;
        }
    }

    private static boolean isLongInput(String value) {
        if (value.isEmpty() || value.equals("-")) {
            return true;
        }
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static String shortId(String value) {
        int separator = value.indexOf(':');
        return separator >= 0 ? value.substring(separator + 1) : value;
    }
}
