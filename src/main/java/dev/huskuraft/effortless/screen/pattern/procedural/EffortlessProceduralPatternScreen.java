package dev.huskuraft.effortless.screen.pattern.procedural;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.DoubleFunction;
import java.util.function.ToDoubleFunction;

import dev.huskuraft.effortless.Effortless;
import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;
import dev.huskuraft.effortless.client.pattern.procedural.CoordinateSpace;
import dev.huskuraft.effortless.client.pattern.procedural.GradientCurve;
import dev.huskuraft.effortless.client.pattern.procedural.GradientDistributionMode;
import dev.huskuraft.effortless.client.pattern.procedural.MaxRunLengthConstraint;
import dev.huskuraft.effortless.client.pattern.procedural.NeighborTopology;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralRuleSet;
import dev.huskuraft.effortless.client.pattern.procedural.SeedMode;
import dev.huskuraft.effortless.client.pattern.procedural.SpatialField;
import dev.huskuraft.effortless.client.pattern.procedural.config.PatternMaterialSource;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralAdvancedConfig;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralBlockEntry;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralNoiseConfig;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.building.pattern.Pattern;
import dev.huskuraft.effortless.building.pattern.Transformer;
import dev.huskuraft.effortless.building.pattern.Transformers;
import dev.huskuraft.effortless.building.pattern.array.ArrayTransformer;
import dev.huskuraft.effortless.building.pattern.mirror.MirrorTransformer;
import dev.huskuraft.effortless.building.pattern.raidal.RadialTransformer;
import dev.huskuraft.effortless.building.structure.BuildMode;
import dev.huskuraft.effortless.building.structure.BuildFeature;
import dev.huskuraft.effortless.building.structure.BuildFeatures;
import dev.huskuraft.effortless.building.structure.builder.Structure;
import dev.huskuraft.effortless.screen.common.EffortlessScreen;
import dev.huskuraft.effortless.screen.item.EffortlessItemPickerScreen;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.core.Axis;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.input.Keys;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.math.Vector3d;
import dev.huskuraft.universal.api.math.Vector3i;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/**
 * Full-screen client-only pattern workbench.
 *
 * <p>The left pane owns preset selection, the center pane owns the ordered
 * visual palette, and the right inspector owns distribution/rule settings.
 * All panes edit one {@link ProceduralWorkbenchSession}; only the final Save
 * writes the local TOML configuration.</p>
 */
public final class EffortlessProceduralPatternScreen extends EffortlessScreen {

    private static final int GAP = 6;
    private static final int HEADER_HEIGHT = 26;
    private static final int FOOTER_HEIGHT = 26;
    private static final int BUTTON_HEIGHT = 20;
    private static final DoubleFunction<String> DECIMAL = value ->
            String.format(Locale.ROOT, "%.2f", value);

    private final ProceduralWorkbenchSession session;
    private final ProceduralTooltipDelay tooltipDelay =
            new ProceduralTooltipDelay();
    private InspectorTab inspectorTab = InspectorTab.MATERIALS;
    private CompactView compactView = CompactView.PALETTE;
    private BuildMode previewMode = BuildMode.WALL;
    private PreviewOrientation previewOrientation =
            PreviewOrientation.defaultFor(previewMode);
    private boolean previewSubtypesExpanded;
    private final EnumMap<
            BuildMode,
            EnumMap<BuildFeatures, BuildFeature>
    > previewFeatureOverrides = new EnumMap<>(BuildMode.class);
    private String presetSearch = "";
    private int selectedBlockIndex;
    private int selectedTransformerIndex;

    private ProceduralPresetList presetList;
    private TextRuleList<Transformer> transformerList;
    private Button enableButton;
    private Button useButton;
    private Button deletePresetButton;
    private Button movePresetUpButton;
    private Button movePresetDownButton;
    private Button deleteBlockButton;
    private Button moveBlockLeftButton;
    private Button moveBlockRightButton;
    private Button fallbackButton;
    private Button materialSourceButton;
    private Button deleteTransformerButton;
    private Button moveTransformerUpButton;
    private Button moveTransformerDownButton;
    private Button undoButton;
    private Button redoButton;
    private Button saveButton;
    private Button cancelButton;
    private TextWidget draftStatusWidget;
    private ReliableEditBox searchField;
    private ReliableEditBox nameField;
    private ReliableEditBox seedField;
    private ReliableEditBox noiseSaltField;
    private SpatialFieldEditorWidget gradientFieldEditor;
    private ProceduralPreviewWidget previewWidget;
    private boolean discardArmed;
    private boolean allowDetach;

    private boolean compactLayout;
    private int margin;
    private int headerHeight;
    private int contentTop;
    private int contentHeight;
    private int leftX;
    private int leftWidth;
    private int centerX;
    private int centerWidth;
    private int rightX;
    private int rightWidth;

    public EffortlessProceduralPatternScreen(Entrance entrance) {
        super(entrance, Text.text("Pattern Workbench"));
        this.session = new ProceduralWorkbenchSession(
                getEntrance().getProceduralConfigStorage().get()
        );
        var player = getEntrance().getClient().getPlayer();
        var stockPattern = getEntrance().getStructureBuilder()
                .getContext(player)
                .pattern();
        if (session.selectedPreset().stockTransformers().isEmpty()) {
            session.importStockGeometry(stockPattern);
        }
    }

    @Override
    protected EffortlessClient getEntrance() {
        return (EffortlessClient) super.getEntrance();
    }

    @Override
    public void onCreate() {
        searchField = null;
        nameField = null;
        seedField = null;
        noiseSaltField = null;
        gradientFieldEditor = null;
        previewWidget = null;
        layout();
        addHeader();
        addPresetPane();
        if (compactLayout) {
            addCompactMainPane();
        } else {
            addPalettePane();
            addInspectorPane();
        }
        addFooter();
    }

    @Override
    public void onReload() {
        var selected = session.selectedPreset();
        boolean customPalette =
                selected.materialSource() == PatternMaterialSource.CUSTOM_PALETTE;
        int presetIndex = session.selectedIndex();
        useButton.setActive(
                !selected.id().equals(session.library().activePresetId())
        );
        deletePresetButton.setActive(session.library().presets().size() > 1);
        movePresetUpButton.setActive(presetIndex > 0);
        movePresetDownButton.setActive(
                presetIndex >= 0
                        && presetIndex < session.library().presets().size() - 1
        );
        selectedBlockIndex = clampBlockIndex(selectedBlockIndex);
        if (deleteBlockButton != null) {
            deleteBlockButton.setActive(
                    customPalette && selected.blocks().size() > 1
            );
        }
        if (moveBlockLeftButton != null) {
            moveBlockLeftButton.setActive(
                    customPalette && selectedBlockIndex > 0
            );
        }
        if (moveBlockRightButton != null) {
            moveBlockRightButton.setActive(
                    customPalette
                            && selectedBlockIndex < selected.blocks().size() - 1
            );
        }
        if (fallbackButton != null) {
            fallbackButton.setMessage(
                    Text.text(customPalette
                            ? "Fallback: "
                                    + shortId(selected.fallbackItemId())
                            : "Fallback: first available live block")
            );
            fallbackButton.setActive(customPalette);
        }
        if (materialSourceButton != null) {
            materialSourceButton.setMessage(materialSourceMessage());
        }
        if (transformerList != null && transformerList.hasSelected()) {
            selectedTransformerIndex = transformerList.indexOfSelected();
        }
        int transformCount = selected.stockTransformers().size();
        selectedTransformerIndex = Math.max(
                0,
                Math.min(
                        selectedTransformerIndex,
                        Math.max(0, transformCount - 1)
                )
        );
        if (deleteTransformerButton != null) {
            deleteTransformerButton.setActive(transformCount > 0);
        }
        if (moveTransformerUpButton != null) {
            moveTransformerUpButton.setActive(
                    transformCount > 0 && selectedTransformerIndex > 0
            );
        }
        if (moveTransformerDownButton != null) {
            moveTransformerDownButton.setActive(
                    transformCount > 0
                            && selectedTransformerIndex < transformCount - 1
            );
        }
        if (undoButton != null) {
            undoButton.setActive(session.canUndo());
        }
        if (redoButton != null) {
            redoButton.setActive(session.canRedo());
        }
        if (saveButton != null) {
            saveButton.setActive(session.isDirty());
        }
        if (draftStatusWidget != null) {
            draftStatusWidget.setMessage(
                    Text.text(session.isDirty()
                                    ? "Unsaved changes"
                                    : "Saved")
                            .withStyle(session.isDirty()
                                    ? ChatFormatting.GOLD
                                    : ChatFormatting.GREEN)
            );
        }
        enableButton.setMessage(enabledMessage());
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        // A button can recreate or close the screen during super's dispatch.
        // Capture visible drafts first so that click uses what the player sees.
        commitVisibleTextInputs();
        // Universal's screen dispatcher can hand the field canvas click to an
        // overlapping tooltip/preview widget before establishing drag focus.
        // Capture it explicitly so direct center/scale manipulation is stable.
        if (gradientFieldEditor != null
                && button == 0
                && gradientFieldEditor.isActive()
                && gradientFieldEditor.containsCanvasPoint(mouseX, mouseY)) {
            gradientFieldEditor.beginEdit(mouseX, mouseY);
            return true;
        }
        if (previewWidget != null
                && previewWidget.containsInteractionPoint(mouseX, mouseY)
                && previewWidget.onMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        UUID before = session.selectedPresetId();
        boolean consumed = super.onMouseClicked(mouseX, mouseY, button);
        if (presetList != null && presetList.hasSelected()) {
            UUID after = presetList.getSelected().getItem().id();
            if (!after.equals(before)) {
                session.select(after);
                selectedBlockIndex = 0;
                recreate();
                return true;
            }
        }
        if (transformerList != null && transformerList.hasSelected()) {
            int after = transformerList.indexOfSelected();
            if (after != selectedTransformerIndex) {
                selectedTransformerIndex = after;
                recreate();
                return true;
            }
        }
        return consumed;
    }

    @Override
    public boolean onMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY
    ) {
        if (gradientFieldEditor != null
                && gradientFieldEditor.isDragging()) {
            return gradientFieldEditor.onMouseDragged(
                    mouseX,
                    mouseY,
                    button,
                    deltaX,
                    deltaY
            );
        }
        if (previewWidget != null && previewWidget.isCameraDragging()) {
            return previewWidget.onMouseDragged(
                    mouseX,
                    mouseY,
                    button,
                    deltaX,
                    deltaY
            );
        }
        return super.onMouseDragged(
                mouseX,
                mouseY,
                button,
                deltaX,
                deltaY
        );
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (gradientFieldEditor != null
                && gradientFieldEditor.isDragging()) {
            return gradientFieldEditor.onMouseReleased(
                    mouseX,
                    mouseY,
                    button
            );
        }
        if (previewWidget != null && previewWidget.isCameraDragging()) {
            return previewWidget.onMouseReleased(mouseX, mouseY, button);
        }
        return super.onMouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean onMouseScrolled(
            double mouseX,
            double mouseY,
            double amountX,
            double amountY
    ) {
        if (previewWidget != null
                && previewWidget.onMouseScrolled(
                        mouseX,
                        mouseY,
                        amountX,
                        amountY
                )) {
            return true;
        }
        return super.onMouseScrolled(mouseX, mouseY, amountX, amountY);
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        commitVisibleTextInputs();
        boolean control = Keys.KEY_LEFT_CONTROL.isDown()
                || Keys.KEY_RIGHT_CONTROL.isDown();
        if (control && keyCode == Keys.KEY_Z.getValue()) {
            boolean shift = Keys.KEY_LEFT_SHIFT.isDown()
                    || Keys.KEY_RIGHT_SHIFT.isDown();
            if (shift) {
                session.redo();
            } else {
                session.undo();
            }
            recreate();
            return true;
        }
        if (control && keyCode == Keys.KEY_Y.getValue()) {
            session.redo();
            recreate();
            return true;
        }
        if (control && keyCode == Keys.KEY_S.getValue()) {
            saveLibrary();
            return true;
        }
        return super.onKeyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void detach() {
        if (allowDetach || !session.isDirty()) {
            super.detach();
            return;
        }
        if (discardArmed) {
            allowDetach = true;
            super.detach();
            return;
        }
        discardArmed = true;
        if (cancelButton != null) {
            cancelButton.setMessage(Text.text("Discard changes?"));
        }
        message(
                "Pattern workbench has unsaved changes; close again to discard",
                ChatFormatting.GOLD
        );
    }

    @Override
    public void renderWidgetBackground(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        renderer.renderGradientRect(
                0,
                0,
                getScreenWidth(),
                getScreenHeight(),
                0x68080A0D,
                0x88080A0D
        );
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        renderWorkbenchPanel(
                renderer,
                leftX,
                contentTop,
                leftWidth,
                contentHeight
        );
        renderWorkbenchPanel(
                renderer,
                centerX,
                contentTop,
                centerWidth,
                contentHeight
        );
        if (!compactLayout) {
            renderWorkbenchPanel(
                    renderer,
                    rightX,
                    contentTop,
                    rightWidth,
                    contentHeight
            );
        }
        renderer.renderRect(
                margin,
                margin,
                getScreenWidth() - margin,
                margin + headerHeight,
                0xC816191D
        );
        renderer.renderRect(
                margin,
                getScreenHeight() - margin - FOOTER_HEIGHT,
                getScreenWidth() - margin,
                getScreenHeight() - margin,
                0xC816191D
        );
        Runnable restoreLabels =
                ProceduralTheme.suppressDirectButtonLabels(children());
        try {
            super.renderWidget(renderer, mouseX, mouseY, deltaTick);
        } finally {
            restoreLabels.run();
        }
        for (var child : children()) {
            if (child instanceof Button button) {
                ProceduralTheme.renderButton(
                        renderer,
                        getTypeface(),
                        button
                );
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
                        getTypeface(),
                        button.getMessage()
                ));
            }
        }
        if (tooltipDelay.isReady(mouseX, mouseY)) {
            super.renderWidgetOverlay(renderer, mouseX, mouseY, deltaTick);
        }
    }

    private void layout() {
        int width = getScreenWidth();
        int height = getScreenHeight();
        compactLayout = width < 700 || height < 400;
        margin = compactLayout ? 4 : clamp(width / 80, 6, 12);
        headerHeight = compactLayout ? 20 : HEADER_HEIGHT;
        contentTop = margin + headerHeight + GAP;
        contentHeight = height - margin * 2 - headerHeight
                - FOOTER_HEIGHT - GAP * 2;

        int available = width - margin * 2 - GAP * 2;
        if (compactLayout) {
            leftWidth = clamp(available / 4, 88, 110);
            centerWidth = available - leftWidth;
            rightWidth = 0;
            leftX = margin;
            centerX = leftX + leftWidth + GAP;
            rightX = centerX;
            return;
        }

        leftWidth = clamp((int) (available * 0.20), 108, 184);
        rightWidth = clamp((int) (available * 0.31), 176, 286);
        centerWidth = available - leftWidth - rightWidth;
        if (centerWidth < 164) {
            int shortage = 164 - centerWidth;
            int leftReduction = Math.min(shortage / 2, leftWidth - 96);
            leftWidth -= leftReduction;
            shortage -= leftReduction;
            rightWidth -= Math.min(shortage, rightWidth - 156);
            centerWidth = available - leftWidth - rightWidth;
        }

        leftX = margin;
        centerX = leftX + leftWidth + GAP;
        rightX = centerX + centerWidth + GAP;
    }

    private void addHeader() {
        int headerY = margin + (compactLayout ? 6 : 9);
        addWidget(new TextWidget(
                getEntrance(),
                margin + (compactLayout ? 6 : 10),
                headerY,
                Text.text("EFFORTLESS")
                        .withStyle(ChatFormatting.GOLD)
        ));
        addWidget(new TextWidget(
                getEntrance(),
                getScreenWidth() / 2,
                headerY,
                compactLayout ? Text.text("WORKBENCH") : getScreenTitle(),
                TextWidget.Gravity.CENTER
        ));
        addWidget(new TextWidget(
                getEntrance(),
                getScreenWidth() - margin - (compactLayout ? 6 : 10),
                headerY,
                Text.text(compactLayout
                                ? "STOCK"
                                : "PATTERN RECIPE / STOCK SERVER OUTPUT")
                        .withStyle(ChatFormatting.GRAY),
                TextWidget.Gravity.END
        ));
    }

    private void addPresetPane() {
        int innerX = leftX + GAP;
        int innerWidth = leftWidth - GAP * 2;
        enableButton = addButton(
                innerX,
                contentTop + GAP,
                innerWidth,
                enabledMessage(),
                button -> session.setEnabled(!session.library().enabled())
        );

        int searchY = contentTop + GAP + BUTTON_HEIGHT + GAP;
        searchField = addWidget(new ReliableEditBox(
                getEntrance(),
                innerX,
                searchY,
                innerWidth,
                BUTTON_HEIGHT,
                Text.text("Search patterns")
        ));
        searchField.setHint(
                Text.text("Search...").withStyle(ChatFormatting.GRAY)
        );
        searchField.setMaxLength(80);
        searchField.setValue(presetSearch);
        searchField.setChangeListener(value -> {
            presetSearch = value;
            resetPresetList();
        });

        int actionsHeight = BUTTON_HEIGHT * 3 + GAP * 2;
        int listY = searchY + BUTTON_HEIGHT + GAP;
        int listHeight = Math.max(
                52,
                contentHeight - (listY - contentTop) - actionsHeight - GAP * 2
        );
        presetList = addWidget(new ProceduralPresetList(
                getEntrance(),
                innerX,
                listY,
                innerWidth - 8,
                listHeight,
                session.library().activePresetId()
        ));
        presetList.setAlwaysShowScrollbar(true);
        resetPresetList();

        int buttonY = listY + listHeight + GAP;
        int half = (innerWidth - GAP) / 2;
        useButton = addButton(
                innerX,
                buttonY,
                half,
                Text.text("Activate"),
                button -> {
                    session.useSelected();
                    presetList.setActivePresetId(
                            session.library().activePresetId()
                    );
                    presetList.selectById(session.selectedPresetId());
                }
        );
        addButton(
                innerX + half + GAP,
                buttonY,
                innerWidth - half - GAP,
                Text.text("New"),
                button -> {
                    session.addPreset();
                    presetSearch = "";
                    selectedBlockIndex = 0;
                    recreate();
                }
        );
        addButton(
                innerX,
                buttonY + BUTTON_HEIGHT + GAP,
                half,
                Text.text(compactLayout ? "Copy" : "Duplicate"),
                button -> {
                    session.duplicateSelected();
                    presetSearch = "";
                    selectedBlockIndex = 0;
                    recreate();
                }
        );
        deletePresetButton = addButton(
                innerX + half + GAP,
                buttonY + BUTTON_HEIGHT + GAP,
                innerWidth - half - GAP,
                Text.text(compactLayout ? "Del" : "Delete"),
                button -> {
                    session.deleteSelected();
                    selectedBlockIndex = 0;
                    recreate();
                }
        );
        movePresetUpButton = addButton(
                innerX,
                buttonY + (BUTTON_HEIGHT + GAP) * 2,
                half,
                Text.text("Up"),
                button -> {
                    session.moveSelected(-1);
                    recreate();
                }
        );
        movePresetDownButton = addButton(
                innerX + half + GAP,
                buttonY + (BUTTON_HEIGHT + GAP) * 2,
                innerWidth - half - GAP,
                Text.text("Down"),
                button -> {
                    session.moveSelected(1);
                    recreate();
                }
        );
    }

    private void addCompactMainPane() {
        int innerX = centerX + GAP;
        int innerWidth = centerWidth - GAP * 2;
        int y = contentTop + GAP;
        int tabCount = CompactView.values().length;
        int columns = innerWidth < 240
                ? 4
                : innerWidth < 420 ? 5 : tabCount;
        int rows = (tabCount + columns - 1) / columns;
        int tabWidth = Math.max(
                1,
                (innerWidth - GAP * (columns - 1)) / columns
        );
        for (int index = 0; index < tabCount; index++) {
            var view = CompactView.values()[index];
            int column = index % columns;
            int row = index / columns;
            int x = innerX + column * (tabWidth + GAP);
            int width = column == columns - 1
                    ? innerX + innerWidth - x
                    : tabWidth;
            addWidget(new WorkbenchToolTab(
                    getEntrance(),
                    x,
                    y + row * (BUTTON_HEIGHT + GAP),
                    width,
                    BUTTON_HEIGHT,
                    Text.text(view.label),
                    Text.text(view.title),
                    Text.translate(
                            "effortless.procedural.tooltip.tab."
                                    + view.key
                    ),
                    view.accent,
                    () -> compactView == view,
                    () -> {
                        compactView = view;
                        recreate();
                    }
            ));
        }
        y += rows * (BUTTON_HEIGHT + GAP);
        int availableHeight = contentTop + contentHeight - y - GAP;
        switch (compactView) {
            case PALETTE -> addCompactPalettePane(
                    innerX,
                    y,
                    innerWidth,
                    availableHeight
            );
            case PREVIEW -> addCompactPreviewPane(
                    innerX,
                    y,
                    innerWidth,
                    availableHeight
            );
            case TUNE -> addCompactTuningPane(innerX, y, innerWidth);
            case MIX -> addMixInspector(innerX, y, innerWidth);
            case GRADIENT -> addGradientInspector(innerX, y, innerWidth);
            case NOISE -> addNoiseInspector(innerX, y, innerWidth);
            case RULES -> addRulesInspector(innerX, y, innerWidth);
            case MASKS -> addMasksInspector(innerX, y, innerWidth);
            case TRANSFORMS -> addTransformsInspector(
                    innerX,
                    y,
                    innerWidth
            );
            case OUTPUT -> addOutputInspector(innerX, y, innerWidth);
        }
    }

    private void addCompactPalettePane(
            int innerX,
            int y,
            int innerWidth,
            int availableHeight
    ) {
        var draft = session.selectedTextDraft();
        int nameLabelWidth = 36;
        addWidget(new TextWidget(
                getEntrance(),
                innerX,
                y + 6,
                Text.text("Name").withStyle(ChatFormatting.GRAY)
        ));
        int sourceWidth = clamp(innerWidth / 3, 80, 108);
        nameField = addWidget(new ReliableEditBox(
                getEntrance(),
                innerX + nameLabelWidth,
                y,
                innerWidth - nameLabelWidth - sourceWidth - GAP,
                BUTTON_HEIGHT,
                Text.text("Pattern name")
        ));
        nameField.setMaxLength(80);
        nameField.setValue(draft.name());
        nameField.setChangeListener(session::setNameDraft);

        materialSourceButton = addButton(
                innerX + innerWidth - sourceWidth,
                y,
                sourceWidth,
                materialSourceMessage(),
                button -> {
                    session.setMaterialSource(nextMaterialSource());
                    recreate();
                }
        );
        y += BUTTON_HEIGHT + GAP;

        int paletteHeight = Math.max(
                48,
                availableHeight - BUTTON_HEIGHT * 3 - GAP * 3
        );
        var palette = addWidget(new ProceduralPaletteWidget(
                getEntrance(),
                innerX,
                y,
                innerWidth,
                paletteHeight,
                () -> session.selectedPreset().blocks(),
                () -> session.selectedPreset().advanced()
                        .gradientDistributionMode(),
                () -> selectedBlockIndex,
                value -> selectedBlockIndex = value,
                this::updateGradientStop
        ));
        palette.setActive(isCustomPalette());
        y += paletteHeight + GAP;

        int actionWidth = Math.max(18, (innerWidth - GAP * 5) / 6);
        var pick = addButton(
                innerX,
                y,
                actionWidth,
                Text.text("Pick"),
                button -> pickBlock(selectedBlockIndex)
        );
        pick.setActive(isCustomPalette());
        var inventory = addButton(
                innerX + actionWidth + GAP,
                y,
                actionWidth,
                Text.text("Inventory"),
                button -> pickInventoryBlock(selectedBlockIndex)
        );
        inventory.setActive(isCustomPalette());
        var add = addButton(
                innerX + (actionWidth + GAP) * 2,
                y,
                actionWidth,
                Text.text("Add"),
                button -> {
                    selectedBlockIndex = session.addBlock();
                    pickBlock(selectedBlockIndex);
                }
        );
        add.setActive(isCustomPalette());
        deleteBlockButton = addButton(
                innerX + (actionWidth + GAP) * 3,
                y,
                actionWidth,
                Text.text("Del"),
                button -> selectedBlockIndex = session.deleteBlock(
                        selectedBlockIndex
                )
        );
        moveBlockLeftButton = addButton(
                innerX + (actionWidth + GAP) * 4,
                y,
                actionWidth,
                Text.text("<"),
                button -> selectedBlockIndex = session.moveBlock(
                        selectedBlockIndex,
                        -1
                )
        );
        moveBlockRightButton = addButton(
                innerX + (actionWidth + GAP) * 5,
                y,
                innerWidth - (actionWidth + GAP) * 5,
                Text.text(">"),
                button -> selectedBlockIndex = session.moveBlock(
                        selectedBlockIndex,
                        1
                )
        );
        y += BUTTON_HEIGHT + GAP;
        fallbackButton = addButton(
                innerX,
                y,
                innerWidth,
                Text.empty(),
                button -> pickFallback()
        );
    }

    private void addCompactPreviewPane(
            int innerX,
            int y,
            int innerWidth,
            int availableHeight
    ) {
        int selectorHeight = addPreviewToolbar(innerX, y, innerWidth);
        y += selectorHeight + GAP;
        previewWidget = addWidget(new ProceduralPreviewWidget(
                getEntrance(),
                innerX,
                y,
                innerWidth,
                Math.max(48, availableHeight - selectorHeight - GAP),
                () -> session.selectedPreset(),
                () -> previewMode,
                () -> previewOrientation,
                this::previewStructure
        ));
    }

    private void addCompactTuningPane(
            int innerX,
            int y,
            int innerWidth
    ) {
        addWidget(new TextWidget(
                getEntrance(),
                innerX,
                y + 4,
                Text.text("Selected: " + shortId(selectedBlock().itemId()))
                        .withStyle(ChatFormatting.GRAY)
        ));
        y += 18;
        addBlockTuningSliders(innerX, y, innerWidth);
        y += 22 * 5;
        if (y + BUTTON_HEIGHT <= contentTop + contentHeight - GAP) {
            fallbackButton = addButton(
                    innerX,
                    y,
                    innerWidth,
                    Text.empty(),
                    button -> pickFallback()
            );
        }
    }

    private void addPalettePane() {
        int innerX = centerX + GAP;
        int innerWidth = centerWidth - GAP * 2;
        int y = contentTop + GAP;
        var draft = session.selectedTextDraft();

        int nameLabelWidth = Math.min(42, innerWidth / 4);
        addWidget(new TextWidget(
                getEntrance(),
                innerX,
                y + 6,
                Text.text("Name").withStyle(ChatFormatting.GRAY)
        ));
        nameField = addWidget(new ReliableEditBox(
                getEntrance(),
                innerX + nameLabelWidth,
                y,
                innerWidth - nameLabelWidth,
                BUTTON_HEIGHT,
                Text.text("Pattern name")
        ));
        nameField.setMaxLength(80);
        nameField.setValue(draft.name());
        nameField.setChangeListener(session::setNameDraft);
        y += BUTTON_HEIGHT + GAP;

        materialSourceButton = addButton(
                innerX,
                y,
                innerWidth,
                materialSourceMessage(),
                button -> {
                    session.setMaterialSource(nextMaterialSource());
                    recreate();
                }
        );
        y += BUTTON_HEIGHT + GAP;

        int selectorHeight = addPreviewToolbar(innerX, y, innerWidth);
        y += selectorHeight + GAP;

        int paletteHeight = clamp(contentHeight / 7, 62, 82);
        int remainingControls = paletteHeight + GAP + BUTTON_HEIGHT + GAP
                + BUTTON_HEIGHT;
        int previewHeight = Math.max(
                72,
                contentTop + contentHeight - GAP - y - remainingControls
        );
        previewWidget = addWidget(new ProceduralPreviewWidget(
                getEntrance(),
                innerX,
                y,
                innerWidth,
                previewHeight,
                () -> session.selectedPreset(),
                () -> previewMode,
                () -> previewOrientation,
                this::previewStructure
        ));
        y += previewHeight + GAP;

        var palette = addWidget(new ProceduralPaletteWidget(
                getEntrance(),
                innerX,
                y,
                innerWidth,
                paletteHeight,
                () -> session.selectedPreset().blocks(),
                () -> session.selectedPreset().advanced()
                        .gradientDistributionMode(),
                () -> selectedBlockIndex,
                value -> selectedBlockIndex = value,
                this::updateGradientStop
        ));
        palette.setActive(isCustomPalette());
        y += paletteHeight + GAP;

        int actionWidth = Math.max(22, (innerWidth - GAP * 5) / 6);
        var pick = addButton(
                innerX,
                y,
                actionWidth,
                Text.text("Pick"),
                button -> pickBlock(selectedBlockIndex)
        );
        pick.setActive(isCustomPalette());
        var inventory = addButton(
                innerX + (actionWidth + GAP),
                y,
                actionWidth,
                Text.text("Inventory"),
                button -> pickInventoryBlock(selectedBlockIndex)
        );
        inventory.setActive(isCustomPalette());
        var add = addButton(
                innerX + (actionWidth + GAP) * 2,
                y,
                actionWidth,
                Text.text("Add"),
                button -> {
                    selectedBlockIndex = session.addBlock();
                    pickBlock(selectedBlockIndex);
                }
        );
        add.setActive(isCustomPalette());
        deleteBlockButton = addButton(
                innerX + (actionWidth + GAP) * 3,
                y,
                actionWidth,
                Text.text("Remove"),
                button -> selectedBlockIndex = session.deleteBlock(
                        selectedBlockIndex
                )
        );
        moveBlockLeftButton = addButton(
                innerX + (actionWidth + GAP) * 4,
                y,
                actionWidth,
                Text.text("<"),
                button -> selectedBlockIndex = session.moveBlock(
                        selectedBlockIndex,
                        -1
                )
        );
        moveBlockRightButton = addButton(
                innerX + (actionWidth + GAP) * 5,
                y,
                innerWidth - (actionWidth + GAP) * 5,
                Text.text(">"),
                button -> selectedBlockIndex = session.moveBlock(
                        selectedBlockIndex,
                        1
                )
        );
        y += BUTTON_HEIGHT + GAP;
        fallbackButton = addButton(
                innerX,
                y,
                innerWidth,
                Text.empty(),
                button -> pickFallback()
        );
    }

    private int addPreviewToolbar(int x, int y, int width) {
        int height = ProceduralPreviewModeSelector.heightFor(
                previewSubtypesExpanded
        );
        addWidget(new ProceduralPreviewModeSelector(
                getEntrance(),
                x,
                y,
                width,
                () -> previewMode,
                value -> {
                    if (previewMode == value) {
                        previewSubtypesExpanded = !previewSubtypesExpanded;
                        recreate();
                        return;
                    }
                    previewMode = value;
                    previewOrientation = PreviewOrientation.defaultFor(value);
                    previewSubtypesExpanded = true;
                    recreate();
                },
                () -> previewOrientation,
                value -> {
                    previewOrientation = value;
                    recreate();
                },
                this::previewStructure,
                this::setPreviewFeature,
                () -> previewSubtypesExpanded
        ));
        return height;
    }

    private Structure previewStructure() {
        var configured = getEntrance().getConfigStorage()
                .getStructure(previewMode);
        var result = configured == null
                ? previewMode.getDefaultStructure()
                : configured;
        var overrides = previewFeatureOverrides.get(previewMode);
        if (overrides != null) {
            for (var type : BuildFeatures.values()) {
                var feature = overrides.get(type);
                if (feature != null) {
                    result = result.withFeature(feature);
                }
            }
        }
        return result;
    }

    private void setPreviewFeature(BuildFeature feature) {
        previewFeatureOverrides.computeIfAbsent(
                previewMode,
                ignored -> new EnumMap<>(BuildFeatures.class)
        ).put(feature.getType(), feature);
        recreate();
    }

    private void addBlockTuningSliders(int innerX, int y, int innerWidth) {
        var baseWeight = addBlockSlider(
                innerX,
                y,
                innerWidth,
                "Base weight",
                0.0,
                64.0,
                0.1,
                ProceduralBlockEntry::weight,
                (entry, value) -> new ProceduralBlockEntry(
                        entry.itemId(),
                        value,
                        entry.gradientStart(),
                        entry.gradientEnd(),
                        entry.noiseMinimum(),
                        entry.noiseMaximum(),
                        entry.gradientPosition()
                )
        );
        baseWeight.setActive(isCustomPalette());
        y += 22;
        var gradientStart = addBlockSlider(
                innerX,
                y,
                innerWidth,
                "Gradient start",
                0.0,
                64.0,
                0.1,
                ProceduralBlockEntry::gradientStart,
                (entry, value) -> new ProceduralBlockEntry(
                        entry.itemId(),
                        entry.weight(),
                        value,
                        entry.gradientEnd(),
                        entry.noiseMinimum(),
                        entry.noiseMaximum(),
                        entry.gradientPosition()
                )
        );
        y += 22;
        var gradientEnd = addBlockSlider(
                innerX,
                y,
                innerWidth,
                "Gradient end",
                0.0,
                64.0,
                0.1,
                ProceduralBlockEntry::gradientEnd,
                (entry, value) -> new ProceduralBlockEntry(
                        entry.itemId(),
                        entry.weight(),
                        entry.gradientStart(),
                        value,
                        entry.noiseMinimum(),
                        entry.noiseMaximum(),
                        entry.gradientPosition()
                )
        );
        boolean endpointWeights = session.selectedPreset().advanced()
                .gradientDistributionMode()
                == GradientDistributionMode.WEIGHTED_ENDPOINTS;
        gradientStart.setActive(isCustomPalette() && endpointWeights);
        gradientEnd.setActive(isCustomPalette() && endpointWeights);
        y += 22;
        var noiseMinimum = addBlockSlider(
                innerX,
                y,
                innerWidth,
                "Noise minimum",
                0.0,
                8.0,
                0.05,
                ProceduralBlockEntry::noiseMinimum,
                (entry, value) -> new ProceduralBlockEntry(
                        entry.itemId(),
                        entry.weight(),
                        entry.gradientStart(),
                        entry.gradientEnd(),
                        value,
                        Math.max(value, entry.noiseMaximum()),
                        entry.gradientPosition()
                )
        );
        noiseMinimum.setActive(isCustomPalette());
        y += 22;
        var noiseMaximum = addBlockSlider(
                innerX,
                y,
                innerWidth,
                "Noise maximum",
                0.0,
                8.0,
                0.05,
                ProceduralBlockEntry::noiseMaximum,
                (entry, value) -> new ProceduralBlockEntry(
                        entry.itemId(),
                        entry.weight(),
                        entry.gradientStart(),
                        entry.gradientEnd(),
                        Math.min(entry.noiseMinimum(), value),
                        value,
                        entry.gradientPosition()
                )
        );
        noiseMaximum.setActive(isCustomPalette());
    }

    private void addInspectorPane() {
        int innerX = rightX + GAP;
        int innerWidth = rightWidth - GAP * 2;
        int y = contentTop + GAP;
        var tabs = InspectorTab.values();
        int tabWidth = Math.max(
                1,
                (innerWidth - GAP * (tabs.length - 1)) / tabs.length
        );
        for (int index = 0; index < tabs.length; index++) {
            var tab = tabs[index];
            int x = innerX + index * (tabWidth + GAP);
            int width = index == tabs.length - 1
                    ? innerX + innerWidth - x
                    : tabWidth;
            addWidget(new WorkbenchToolTab(
                    getEntrance(),
                    x,
                    y,
                    width,
                    BUTTON_HEIGHT,
                    Text.text(tab.label),
                    Text.text(tab.title),
                    Text.translate(
                            "effortless.procedural.tooltip.tab." + tab.key
                    ),
                    tab.accent,
                    () -> inspectorTab == tab,
                    () -> {
                        inspectorTab = tab;
                        recreate();
                    }
            ));
        }
        y += BUTTON_HEIGHT + GAP;

        switch (inspectorTab) {
            case MATERIALS -> addMaterialsInspector(innerX, y, innerWidth);
            case GRADIENT -> addGradientInspector(innerX, y, innerWidth);
            case NOISE -> addNoiseInspector(innerX, y, innerWidth);
            case RULES -> addRulesInspector(innerX, y, innerWidth);
            case MASKS -> addMasksInspector(innerX, y, innerWidth);
            case TRANSFORMS -> addTransformsInspector(
                    innerX,
                    y,
                    innerWidth
            );
            case OUTPUT -> addOutputInspector(innerX, y, innerWidth);
        }
    }

    private void addMaterialsInspector(int x, int y, int width) {
        var preset = session.selectedPreset();
        addWidget(new TextWidget(
                getEntrance(),
                x,
                y + 4,
                Text.text("Selected: " + shortId(selectedBlock().itemId()))
                        .withStyle(ChatFormatting.GRAY)
        ));
        y += 18;
        addBlockTuningSliders(x, y, width);
        y += 22 * 5 + GAP;

        var options = addOptions(
                x,
                y,
                width,
                contentTop + contentHeight - y - GAP
        );
        options.addSwitchEntry(
                Text.text("Sequence layer"),
                Text.empty(),
                preset.sequenceEnabled(),
                value -> session.replaceSelected(current -> current.withSequence(
                        value,
                        current.sequenceOffset(),
                        current.sequenceAlternateWeight()
                ))
        );
        options.addIntegerEntry(
                Text.text("Sequence offset"),
                Text.empty(),
                preset.sequenceOffset(),
                -1_000_000,
                1_000_000,
                value -> session.replaceSelected(current -> current.withSequence(
                        current.sequenceEnabled(),
                        value,
                        current.sequenceAlternateWeight()
                ))
        );
        options.addNumberEntry(
                Text.text("Sequence alternate weight"),
                Text.empty(),
                preset.sequenceAlternateWeight(),
                0.0,
                1_000_000.0,
                0.05,
                value -> session.replaceSelected(current -> current.withSequence(
                        current.sequenceEnabled(),
                        current.sequenceOffset(),
                        value
                ))
        );
    }

    private void addGradientInspector(int x, int y, int width) {
        var preset = session.selectedPreset();
        var advanced = preset.advanced();
        var field = advanced.gradientField();
        int canvasHeight = clamp(
                contentHeight / (compactLayout ? 5 : 4),
                72,
                104
        );
        gradientFieldEditor = addWidget(new SpatialFieldEditorWidget(
                getEntrance(),
                x,
                y,
                width,
                canvasHeight,
                () -> session.selectedPreset().advanced().gradientField(),
                session::updateGradientField
        ));
        y += canvasHeight + GAP;
        addWidget(new SpatialFieldToolbarWidget(
                getEntrance(),
                x,
                y,
                width,
                () -> session.selectedPreset().advanced().gradientField(),
                session::updateGradientField
        ));
        y += SpatialFieldToolbarWidget.HEIGHT + GAP;
        var options = addOptions(
                x,
                y,
                width,
                contentTop + contentHeight - y - GAP
        );
        options.addSection(Text.text("SOURCE & PALETTE"));
        addFieldAssetControls(options, true);
        options.addSwitchEntry(
                Text.text("Gradient layer"),
                Text.empty(),
                preset.gradientEnabled(),
                value -> session.replaceSelected(current -> current.withGradient(
                        value,
                        current.gradientCoordinate()
                ))
        );
        var selectedStop = options.addRangeEntry(
                Text.text("Selected stop position"),
                Text.empty(),
                () -> ProceduralPaletteWidget.effectiveStop(
                        session.selectedPreset().blocks(),
                        clampBlockIndex(selectedBlockIndex)
                ),
                0.0,
                1.0,
                0.01,
                value -> updateGradientStop(
                        clampBlockIndex(selectedBlockIndex),
                        value
                )
        );
        selectedStop.setActive(
                isCustomPalette()
                        && advanced.gradientDistributionMode()
                        != GradientDistributionMode.WEIGHTED_ENDPOINTS
        );
        options.addSelectorEntry(
                Text.text("Gradient behavior"),
                Text.empty(),
                labels(GradientDistributionMode.values()),
                List.of(GradientDistributionMode.values()),
                advanced.gradientDistributionMode(),
                value -> {
                    session.replaceSelected(current -> current.withAdvanced(
                            current.advanced()
                                    .withGradientDistributionMode(value)
                    ));
                    recreate();
                }
        );
        options.addSection(Text.text("CURVE & REPEAT"));
        options.addSelectorEntry(
                Text.text("Gradient curve"),
                Text.empty(),
                labels(GradientCurve.values()),
                List.of(GradientCurve.values()),
                advanced.gradientCurve(),
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withGradientCurve(
                                value,
                                current.advanced().gradientSteps()
                        )
                ))
        );
        options.addIntegerEntry(
                Text.text("Stepped-gradient steps"),
                Text.empty(),
                advanced.gradientSteps(),
                2,
                256,
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withGradientCurve(
                                current.advanced().gradientCurve(),
                                value
                        )
                ))
        );
        options.addIntegerEntry(
                Text.text("Gradient repeat"),
                Text.empty(),
                field.repeat(),
                1,
                64,
                value -> updateGradientField(current ->
                        current.withRepeat(value)
                )
        );
        options.addSwitchEntry(
                Text.text("Reverse gradient"),
                Text.empty(),
                field.inverted(),
                value -> updateGradientField(current ->
                        current.withInverted(value)
                )
        );
        options.addSection(Text.text("FIELD TRANSFORM"));
        options.addRangeEntry(
                Text.text("Gradient rotation"),
                Text.empty(),
                () -> session.selectedPreset().advanced().gradientField()
                        .rotationDegrees(),
                -360.0,
                360.0,
                5.0,
                value -> updateGradientField(current ->
                        current.withRotation(value)
                )
        );
        addGradientCenterOptions(options);
        addGradientScaleOptions(options);
        options.addIntegerEntry(
                Text.text("Polygon sides"),
                Text.empty(),
                field.polygonSides(),
                3,
                32,
                value -> updateGradientField(current ->
                        current.withPolygonSides(value)
                )
        );
        options.addRangeEntry(
                Text.text("Curve amount"),
                Text.empty(),
                field.curvature(),
                -4.0,
                4.0,
                0.05,
                value -> updateGradientField(current ->
                        current.withCurvature(value)
                )
        );
        options.addSection(Text.text("DISTORTION"));
        options.addRangeEntry(
                Text.text("Gradient warp"),
                Text.empty(),
                field.warpAmount(),
                0.0,
                2.0,
                0.05,
                value -> updateGradientField(current -> current.withWarp(
                        value,
                        current.warpFrequency(),
                        current.warpSalt()
                ))
        );
        options.addLogRangeEntry(
                Text.text("Gradient warp frequency"),
                Text.empty(),
                field.warpFrequency(),
                0.000001,
                1024.0,
                0.01,
                value -> updateGradientField(current -> current.withWarp(
                        current.warpAmount(),
                        value,
                        current.warpSalt()
                ))
        );
    }

    private void addGradientCenterOptions(
            ProceduralSettingOptionsList options
    ) {
        options.addTripleRangeEntry(
                Text.text("Gradient center"),
                Text.empty(),
                List.of(
                        () -> session.selectedPreset().advanced()
                                .gradientField().centerX(),
                        () -> session.selectedPreset().advanced()
                                .gradientField().centerY(),
                        () -> session.selectedPreset().advanced()
                                .gradientField().centerZ()
                ),
                -4.0,
                4.0,
                0.05,
                false,
                List.of(
                        value -> updateGradientField(current ->
                                current.withCenter(
                                        value,
                                        current.centerY(),
                                        current.centerZ()
                                )),
                        value -> updateGradientField(current ->
                                current.withCenter(
                                        current.centerX(),
                                        value,
                                        current.centerZ()
                                )),
                        value -> updateGradientField(current ->
                                current.withCenter(
                                        current.centerX(),
                                        current.centerY(),
                                        value
                                ))
                )
        );
    }

    private void addGradientScaleOptions(
            ProceduralSettingOptionsList options
    ) {
        options.addTripleRangeEntry(
                Text.text("Gradient scale"),
                Text.empty(),
                List.of(
                        () -> session.selectedPreset().advanced()
                                .gradientField().scaleX(),
                        () -> session.selectedPreset().advanced()
                                .gradientField().scaleY(),
                        () -> session.selectedPreset().advanced()
                                .gradientField().scaleZ()
                ),
                0.000001,
                64.0,
                0.05,
                true,
                List.of(
                        value -> updateGradientField(current ->
                                current.withScale(
                                        value,
                                        current.scaleY(),
                                        current.scaleZ()
                                )),
                        value -> updateGradientField(current ->
                                current.withScale(
                                        current.scaleX(),
                                        value,
                                        current.scaleZ()
                                )),
                        value -> updateGradientField(current ->
                                current.withScale(
                                        current.scaleX(),
                                        current.scaleY(),
                                        value
                                ))
                )
        );
    }

    private void updateGradientField(
            java.util.function.UnaryOperator<SpatialField> updater
    ) {
        session.updateGradientField(
                updater.apply(
                        session.selectedPreset().advanced().gradientField()
                )
        );
    }

    private void addNoiseInspector(int x, int y, int width) {
        var preset = session.selectedPreset();
        var noise = preset.advanced().noiseConfig();
        var options = addOptions(
                x,
                y,
                width,
                contentTop + contentHeight - y - GAP
        );
        options.addSection(Text.text("SOURCE"));
        addFieldAssetControls(options, false);
        options.addSwitchEntry(
                Text.text("Noise layer"),
                Text.empty(),
                preset.noiseEnabled(),
                value -> session.replaceSelected(current -> current.withNoise(
                        value,
                        current.noiseFrequency(),
                        current.noiseSalt()
                ))
        );
        options.addLogRangeEntry(
                Text.text("Noise frequency"),
                Text.empty(),
                preset.noiseFrequency(),
                0.000001,
                1024.0,
                0.01,
                value -> session.replaceSelected(current -> current.withNoise(
                        current.noiseEnabled(),
                        value,
                        current.noiseSalt()
                ))
        );
        options.addSection(Text.text("TRANSFORM"));
        addNoiseScaleOptions(options, noise);
        addNoiseOffsetOptions(options, noise);
        options.addRangeEntry(
                Text.text("Noise rotation"),
                Text.empty(),
                noise.rotationDegrees(),
                -360.0,
                360.0,
                5.0,
                value -> updateNoiseConfig(current ->
                        current.withRotation(value)
                )
        );
        options.addSection(Text.text("DETAIL"));
        options.addIntegerEntry(
                Text.text("Noise octaves"),
                Text.empty(),
                noise.octaves(),
                1,
                8,
                value -> updateNoiseConfig(current -> current.withFractal(
                        value,
                        current.persistence(),
                        current.lacunarity()
                ))
        );
        options.addSection(Text.text("DISTORTION"));
        options.addRangeEntry(
                Text.text("Noise persistence"),
                Text.empty(),
                noise.persistence(),
                0.0,
                1.0,
                0.05,
                value -> updateNoiseConfig(current -> current.withFractal(
                        current.octaves(),
                        value,
                        current.lacunarity()
                ))
        );
        options.addRangeEntry(
                Text.text("Noise lacunarity"),
                Text.empty(),
                noise.lacunarity(),
                1.0,
                8.0,
                0.1,
                value -> updateNoiseConfig(current -> current.withFractal(
                        current.octaves(),
                        current.persistence(),
                        value
                ))
        );
        options.addRangeEntry(
                Text.text("Noise warp"),
                Text.empty(),
                noise.warpStrength(),
                0.0,
                64.0,
                0.1,
                value -> updateNoiseConfig(current -> current.withWarp(
                        value,
                        current.warpFrequency(),
                        current.warpSalt()
                ))
        );
        options.addLogRangeEntry(
                Text.text("Noise warp frequency"),
                Text.empty(),
                noise.warpFrequency(),
                0.000001,
                1024.0,
                0.01,
                value -> updateNoiseConfig(current -> current.withWarp(
                        current.warpStrength(),
                        value,
                        current.warpSalt()
                ))
        );
    }

    private void addNoiseScaleOptions(
            ProceduralSettingOptionsList options,
            ProceduralNoiseConfig noise
    ) {
        options.addTripleRangeEntry(
                Text.text("Noise scale"),
                Text.empty(),
                List.of(
                        () -> session.selectedPreset().advanced()
                                .noiseConfig().scaleX(),
                        () -> session.selectedPreset().advanced()
                                .noiseConfig().scaleY(),
                        () -> session.selectedPreset().advanced()
                                .noiseConfig().scaleZ()
                ),
                0.000001,
                64.0,
                0.05,
                true,
                List.of(
                        value -> updateNoiseConfig(current ->
                                current.withScale(
                                        value,
                                        current.scaleY(),
                                        current.scaleZ()
                                )),
                        value -> updateNoiseConfig(current ->
                                current.withScale(
                                        current.scaleX(),
                                        value,
                                        current.scaleZ()
                                )),
                        value -> updateNoiseConfig(current ->
                                current.withScale(
                                        current.scaleX(),
                                        current.scaleY(),
                                        value
                                ))
                )
        );
    }

    private void addNoiseOffsetOptions(
            ProceduralSettingOptionsList options,
            ProceduralNoiseConfig noise
    ) {
        options.addTripleRangeEntry(
                Text.text("Noise offset"),
                Text.empty(),
                List.of(
                        () -> session.selectedPreset().advanced()
                                .noiseConfig().offsetX(),
                        () -> session.selectedPreset().advanced()
                                .noiseConfig().offsetY(),
                        () -> session.selectedPreset().advanced()
                                .noiseConfig().offsetZ()
                ),
                -1_000_000.0,
                1_000_000.0,
                0.1,
                false,
                List.of(
                        value -> updateNoiseConfig(current ->
                                current.withOffset(
                                        value,
                                        current.offsetY(),
                                        current.offsetZ()
                                )),
                        value -> updateNoiseConfig(current ->
                                current.withOffset(
                                        current.offsetX(),
                                        value,
                                        current.offsetZ()
                                )),
                        value -> updateNoiseConfig(current ->
                                current.withOffset(
                                        current.offsetX(),
                                        current.offsetY(),
                                        value
                                ))
                )
        );
    }

    private void updateNoiseConfig(
            java.util.function.UnaryOperator<ProceduralNoiseConfig> updater
    ) {
        session.updateNoiseConfig(
                updater.apply(
                        session.selectedPreset().advanced().noiseConfig()
                )
        );
    }

    private void addFieldAssetControls(
            ProceduralSettingOptionsList options,
            boolean gradient
    ) {
        var assets = session.library().fieldAssets();
        var values = new java.util.ArrayList<String>(assets.size() + 1);
        var labels = new java.util.ArrayList<Text>(assets.size() + 1);
        values.add("");
        labels.add(Text.text("Local settings"));
        for (var asset : assets) {
            values.add(asset.id().toString());
            labels.add(Text.text(asset.name()));
        }
        String selected = gradient
                ? session.selectedPreset().advanced().gradientFieldAssetId()
                : session.selectedPreset().advanced().noiseFieldAssetId();
        if (!values.contains(selected)) {
            selected = "";
        }
        options.addSelectorEntry(
                Text.text(gradient
                        ? "Gradient field asset"
                        : "Noise field asset"),
                Text.empty(),
                labels,
                values,
                selected,
                value -> {
                    if (gradient) {
                        session.linkGradientFieldAsset(value);
                    } else {
                        session.linkNoiseFieldAsset(value);
                    }
                    recreate();
                }
        );
        options.addTab(
                Text.text("Reusable field library"),
                Text.empty(),
                selected,
                ignored -> {
                },
                (entry, value) -> {
                    entry.getButton().setMessage(Text.text("Save new asset"));
                    entry.getButton().setOnPressListener(button -> {
                        var preset = session.selectedPreset();
                        var id = session.createFieldAsset(
                                preset.name() + " field "
                                        + (session.library()
                                        .fieldAssets()
                                        .size() + 1)
                        );
                        if (gradient) {
                            session.linkGradientFieldAsset(id.toString());
                        } else {
                            session.linkNoiseFieldAsset(id.toString());
                        }
                        recreate();
                    });
                }
        );
        options.addTab(
                Text.text("Linked field asset"),
                Text.empty(),
                selected,
                ignored -> {
                },
                (entry, value) -> {
                    entry.getButton().setMessage(Text.text("Delete asset"));
                    entry.getButton().setActive(!value.isBlank());
                    entry.getButton().setOnPressListener(button -> {
                        if (!value.isBlank()) {
                            session.deleteFieldAsset(value);
                            recreate();
                        }
                    });
                }
        );
    }

    private void updateGradientStop(int index, double value) {
        session.replaceSelected(current -> {
            var blocks = current.blocks();
            if (index < 0 || index >= blocks.size()) {
                return current;
            }
            var positioned = new java.util.ArrayList<ProceduralBlockEntry>(
                    blocks.size()
            );
            for (int blockIndex = 0;
                    blockIndex < blocks.size();
                    blockIndex++) {
                var entry = blocks.get(blockIndex);
                double position = ProceduralPaletteWidget.effectiveStop(
                        blocks,
                        blockIndex
                );
                if (blockIndex == index) {
                    double minimum = blockIndex == 0
                            ? 0.0
                            : ProceduralPaletteWidget.effectiveStop(
                                    blocks,
                                    blockIndex - 1
                            ) + 0.001;
                    double maximum = blockIndex == blocks.size() - 1
                            ? 1.0
                            : ProceduralPaletteWidget.effectiveStop(
                                    blocks,
                                    blockIndex + 1
                            ) - 0.001;
                    position = Math.max(
                            minimum,
                            Math.min(maximum, value)
                    );
                }
                positioned.add(new ProceduralBlockEntry(
                        entry.itemId(),
                        entry.weight(),
                        entry.gradientStart(),
                        entry.gradientEnd(),
                        entry.noiseMinimum(),
                        entry.noiseMaximum(),
                        position
                ));
            }
            return current.withBlocks(positioned);
        });
    }

    private void addMixInspector(int x, int y, int width) {
        var preset = session.selectedPreset();
        var draft = session.selectedTextDraft();
        int labelWidth = Math.min(58, width / 3);
        addWidget(new TextWidget(
                getEntrance(),
                x,
                y + 6,
                Text.text("Seed").withStyle(ChatFormatting.GRAY)
        ));
        seedField = addWidget(new ReliableEditBox(
                getEntrance(),
                x + labelWidth,
                y,
                width - labelWidth,
                BUTTON_HEIGHT,
                Text.text("Seed")
        ));
        seedField.setFilter(
                EffortlessProceduralPatternScreen::isLongInput
        );
        seedField.setMaxLength(20);
        seedField.setValue(draft.seed());
        seedField.setChangeListener(session::setSeedDraft);
        y += BUTTON_HEIGHT + GAP;

        int available = contentTop + contentHeight - y - GAP;
        var options = addOptions(x, y, width, available);
        options.addSwitchEntry(
                Text.text("Sequence layer"),
                Text.empty(),
                preset.sequenceEnabled(),
                value -> session.replaceSelected(current -> current.withSequence(
                        value,
                        current.sequenceOffset(),
                        current.sequenceAlternateWeight()
                ))
        );
        options.addIntegerEntry(
                Text.text("Sequence offset"),
                Text.empty(),
                preset.sequenceOffset(),
                -1_000_000,
                1_000_000,
                value -> session.replaceSelected(current -> current.withSequence(
                        current.sequenceEnabled(),
                        value,
                        current.sequenceAlternateWeight()
                ))
        );
        options.addNumberEntry(
                Text.text("Sequence alternate weight"),
                Text.empty(),
                preset.sequenceAlternateWeight(),
                0.0,
                1_000_000.0,
                0.05,
                value -> session.replaceSelected(current -> current.withSequence(
                        current.sequenceEnabled(),
                        current.sequenceOffset(),
                        value
                ))
        );
        options.addSwitchEntry(
                Text.text("Gradient layer"),
                Text.empty(),
                preset.gradientEnabled(),
                value -> session.replaceSelected(current -> current.withGradient(
                        value,
                        current.gradientCoordinate()
                ))
        );
        options.addSelectorEntry(
                Text.text("Gradient coordinate"),
                Text.empty(),
                labels(Coordinate.values()),
                List.of(Coordinate.values()),
                preset.gradientCoordinate(),
                value -> session.replaceSelected(current -> current.withGradient(
                        current.gradientEnabled(),
                        value
                ))
        );
        options.addSelectorEntry(
                Text.text("Gradient behavior"),
                Text.empty(),
                compactLayout
                        ? List.of(
                                Text.text("weighted"),
                                Text.text("blend"),
                                Text.text("bands")
                        )
                        : labels(GradientDistributionMode.values()),
                List.of(GradientDistributionMode.values()),
                preset.advanced().gradientDistributionMode(),
                value -> {
                    session.replaceSelected(current -> current.withAdvanced(
                            current.advanced().withGradientDistributionMode(value)
                    ));
                    recreate();
                }
        );
        options.addSelectorEntry(
                Text.text("Gradient curve"),
                Text.empty(),
                labels(GradientCurve.values()),
                List.of(GradientCurve.values()),
                preset.advanced().gradientCurve(),
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withGradientCurve(
                                value,
                                current.advanced().gradientSteps()
                        )
                ))
        );
        options.addIntegerEntry(
                Text.text("Stepped-gradient steps"),
                Text.empty(),
                preset.advanced().gradientSteps(),
                2,
                256,
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withGradientCurve(
                                current.advanced().gradientCurve(),
                                value
                        )
                ))
        );
        options.addSwitchEntry(
                Text.text("Noise layer"),
                Text.empty(),
                preset.noiseEnabled(),
                value -> session.replaceSelected(current -> current.withNoise(
                        value,
                        current.noiseFrequency(),
                        current.noiseSalt()
                ))
        );
        options.addNumberEntry(
                Text.text("Noise frequency"),
                Text.empty(),
                preset.noiseFrequency(),
                0.000001,
                1024.0,
                0.01,
                value -> session.replaceSelected(current -> current.withNoise(
                        current.noiseEnabled(),
                        value,
                        current.noiseSalt()
                ))
        );
    }

    private void addRulesInspector(int x, int y, int width) {
        var preset = session.selectedPreset();
        var options = addOptions(
                x,
                y,
                width,
                contentTop + contentHeight - y - GAP
        );
        options.addIntegerEntry(
                Text.text("Retry limit"),
                Text.empty(),
                preset.retryLimit(),
                1,
                ProceduralRuleSet.MAX_RETRY_LIMIT,
                value -> session.replaceSelected(
                        current -> current.withRetryLimit(value)
                )
        );
        options.addIntegerEntry(
                Text.text("Maximum identical run (0 = off)"),
                Text.empty(),
                preset.maximumRunLength(),
                0,
                MaxRunLengthConstraint.MAXIMUM_RUN_LENGTH,
                value -> session.replaceSelected(
                        current -> current.withMaximumRunLength(value)
                )
        );
        options.addSelectorEntry(
                Text.text("Adjacency neighbors"),
                Text.empty(),
                labels(NeighborTopology.values()),
                List.of(NeighborTopology.values()),
                preset.advanced().adjacencyTopology(),
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withAdjacencyTopology(value)
                ))
        );
        options.addIntegerEntry(
                Text.text("Deterministic repair passes"),
                Text.empty(),
                preset.advanced().repairPasses(),
                0,
                ProceduralRuleSet.MAX_REPAIR_PASSES,
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withRepairPasses(value)
                ))
        );
        options.addTab(
                Text.text("Forbidden adjacency"),
                Text.empty(),
                preset.forbiddenAdjacency(),
                value -> session.replaceSelected(
                        current -> current.withForbiddenAdjacency(value)
                ),
                (entry, value) -> {
                    entry.getButton().setMessage(
                            Text.text(value.size() + " rules")
                    );
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessForbiddenAdjacencyScreen(
                                    getEntrance(),
                                    entry::setItem,
                                    value
                            ).attach()
                    );
                }
        );
        options.addTab(
                Text.text("Preferred adjacency"),
                Text.empty(),
                preset.preferredAdjacency(),
                value -> session.replaceSelected(
                        current -> current.withPreferredAdjacency(value)
                ),
                (entry, value) -> {
                    entry.getButton().setMessage(
                            Text.text(value.size() + " rules")
                    );
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessPreferredAdjacencyScreen(
                                    getEntrance(),
                                    entry::setItem,
                                    value
                            ).attach()
                    );
                }
        );
        options.addTab(
                Text.text("Vertical neighbor rules"),
                Text.empty(),
                preset.verticalRules(),
                value -> session.replaceSelected(
                        current -> current.withVerticalRules(value)
                ),
                (entry, value) -> {
                    entry.getButton().setMessage(
                            Text.text(value.size() + " rules")
                    );
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessVerticalRulesScreen(
                                    getEntrance(),
                                    entry::setItem,
                                    value
                            ).attach()
                    );
                }
        );
        options.addTab(
                Text.text("Directional neighbor rules"),
                Text.empty(),
                preset.advanced().directionalRules(),
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withDirectionalRules(value)
                )),
                (entry, value) -> configureRuleButton(
                        entry,
                        value.size(),
                        () -> ProceduralRuleEditorLauncher.directional(
                                getEntrance(),
                                value,
                                entry::setItem
                        )
                )
        );
        options.addTab(
                Text.text("Minimum spacing rules"),
                Text.empty(),
                preset.advanced().spacingRules(),
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withSpacingRules(value)
                )),
                (entry, value) -> configureRuleButton(
                        entry,
                        value.size(),
                        () -> ProceduralRuleEditorLauncher.spacing(
                                getEntrance(),
                                value,
                                entry::setItem
                        )
                )
        );
        options.addTab(
                Text.text("Neighbor-count rules"),
                Text.empty(),
                preset.advanced().neighborCountRules(),
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withNeighborCountRules(value)
                )),
                (entry, value) -> configureRuleButton(
                        entry,
                        value.size(),
                        () -> ProceduralRuleEditorLauncher.neighborCounts(
                                getEntrance(),
                                value,
                                entry::setItem
                        )
                )
        );
        options.addTab(
                Text.text("Block density / quota rules"),
                Text.empty(),
                preset.advanced().quotaRules(),
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withQuotaRules(value)
                )),
                (entry, value) -> configureRuleButton(
                        entry,
                        value.size(),
                        () -> ProceduralRuleEditorLauncher.quotas(
                                getEntrance(),
                                value,
                                entry::setItem
                        )
                )
        );
        addAdvancedTab(options);
    }

    private void addMasksInspector(int x, int y, int width) {
        var preset = session.selectedPreset();
        var advanced = preset.advanced();
        var options = addOptions(
                x,
                y,
                width,
                contentTop + contentHeight - y - GAP
        );
        options.addSwitchEntry(
                Text.text("Inspect existing world"),
                Text.empty(),
                preset.inspectExistingWorld(),
                value -> session.replaceSelected(
                        current -> current.withInspectExistingWorld(value)
                )
        );
        options.addSelectorEntry(
                Text.text("Coordinate space"),
                Text.empty(),
                labels(CoordinateSpace.values()),
                List.of(CoordinateSpace.values()),
                advanced.coordinateSpace(),
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withCoordinateSpace(value)
                ))
        );
        options.addSelectorEntry(
                Text.text("Seed mode"),
                Text.empty(),
                labels(SeedMode.values()),
                List.of(SeedMode.values()),
                advanced.seedMode(),
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withSeedMode(value)
                ))
        );
        options.addTab(
                Text.text("Named masks / bands / rings"),
                Text.empty(),
                advanced.maskLayers(),
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withMaskLayers(value)
                )),
                (entry, value) -> configureRuleButton(
                        entry,
                        value.size(),
                        () -> ProceduralRuleEditorLauncher.masks(
                                getEntrance(),
                                value,
                                entry::setItem
                        )
                )
        );
        addAdvancedTab(options);
    }

    private void addTransformsInspector(int x, int y, int width) {
        var transforms = session.selectedPreset().stockTransformers();
        int bottom = contentTop + contentHeight - GAP;
        int listHeight = clamp((bottom - y) / 3, 56, 92);
        transformerList = addWidget(new TextRuleList<>(
                getEntrance(),
                x,
                y,
                width - 8,
                listHeight,
                EffortlessProceduralPatternScreen::transformerTitle,
                EffortlessProceduralPatternScreen::transformerSummary
        ));
        transformerList.setAlwaysShowScrollbar(true);
        transformerList.reset(transforms);
        if (!transforms.isEmpty()) {
            selectedTransformerIndex = Math.min(
                    selectedTransformerIndex,
                    transforms.size() - 1
            );
            transformerList.selectByIndex(selectedTransformerIndex);
        }
        y += listHeight + GAP;

        int buttonWidth = Math.max(20, (width - GAP * 2) / 3);
        addButton(
                x,
                y,
                buttonWidth,
                Text.text("+Array"),
                button -> {
                    session.addTransformer(ArrayTransformer.DEFAULT.withRandomId());
                    selectedTransformerIndex =
                            session.selectedPreset().stockTransformers().size() - 1;
                    recreate();
                }
        );
        addButton(
                x + (buttonWidth + GAP),
                y,
                buttonWidth,
                Text.text("+Mirror"),
                button -> {
                    var position = Transformer.roundAllHalf(
                            getEntrance().getClient().getPlayer().getPosition()
                    );
                    session.addTransformer(new MirrorTransformer(
                            position,
                            Axis.X
                    ));
                    selectedTransformerIndex =
                            session.selectedPreset().stockTransformers().size() - 1;
                    recreate();
                }
        );
        addButton(
                x + (buttonWidth + GAP) * 2,
                y,
                width - (buttonWidth + GAP) * 2,
                Text.text("+Radial"),
                button -> {
                    var position = Transformer.roundAllHalf(
                            getEntrance().getClient().getPlayer().getPosition()
                    );
                    session.addTransformer(new RadialTransformer(
                            position,
                            Axis.Y,
                            RadialTransformer.DEFAULT_SLICE,
                            RadialTransformer.DEFAULT_RADIUS,
                            RadialTransformer.DEFAULT_LENGTH
                    ));
                    selectedTransformerIndex =
                            session.selectedPreset().stockTransformers().size() - 1;
                    recreate();
                }
        );
        y += BUTTON_HEIGHT + GAP;
        moveTransformerUpButton = addButton(
                x,
                y,
                buttonWidth,
                Text.text("Up"),
                button -> {
                    selectedTransformerIndex = session.moveTransformer(
                            selectedTransformerIndex,
                            -1
                    );
                    recreate();
                }
        );
        moveTransformerDownButton = addButton(
                x + buttonWidth + GAP,
                y,
                buttonWidth,
                Text.text("Down"),
                button -> {
                    selectedTransformerIndex = session.moveTransformer(
                            selectedTransformerIndex,
                            1
                    );
                    recreate();
                }
        );
        deleteTransformerButton = addButton(
                x + (buttonWidth + GAP) * 2,
                y,
                width - (buttonWidth + GAP) * 2,
                Text.text("Del"),
                button -> {
                    selectedTransformerIndex = session.deleteTransformer(
                            selectedTransformerIndex
                    );
                    recreate();
                }
        );
        y += BUTTON_HEIGHT + GAP;

        addButton(
                x,
                y,
                width,
                Text.text("Import active stock pattern"),
                button -> {
                    var player = getEntrance().getClient().getPlayer();
                    session.importStockPattern(
                            getEntrance().getStructureBuilder()
                                    .getContext(player)
                                    .pattern()
                    );
                    selectedTransformerIndex = 0;
                    recreate();
                }
        );
        y += BUTTON_HEIGHT + GAP;

        if (transforms.isEmpty()) {
            addWidget(new TextWidget(
                    getEntrance(),
                    x,
                    y + 4,
                    Text.text("No geometry transforms; use with any shape")
                            .withStyle(ChatFormatting.GRAY)
            ));
            return;
        }

        var selected = transforms.get(selectedTransformerIndex);
        var options = addOptions(x, y, width, bottom - y);
        switch (selected.getType()) {
            case ARRAY -> addArrayTransformOptions(
                    options,
                    (ArrayTransformer) selected
            );
            case MIRROR -> addMirrorTransformOptions(
                    options,
                    (MirrorTransformer) selected
            );
            case RADIAL -> addRadialTransformOptions(
                    options,
                    (RadialTransformer) selected
            );
            case RANDOMIZER -> throw new IllegalStateException(
                    "Randomizers are edited in Materials and Mix"
            );
        }
    }

    private void addArrayTransformOptions(
            ProceduralSettingOptionsList options,
            ArrayTransformer value
    ) {
        options.addIntegerEntry(
                Text.text("Array offset X"),
                Text.empty(),
                value.offset().x(),
                -30_000_000,
                30_000_000,
                changed -> updateSelectedTransformer(transformer ->
                        ((ArrayTransformer) transformer).withOffsetX(changed)
                )
        );
        options.addIntegerEntry(
                Text.text("Array offset Y"),
                Text.empty(),
                value.offset().y(),
                -30_000_000,
                30_000_000,
                changed -> updateSelectedTransformer(transformer ->
                        ((ArrayTransformer) transformer).withOffsetY(changed)
                )
        );
        options.addIntegerEntry(
                Text.text("Array offset Z"),
                Text.empty(),
                value.offset().z(),
                -30_000_000,
                30_000_000,
                changed -> updateSelectedTransformer(transformer ->
                        ((ArrayTransformer) transformer).withOffsetZ(changed)
                )
        );
        options.addIntegerEntry(
                Text.text("Array count"),
                Text.empty(),
                value.count(),
                ArrayTransformer.COUNT_RANGE.min(),
                ArrayTransformer.COUNT_RANGE.max(),
                changed -> updateSelectedTransformer(transformer ->
                        ((ArrayTransformer) transformer).withCount(changed)
                )
        );
    }

    private void addMirrorTransformOptions(
            ProceduralSettingOptionsList options,
            MirrorTransformer value
    ) {
        addTransformPositionOptions(options, value.position(), position ->
                updateSelectedTransformer(transformer ->
                        ((MirrorTransformer) transformer).withPosition(position)
                )
        );
        options.addSelectorEntry(
                Text.text("Transform axis"),
                Text.empty(),
                labels(Axis.values()),
                List.of(Axis.values()),
                value.axis(),
                changed -> updateSelectedTransformer(transformer ->
                        ((MirrorTransformer) transformer).withAxis(changed)
                )
        );
        options.addIntegerEntry(
                Text.text("Mirror range"),
                Text.empty(),
                value.size(),
                MirrorTransformer.SIZE_RANGE.min(),
                MirrorTransformer.SIZE_RANGE.max(),
                changed -> updateSelectedTransformer(transformer ->
                        ((MirrorTransformer) transformer).withSize(changed)
                )
        );
    }

    private void addRadialTransformOptions(
            ProceduralSettingOptionsList options,
            RadialTransformer value
    ) {
        addTransformPositionOptions(options, value.position(), position ->
                updateSelectedTransformer(transformer ->
                        ((RadialTransformer) transformer).withPosition(position)
                )
        );
        options.addSelectorEntry(
                Text.text("Transform axis"),
                Text.empty(),
                labels(Axis.values()),
                List.of(Axis.values()),
                value.axis(),
                changed -> updateSelectedTransformer(transformer ->
                        ((RadialTransformer) transformer).withAxis(changed)
                )
        );
        options.addIntegerEntry(
                Text.text("Radial slices"),
                Text.empty(),
                value.slices(),
                RadialTransformer.SLICE_RANGE.min(),
                RadialTransformer.SLICE_RANGE.max(),
                changed -> updateSelectedTransformer(transformer ->
                        ((RadialTransformer) transformer).withSlice(changed)
                )
        );
        options.addIntegerEntry(
                Text.text("Radial radius"),
                Text.empty(),
                value.radius(),
                RadialTransformer.RADIUS_RANGE.min(),
                RadialTransformer.RADIUS_RANGE.max(),
                changed -> updateSelectedTransformer(transformer ->
                        ((RadialTransformer) transformer).withRadius(changed)
                )
        );
        options.addIntegerEntry(
                Text.text("Radial length"),
                Text.empty(),
                value.length(),
                RadialTransformer.LENGTH_RANGE.min(),
                RadialTransformer.LENGTH_RANGE.max(),
                changed -> updateSelectedTransformer(transformer ->
                        ((RadialTransformer) transformer).withLength(changed)
                )
        );
    }

    private void addTransformPositionOptions(
            ProceduralSettingOptionsList options,
            Vector3d value,
            java.util.function.Consumer<Vector3d> consumer
    ) {
        options.addPositionEntry(
                Text.text("Transform center X"),
                Text.empty(),
                value.x(),
                -30_000_000.0,
                30_000_000.0,
                changed -> consumer.accept(value.withX(changed))
        );
        options.addPositionEntry(
                Text.text("Transform center Y"),
                Text.empty(),
                value.y(),
                -30_000_000.0,
                30_000_000.0,
                changed -> consumer.accept(value.withY(changed))
        );
        options.addPositionEntry(
                Text.text("Transform center Z"),
                Text.empty(),
                value.z(),
                -30_000_000.0,
                30_000_000.0,
                changed -> consumer.accept(value.withZ(changed))
        );
    }

    private void updateSelectedTransformer(
            java.util.function.UnaryOperator<Transformer> updater
    ) {
        session.updateTransformer(selectedTransformerIndex, updater);
    }

    private void addOutputInspector(int x, int y, int width) {
        var preset = session.selectedPreset();
        var draft = session.selectedTextDraft();
        int labelWidth = Math.min(58, width / 3);
        addWidget(new TextWidget(
                getEntrance(),
                x,
                y + 6,
                Text.text("Noise salt").withStyle(ChatFormatting.GRAY)
        ));
        noiseSaltField = addWidget(new ReliableEditBox(
                getEntrance(),
                x + labelWidth,
                y,
                width - labelWidth,
                BUTTON_HEIGHT,
                Text.text("Noise salt")
        ));
        noiseSaltField.setFilter(
                EffortlessProceduralPatternScreen::isLongInput
        );
        noiseSaltField.setMaxLength(20);
        noiseSaltField.setValue(draft.noiseSalt());
        noiseSaltField.setChangeListener(session::setNoiseSaltDraft);
        y += BUTTON_HEIGHT + GAP;

        var options = addOptions(
                x,
                y,
                width,
                contentTop + contentHeight - y - GAP
        );
        var player = getEntrance().getClient().getPlayer();
        var currentMode = getEntrance().getStructureBuilder()
                .getContext(player)
                .buildMode();
        options.addSelectorEntry(
                Text.text("Active build tool"),
                Text.empty(),
                compactLayout
                        ? compactBuildModeLabels()
                        : labels(BuildMode.values()),
                List.of(BuildMode.values()),
                currentMode,
                value -> getEntrance().getStructureBuilder().setStructure(
                        player,
                        getEntrance().getConfigStorage().getStructure(value)
                )
        );
        options.addIntegerEntry(
                Text.text("Cleanup passes"),
                Text.empty(),
                preset.advanced().cleanupPasses(),
                0,
                ProceduralRuleSet.MAX_CLEANUP_PASSES,
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withCleanup(
                                value,
                                current.advanced().cleanupRules()
                        )
                ))
        );
        options.addTab(
                Text.text("Post-generation cleanup rules"),
                Text.empty(),
                preset.advanced().cleanupRules(),
                value -> session.replaceSelected(current -> current.withAdvanced(
                        current.advanced().withCleanup(
                                current.advanced().cleanupPasses(),
                                value
                        )
                )),
                (entry, value) -> configureRuleButton(
                        entry,
                        value.size(),
                        () -> ProceduralRuleEditorLauncher.cleanup(
                                getEntrance(),
                                value,
                                entry::setItem
                        )
                )
        );
        addAdvancedTab(options);
    }

    private void addAdvancedTab(ProceduralSettingOptionsList options) {
        var preset = session.selectedPreset();
        options.addTab(
                Text.text("Advanced rules, masks and composition"),
                Text.empty(),
                preset.advanced(),
                value -> session.replaceSelected(
                        current -> current.withAdvanced(value)
                ),
                (entry, value) -> {
                    entry.getButton().setMessage(
                            Text.text(advancedRuleCount(value) + " entries")
                    );
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessAdvancedProceduralSettingsScreen(
                                    getEntrance(),
                                    entry::setItem,
                                    session.selectedPreset().advanced(),
                                    session.library().presets(),
                                    session.selectedPresetId()
                            ).attach()
                    );
                }
        );
    }

    private ProceduralSettingOptionsList addOptions(
            int x,
            int y,
            int width,
            int height
    ) {
        var options = addWidget(new ProceduralSettingOptionsList(
                getEntrance(),
                x,
                y,
                width - 8,
                Math.max(30, height),
                false,
                false
        ));
        options.setAlwaysShowScrollbar(true);
        return options;
    }

    private static void configureRuleButton(
            dev.huskuraft.effortless.screen.settings.SettingOptionsList.ButtonEntry<?> entry,
            int count,
            Runnable open
    ) {
        entry.getButton().setMessage(Text.text(count + " rules"));
        entry.getButton().setOnPressListener(button -> open.run());
    }

    private void addFooter() {
        int y = getScreenHeight() - margin - FOOTER_HEIGHT + 3;
        int buttonWidth = clamp(
                (getScreenWidth() - margin * 2) / 11,
                46,
                88
        );
        cancelButton = addButton(
                margin + GAP,
                y,
                buttonWidth,
                Text.text("Cancel"),
                button -> detach()
        );
        undoButton = addButton(
                margin + GAP + buttonWidth + GAP,
                y,
                buttonWidth,
                Text.text("Undo"),
                button -> {
                    session.undo();
                    selectedBlockIndex = 0;
                    selectedTransformerIndex = 0;
                    recreate();
                }
        );
        redoButton = addButton(
                margin + GAP + (buttonWidth + GAP) * 2,
                y,
                buttonWidth,
                Text.text("Redo"),
                button -> {
                    session.redo();
                    selectedBlockIndex = 0;
                    selectedTransformerIndex = 0;
                    recreate();
                }
        );
        draftStatusWidget = addWidget(new TextWidget(
                getEntrance(),
                getScreenWidth() / 2,
                y + 6,
                Text.empty(),
                TextWidget.Gravity.CENTER
        ));
        addButton(
                getScreenWidth() - margin - GAP
                        - (buttonWidth + GAP) * 3,
                y,
                buttonWidth,
                Text.text("Import"),
                button -> importLibrary()
        );
        addButton(
                getScreenWidth() - margin - GAP
                        - (buttonWidth + GAP) * 2,
                y,
                buttonWidth,
                Text.text("Export"),
                button -> exportLibrary()
        );
        saveButton = addButton(
                getScreenWidth() - margin - GAP - buttonWidth,
                y,
                buttonWidth,
                Text.text("Save"),
                button -> saveLibrary()
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

    private ProceduralSlider addBlockSlider(
            int x,
            int y,
            int width,
            String label,
            double minimum,
            double maximum,
            double step,
            ToDoubleFunction<ProceduralBlockEntry> getter,
            BlockValueUpdater updater
    ) {
        return addWidget(new ProceduralSlider(
                getEntrance(),
                x,
                y,
                width,
                Text.text(label),
                ProceduralTooltips.setting(Text.text(
                        switch (label) {
                            case "Gradient start" -> "Gradient start weight";
                            case "Gradient end" -> "Gradient end weight";
                            case "Noise minimum" -> "Noise minimum multiplier";
                            case "Noise maximum" -> "Noise maximum multiplier";
                            default -> label;
                        }
                )),
                minimum,
                maximum,
                step,
                () -> getter.applyAsDouble(selectedBlock()),
                value -> session.updateBlock(
                        selectedBlockIndex,
                        entry -> updater.update(entry, value)
                ),
                DECIMAL
        ));
    }

    private void pickBlock(int index) {
        var blocks = session.selectedPreset().blocks();
        if (index < 0 || index >= blocks.size()) {
            return;
        }
        String currentId = blocks.get(index).itemId();
        new EffortlessItemPickerScreen(
                getEntrance(),
                item -> item instanceof BlockItem
                        && (item.getId().getString().equals(currentId)
                        || session.selectedPreset().blocks().stream()
                                .noneMatch(entry -> entry.itemId().equals(
                                        item.getId().getString()
                                ))),
                item -> session.updateBlock(
                        index,
                        entry -> new ProceduralBlockEntry(
                                item.getId().getString(),
                                entry.weight(),
                                entry.gradientStart(),
                                entry.gradientEnd(),
                                entry.noiseMinimum(),
                                entry.noiseMaximum(),
                                entry.gradientPosition()
                        )
                )
        ).attach();
    }

    private void pickInventoryBlock(int index) {
        var blocks = session.selectedPreset().blocks();
        if (index < 0 || index >= blocks.size()) {
            return;
        }
        String currentId = blocks.get(index).itemId();
        var counts = new java.util.LinkedHashMap<String, Integer>();
        var player = getEntrance().getClient().getPlayer();
        java.util.stream.Stream.of(
                        player.getInventory().getBagItems(),
                        player.getInventory().getOffhandItems()
                )
                .flatMap(List::stream)
                .filter(stack -> stack.getItem() instanceof BlockItem)
                .filter(stack -> stack.getCount() > 0)
                .forEach(stack -> counts.merge(
                        stack.getItem().getId().getString(),
                        stack.getCount(),
                        Integer::sum
                ));
        var stacks = counts.entrySet().stream()
                .map(entry -> dev.huskuraft.universal.api.core.ItemStack.of(
                        dev.huskuraft.universal.api.core.Item.fromId(
                                dev.huskuraft.universal.api.core.ResourceLocation
                                        .decompose(entry.getKey())
                        ),
                        entry.getValue()
                ))
                .toList();
        new EffortlessItemPickerScreen(
                getEntrance(),
                Text.translate("effortless.procedural.inventory_picker.title"),
                item -> item instanceof BlockItem
                        && (item.getId().getString().equals(currentId)
                        || session.selectedPreset().blocks().stream()
                                .noneMatch(entry -> entry.itemId().equals(
                                        item.getId().getString()
                                ))),
                item -> session.updateBlock(
                        index,
                        entry -> new ProceduralBlockEntry(
                                item.getId().getString(),
                                entry.weight(),
                                entry.gradientStart(),
                                entry.gradientEnd(),
                                entry.noiseMinimum(),
                                entry.noiseMaximum(),
                                entry.gradientPosition()
                        )
                ),
                stacks
        ).attach();
    }

    private void pickFallback() {
        new EffortlessItemPickerScreen(
                getEntrance(),
                item -> item instanceof BlockItem
                        && session.selectedPreset().blocks().stream()
                                .anyMatch(entry -> entry.itemId().equals(
                                        item.getId().getString()
                                )),
                item -> session.replaceSelected(current ->
                        current.withFallbackItemId(item.getId().getString())
                )
        ).attach();
    }

    private ProceduralBlockEntry selectedBlock() {
        var blocks = session.selectedPreset().blocks();
        selectedBlockIndex = clampBlockIndex(selectedBlockIndex);
        return blocks.get(selectedBlockIndex);
    }

    private int clampBlockIndex(int value) {
        int size = session.selectedPreset().blocks().size();
        return Math.max(0, Math.min(value, Math.max(0, size - 1)));
    }

    private void saveLibrary() {
        try {
            commitVisibleTextInputs();
            var materialized = session.materialize();
            getEntrance().getProceduralConfigStorage().set(materialized);
            applyActivePattern(materialized);
            session.markSaved();
            String active = materialized.activePreset()
                    .map(ProceduralPatternPreset::name)
                    .orElse("none");
            message(
                    "Saved " + materialized.presets().size()
                            + " patterns; active: " + active,
                    ChatFormatting.GREEN
            );
            allowDetach = true;
            detach();
        } catch (IllegalArgumentException exception) {
            message(
                    "Pattern: " + exception.getMessage(),
                    ChatFormatting.RED
            );
        }
    }

    private void commitVisibleTextInputs() {
        if (searchField != null) {
            searchField.commitVisibleValue();
        }
        if (nameField != null) {
            nameField.commitVisibleValue();
        }
        if (seedField != null) {
            seedField.commitVisibleValue();
        }
        if (noiseSaltField != null) {
            noiseSaltField.commitVisibleValue();
        }
    }

    private void exportLibrary() {
        try {
            var path = getEntrance().getProceduralConfigStorage()
                    .exportLibrary(session.materialize());
            message("Exported pattern library to " + path, ChatFormatting.GREEN);
        } catch (IOException | IllegalArgumentException exception) {
            message(
                    "Could not export pattern library: "
                            + exception.getMessage(),
                    ChatFormatting.RED
            );
        }
    }

    private void importLibrary() {
        try {
            var imported = getEntrance().getProceduralConfigStorage()
                    .importLibrary();
            session.replaceLibrary(imported.library());
            selectedBlockIndex = 0;
            message(
                    "Imported pattern library from " + imported.path()
                            + " (press Save to keep them)",
                    ChatFormatting.GREEN
            );
            recreate();
        } catch (IOException | RuntimeException exception) {
            message(
                    "Could not import pattern library: "
                            + exception.getMessage(),
                    ChatFormatting.RED
            );
        }
    }

    private Text enabledMessage() {
        return Text.text(
                compactLayout
                        ? (session.library().enabled()
                                ? "Patterns: on"
                                : "Patterns: off")
                        : (session.library().enabled()
                                ? "Patterns: enabled"
                                : "Patterns: disabled")
        );
    }

    private Text materialSourceMessage() {
        var source = session.selectedPreset().materialSource();
        return Text.text(compactLayout
                ? source.displayName()
                : "Materials: " + source.displayName());
    }

    private void resetPresetList() {
        if (presetList == null) {
            return;
        }
        String query = presetSearch.trim().toLowerCase(Locale.ROOT);
        var values = session.library().presets().stream()
                .filter(preset -> query.isEmpty()
                        || preset.name().toLowerCase(Locale.ROOT)
                                .contains(query)
                        || preset.materialSource().displayName()
                                .toLowerCase(Locale.ROOT)
                                .contains(query)
                        || preset.blocks().stream().anyMatch(entry ->
                                entry.itemId().toLowerCase(Locale.ROOT)
                                        .contains(query)
                        ))
                .toList();
        presetList.reset(values);
        presetList.selectById(session.selectedPresetId());
    }

    private boolean isCustomPalette() {
        return session.selectedPreset().materialSource()
                == PatternMaterialSource.CUSTOM_PALETTE;
    }

    private PatternMaterialSource nextMaterialSource() {
        var values = PatternMaterialSource.values();
        var current = session.selectedPreset().materialSource();
        return values[(current.ordinal() + 1) % values.length];
    }

    private void applyActivePattern(
            dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary library
    ) {
        var player = getEntrance().getClient().getPlayer();
        var transforms = library.activePreset()
                .map(ProceduralPatternPreset::stockTransformers)
                .orElse(List.of());
        getEntrance().getStructureBuilder().setPattern(
                player,
                new Pattern(library.enabled(), transforms)
        );
    }

    private void message(String value, ChatFormatting formatting) {
        getEntrance().getClient().getPlayer().sendMessage(
                Effortless.getSystemMessage(
                        Text.text(value).withStyle(formatting)
                )
        );
    }

    private static int advancedRuleCount(ProceduralAdvancedConfig value) {
        return value.maskLayers().size()
                + value.directionalRules().size()
                + value.spacingRules().size()
                + value.neighborCountRules().size()
                + value.quotaRules().size()
                + value.cleanupRules().size();
    }

    private static void renderWorkbenchPanel(
            Renderer renderer,
            int x,
            int y,
            int width,
            int height
    ) {
        renderer.renderRect(x, y, x + width, y + height, 0xB8121418);
        renderer.renderRect(x, y, x + width, y + 1, 0xFF6D7278);
        renderer.renderRect(x, y, x + 1, y + height, 0xFF4D5156);
        renderer.renderRect(
                x + width - 1,
                y,
                x + width,
                y + height,
                0xFF090A0C
        );
    }

    private static List<Text> labels(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(value -> Text.text(
                        value.name().toLowerCase().replace('_', ' ')
                ))
                .toList();
    }

    private static List<Text> compactBuildModeLabels() {
        return List.of(
                Text.text("off"),
                Text.text("single"),
                Text.text("line"),
                Text.text("wall"),
                Text.text("floor"),
                Text.text("cuboid"),
                Text.text("diag line"),
                Text.text("diag wall"),
                Text.text("slope"),
                Text.text("circle"),
                Text.text("cylinder"),
                Text.text("sphere"),
                Text.text("pyramid"),
                Text.text("cone")
        );
    }

    private static boolean isLongInput(String value) {
        if (value.isEmpty() || value.equals("-")) {
            return true;
        }
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String shortId(String value) {
        int separator = value.indexOf(':');
        return separator < 0 ? value : value.substring(separator + 1);
    }

    private static String transformerTitle(Transformer transformer) {
        return switch (transformer.getType()) {
            case ARRAY -> "Array";
            case MIRROR -> "Mirror";
            case RADIAL -> "Radial";
            case RANDOMIZER -> "Materials";
        };
    }

    private static String transformerSummary(Transformer transformer) {
        return switch (transformer.getType()) {
            case ARRAY -> {
                var value = (ArrayTransformer) transformer;
                yield value.offset().x() + "," + value.offset().y() + ","
                        + value.offset().z() + " x" + value.count();
            }
            case MIRROR -> {
                var value = (MirrorTransformer) transformer;
                yield value.axis().name() + " at "
                        + DECIMAL.apply(value.getPosition(value.axis()));
            }
            case RADIAL -> {
                var value = (RadialTransformer) transformer;
                yield value.axis().name() + " / " + value.slices()
                        + " slices";
            }
            case RANDOMIZER -> "Edited in Materials and Mix";
        };
    }

    private enum InspectorTab {
        MATERIALS("Mat", "Materials", "materials", 0xFF6B8EA4),
        GRADIENT("Grad", "Gradient", "gradient", 0xFFA18450),
        NOISE("Noise", "Noise", "noise", 0xFF84709D),
        RULES("Rules", "Rules", "rules", 0xFFA66F6F),
        MASKS("Masks", "Masks", "masks", 0xFF61978E),
        TRANSFORMS("Geo", "Geometry", "geometry", 0xFF738E70),
        OUTPUT("Out", "Output", "output", 0xFF858B92);

        private final String label;
        private final String title;
        private final String key;
        private final int accent;

        InspectorTab(String label, String title, String key, int accent) {
            this.label = label;
            this.title = title;
            this.key = key;
            this.accent = accent;
        }
    }

    private enum CompactView {
        PALETTE("Mat", "Palette", "materials", 0xFF6B8EA4),
        PREVIEW("View", "Preview", "preview", 0xFF6B959E),
        TUNE("Tune", "Material tuning", "tuning", 0xFF748EAC),
        MIX("Mix", "Layer mixing", "mix", 0xFF7C86A6),
        GRADIENT("Grad", "Gradient", "gradient", 0xFFA18450),
        NOISE("Noise", "Noise", "noise", 0xFF84709D),
        RULES("Rules", "Rules", "rules", 0xFFA66F6F),
        MASKS("Masks", "Masks", "masks", 0xFF61978E),
        TRANSFORMS("Geo", "Geometry", "geometry", 0xFF738E70),
        OUTPUT("Out", "Output", "output", 0xFF858B92);

        private final String label;
        private final String title;
        private final String key;
        private final int accent;

        CompactView(String label, String title, String key, int accent) {
            this.label = label;
            this.title = title;
            this.key = key;
            this.accent = accent;
        }
    }

    @FunctionalInterface
    private interface BlockValueUpdater {
        ProceduralBlockEntry update(
                ProceduralBlockEntry entry,
                double value
        );
    }
}
