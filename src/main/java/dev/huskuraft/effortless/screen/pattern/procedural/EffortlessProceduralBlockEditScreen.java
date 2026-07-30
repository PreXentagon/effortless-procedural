package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.GradientDistributionMode;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralBlockEntry;
import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

final class EffortlessProceduralBlockEditScreen
        extends EffortlessProceduralScreen {

    private final Consumer<ProceduralBlockEntry> consumer;
    private final GradientDistributionMode gradientMode;
    private ProceduralBlockEntry entry;

    EffortlessProceduralBlockEditScreen(
            Entrance entrance,
            Consumer<ProceduralBlockEntry> consumer,
            ProceduralBlockEntry entry,
            GradientDistributionMode gradientMode
    ) {
        super(entrance, Text.text("Edit " + entry.itemId()), PANEL_WIDTH_60, PANEL_HEIGHT_FULL);
        this.consumer = consumer;
        this.entry = entry;
        this.gradientMode = gradientMode;
        setDraftCommit(() -> {
            consumer.accept(this.entry);
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
        options.addNumberEntry(
                Text.text("Base weight"), Text.empty(), entry.weight(), 0.0, 1_000_000.0,
                value -> entry = copy(value, entry.gradientStart(), entry.gradientEnd(),
                        entry.noiseMinimum(), entry.noiseMaximum())
        );
        if (gradientMode == GradientDistributionMode.WEIGHTED_ENDPOINTS) {
            options.addNumberEntry(
                    Text.text("Gradient start weight"), Text.empty(),
                    entry.gradientStart(),
                    0.0, 1_000_000.0,
                    value -> entry = copy(
                            entry.weight(), value, entry.gradientEnd(),
                            entry.noiseMinimum(), entry.noiseMaximum()
                    )
            );
            options.addNumberEntry(
                    Text.text("Gradient end weight"), Text.empty(),
                    entry.gradientEnd(),
                    0.0, 1_000_000.0,
                    value -> entry = copy(
                            entry.weight(), entry.gradientStart(), value,
                            entry.noiseMinimum(), entry.noiseMaximum()
                    )
            );
        }
        options.addNumberEntry(
                Text.text("Noise minimum multiplier"), Text.empty(), entry.noiseMinimum(),
                0.0, 1_000_000.0, 0.05,
                value -> entry = copy(entry.weight(), entry.gradientStart(),
                        entry.gradientEnd(), value,
                        Math.max(value, entry.noiseMaximum()))
        );
        options.addNumberEntry(
                Text.text("Noise maximum multiplier"), Text.empty(), entry.noiseMaximum(),
                0.0, 1_000_000.0, 0.05,
                value -> entry = copy(entry.weight(), entry.gradientStart(),
                        entry.gradientEnd(),
                        Math.min(entry.noiseMinimum(), value), value)
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

    private ProceduralBlockEntry copy(
            double weight,
            double gradientStart,
            double gradientEnd,
            double noiseMinimum,
            double noiseMaximum
    ) {
        return new ProceduralBlockEntry(
                entry.itemId(),
                weight,
                gradientStart,
                gradientEnd,
                noiseMinimum,
                noiseMaximum,
                entry.gradientPosition()
        );
    }
}
