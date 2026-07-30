package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPreferredPair;
import dev.huskuraft.effortless.screen.item.EffortlessItemPickerScreen;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

final class EffortlessPreferredAdjacencyScreen
        extends EffortlessProceduralScreen {

    private final Consumer<List<ProceduralPreferredPair>> consumer;
    private final List<ProceduralPreferredPair> working;
    private TextRuleList<ProceduralPreferredPair> entries;
    private Button editButton;
    private Button deleteButton;

    EffortlessPreferredAdjacencyScreen(
            Entrance entrance,
            Consumer<List<ProceduralPreferredPair>> consumer,
            List<ProceduralPreferredPair> entries
    ) {
        super(entrance, Text.text("Preferred adjacency"), PANEL_WIDTH_60, PANEL_HEIGHT_FULL);
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
        addWidget(new TextWidget(
                getEntrance(), getLeft() + getWidth() / 2,
                getTop() + PANEL_TITLE_HEIGHT_1 - 10,
                workbenchTitle(),
                TextWidget.Gravity.CENTER
        ));
        entries = addWidget(new TextRuleList<>(
                getEntrance(),
                getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - PANEL_TITLE_HEIGHT_1 - PANEL_BUTTON_ROW_HEIGHT_2,
                pair -> pair.itemId() + " prefers " + pair.preferredNeighborItemId(),
                pair -> "weight multiplier ×" + pair.multiplier()
        ));
        entries.setAlwaysShowScrollbar(true);
        entries.reset(working);
        editButton = addWidget(action("Edit weight", 0f, button -> editSelected()));
        deleteButton = addWidget(action("Delete", 1f / 3f, button -> {
            if (entries.hasSelected()) {
                int index = entries.indexOfSelected();
                working.remove(index);
                entries.reset(working);
            }
        }));
        addWidget(action("Add", 2f / 3f, button -> choosePair()));
        addWidget(Button.builder(getEntrance(), Text.text("Discard"), b ->
                discardAndDetach()
        ).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 0f, 0.5f).build());
        addWidget(Button.builder(getEntrance(), Text.text("Back"), b ->
                detach()
        ).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 0.5f, 0.5f).build());
    }

    @Override
    public void onReload() {
        editButton.setActive(entries.hasSelected());
        deleteButton.setActive(entries.hasSelected());
        if (entries.consumeDoubleClick() && entries.hasSelected()) {
            editSelected();
        }
    }

    private void choosePair() {
        new EffortlessItemPickerScreen(
                getEntrance(),
                item -> item instanceof BlockItem,
                first -> new EffortlessItemPickerScreen(
                        getEntrance(),
                        item -> item instanceof BlockItem,
                        second -> edit(new ProceduralPreferredPair(
                            first.getId().getString(),
                            second.getId().getString(),
                            2.0
                        ), -1)
                ).attach()
        ).attach();
    }

    private void editSelected() {
        if (entries.hasSelected()) {
            int index = entries.indexOfSelected();
            edit(working.get(index), index);
        }
    }

    private void edit(ProceduralPreferredPair pair, int replaceIndex) {
        int insertIndex = entries.hasSelected()
                ? entries.indexOfSelected() + 1
                : working.size();
        new EffortlessPreferredAdjacencyEditScreen(
                getEntrance(),
                result -> {
                    if (replaceIndex >= 0) {
                        working.set(replaceIndex, result);
                    } else {
                        working.add(insertIndex, result);
                    }
                    entries.reset(working);
                },
                pair
        ).attach();
    }

    private Button action(String label, float x, Button.OnPress press) {
        return Button.builder(getEntrance(), Text.text(label), press)
                .setBoundsGrid(
                        getLeft(), getTop(), getWidth(), getHeight(),
                        1f, x, 1f / 3f
                ).build();
    }
}
