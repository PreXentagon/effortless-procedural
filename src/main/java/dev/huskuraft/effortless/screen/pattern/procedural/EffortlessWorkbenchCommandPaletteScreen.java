package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;
import java.util.Locale;

import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

/** Searchable workbench actions, equivalent to a compact command palette. */
final class EffortlessWorkbenchCommandPaletteScreen
        extends EffortlessProceduralScreen {

    private final List<Command> commands;
    private TextRuleList<Command> entries;
    private ReliableEditBox search;
    private Button runButton;

    EffortlessWorkbenchCommandPaletteScreen(
            Entrance entrance,
            List<Command> commands
    ) {
        super(
                entrance,
                Text.text("Workbench commands"),
                PANEL_WIDTH_60,
                PANEL_HEIGHT_FULL
        );
        this.commands = List.copyOf(commands);
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
        search = addWidget(new ReliableEditBox(
                getEntrance(),
                getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1,
                getWidth() - PADDINGS_H * 2,
                20,
                Text.text("Find a tool or action")
        ));
        search.setHint(Text.text("Type a command..."));
        search.setMaxLength(80);
        search.setChangeListener(ignored -> refresh());

        entries = addWidget(new TextRuleList<>(
                getEntrance(),
                getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1 + 26,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - PANEL_TITLE_HEIGHT_1 - 26
                        - PANEL_BUTTON_ROW_HEIGHT_1,
                Command::label,
                Command::details
        ));
        entries.setAlwaysShowScrollbar(true);
        runButton = addWidget(Button.builder(
                getEntrance(), Text.text("Run"), ignored -> runSelected()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                0f, 0.5f, 0.5f
        ).build());
        addWidget(Button.builder(
                getEntrance(), Text.text("Cancel"), ignored -> detach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                0f, 0f, 0.5f
        ).build());
        refresh();
        setFocused(search);
    }

    @Override
    public void onReload() {
        runButton.setActive(entries.hasSelected());
        if (entries.consumeDoubleClick()) {
            runSelected();
        }
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        return switch (keyCode) {
            case 257, 335 -> {
                runSelected();
                yield true;
            }
            case 264 -> {
                entries.moveSelection(1);
                yield true;
            }
            case 265 -> {
                entries.moveSelection(-1);
                yield true;
            }
            default -> super.onKeyPressed(keyCode, scanCode, modifiers);
        };
    }

    private void refresh() {
        if (entries == null) {
            return;
        }
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        entries.reset(commands.stream()
                .filter(command -> query.isEmpty()
                        || command.label().toLowerCase(Locale.ROOT)
                                .contains(query)
                        || command.details().toLowerCase(Locale.ROOT)
                                .contains(query))
                .toList());
        if (!entries.children().isEmpty()) {
            entries.selectByIndex(0);
        }
    }

    private void runSelected() {
        if (!entries.hasSelected()) {
            return;
        }
        var command = entries.getSelected().getItem();
        detach();
        command.action().run();
    }

    record Command(String label, String details, Runnable action) {
    }
}
