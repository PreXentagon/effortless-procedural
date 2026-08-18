package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

/**
 * Role-to-recipe editor shared by trees, splines, and future generators.
 */
final class EffortlessGeneratorRecipesScreen
        extends EffortlessProceduralScreen {

    private final ProceduralPatternLibrary library;
    private final UUID currentPreset;
    private final Consumer<Map<String, String>> consumer;
    private final List<Role> working;
    private TextRuleList<Role> entries;
    private Button chooseButton;
    private Button mainButton;

    EffortlessGeneratorRecipesScreen(
            Entrance entrance,
            String title,
            ProceduralPatternLibrary library,
            UUID currentPreset,
            Map<String, String> roles,
            Consumer<Map<String, String>> consumer
    ) {
        super(entrance, Text.text(title), PANEL_WIDTH_60, PANEL_HEIGHT_FULL);
        this.library = library;
        this.currentPreset = currentPreset;
        this.consumer = consumer;
        this.working = new ArrayList<>();
        roles.forEach((name, recipe) -> working.add(new Role(name, recipe)));
        setDraftCommit(() -> {
            var result = new LinkedHashMap<String, String>();
            working.forEach(role -> result.put(role.name(), role.recipeId()));
            consumer.accept(Map.copyOf(result));
            return true;
        });
    }

    @Override
    public void onCreate() {
        addWidget(new TextWidget(
                getEntrance(), getLeft() + getWidth() / 2,
                getTop() + PANEL_TITLE_HEIGHT_1 - 10,
                workbenchTitle(), TextWidget.Gravity.CENTER
        ));
        entries = addWidget(new TextRuleList<>(
                getEntrance(), getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - PANEL_TITLE_HEIGHT_1
                        - PANEL_BUTTON_ROW_HEIGHT_2,
                Role::name,
                this::recipeName
        ));
        entries.setAlwaysShowScrollbar(true);
        entries.reset(working);
        entries.selectByIndex(0);
        mainButton = addWidget(Button.builder(
                getEntrance(), Text.text("Use main"), ignored -> setMain()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                1f, 0f, 0.5f
        ).build());
        chooseButton = addWidget(Button.builder(
                getEntrance(), Text.text("Choose pattern"),
                ignored -> choose()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                1f, 0.5f, 0.5f
        ).build());
        addWidget(Button.builder(
                getEntrance(), Text.text("Discard"),
                ignored -> discardAndDetach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                0f, 0f, 0.5f
        ).build());
        addWidget(Button.builder(
                getEntrance(), Text.text("Back"), ignored -> detach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                0f, 0.5f, 0.5f
        ).build());
    }

    @Override
    public void onReload() {
        boolean selected = entries.hasSelected();
        chooseButton.setActive(selected);
        mainButton.setActive(selected);
        if (entries.consumeDoubleClick() && selected) {
            choose();
        }
    }

    private void choose() {
        if (!entries.hasSelected()) {
            return;
        }
        int index = entries.indexOfSelected();
        var role = working.get(index);
        new EffortlessRecipePickerScreen(
                getEntrance(), role.name(), library, currentPreset,
                role.recipeId(), value -> {
                    working.set(index, new Role(role.name(), value));
                    entries.reset(working);
                    entries.selectByIndex(index);
                }
        ).attach();
    }

    private void setMain() {
        if (!entries.hasSelected()) {
            return;
        }
        int index = entries.indexOfSelected();
        var role = working.get(index);
        working.set(index, new Role(role.name(), ""));
        entries.reset(working);
        entries.selectByIndex(index);
    }

    private String recipeName(Role role) {
        if (role.recipeId().isBlank()) {
            return "Main pattern";
        }
        try {
            UUID id = UUID.fromString(role.recipeId());
            return library.presets().stream()
                    .filter(preset -> preset.id().equals(id))
                    .findFirst()
                    .map(preset -> preset.name() + " · "
                            + preset.blocks().size() + " blocks")
                    .orElse("Missing pattern · " + role.recipeId());
        } catch (IllegalArgumentException exception) {
            return "Invalid pattern id · " + role.recipeId();
        }
    }

    private record Role(String name, String recipeId) {
        private Role {
            recipeId = recipeId == null ? "" : recipeId;
        }
    }
}
