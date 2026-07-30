package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

final class EffortlessAdvancedRuleListScreen<T>
        extends EffortlessProceduralScreen {

    private final Consumer<List<T>> consumer;
    private final List<T> working;
    private final Function<T, String> primary;
    private final Function<T, String> secondary;
    private final Supplier<T> defaultRule;
    private final Consumer<EditRequest<T>> editor;
    private TextRuleList<T> entries;
    private Button editButton;
    private Button upButton;
    private Button downButton;
    private Button deleteButton;

    EffortlessAdvancedRuleListScreen(
            Entrance entrance,
            String title,
            Consumer<List<T>> consumer,
            List<T> original,
            Function<T, String> primary,
            Function<T, String> secondary,
            Supplier<T> defaultRule,
            Consumer<EditRequest<T>> editor
    ) {
        super(entrance, Text.text(title), PANEL_WIDTH_60, PANEL_HEIGHT_FULL);
        this.consumer = consumer;
        this.working = new ArrayList<>(original);
        this.primary = primary;
        this.secondary = secondary;
        this.defaultRule = defaultRule;
        this.editor = editor;
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
        entries = addWidget(new TextRuleList<>(
                getEntrance(),
                getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - PANEL_TITLE_HEIGHT_1 - PANEL_BUTTON_ROW_HEIGHT_2,
                primary,
                secondary
        ));
        entries.setAlwaysShowScrollbar(true);
        entries.reset(working);

        editButton = addWidget(action("Edit", 0f, button -> editSelected()));
        upButton = addWidget(action("Up", 0.2f, button -> moveSelected(-1)));
        downButton = addWidget(action("Down", 0.4f, button ->
                moveSelected(1)));
        deleteButton = addWidget(action("Delete", 0.6f, button -> {
            if (entries.hasSelected()) {
                int index = entries.indexOfSelected();
                working.remove(index);
                entries.reset(working);
            }
        }));
        addWidget(action("Add", 0.8f, button -> addRule()));

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

    @Override
    public void onReload() {
        editButton.setActive(entries.hasSelected());
        upButton.setActive(
                entries.hasSelected() && entries.indexOfSelected() > 0
        );
        downButton.setActive(
                entries.hasSelected()
                        && entries.indexOfSelected() < working.size() - 1
        );
        deleteButton.setActive(entries.hasSelected());
        if (entries.consumeDoubleClick() && entries.hasSelected()) {
            editSelected();
        }
    }

    private void editSelected() {
        if (entries.hasSelected()) {
            int index = entries.indexOfSelected();
            editor.accept(new EditRequest<>(
                    value -> {
                        working.set(index, value);
                        entries.reset(working);
                    },
                    working.get(index)
            ));
        }
    }

    private void addRule() {
        int index = entries.hasSelected()
                ? entries.indexOfSelected() + 1
                : working.size();
        editor.accept(new EditRequest<>(
                value -> {
                    working.add(index, value);
                    entries.reset(working);
                },
                defaultRule.get()
        ));
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

    private Button action(String label, float horizontal, Button.OnPress press) {
        return Button.builder(getEntrance(), Text.text(label), press)
                .setBoundsGrid(
                        getLeft(), getTop(), getWidth(), getHeight(),
                        1f, horizontal, 0.2f
                )
                .build();
    }

    record EditRequest<T>(Consumer<T> consumer, T value) {
    }
}
