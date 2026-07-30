package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralForbiddenPair;
import dev.huskuraft.effortless.screen.item.EffortlessItemPickerScreen;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

final class EffortlessForbiddenAdjacencyScreen
        extends EffortlessProceduralScreen {

    private final Consumer<List<ProceduralForbiddenPair>> consumer;
    private final List<ProceduralForbiddenPair> working;
    private TextRuleList<ProceduralForbiddenPair> entries;
    private Button editButton;
    private Button deleteButton;

    EffortlessForbiddenAdjacencyScreen(
            Entrance entrance,
            Consumer<List<ProceduralForbiddenPair>> consumer,
            List<ProceduralForbiddenPair> entries
    ) {
        super(entrance, Text.text("Forbidden adjacency"), PANEL_WIDTH_60, PANEL_HEIGHT_FULL);
        this.consumer = consumer;
        this.working = new ArrayList<>(entries);
        setDraftCommit(() -> {
            consumer.accept(List.copyOf(working));
            return true;
        });
    }

    @Override
    protected EffortlessClient getEntrance() {
        return (EffortlessClient) super.getEntrance();
    }

    @Override
    public void onCreate() {
        title();
        entries = addWidget(new TextRuleList<>(
                getEntrance(),
                getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - PANEL_TITLE_HEIGHT_1 - PANEL_BUTTON_ROW_HEIGHT_2,
                pair -> pair.firstItemId() + " cannot touch",
                ProceduralForbiddenPair::secondItemId
        ));
        entries.setAlwaysShowScrollbar(true);
        entries.reset(working);
        editButton = addWidget(action("Edit", 0f, button -> {
            if (entries.hasSelected()) {
                choosePair(true);
            }
        }));
        deleteButton = addWidget(action("Delete", 1f / 3f, button -> {
            if (entries.hasSelected()) {
                int index = entries.indexOfSelected();
                working.remove(index);
                entries.reset(working);
            }
        }));
        addWidget(action("Add", 2f / 3f, button -> choosePair(false)));
        saveCancel();
    }

    @Override
    public void onReload() {
        editButton.setActive(entries.hasSelected());
        deleteButton.setActive(entries.hasSelected());
        if (entries.consumeDoubleClick() && entries.hasSelected()) {
            choosePair(true);
        }
    }

    private void choosePair(boolean replace) {
        int replaceIndex = replace && entries.hasSelected()
                ? entries.indexOfSelected()
                : -1;
        int insertIndex = entries.hasSelected()
                ? entries.indexOfSelected() + 1
                : working.size();
        new EffortlessItemPickerScreen(
                getEntrance(),
                item -> item instanceof BlockItem,
                first -> new EffortlessItemPickerScreen(
                        getEntrance(),
                        item -> item instanceof BlockItem,
                        second -> {
                            var pair = new ProceduralForbiddenPair(
                                    first.getId().getString(),
                                    second.getId().getString()
                            );
                            if (replaceIndex >= 0) {
                                working.set(replaceIndex, pair);
                            } else {
                                working.add(insertIndex, pair);
                            }
                            entries.reset(working);
                        }
                ).attach()
        ).attach();
    }

    private void title() {
        addWidget(new TextWidget(
                getEntrance(), getLeft() + getWidth() / 2,
                getTop() + PANEL_TITLE_HEIGHT_1 - 10,
                workbenchTitle(),
                TextWidget.Gravity.CENTER
        ));
    }

    private Button action(String label, float x, Button.OnPress press) {
        return Button.builder(getEntrance(), Text.text(label), press)
                .setBoundsGrid(
                        getLeft(), getTop(), getWidth(), getHeight(),
                        1f, x, 1f / 3f
                ).build();
    }

    private void saveCancel() {
        addWidget(Button.builder(getEntrance(), Text.text("Discard"), b ->
                discardAndDetach()
        ).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 0f, 0.5f).build());
        addWidget(Button.builder(getEntrance(), Text.text("Back"), b ->
                detach()
        ).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 0.5f, 0.5f).build());
    }
}
