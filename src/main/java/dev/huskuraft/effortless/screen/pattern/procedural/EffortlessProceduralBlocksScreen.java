package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.GradientDistributionMode;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralBlockEntry;
import dev.huskuraft.effortless.screen.item.EffortlessItemPickerScreen;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

final class EffortlessProceduralBlocksScreen
        extends EffortlessProceduralScreen {

    private final Consumer<List<ProceduralBlockEntry>> consumer;
    private final List<ProceduralBlockEntry> working;
    private final GradientDistributionMode gradientMode;
    private ProceduralBlockList entries;
    private Button editButton;
    private Button upButton;
    private Button downButton;
    private Button deleteButton;

    EffortlessProceduralBlocksScreen(
            Entrance entrance,
            Consumer<List<ProceduralBlockEntry>> consumer,
            List<ProceduralBlockEntry> entries,
            GradientDistributionMode gradientMode
    ) {
        super(
                entrance,
                Text.text(
                        "Block palette ("
                                + gradientMode.name().toLowerCase()
                                        .replace('_', ' ')
                                + ")"
                ),
                PANEL_WIDTH_60,
                PANEL_HEIGHT_FULL
        );
        this.consumer = consumer;
        this.working = new ArrayList<>(entries);
        this.gradientMode = gradientMode;
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
                getEntrance(),
                getLeft() + getWidth() / 2,
                getTop() + PANEL_TITLE_HEIGHT_1 - 10,
                workbenchTitle(),
                TextWidget.Gravity.CENTER
        ));
        entries = addWidget(new ProceduralBlockList(
                getEntrance(),
                getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - PANEL_TITLE_HEIGHT_1 - PANEL_BUTTON_ROW_HEIGHT_2,
                gradientMode
        ));
        entries.setAlwaysShowScrollbar(true);
        entries.reset(working);

        editButton = addWidget(actionButton("Edit", 0f, button -> editSelected()));
        upButton = addWidget(actionButton("Up", 0.2f, button ->
                moveSelected(-1)));
        downButton = addWidget(actionButton("Down", 0.4f, button ->
                moveSelected(1)));
        deleteButton = addWidget(actionButton("Delete", 0.6f, button -> {
            if (entries.hasSelected()) {
                int index = entries.indexOfSelected();
                working.remove(index);
                entries.reset(working);
            }
        }));
        addWidget(actionButton("Add", 0.8f, button -> addBlock()));

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

    @Override
    public void onReload() {
        boolean selected = entries.hasSelected();
        editButton.setActive(selected);
        upButton.setActive(selected && entries.indexOfSelected() > 0);
        downButton.setActive(
                selected && entries.indexOfSelected() < working.size() - 1
        );
        deleteButton.setActive(selected && entries.items().size() > 1);
        if (entries.consumeDoubleClick() && selected) {
            editSelected();
        }
    }

    private Button actionButton(
            String message,
            float horizontal,
            Button.OnPress press
    ) {
        return Button.builder(getEntrance(), Text.text(message), press)
                .setBoundsGrid(
                        getLeft(), getTop(), getWidth(), getHeight(),
                        1f, horizontal, 0.2f
                ).build();
    }

    private void moveSelected(int offset) {
        if (!entries.hasSelected()) {
            return;
        }
        int index = entries.indexOfSelected();
        int target = index + offset;
        if (target < 0 || target >= working.size()) {
            return;
        }
        if (offset < 0) {
            entries.moveUpSelected();
        } else {
            entries.moveDownSelected();
        }
        working.clear();
        working.addAll(entries.items());
    }

    private void addBlock() {
        new EffortlessItemPickerScreen(
                getEntrance(),
                item -> item instanceof BlockItem
                        && working.stream().noneMatch(
                                entry -> entry.itemId().equals(item.getId().getString())
                        ),
                item -> editEntry(
                        ProceduralBlockEntry.weighted(item.getId().getString(), 1.0),
                        -1
                )
        ).attach();
    }

    private void editSelected() {
        if (entries.hasSelected()) {
            int index = entries.indexOfSelected();
            editEntry(working.get(index), index);
        }
    }

    private void editEntry(ProceduralBlockEntry entry, int replaceIndex) {
        int insertIndex = entries.hasSelected()
                ? entries.indexOfSelected() + 1
                : working.size();
        new EffortlessProceduralBlockEditScreen(
                getEntrance(),
                result -> {
                    if (replaceIndex >= 0) {
                        working.set(replaceIndex, result);
                    } else {
                        working.add(insertIndex, result);
                    }
                    entries.reset(working);
                },
                entry,
                gradientMode
        ).attach();
    }
}
