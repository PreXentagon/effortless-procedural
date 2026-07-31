package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.road.RoadProfile;
import dev.huskuraft.effortless.client.road.SplineCrossSectionBand;
import dev.huskuraft.effortless.client.road.SplineCutoutConfig;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

/** Focused editor for reusable spline cross-sections and deep cutouts. */
final class EffortlessSplineGeometryScreen extends EffortlessProceduralScreen {

    private final Consumer<RoadProfile> consumer;
    private final ProceduralPatternLibrary library;
    private final UUID currentPresetId;
    private RoadProfile profile;

    EffortlessSplineGeometryScreen(
            Entrance entrance,
            Consumer<RoadProfile> consumer,
            RoadProfile profile,
            ProceduralPatternLibrary library,
            UUID currentPresetId
    ) {
        super(
                entrance, Text.translate(
                        "effortless.procedural.spline.geometry.title"
                ),
                PANEL_WIDTH_60, PANEL_HEIGHT_FULL
        );
        this.consumer = consumer;
        this.profile = profile;
        this.library = library;
        this.currentPresetId = currentPresetId;
        setDraftCommit(() -> {
            consumer.accept(this.profile);
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
                workbenchTitle(), TextWidget.Gravity.CENTER
        ));
        var options = addWidget(new ProceduralSettingOptionsList(
                getEntrance(),
                getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - PANEL_TITLE_HEIGHT_1
                        - PANEL_BUTTON_ROW_HEIGHT_1,
                false, false
        ));
        options.setAlwaysShowScrollbar(true);

        options.addSection(Text.translate(
                "effortless.procedural.spline.geometry.bands.section"
        ));
        options.addTab(
                Text.translate(
                        "effortless.procedural.spline.geometry.bands"
                ),
                Text.translate(
                        "effortless.procedural.tooltip.spline.geometry.bands"
                ),
                profile.crossSectionBands(),
                value -> profile = profile.withCrossSectionBands(value),
                (entry, value) -> {
                    entry.getButton().setMessage(Text.text(
                            value.size() + " bands"
                    ));
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessAdvancedRuleListScreen<>(
                                    getEntrance(),
                                    "Spline cross-section bands",
                                    entry::setItem,
                                    value,
                                    SplineCrossSectionBand::name,
                                    band -> band.side().name().toLowerCase()
                                            + " · " + band.width()
                                            + " wide · y "
                                            + signed(band.heightOffset()),
                                    () -> SplineCrossSectionBand.DEFAULT,
                                    request -> new EffortlessSplineBandEditScreen(
                                            getEntrance(),
                                            request.consumer(),
                                            request.value(),
                                            library,
                                            currentPresetId
                                    ).attach()
                            ).attach()
                    );
                }
        );

        options.addSection(Text.translate(
                "effortless.procedural.spline.geometry.cutout.section"
        ));
        options.addSwitchEntry(
                Text.translate(
                        "effortless.procedural.spline.geometry.cutout.enabled"
                ),
                Text.translate(
                        "effortless.procedural.tooltip.spline.geometry.cutout.enabled"
                ),
                profile.cutout().enabled(),
                value -> updateCutout(current -> current.withEnabled(value))
        );
        options.addIntegerEntry(
                Text.translate(
                        "effortless.procedural.spline.geometry.cutout.depth"
                ),
                Text.translate(
                        "effortless.procedural.tooltip.spline.geometry.cutout.depth"
                ),
                profile.cutout().depth(), 1, SplineCutoutConfig.MAX_DEPTH,
                value -> updateCutout(current -> current.withDepth(value))
        );
        options.addRangeEntry(
                Text.translate(
                        "effortless.procedural.spline.geometry.cutout.taper"
                ),
                Text.translate(
                        "effortless.procedural.tooltip.spline.geometry.cutout.taper"
                ),
                profile.cutout().taperPerLayer(), 0.0, 1.0, 0.05,
                value -> updateCutout(current ->
                        current.withTaperPerLayer(value))
        );
        options.addSwitchEntry(
                Text.translate(
                        "effortless.procedural.spline.geometry.cutout.walls"
                ), Text.empty(),
                profile.cutout().lineWalls(),
                value -> updateCutout(current ->
                        current.withLineWalls(value))
        );
        options.addIntegerEntry(
                Text.translate(
                        "effortless.procedural.spline.geometry.cutout.wall_thickness"
                ), Text.empty(),
                profile.cutout().wallThickness(), 0,
                SplineCutoutConfig.MAX_WALL_THICKNESS,
                value -> updateCutout(current ->
                        current.withWallThickness(value))
        );
        options.addSwitchEntry(
                Text.translate(
                        "effortless.procedural.spline.geometry.cutout.floor"
                ), Text.empty(),
                profile.cutout().lineFloor(),
                value -> updateCutout(current ->
                        current.withLineFloor(value))
        );
        options.addTab(
                Text.translate(
                        "effortless.procedural.spline.geometry.cutout.recipes"
                ),
                Text.translate(
                        "effortless.procedural.tooltip.spline.geometry.cutout.recipes"
                ),
                profile.cutout(),
                value -> profile = profile.withCutout(value),
                (entry, value) -> {
                    long linked = List.of(
                            value.wallRecipeId(), value.floorRecipeId()
                    ).stream().filter(id -> !id.isBlank()).count();
                    entry.getButton().setMessage(Text.text(
                            linked + " linked · 2 roles"
                    ));
                    entry.getButton().setOnPressListener(button -> {
                        var roles = new java.util.LinkedHashMap<
                                String, String>();
                        roles.put("Cutout walls", value.wallRecipeId());
                        roles.put("Cutout floor", value.floorRecipeId());
                        new EffortlessGeneratorRecipesScreen(
                                getEntrance(), "Cutout material recipes",
                                library, currentPresetId, roles,
                                changed -> entry.setItem(value.withRecipes(
                                        changed.getOrDefault(
                                                "Cutout walls", ""
                                        ),
                                        changed.getOrDefault(
                                                "Cutout floor", ""
                                        )
                                ))
                        ).attach();
                    });
                }
        );

        addWidget(Button.builder(
                getEntrance(), Text.text("Discard"),
                button -> discardAndDetach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                0f, 0f, 0.5f
        ).build());
        addWidget(Button.builder(
                getEntrance(), Text.text("Back"), button -> detach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                0f, 0.5f, 0.5f
        ).build());
    }

    private void updateCutout(
            java.util.function.UnaryOperator<SplineCutoutConfig> updater
    ) {
        profile = profile.withCutout(updater.apply(profile.cutout()));
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
