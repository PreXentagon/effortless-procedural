package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralMaterial;
import dev.huskuraft.effortless.screen.item.EffortlessItemPickerScreen;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

final class EffortlessAllowedItemsScreen extends EffortlessProceduralScreen {

    private final Consumer<List<String>> consumer;
    private final List<String> working;
    private TextRuleList<String> entries;
    private Button deleteButton;
    private Button skipButton;
    private Button eraserButton;

    EffortlessAllowedItemsScreen(
            Entrance entrance,
            Consumer<List<String>> consumer,
            List<String> entries
    ) {
        this(entrance, "Allowed neighbor blocks", consumer, entries);
    }

    EffortlessAllowedItemsScreen(
            Entrance entrance,
            String title,
            Consumer<List<String>> consumer,
            List<String> entries
    ) {
        super(entrance, Text.text(title), PANEL_WIDTH_60, PANEL_HEIGHT_FULL);
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
                value -> value,
                value -> ""
        ));
        entries.setAlwaysShowScrollbar(true);
        entries.reset(working);
        deleteButton = addWidget(Button.builder(getEntrance(), Text.text("Delete"), b -> {
            if (entries.hasSelected()) {
                int index = entries.indexOfSelected();
                working.remove(index);
                entries.reset(working);
            }
        }).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(), 1f, 0f, 0.25f
        ).build());
        addWidget(Button.builder(getEntrance(), Text.text("Add"), b ->
                new EffortlessItemPickerScreen(
                        getEntrance(),
                        item -> item instanceof BlockItem
                                && !working.contains(item.getId().getString()),
                        item -> {
                            int index = entries.hasSelected()
                                    ? entries.indexOfSelected() + 1
                                    : working.size();
                            working.add(index, item.getId().getString());
                            entries.reset(working);
                        }
                ).attach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(), 1f, 0.25f, 0.25f
        ).build());
        skipButton = addWidget(Button.builder(
                getEntrance(), Text.text("Skip"), b -> addSpecial(
                        ProceduralMaterial.SKIP_ID
                )
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(), 1f, 0.5f, 0.25f
        ).build());
        eraserButton = addWidget(Button.builder(
                getEntrance(), Text.text("Eraser"), b -> addSpecial(
                        ProceduralMaterial.ERASER_ID
                )
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(), 1f, 0.75f, 0.25f
        ).build());
        addWidget(Button.builder(getEntrance(), Text.text("Discard"), b ->
                discardAndDetach()
        ).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 0f, 0.5f).build());
        addWidget(Button.builder(getEntrance(), Text.text("Back"), b ->
                detach()
        ).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 0.5f, 0.5f).build());
    }

    @Override
    public void onReload() {
        deleteButton.setActive(entries.hasSelected() && entries.items().size() > 1);
        skipButton.setActive(!working.contains(ProceduralMaterial.SKIP_ID));
        eraserButton.setActive(!working.contains(ProceduralMaterial.ERASER_ID));
    }

    private void addSpecial(String itemId) {
        if (working.contains(itemId)) {
            return;
        }
        int index = entries.hasSelected()
                ? entries.indexOfSelected() + 1
                : working.size();
        working.add(index, itemId);
        entries.reset(working);
    }
}
