package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPreferredPair;
import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

final class EffortlessPreferredAdjacencyEditScreen
        extends EffortlessProceduralScreen {

    private final Consumer<ProceduralPreferredPair> consumer;
    private ProceduralPreferredPair pair;

    EffortlessPreferredAdjacencyEditScreen(
            Entrance entrance,
            Consumer<ProceduralPreferredPair> consumer,
            ProceduralPreferredPair pair
    ) {
        super(entrance, Text.text("Preferred adjacency weight"), PANEL_WIDTH_60, PANEL_HEIGHT_FULL);
        this.consumer = consumer;
        this.pair = pair;
        setDraftCommit(() -> {
            consumer.accept(this.pair);
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
                getEntrance(), getLeft() + getWidth() / 2,
                getTop() + PANEL_TITLE_HEIGHT_1 - 10,
                workbenchTitle(),
                TextWidget.Gravity.CENTER
        ));
        var options = addWidget(new ProceduralSettingOptionsList(
                getEntrance(), getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - PANEL_TITLE_HEIGHT_1 - PANEL_BUTTON_ROW_HEIGHT_1,
                false, false
        ));
        options.addNumberEntry(
                Text.text(pair.itemId() + " next to " + pair.preferredNeighborItemId()),
                Text.empty(),
                pair.multiplier(),
                0.000001,
                1_000_000.0,
                value -> pair = new ProceduralPreferredPair(
                        pair.itemId(), pair.preferredNeighborItemId(), value
                )
        );
        addWidget(Button.builder(getEntrance(), Text.text("Discard"), b ->
                discardAndDetach()
        ).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 0f, 0.5f).build());
        addWidget(Button.builder(getEntrance(), Text.text("Back"), b ->
                detach()
        ).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 0.5f, 0.5f).build());
    }
}
