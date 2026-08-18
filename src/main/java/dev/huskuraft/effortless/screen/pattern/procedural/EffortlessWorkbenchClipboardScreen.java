package dev.huskuraft.effortless.screen.pattern.procedural;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.building.clipboard.Clipboard;
import dev.huskuraft.effortless.building.clipboard.Snapshot;
import dev.huskuraft.effortless.building.clipboard.SnapshotTransform;
import dev.huskuraft.effortless.networking.packets.player.PlayerSnapshotSharePacket;
import dev.huskuraft.effortless.screen.clipboard.StructureSnapshotWidget;
import dev.huskuraft.effortless.screen.common.EffortlessScreen;
import dev.huskuraft.effortless.screen.player.EffortlessOnlinePlayersScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/** Workbench-styled editor for the existing stock clipboard model. */
public final class EffortlessWorkbenchClipboardScreen
        extends EffortlessScreen {

    private static final int GAP = WorkbenchScreenLayout.GAP;
    private static final int BUTTON_HEIGHT =
            WorkbenchScreenLayout.CONTROL_HEIGHT;
    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ProceduralTooltipDelay tooltipDelay =
            new ProceduralTooltipDelay();
    private Clipboard clipboard;
    private ClipboardTab tab = ClipboardTab.CURRENT;
    private TextRuleList<Snapshot> snapshotList;
    private StructureSnapshotWidget snapshotWidget;
    private Button enableButton;
    private Button useButton;
    private Button clearButton;
    private Button saveToLibraryButton;
    private Button removeButton;
    private Button shareButton;
    private TextWidget detailsWidget;
    private final List<Button> transformButtons = new ArrayList<>();
    private Snapshot displayedSnapshot = Snapshot.EMPTY;
    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;

    public EffortlessWorkbenchClipboardScreen(Entrance entrance) {
        super(entrance, Text.text("Clipboard Workspace"));
        clipboard = currentClipboard();
        displayedSnapshot = clipboard.snapshot();
    }

    @Override
    protected EffortlessClient getEntrance() {
        return (EffortlessClient) super.getEntrance();
    }

    @Override
    public void onCreate() {
        snapshotList = null;
        snapshotWidget = null;
        enableButton = null;
        useButton = null;
        clearButton = null;
        saveToLibraryButton = null;
        removeButton = null;
        shareButton = null;
        detailsWidget = null;
        transformButtons.clear();
        var layout = layout();

        for (int index = 0; index < ClipboardTab.values().length; index++) {
            var value = ClipboardTab.values()[index];
            var bounds = layout.tab(index, ClipboardTab.values().length);
            addWidget(new WorkbenchToolTab(
                    getEntrance(),
                    bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    Text.text(value.label),
                    Text.text(value.title),
                    Text.text(value.summary),
                    value.accent,
                    () -> tab == value,
                    () -> {
                        tab = value;
                        displayedSnapshot = tab == ClipboardTab.CURRENT
                                ? clipboard.snapshot() : Snapshot.EMPTY;
                        recreate();
                    }
            ));
        }

        int bodyTop = layout.bodyTop();
        int bodyHeight = layout.bodyHeight();
        var panes = ClipboardPaneLayout.create(
                layout, tab != ClipboardTab.CURRENT
        );
        previewX = panes.previewX();
        previewY = bodyTop;
        previewWidth = panes.previewWidth();
        previewHeight = bodyHeight;

        if (tab != ClipboardTab.CURRENT) {
            snapshotList = addWidget(new TextRuleList<>(
                    getEntrance(), panes.listX(), bodyTop,
                    panes.listWidth() - 8, bodyHeight,
                    EffortlessWorkbenchClipboardScreen::snapshotName,
                    EffortlessWorkbenchClipboardScreen::snapshotDetails
            ));
            snapshotList.setAlwaysShowScrollbar(true);
            var snapshots = tab == ClipboardTab.HISTORY
                    ? clipboardConfig().history()
                    : clipboardConfig().collections();
            snapshotList.reset(snapshots);
            if (panes.compact() && snapshots.isEmpty()) {
                addWidget(new TextWidget(
                        getEntrance(),
                        panes.workspaceX() + panes.workspaceWidth() / 2,
                        bodyTop + bodyHeight / 2 - 6,
                        Text.text("No saved snapshots")
                                .withStyle(ChatFormatting.GRAY),
                        TextWidget.Gravity.CENTER
                ));
            }
        }

        if (panes.previewVisible()) {
            snapshotWidget = addWidget(new StructureSnapshotWidget(
                    getEntrance(), previewX, bodyTop,
                    previewWidth, bodyHeight, displayedSnapshot
            ));
            snapshotWidget.setBackgroundColor(0xC00A0D10);
            addWidget(new TextWidget(
                    getEntrance(), previewX + 8, bodyTop + 8,
                    Text.text("FIXED-LIGHT SNAPSHOT")
                            .withStyle(ChatFormatting.GRAY)
            ));
        } else if (tab == ClipboardTab.CURRENT) {
            addCompactTransformControls(
                    panes.workspaceX(), bodyTop, panes.workspaceWidth()
            );
        }

        addActions(
                panes.actionsX(), bodyTop, panes.actionsWidth(),
                panes.compact()
        );
        addFooter(layout);
    }

    private void addActions(int x, int y, int width, boolean compact) {
        addWidget(new TextWidget(
                getEntrance(), x + GAP, y + 4,
                Text.text(tab.title.toUpperCase(java.util.Locale.ROOT))
                        .withStyle(ChatFormatting.GOLD)
        ));
        y += 20;
        detailsWidget = addWidget(new TextWidget(
                getEntrance(), x + GAP, y + 2,
                Text.text(snapshotDetails(displayedSnapshot))
                        .withStyle(ChatFormatting.GRAY)
        ));
        y += 20;
        int innerWidth = width - GAP * 2;
        enableButton = addButton(x + GAP, y, innerWidth, Text.empty(), button -> {
            clipboard = clipboard.toggled();
            applyClipboard();
        });
        y += BUTTON_HEIGHT + GAP;

        if (tab == ClipboardTab.CURRENT) {
            int half = (innerWidth - GAP) / 2;
            clearButton = addButton(
                    x + GAP, y, half, Text.text("Clear"), button -> {
                        clipboard = clipboard.withSnapshot(Snapshot.EMPTY);
                        displayedSnapshot = Snapshot.EMPTY;
                        applyClipboard();
                    }
            );
            shareButton = addButton(
                    x + GAP + half + GAP, y, innerWidth - half - GAP,
                    Text.text("Share"), button -> shareSnapshot()
            );
            y += BUTTON_HEIGHT + GAP;
            saveToLibraryButton = addButton(
                    x + GAP, y, innerWidth, Text.text("Save to library"),
                    button -> saveDisplayedToLibrary()
            );
            if (!compact) {
                y += BUTTON_HEIGHT + GAP * 2;
                addTransformControls(x, y, innerWidth);
            }
            return;
        }

        useButton = addButton(
                x + GAP, y, innerWidth, Text.text("Use selected"),
                button -> useDisplayedSnapshot()
        );
        y += BUTTON_HEIGHT + GAP;
        if (tab == ClipboardTab.HISTORY) {
            saveToLibraryButton = addButton(
                    x + GAP, y, innerWidth, Text.text("Save to library"),
                    button -> saveDisplayedToLibrary()
            );
        } else {
            removeButton = addButton(
                    x + GAP, y, innerWidth,
                    Text.text("Remove from library"),
                    button -> removeDisplayedFromLibrary()
            );
        }
    }

    private void addCompactTransformControls(int x, int y, int width) {
        int innerX = x + GAP;
        int innerWidth = width - GAP * 2;
        addWidget(new TextWidget(
                getEntrance(), innerX, y + 3,
                Text.text("ROTATE").withStyle(ChatFormatting.GRAY)
        ));
        addTransformRow(
                innerX, y + 16, innerWidth,
                List.of("X", "Y", "Z"),
                List.of(
                        SnapshotTransform.ROTATE_X,
                        SnapshotTransform.ROTATE_Y,
                        SnapshotTransform.ROTATE_Z
                ),
                3
        );
        addWidget(new TextWidget(
                getEntrance(), innerX, y + 43,
                Text.text("MIRROR").withStyle(ChatFormatting.GRAY)
        ));
        addTransformRow(
                innerX, y + 56, innerWidth,
                List.of("X", "Y", "Z"),
                List.of(
                        SnapshotTransform.MIRROR_X,
                        SnapshotTransform.MIRROR_Y,
                        SnapshotTransform.MIRROR_Z
                ),
                3
        );
        addWidget(new TextWidget(
                getEntrance(), innerX, y + 83,
                Text.text("OFFSET").withStyle(ChatFormatting.GRAY)
        ));
        addTransformRow(
                innerX, y + 96, innerWidth,
                List.of("−X", "+X", "−Y", "+Y", "−Z", "+Z"),
                List.of(
                        SnapshotTransform.DECREASE_X,
                        SnapshotTransform.INCREASE_X,
                        SnapshotTransform.DECREASE_Y,
                        SnapshotTransform.INCREASE_Y,
                        SnapshotTransform.DECREASE_Z,
                        SnapshotTransform.INCREASE_Z
                ),
                6
        );
    }

    private void addTransformControls(int x, int y, int innerWidth) {

        addWidget(new TextWidget(
                getEntrance(), x + GAP, y + 3,
                Text.text("ROTATE").withStyle(ChatFormatting.GRAY)
        ));
        y += 16;
        y = addTransformRow(
                x + GAP, y, innerWidth,
                List.of("X", "Y", "Z"),
                List.of(
                        SnapshotTransform.ROTATE_X,
                        SnapshotTransform.ROTATE_Y,
                        SnapshotTransform.ROTATE_Z
                )
        );
        y += GAP;
        addWidget(new TextWidget(
                getEntrance(), x + GAP, y + 3,
                Text.text("MIRROR").withStyle(ChatFormatting.GRAY)
        ));
        y += 16;
        y = addTransformRow(
                x + GAP, y, innerWidth,
                List.of("X", "Y", "Z"),
                List.of(
                        SnapshotTransform.MIRROR_X,
                        SnapshotTransform.MIRROR_Y,
                        SnapshotTransform.MIRROR_Z
                )
        );
        y += GAP;
        addWidget(new TextWidget(
                getEntrance(), x + GAP, y + 3,
                Text.text("OFFSET").withStyle(ChatFormatting.GRAY)
        ));
        y += 16;
        addTransformRow(
                x + GAP, y, innerWidth,
                List.of("−X", "+X", "−Y", "+Y", "−Z", "+Z"),
                List.of(
                        SnapshotTransform.DECREASE_X,
                        SnapshotTransform.INCREASE_X,
                        SnapshotTransform.DECREASE_Y,
                        SnapshotTransform.INCREASE_Y,
                        SnapshotTransform.DECREASE_Z,
                        SnapshotTransform.INCREASE_Z
                )
        );
    }

    private int addTransformRow(
            int x,
            int y,
            int width,
            List<String> labels,
            List<SnapshotTransform> transforms
    ) {
        return addTransformRow(
                x, y, width, labels, transforms,
                labels.size() <= 3 ? labels.size() : 3
        );
    }

    private int addTransformRow(
            int x,
            int y,
            int width,
            List<String> labels,
            List<SnapshotTransform> transforms,
            int columns
    ) {
        int rows = (labels.size() + columns - 1) / columns;
        int cellWidth = (width - GAP * (columns - 1)) / columns;
        for (int index = 0; index < labels.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            var transform = transforms.get(index);
            transformButtons.add(addButton(
                    x + column * (cellWidth + GAP),
                    y + row * (BUTTON_HEIGHT + GAP),
                    cellWidth,
                    Text.text(labels.get(index)),
                    button -> transform(transform)
            ));
        }
        return y + rows * BUTTON_HEIGHT + Math.max(0, rows - 1) * GAP;
    }

    private void addFooter(WorkbenchScreenLayout layout) {
        int actionWidth = Math.min(140, Math.max(96, layout.width() / 7));
        addButton(
                layout.left(), layout.footerY(), actionWidth,
                Text.text("Back"), button -> detach()
        );
        addButton(
                layout.right() - actionWidth, layout.footerY(), actionWidth,
                Text.text("Settings"),
                button -> new EffortlessWorkbenchSettingsScreen(
                        getEntrance()
                ).attach()
        );
    }

    private WorkbenchScreenLayout layout() {
        return WorkbenchScreenLayout.create(
                getScreenWidth(), getScreenHeight()
        );
    }

    @Override
    public void onReload() {
        if (snapshotList != null && snapshotList.hasSelected()) {
            var selected = snapshotList.getSelected().getItem();
            if (selected != displayedSnapshot) {
                displayedSnapshot = selected;
                if (snapshotWidget != null) {
                    snapshotWidget.setSnapshot(selected);
                }
            }
        } else if (tab == ClipboardTab.CURRENT) {
            displayedSnapshot = clipboard.snapshot();
            if (snapshotWidget != null) {
                snapshotWidget.setSnapshot(displayedSnapshot);
            }
        }
        boolean hasClipboard = !clipboard.isEmpty();
        boolean hasDisplayed = !displayedSnapshot.isEmpty();
        enableButton.setMessage(Text.text(
                clipboard.enabled() ? "Disable clipboard" : "Enable clipboard"
        ));
        if (detailsWidget != null) {
            detailsWidget.setMessage(
                    Text.text(snapshotDetails(displayedSnapshot))
                            .withStyle(ChatFormatting.GRAY)
            );
        }
        if (clearButton != null) {
            clearButton.setActive(hasClipboard);
        }
        if (shareButton != null) {
            shareButton.setActive(hasClipboard);
        }
        if (useButton != null) {
            useButton.setActive(hasDisplayed);
        }
        if (saveToLibraryButton != null) {
            saveToLibraryButton.setActive(
                    hasDisplayed
                            && !clipboardConfig().collections()
                                    .contains(displayedSnapshot)
            );
        }
        if (removeButton != null) {
            removeButton.setActive(hasDisplayed);
        }
        transformButtons.forEach(button -> button.setActive(
                tab == ClipboardTab.CURRENT && hasClipboard
        ));
    }

    private void transform(SnapshotTransform transform) {
        if (clipboard.isEmpty()) {
            return;
        }
        clipboard = clipboard.withSnapshot(
                clipboard.snapshot().update(transform)
        );
        displayedSnapshot = clipboard.snapshot();
        applyClipboard();
        if (snapshotWidget != null) {
            snapshotWidget.setSnapshot(displayedSnapshot);
        }
    }

    private void useDisplayedSnapshot() {
        if (displayedSnapshot.isEmpty()) {
            return;
        }
        clipboard = clipboard.withSnapshot(displayedSnapshot);
        applyClipboard();
        tab = ClipboardTab.CURRENT;
        recreate();
    }

    private void saveDisplayedToLibrary() {
        if (displayedSnapshot.isEmpty()) {
            return;
        }
        getEntrance().getConfigStorage().update(config ->
                config.withClipboardConfig(
                        config.clipboardConfig().appendCollection(
                                displayedSnapshot
                        )
                )
        );
        recreate();
    }

    private void removeDisplayedFromLibrary() {
        var values = new ArrayList<>(clipboardConfig().collections());
        if (!values.remove(displayedSnapshot)) {
            return;
        }
        getEntrance().getConfigStorage().update(config ->
                config.withClipboardConfig(
                        config.clipboardConfig().withCollections(values)
                )
        );
        displayedSnapshot = Snapshot.EMPTY;
        recreate();
    }

    private void shareSnapshot() {
        if (clipboard.isEmpty()) {
            return;
        }
        new EffortlessOnlinePlayersScreen(getEntrance(), playerInfo -> {
            var player = getEntrance().getClient().getPlayer();
            getEntrance().getChannel().sendPacket(
                    new PlayerSnapshotSharePacket(
                            player.getId(),
                            playerInfo.getId(),
                            clipboard.snapshot()
                    )
            );
        }).attach();
    }

    private void applyClipboard() {
        getEntrance().getStructureBuilder().setClipboard(
                getEntrance().getClient().getPlayer(), clipboard
        );
    }

    private Clipboard currentClipboard() {
        var player = getEntrance().getClient().getPlayer();
        return getEntrance().getStructureBuilder()
                .getContext(player).clipboard();
    }

    private dev.huskuraft.effortless.building.config.ClipboardConfig
            clipboardConfig() {
        return getEntrance().getConfigStorage().get().clipboardConfig();
    }

    private static String snapshotName(Snapshot snapshot) {
        if (snapshot.name() != null && !snapshot.name().isBlank()) {
            return snapshot.name();
        }
        return snapshot.blockData().size() + " block snapshot";
    }

    private static String snapshotDetails(Snapshot snapshot) {
        if (snapshot.isEmpty()) {
            return "Empty";
        }
        var box = snapshot.box();
        String size = box.x() + " × " + box.y() + " × " + box.z()
                + " · " + snapshot.blockData().size() + " blocks";
        if (snapshot.createdTimestamp() <= 0) {
            return size;
        }
        return size + " · " + TIME.format(
                Instant.ofEpochMilli(snapshot.createdTimestamp())
        );
    }

    private Button addButton(
            int x,
            int y,
            int width,
            Text label,
            Button.OnPress press
    ) {
        return addWidget(Button.builder(getEntrance(), label, press)
                .setBounds(x, y, Math.max(1, width), BUTTON_HEIGHT)
                .build());
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
        if (displayedSnapshot.isEmpty() && snapshotWidget != null) {
            int centerX = previewX + previewWidth / 2;
            int centerY = previewY + previewHeight / 2;
            renderer.renderTextFromCenter(
                    getTypeface(), Text.text("No snapshot selected"),
                    centerX, centerY - 8, ProceduralTheme.TEXT, true
            );
            renderer.renderTextFromCenter(
                    getTypeface(),
                    Text.text(tab == ClipboardTab.CURRENT
                            ? "Copy a structure to begin"
                            : "Choose an entry from the list"),
                    centerX, centerY + 6, ProceduralTheme.MUTED, true
            );
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

    private enum ClipboardTab {
        CURRENT("Current", "Current clipboard", "Preview and transform.",
                0xFF6B959E),
        HISTORY("History", "Copy history", "Reuse a recent snapshot.",
                0xFFA18450),
        LIBRARY("Library", "Saved snapshots", "Reusable local snapshots.",
                0xFF738E70);

        private final String label;
        private final String title;
        private final String summary;
        private final int accent;

        ClipboardTab(
                String label, String title, String summary, int accent
        ) {
            this.label = label;
            this.title = title;
            this.summary = summary;
            this.accent = accent;
        }
    }
}
