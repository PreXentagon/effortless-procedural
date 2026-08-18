package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

/** Searchable selector that remains usable with large pattern libraries. */
final class EffortlessRecipePickerScreen extends EffortlessProceduralScreen {

    private final List<Choice> choices;
    private final Consumer<String> consumer;
    private final String original;
    private TextRuleList<Choice> entries;
    private Button useButton;
    private ReliableEditBox search;

    EffortlessRecipePickerScreen(
            Entrance entrance,
            String role,
            ProceduralPatternLibrary library,
            UUID currentPreset,
            String selected,
            Consumer<String> consumer
    ) {
        super(
                entrance,
                Text.text("Choose " + role + " recipe"),
                PANEL_WIDTH_60,
                PANEL_HEIGHT_FULL
        );
        this.consumer = consumer;
        this.original = selected == null ? "" : selected;
        var values = new ArrayList<Choice>();
        values.add(new Choice("", "Use main recipe", "No linked recipe"));
        for (var preset : library.presets()) {
            if (preset.id().equals(currentPreset)) {
                continue;
            }
            values.add(new Choice(
                    preset.id().toString(),
                    preset.name(),
                    preset.blocks().size() + " blocks, seed " + preset.seed()
            ));
        }
        choices = List.copyOf(values);
    }

    @Override
    public void onCreate() {
        addWidget(new TextWidget(
                getEntrance(), getLeft() + getWidth() / 2,
                getTop() + PANEL_TITLE_HEIGHT_1 - 10,
                workbenchTitle(), TextWidget.Gravity.CENTER
        ));
        search = addWidget(new ReliableEditBox(
                getEntrance(), getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1,
                getWidth() - PADDINGS_H * 2,
                20, Text.text("Search recipes")
        ));
        search.setMaxLength(80);
        search.setChangeListener(ignored -> refreshEntries());
        entries = addWidget(new TextRuleList<>(
                getEntrance(), getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1 + 26,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - PANEL_TITLE_HEIGHT_1 - 26
                        - PANEL_BUTTON_ROW_HEIGHT_1,
                Choice::name,
                Choice::details
        ));
        entries.setAlwaysShowScrollbar(true);
        useButton = addWidget(Button.builder(
                getEntrance(), Text.text("Use selected"), ignored -> apply()
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
        refreshEntries();
    }

    @Override
    public void onReload() {
        useButton.setActive(entries.hasSelected());
        if (entries.consumeDoubleClick() && entries.hasSelected()) {
            apply();
        }
    }

    private void refreshEntries() {
        if (entries == null) {
            return;
        }
        String query = search == null
                ? ""
                : search.getValue().trim().toLowerCase(Locale.ROOT);
        var filtered = choices.stream()
                .filter(value -> query.isEmpty()
                        || value.name().toLowerCase(Locale.ROOT).contains(query)
                        || value.details().toLowerCase(Locale.ROOT)
                                .contains(query))
                .toList();
        entries.reset(filtered);
        String desired = original;
        entries.selectFirst(choice -> choice.id().equals(desired));
    }

    private void apply() {
        if (!entries.hasSelected()) {
            return;
        }
        consumer.accept(entries.getSelected().getItem().id());
        detach();
    }

    private record Choice(String id, String name, String details) {
    }
}
