package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.Optional;
import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.screen.common.EffortlessScreen;
import dev.huskuraft.effortless.screen.general.EffortlessPlayerGeneralSettingsListScreen;
import dev.huskuraft.effortless.screen.item.EffortlessItemsScreen;
import dev.huskuraft.effortless.session.config.ConstraintConfig;
import dev.huskuraft.universal.api.core.Item;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/** Workbench-styled editor for the unchanged server constraint model. */
public final class EffortlessServerSettingsScreen extends EffortlessScreen {

    private static final int FORM_WIDTH = 720;

    private final Consumer<ConstraintConfig> consumer;
    private final ProceduralTooltipDelay tooltipDelay =
            new ProceduralTooltipDelay();
    private ConstraintConfig config;
    private ServerTab tab = ServerTab.PERMISSIONS;

    public EffortlessServerSettingsScreen(
            Entrance entrance,
            ConstraintConfig config,
            Consumer<ConstraintConfig> consumer
    ) {
        super(entrance, Text.text("Server Policy"));
        this.config = config;
        this.consumer = consumer;
    }

    @Override
    protected EffortlessClient getEntrance() {
        return (EffortlessClient) super.getEntrance();
    }

    @Override
    public void onCreate() {
        var layout = layout();
        for (int index = 0; index < ServerTab.values().length; index++) {
            var value = ServerTab.values()[index];
            var bounds = layout.tab(index, ServerTab.values().length);
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
        var options = addOptions(
                form.x(), form.y(), form.width(), form.height()
        );
        switch (tab) {
            case PERMISSIONS -> addPermissions(options);
            case LIMITS -> addLimits(options);
            case ITEMS -> addItems(options);
        }

        int actionWidth = Math.min(120, Math.max(82, layout.width() / 7));
        int gap = WorkbenchScreenLayout.GAP;
        addButton(layout.left(), layout.footerY(), actionWidth,
                Text.text("Cancel"),
                button -> detach());
        addButton(layout.left() + actionWidth + gap, layout.footerY(),
                actionWidth,
                Text.text("Reset"), button -> {
                    config = ConstraintConfig.DEFAULT;
                    recreate();
                });
        addButton(layout.left() + (actionWidth + gap) * 2,
                layout.footerY(),
                Math.max(100, actionWidth), Text.text("Player overrides"),
                button -> new EffortlessPlayerGeneralSettingsListScreen(
                        getEntrance(), getEntrance().getSessionManager()
                                .getServerSessionConfigOrEmpty().playerConfigs(),
                        value -> getEntrance().getSessionManager()
                                .updatePlayerConfig(value)
                ).attach());
        addButton(layout.right() - actionWidth, layout.footerY(), actionWidth,
                Text.text("Save"), button -> {
                    consumer.accept(config);
                    detach();
                });
    }

    private WorkbenchScreenLayout layout() {
        return WorkbenchScreenLayout.create(
                getScreenWidth(), getScreenHeight()
        );
    }

    private void addPermissions(ProceduralSettingOptionsList options) {
        options.addSection(Text.text("BUILD PERMISSIONS"));
        options.addSwitchEntry(Text.translate(
                        "effortless.general_settings.allow_use_mod"),
                Text.empty(), config.allowUseMod(),
                value -> config = config.withAllowUseMod(value));
        options.addSwitchEntry(Text.translate(
                        "effortless.general_settings.allow_break_blocks"),
                Text.empty(), config.allowBreakBlocks(),
                value -> config = config.withAllowBreakBlocks(value));
        options.addSwitchEntry(Text.translate(
                        "effortless.general_settings.allow_place_blocks"),
                Text.empty(), config.allowPlaceBlocks(),
                value -> config = config.withAllowPlaceBlocks(value));
        options.addSwitchEntry(Text.translate(
                        "effortless.general_settings.allow_interact_blocks"),
                Text.empty(), config.allowInteractBlocks(),
                value -> config = config.withAllowInteractBlocks(value));
        options.addSwitchEntry(Text.translate(
                        "effortless.general_settings.allow_copy_paste_structures"),
                Text.empty(), config.allowCopyPasteStructures(),
                value -> config = config.withAllowCopyPasteStructures(value));
        options.addSwitchEntry(Text.translate(
                        "effortless.general_settings.use_proper_tools"),
                Text.empty(), config.useProperToolsOnly(),
                value -> config = config.withUseProperToolsOnly(value));
    }

    private void addLimits(ProceduralSettingOptionsList options) {
        options.addSection(Text.text("AUTHORITATIVE SERVER LIMITS"));
        addInteger(options, "effortless.general_settings.max_reach_distance",
                config.maxReachDistance(),
                ConstraintConfig.MAX_REACH_DISTANCE_RANGE_START,
                ConstraintConfig.MAX_REACH_DISTANCE_RANGE_END, 8,
                value -> config = config.withMaxReachDistance(value));
        addInteger(options,
                "effortless.general_settings.max_block_break_volume",
                config.maxBlockBreakVolume(),
                ConstraintConfig.MAX_BLOCK_BREAK_VOLUME_RANGE_START,
                ConstraintConfig.MAX_BLOCK_BREAK_VOLUME_RANGE_END, 100,
                value -> config = config.withMaxBlockBreakVolume(value));
        addInteger(options,
                "effortless.general_settings.max_block_place_volume",
                config.maxBlockPlaceVolume(),
                ConstraintConfig.MAX_BLOCK_PLACE_VOLUME_RANGE_START,
                ConstraintConfig.MAX_BLOCK_PLACE_VOLUME_RANGE_END, 100,
                value -> config = config.withMaxBlockPlaceVolume(value));
        addInteger(options,
                "effortless.general_settings.max_block_interact_volume",
                config.maxBlockInteractVolume(),
                ConstraintConfig.MAX_BLOCK_INTERACT_VOLUME_RANGE_START,
                ConstraintConfig.MAX_BLOCK_INTERACT_VOLUME_RANGE_END, 100,
                value -> config = config.withMaxBlockInteractVolume(value));
        addInteger(options,
                "effortless.general_settings.max_structure_copy_paste_volume",
                config.maxStructureCopyPasteVolume(),
                ConstraintConfig.MAX_STRUCTURE_COPY_PASTE_VOLUME_RANGE_START,
                ConstraintConfig.MAX_STRUCTURE_COPY_PASTE_VOLUME_RANGE_END,
                100, value -> config =
                        config.withMaxStructureCopyPasteVolume(value));
    }

    private void addItems(ProceduralSettingOptionsList options) {
        options.addSection(Text.text("ITEM POLICY"));
        options.addTab(
                Text.translate("effortless.general_settings.whitelisted_items"),
                Text.empty(), config.whitelistedItems(),
                value -> config = config.withWhitelistedItems(value),
                (entry, value) -> {
                    entry.getButton().setMessage(Text.translate(
                            "effortless.general_settings.items", value.size()
                    ));
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessItemsScreen(
                                    getEntrance(),
                                    Text.translate("effortless.general_settings.whitelisted_items"),
                                    value.stream().map(Item::fromIdOptional)
                                            .filter(Optional::isPresent)
                                            .map(Optional::get).toList(),
                                    changed -> entry.setItem(changed.stream()
                                            .map(Item::getId).distinct().toList())
                            ).attach());
                }
        );
        options.addTab(
                Text.translate("effortless.general_settings.blacklisted_items"),
                Text.empty(), config.blacklistedItems(),
                value -> config = config.withBlacklistedItems(value),
                (entry, value) -> {
                    entry.getButton().setMessage(Text.translate(
                            "effortless.general_settings.items", value.size()
                    ));
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessItemsScreen(
                                    getEntrance(),
                                    Text.translate("effortless.general_settings.blacklisted_items"),
                                    value.stream().map(Item::fromIdOptional)
                                            .filter(Optional::isPresent)
                                            .map(Optional::get).toList(),
                                    changed -> entry.setItem(changed.stream()
                                            .map(Item::getId).distinct().toList())
                            ).attach());
                }
        );
    }

    private void addInteger(
            ProceduralSettingOptionsList options,
            String key,
            int value,
            int minimum,
            int maximum,
            int step,
            Consumer<Integer> consumer
    ) {
        options.addIntegerEntry(Text.translate(key), Text.empty(), value,
                minimum, maximum, step, consumer);
    }

    private ProceduralSettingOptionsList addOptions(
            int x, int y, int width, int height
    ) {
        var options = addWidget(new ProceduralSettingOptionsList(
                getEntrance(), x, y, width - 8, Math.max(40, height),
                false, false
        ));
        options.setAlwaysShowScrollbar(true);
        return options;
    }

    private Button addButton(
            int x, int y, int width, Text label, Consumer<Button> action
    ) {
        return addWidget(new Button(
                getEntrance(), x, y, width,
                WorkbenchScreenLayout.CONTROL_HEIGHT, label,
                button -> action.accept(button)
        ));
    }

    @Override
    public void renderWidgetBackground(
            Renderer renderer, int mouseX, int mouseY, float deltaTick
    ) {
        ProceduralTheme.renderWorkbenchBackdrop(
                renderer, getScreenWidth(), getScreenHeight()
        );
    }

    @Override
    public void renderWidget(
            Renderer renderer, int mouseX, int mouseY, float deltaTick
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
            Renderer renderer, int mouseX, int mouseY, float deltaTick
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

    private enum ServerTab {
        PERMISSIONS("Access", "Permissions", "Allowed build actions.",
                0xFFA66F6F),
        LIMITS("Limits", "Limits", "Reach and operation budgets.",
                0xFFA18450),
        ITEMS("Items", "Items", "Whitelist and blacklist policy.",
                0xFF6B959E);

        private final String label;
        private final String title;
        private final String summary;
        private final int accent;

        ServerTab(
                String label, String title, String summary, int accent
        ) {
            this.label = label;
            this.title = title;
            this.summary = summary;
            this.accent = accent;
        }
    }
}
