package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.building.config.BuilderConfig;
import dev.huskuraft.effortless.building.config.ClientConfig;
import dev.huskuraft.effortless.building.config.ProceduralSafetyConfig;
import dev.huskuraft.effortless.building.config.RenderConfig;
import dev.huskuraft.effortless.networking.packets.player.PlayerPermissionCheckPacket;
import dev.huskuraft.effortless.screen.common.EffortlessScreen;
import dev.huskuraft.effortless.screen.item.EffortlessItemPickerScreen;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.gui.tooltip.TooltipHelper;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/** Full-screen settings hub using the same visual language as the workbench. */
public class EffortlessWorkbenchSettingsScreen extends EffortlessScreen {

    private static final int FORM_WIDTH = 720;

    private final ProceduralTooltipDelay tooltipDelay =
            new ProceduralTooltipDelay();
    private ClientConfig config;
    private SettingsTab tab = SettingsTab.PREVIEW;

    public EffortlessWorkbenchSettingsScreen(Entrance entrance) {
        super(entrance, Text.text("Effortless Settings"));
        config = getEntrance().getConfigStorage().get();
    }

    @Override
    protected EffortlessClient getEntrance() {
        return (EffortlessClient) super.getEntrance();
    }

    @Override
    public void onCreate() {
        var layout = layout();
        for (int index = 0; index < SettingsTab.values().length; index++) {
            var value = SettingsTab.values()[index];
            var bounds = layout.tab(index, SettingsTab.values().length);
            addWidget(new WorkbenchToolTab(
                    getEntrance(), bounds.x(), bounds.y(),
                    bounds.width(), bounds.height(),
                    Text.text(value.label), Text.text(value.title),
                    Text.text(value.summary), value.accent,
                    () -> tab == value,
                    () -> {
                        tab = value;
                        recreate();
                    }
            ));
        }
        var form = layout.centeredBody(FORM_WIDTH);
        switch (tab) {
            case PREVIEW -> addPreviewSettings(
                    form.x(), form.y(), form.width(), form.height()
            );
            case PERFORMANCE -> addPerformanceSettings(
                    form.x(), form.y(), form.width(), form.height()
            );
            case BUILDING -> addBuildingSettings(
                    form.x(), form.y(), form.width(), form.height()
            );
            case SERVER -> addServerSettings(
                    form.x(), form.y(), form.width(), form.height()
            );
        }

        int actionWidth = Math.min(140, Math.max(96, layout.width() / 6));
        addButton(
                layout.left(), layout.footerY(), actionWidth,
                Text.text("Cancel"),
                button -> detach()
        );
        addButton(
                layout.right() - actionWidth,
                layout.footerY(),
                actionWidth,
                Text.text("Save"),
                button -> {
                    getEntrance().getConfigStorage().update(
                            ignored -> config
                    );
                    detachAll();
                }
        );
    }

    private WorkbenchScreenLayout layout() {
        return WorkbenchScreenLayout.create(
                getScreenWidth(), getScreenHeight()
        );
    }

    private void addPreviewSettings(int x, int y, int width, int height) {
        var render = config.renderConfig();
        var options = addOptions(x, y, width, height);
        options.addSection(Text.text("WORLD PREVIEW"));
        options.addSwitchEntry(
                Text.translate("effortless.render_settings.show_block_preview"),
                Text.empty(), render.showBlockPreview(),
                value -> updateRender(current -> new RenderConfig(
                        value,
                        current.showOtherPlayersBuild(),
                        current.showOtherPlayersBuildTooltips(),
                        current.maxRenderVolume(),
                        current.maxRenderDistance(),
                        current.eraserPreviewBlockId(),
                        current.cutoutPreviewBlockId()
                ))
        );
        options.addSwitchEntry(
                Text.translate(
                        "effortless.render_settings.show_other_players_build"
                ),
                Text.empty(), render.showOtherPlayersBuild(),
                value -> updateRender(current -> new RenderConfig(
                        current.showBlockPreview(), value,
                        current.showOtherPlayersBuildTooltips(),
                        current.maxRenderVolume(),
                        current.maxRenderDistance(),
                        current.eraserPreviewBlockId(),
                        current.cutoutPreviewBlockId()
                ))
        );
        options.addIntegerEntry(
                Text.translate("effortless.render_settings.max_render_volume"),
                Text.empty(), render.maxRenderVolume(),
                RenderConfig.MAX_RENDER_VOLUME_MIN,
                RenderConfig.MAX_RENDER_VOLUME_MAX,
                value -> updateRender(current -> new RenderConfig(
                        current.showBlockPreview(),
                        current.showOtherPlayersBuild(),
                        current.showOtherPlayersBuildTooltips(),
                        value, current.maxRenderDistance(),
                        current.eraserPreviewBlockId(),
                        current.cutoutPreviewBlockId()
                ))
        );
        options.addSection(Text.text("SPECIAL OUTPUT MARKERS"));
        addMarkerEntry(
                options,
                "effortless.render_settings.eraser_preview_block",
                render.eraserPreviewBlockId(),
                value -> updateRender(current ->
                        current.withEraserPreviewBlockId(value))
        );
        addMarkerEntry(
                options,
                "effortless.render_settings.cutout_preview_block",
                render.cutoutPreviewBlockId(),
                value -> updateRender(current ->
                        current.withCutoutPreviewBlockId(value))
        );
    }

    private void addPerformanceSettings(
            int x,
            int y,
            int width,
            int height
    ) {
        var safety = config.proceduralSafetyConfig();
        var options = addOptions(x, y, width, height);
        options.addSection(Text.text("COMPILATION & FEEDBACK"));
        options.addSwitchEntry(
                Text.translate("effortless.procedural_settings.show_preparation"),
                Text.empty(), safety.showPreparationMessages(),
                value -> updateSafety(current ->
                        current.withPreparationMessages(value))
        );
        addSafetyInteger(
                options, "effortless.procedural_settings.async_threshold",
                safety.asyncPreviewPositionThreshold(), 0,
                ProceduralSafetyConfig.MAX_COMPILED_POSITIONS_LIMIT, 1_000,
                value -> updateSafety(current ->
                        current.withAsyncThreshold(value))
        );
        options.addSection(Text.text("LOCAL SAFETY BUDGETS"));
        addSafetyInteger(
                options, "effortless.procedural_settings.max_positions",
                safety.maxCompiledPositions(), 1,
                ProceduralSafetyConfig.MAX_COMPILED_POSITIONS_LIMIT, 10_000,
                value -> updateSafety(current ->
                        current.withMaxPositions(value))
        );
        addSafetyInteger(
                options, "effortless.procedural_settings.max_memory_mib",
                safety.maxEstimatedMemoryMiB(), 1,
                ProceduralSafetyConfig.MAX_MEMORY_MIB_LIMIT, 16,
                value -> updateSafety(current ->
                        current.withMaxMemoryMiB(value))
        );
        addSafetyInteger(
                options, "effortless.procedural_settings.max_packet_mib",
                safety.maxPacketMiB(), 1,
                ProceduralSafetyConfig.MAX_PACKET_MIB_LIMIT, 1,
                value -> updateSafety(current ->
                        current.withMaxPacketMiB(value))
        );
        addSafetyInteger(
                options, "effortless.procedural_settings.max_work_millions",
                safety.maxEstimatedWorkMillions(), 1,
                ProceduralSafetyConfig.MAX_WORK_MILLIONS_LIMIT, 50,
                value -> updateSafety(current ->
                        current.withMaxWorkMillions(value))
        );
        addSafetyInteger(
                options, "effortless.procedural_settings.road_preview_distance",
                safety.roadPreviewDistance(), 1,
                ProceduralSafetyConfig.MAX_PREVIEW_DISTANCE, 16,
                value -> updateSafety(current ->
                        current.withRoadPreviewDistance(value))
        );
    }

    private void addBuildingSettings(int x, int y, int width, int height) {
        var builder = config.builderConfig();
        var options = addOptions(x, y, width, height);
        options.addSection(Text.text("SURVIVAL BUILDING"));
        options.addIntegerEntry(
                Text.translate(
                        "effortless.builder_settings.reserved_tool_durability"
                ),
                Text.empty(), builder.reservedToolDurability(),
                BuilderConfig.RESERVED_TOOL_DURABILITY_RANGE.min(),
                BuilderConfig.RESERVED_TOOL_DURABILITY_RANGE.max(),
                value -> config = config.withBuilderConfig(
                        new BuilderConfig(value, builder().passiveMode())
                )
        );
        options.addSwitchEntry(
                Text.translate("effortless.builder_settings.passive_mode"),
                Text.empty(), builder.passiveMode(),
                value -> config = config.withBuilderConfig(
                        new BuilderConfig(
                                builder().reservedToolDurability(), value
                        )
                )
        );
        options.addSection(Text.text("COMPATIBILITY"));
        options.addSwitchEntry(
                Text.text("Stock server enforcement"),
                Text.text("Recipes compile to existing block/air snapshots; "
                        + "client limits never bypass server policy."),
                true,
                ignored -> {
                }
        ).setActive(false);
    }

    private void addServerSettings(int x, int y, int width, int height) {
        addWidget(new TextWidget(
                getEntrance(), x + 8, y + 8,
                Text.text("SERVER POLICY").withStyle(ChatFormatting.GOLD)
        ));
        int textY = y + 30;
        var description = Text.text(
                "These settings belong to the connected server and require operator permission."
        );
        for (var line : TooltipHelper.wrapLines(
                getTypeface(), description, width - 16
        )) {
            addWidget(new TextWidget(
                    getEntrance(), x + 8, textY,
                    line.withStyle(ChatFormatting.GRAY)
            ));
            textY += 11;
        }
        addButton(
                x + 8, Math.max(y + 56, textY + 8),
                Math.min(240, width - 16),
                Text.text("Open server settings"),
                button -> openServerSettings()
        );
    }

    private void openServerSettings() {
        if (!getEntrance().getSessionManager().isSessionValid()) {
            new EffortlessWorkbenchMessageScreen(
                    getEntrance(),
                    Text.translate("effortless.session_status.title"),
                    sessionStatusMessage()
            ).attach();
            return;
        }
        var player = getEntrance().getClient().getPlayer();
        getEntrance().getChannel().sendPacket(
                new PlayerPermissionCheckPacket(player.getId()),
                packet -> getEntrance().getClient().execute(() -> {
                    if (!packet.granted()) {
                        new EffortlessWorkbenchMessageScreen(
                                getEntrance(),
                                Text.translate("effortless.not_an_operator.title"),
                                Text.translate("effortless.not_an_operator.message")
                        ).attach();
                        return;
                    }
                    new EffortlessServerSettingsScreen(
                            getEntrance(),
                            getEntrance().getSessionManager()
                                    .getServerSessionConfigOrEmpty()
                                    .getGlobalConfig(),
                            value -> getEntrance().getSessionManager()
                                    .updateGlobalConfig(value)
                    ).attach();
                })
        );
    }

    private Text sessionStatusMessage() {
        return switch (getEntrance().getSessionManager().getSessionStatus()) {
            case MOD_MISSING -> Text.translate(
                    "effortless.session_status.message.mod_missing"
            );
            case SERVER_MOD_MISSING -> Text.translate(
                    "effortless.session_status.message.server_mod_missing"
            );
            case CLIENT_MOD_MISSING -> Text.translate(
                    "effortless.session_status.message.client_mod_missing"
            );
            case PROTOCOL_NOT_MATCH -> Text.translate(
                    "effortless.session_status.message.protocol_not_match",
                    getEntrance().getSessionManager().getServerSession()
                            .protocolVersion(),
                    getEntrance().getSessionManager().getLastSession()
                            .protocolVersion()
            );
            case SUCCESS -> Text.translate(
                    "effortless.session_status.message.success",
                    getEntrance().getSessionManager().getServerSession()
                            .loaderType().name()
            );
        };
    }

    private ProceduralSettingOptionsList addOptions(
            int x,
            int y,
            int width,
            int height
    ) {
        var options = addWidget(new ProceduralSettingOptionsList(
                getEntrance(), x, y, width - 8, Math.max(40, height),
                false, false
        ));
        options.setAlwaysShowScrollbar(true);
        return options;
    }

    private void addMarkerEntry(
            ProceduralSettingOptionsList options,
            String titleKey,
            String value,
            Consumer<String> consumer
    ) {
        options.addTab(
                Text.translate(titleKey), Text.empty(), value, consumer,
                (entry, current) -> {
                    entry.getButton().setMessage(Text.text(shortId(current)));
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessItemPickerScreen(
                                    getEntrance(),
                                    item -> item instanceof BlockItem,
                                    item -> entry.setItem(
                                            item.getId().getString()
                                    )
                            ).attach()
                    );
                }
        );
    }

    private void addSafetyInteger(
            ProceduralSettingOptionsList options,
            String key,
            int value,
            int minimum,
            int maximum,
            int step,
            Consumer<Integer> consumer
    ) {
        options.addIntegerEntry(
                Text.translate(key), Text.empty(), value,
                minimum, maximum, step, consumer
        );
    }

    private void updateRender(
            java.util.function.UnaryOperator<RenderConfig> operation
    ) {
        config = config.withRenderConfig(operation.apply(config.renderConfig()));
    }

    private void updateSafety(
            java.util.function.UnaryOperator<ProceduralSafetyConfig> operation
    ) {
        config = config.withProceduralSafetyConfig(
                operation.apply(config.proceduralSafetyConfig())
        );
    }

    private BuilderConfig builder() {
        return config.builderConfig();
    }

    private Button addButton(
            int x,
            int y,
            int width,
            Text label,
            Consumer<Button> action
    ) {
        return addWidget(new Button(
                getEntrance(), x, y, width,
                WorkbenchScreenLayout.CONTROL_HEIGHT,
                label, button -> action.accept(button)
        ));
    }

    @Override
    public void renderWidgetBackground(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        ProceduralTheme.renderWorkbenchBackdrop(
                renderer, getScreenWidth(), getScreenHeight()
        );
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        ProceduralTheme.renderWorkbenchFrame(
                renderer, getTypeface(), getScreenTitle(), layout()
        );
        Runnable restore = ProceduralTheme.suppressDirectButtonLabels(
                children()
        );
        try {
            super.renderWidget(renderer, mouseX, mouseY, deltaTick);
        } finally {
            restore.run();
        }
        for (var child : children()) {
            if (child instanceof Button button) {
                ProceduralTheme.renderButton(renderer, getTypeface(), button);
            }
        }
    }

    @Override
    public void renderWidgetOverlay(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        for (var child : children()) {
            if (child instanceof Button button) {
                button.setTooltip(ProceduralTooltips.action(
                        getTypeface(), button.getMessage()
                ));
            }
        }
        if (tooltipDelay.isReady(mouseX, mouseY)) {
            super.renderWidgetOverlay(renderer, mouseX, mouseY, deltaTick);
        }
    }

    private static String shortId(String value) {
        int separator = value.indexOf(':');
        return separator < 0 ? value : value.substring(separator + 1);
    }

    private enum SettingsTab {
        PREVIEW("View", "Preview", "Preview rendering and marker blocks.",
                0xFF6B959E),
        PERFORMANCE("Limits", "Performance", "Client-only compilation budgets.",
                0xFFA18450),
        BUILDING("Build", "Building", "Survival and builder behavior.",
                0xFF738E70),
        SERVER("Server", "Server", "Connected-server policy settings.",
                0xFFA66F6F);

        private final String label;
        private final String title;
        private final String summary;
        private final int accent;

        SettingsTab(
                String label,
                String title,
                String summary,
                int accent
        ) {
            this.label = label;
            this.title = title;
            this.summary = summary;
            this.accent = accent;
        }
    }
}
