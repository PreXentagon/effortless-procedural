package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.road.SplineCrossSectionBand;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

/** Edits one ordered spline cross-section band. */
final class EffortlessSplineBandEditScreen extends EffortlessProceduralScreen {

    private final Consumer<SplineCrossSectionBand> consumer;
    private final ProceduralPatternLibrary library;
    private final UUID currentPresetId;
    private SplineCrossSectionBand band;
    private ReliableEditBox nameField;
    private String nameDraft;

    EffortlessSplineBandEditScreen(
            Entrance entrance,
            Consumer<SplineCrossSectionBand> consumer,
            SplineCrossSectionBand band,
            ProceduralPatternLibrary library,
            UUID currentPresetId
    ) {
        super(
                entrance, Text.translate(
                        "effortless.procedural.spline.band.title"
                ),
                PANEL_WIDTH_60, PANEL_HEIGHT_FULL
        );
        this.consumer = consumer;
        this.band = band;
        this.library = library;
        this.currentPresetId = currentPresetId;
        this.nameDraft = band.name();
        setDraftCommit(this::commitDraft);
    }

    @Override
    protected EffortlessClient getEntrance() {
        return (EffortlessClient) super.getEntrance();
    }

    @Override
    public void onCreate() {
        addWidget(new TextWidget(
                getEntrance(), getLeft() + getWidth() / 2,
                getTop() + PANEL_TITLE_HEIGHT_1 - 10,
                workbenchTitle(), TextWidget.Gravity.CENTER
        ));
        int top = getTop() + PANEL_TITLE_HEIGHT_1;
        addWidget(new TextWidget(
                getEntrance(), getLeft() + PADDINGS_H, top + 6,
                Text.translate("effortless.procedural.spline.band.name")
        ));
        nameField = addWidget(new ReliableEditBox(
                getEntrance(), getLeft() + PADDINGS_H + 80, top,
                getWidth() - PADDINGS_H * 2 - 80, 20,
                Text.translate("effortless.procedural.spline.band.name")
        ));
        nameField.setMaxLength(80);
        nameField.setValue(nameDraft);
        nameField.setChangeListener(value -> nameDraft = value);
        top += 26;

        var options = addWidget(new ProceduralSettingOptionsList(
                getEntrance(), getLeft() + PADDINGS_H, top,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - (top - getTop()) - PANEL_BUTTON_ROW_HEIGHT_1,
                false, false
        ));
        options.setAlwaysShowScrollbar(true);
        options.addIntegerEntry(
                Text.translate("effortless.procedural.spline.band.width"),
                Text.translate(
                        "effortless.procedural.tooltip.spline.band.width"
                ),
                band.width(), 1, SplineCrossSectionBand.MAX_WIDTH,
                value -> band = band.withWidth(value)
        );
        options.addIntegerEntry(
                Text.translate("effortless.procedural.spline.band.height"),
                Text.translate(
                        "effortless.procedural.tooltip.spline.band.height"
                ),
                band.heightOffset(),
                -SplineCrossSectionBand.MAX_HEIGHT_OFFSET,
                SplineCrossSectionBand.MAX_HEIGHT_OFFSET,
                value -> band = band.withHeightOffset(value)
        );
        options.addIntegerEntry(
                Text.translate("effortless.procedural.spline.band.depth"),
                Text.translate(
                        "effortless.procedural.tooltip.spline.band.depth"
                ),
                band.depth(), 1, SplineCrossSectionBand.MAX_DEPTH,
                value -> band = band.withDepth(value)
        );
        options.addSelectorEntry(
                Text.translate("effortless.procedural.spline.band.side"),
                Text.empty(),
                labels(SplineCrossSectionBand.Side.values()),
                List.of(SplineCrossSectionBand.Side.values()),
                band.side(), value -> band = band.withSide(value)
        );
        options.addSelectorEntry(
                Text.translate(
                        "effortless.procedural.spline.band.placement"
                ),
                Text.translate(
                        "effortless.procedural.tooltip.spline.band.placement"
                ),
                labels(SplineCrossSectionBand.Placement.values()),
                List.of(SplineCrossSectionBand.Placement.values()),
                band.placement(),
                value -> band = band.withPlacement(value)
        );
        options.addTab(
                Text.translate("effortless.procedural.spline.band.recipe"),
                Text.translate(
                        "effortless.procedural.tooltip.spline.band.recipe"
                ),
                band.recipeId(),
                value -> band = band.withRecipeId(value),
                (entry, value) -> {
                    entry.getButton().setMessage(Text.text(
                            value.isBlank() ? "Main pattern" : "Linked pattern"
                    ));
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessRecipePickerScreen(
                                    getEntrance(), band.name(), library,
                                    currentPresetId, value, entry::setItem
                            ).attach()
                    );
                }
        );

        addWidget(Button.builder(
                getEntrance(), Text.text("Discard"),
                button -> discardAndDetach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                0f, 0f, 0.5f
        ).build());
        addWidget(Button.builder(
                getEntrance(), Text.text("Back"), button -> detach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                0f, 0.5f, 0.5f
        ).build());
    }

    private boolean commitDraft() {
        nameField.commitVisibleValue();
        String name = nameDraft.strip();
        consumer.accept(band.withName(
                name.isEmpty() ? "Cross-section band" : name
        ));
        return true;
    }

    private static List<Text> labels(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(value -> Text.text(
                        value.name().toLowerCase().replace('_', ' ')
                ))
                .toList();
    }
}
