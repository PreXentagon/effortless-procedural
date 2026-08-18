package dev.huskuraft.effortless.screen.pattern.procedural;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import dev.huskuraft.effortless.Effortless;
import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;
import dev.huskuraft.effortless.client.pattern.procedural.CoordinateSpace;
import dev.huskuraft.effortless.client.pattern.procedural.GradientCurve;
import dev.huskuraft.effortless.client.pattern.procedural.GradientDistributionMode;
import dev.huskuraft.effortless.client.pattern.procedural.MaxRunLengthConstraint;
import dev.huskuraft.effortless.client.pattern.procedural.NeighborTopology;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralMaterial;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralRuleSet;
import dev.huskuraft.effortless.client.pattern.procedural.SeedMode;
import dev.huskuraft.effortless.client.pattern.procedural.SpatialField;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralPlacementMode;
import dev.huskuraft.effortless.client.pattern.procedural.config.PatternMaterialSource;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralAdvancedConfig;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralBlockEntry;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralCompositionLayer;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralCompositionTemplates;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralNoiseConfig;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.generator.ClientToolSubtype;
import dev.huskuraft.effortless.client.road.SplineSubtype;
import dev.huskuraft.effortless.client.road.SplineMaterialLinks;
import dev.huskuraft.effortless.client.tree.TreeArchetype;
import dev.huskuraft.effortless.client.tree.TreeGenerationConfig;
import dev.huskuraft.effortless.client.tree.TreeVariationSource;
import dev.huskuraft.effortless.client.tree.TreeVariationStrength;
import dev.huskuraft.effortless.building.pattern.Pattern;
import dev.huskuraft.effortless.building.pattern.Transformer;
import dev.huskuraft.effortless.building.pattern.Transformers;
import dev.huskuraft.effortless.building.pattern.array.ArrayTransformer;
import dev.huskuraft.effortless.building.pattern.mirror.MirrorTransformer;
import dev.huskuraft.effortless.building.pattern.raidal.RadialTransformer;
import dev.huskuraft.effortless.building.structure.BuildMode;
import dev.huskuraft.effortless.building.structure.BuildFeature;
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
 * Full-screen client-only Effortless workbench.
 *
 * <p>The left pane owns recipe selection, the center pane owns the active
 * build-tool selector, preview and ordered palette, and the right inspector
 * owns form/distribution/rule settings. All panes edit one
 * {@link ProceduralWorkbenchSession}; only the final Save writes recipe
 * changes to the local TOML configuration.</p>
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
    private InspectorTab inspectorTab = InspectorTab.FORM;
    private CompactView compactView = CompactView.FORM;
    private ProceduralPreviewType previewType = ProceduralPreviewType.WALL;
    private PreviewOrientation previewOrientation =
            PreviewOrientation.defaultFor(previewType);
    private boolean previewSubtypesExpanded;
    private SplineSubtype previewSplineSubtype = SplineSubtype.FLAT_ROAD;
    private TreeArchetype previewTreeArchetype = TreeArchetype.OAK;
    private String presetSearch = "";
    private int selectedBlockIndex;
    private int selectedTransformerIndex;
    private int selectedCompositionIndex;
    private int soloCompositionIndex = -1;

    private ProceduralPresetList presetList;
    private TextRuleList<Transformer> transformerList;
    private TextRuleList<ProceduralCompositionLayer> compositionList;
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
        super(entrance, Text.text("Effortless Workbench"));
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
        previewType = activeBuildTool();
        previewOrientation = PreviewOrientation.defaultFor(previewType);
        syncPreviewSubtypesFromRecipe();
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
            String previewStatus = previewWidget == null
                    ? "idle" : previewWidget.compactStatusText();
            int layerCount = session.selectedPreset().advanced()
                    .compositionLayers().size();
            draftStatusWidget.setMessage(
                    Text.text((session.isDirty()
                                    ? "Unsaved"
                                    : "Saved")
                                    + "  |  " + previewStatus
                                    + "  |  L" + layerCount)
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
                resetSelectedRecipeUi();
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
        if (compositionList != null && compositionList.hasSelected()) {
            int after = compositionList.indexOfSelected();
            if (after != selectedCompositionIndex) {
                selectedCompositionIndex = after;
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
        if (control && keyCode == Keys.KEY_P.getValue()) {
            openCommandPalette();
            return true;
        }
        if (control && keyCode == Keys.KEY_F.getValue()
                && searchField != null) {
            setFocused(searchField);
            searchField.setCursorPosition(searchField.getValue().length());
            searchField.setHighlightPos(0);
            return true;
        }
        if (control && keyCode == Keys.KEY_D.getValue()) {
            duplicateSelectedRecipe();
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
                "Builder workbench has unsaved changes; close again to discard",
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
                        : "CTRL+P COMMANDS / STOCK SERVER OUTPUT")
                        .withStyle(ChatFormatting.GRAY),
                TextWidget.Gravity.END
        ));
    }

    private void addPresetPane() {
        int innerX = leftX + GAP;
        int innerWidth = leftWidth - GAP * 2;
        int y = contentTop + GAP;
        addWidget(new TextWidget(
                getEntrance(),
                innerX,
                y + 3,
                Text.text("RECIPES").withStyle(ChatFormatting.GRAY)
        ));
        y += 15;
        enableButton = addButton(
                innerX,
                y,
                innerWidth,
                enabledMessage(),
                button -> session.setEnabled(!session.library().enabled())
        );

        int searchY = y + BUTTON_HEIGHT + GAP;
        searchField = addWidget(new ReliableEditBox(
                getEntrance(),
                innerX,
                searchY,
                innerWidth,
                BUTTON_HEIGHT,
                Text.text("Search recipes")
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
                    resetSelectedRecipeUi();
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
                    resetSelectedRecipeUi();
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
                    resetSelectedRecipeUi();
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
        y = addPanelTabs(
                innerX, y, innerWidth,
                CompactView.values(),
                () -> compactView,
                value -> compactView = value
        );
        int availableHeight = contentTop + contentHeight - y - GAP;
        switch (compactView) {
            case FORM -> addActiveToolInspector(innerX, y, innerWidth);
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
            case SCENE -> addSceneInspector(innerX, y, innerWidth);
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
                Text.text("Recipe name")
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
                availableHeight - BUTTON_HEIGHT * 4 - GAP * 4
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
        addSpecialMaterialButtons(innerX, y, innerWidth);
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
                this::previewPreset,
                () -> session.library(),
                () -> previewType,
                () -> previewOrientation,
                this::previewStructure,
                this::previewClientSubtype
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
                Text.text("Recipe name")
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
                + BUTTON_HEIGHT + GAP + BUTTON_HEIGHT;
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
                this::previewPreset,
                () -> session.library(),
                () -> previewType,
                () -> previewOrientation,
                this::previewStructure,
                this::previewClientSubtype
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
        addSpecialMaterialButtons(innerX, y, innerWidth);
        y += BUTTON_HEIGHT + GAP;
        fallbackButton = addButton(
                innerX,
                y,
                innerWidth,
                Text.empty(),
                button -> pickFallback()
        );
    }

    private void addSpecialMaterialButtons(int x, int y, int width) {
        int half = (width - GAP) / 2;
        var skip = addButton(
                x,
                y,
                half,
                Text.text("Add Skip"),
                button -> selectedBlockIndex = session.addSpecialBlock(
                        ProceduralMaterial.SKIP_ID
                )
        );
        skip.setActive(isCustomPalette());
        var eraser = addButton(
                x + half + GAP,
                y,
                width - half - GAP,
                Text.text("Add Eraser"),
                button -> selectedBlockIndex = session.addSpecialBlock(
                        ProceduralMaterial.ERASER_ID
                )
        );
        eraser.setActive(isCustomPalette());
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
                () -> previewType,
                value -> {
                    if (previewType == value) {
                        if (!value.hasSubtypes()) {
                            previewSubtypesExpanded = false;
                            recreate();
                            return;
                        }
                        previewSubtypesExpanded = !previewSubtypesExpanded;
                        recreate();
                        return;
                    }
                    previewType = value;
                    previewOrientation = PreviewOrientation.defaultFor(value);
                    previewSubtypesExpanded = value.hasSubtypes();
                    selectBuildTool(value);
                },
                () -> previewOrientation,
                value -> {
                    previewOrientation = value;
                    recreate();
                },
                this::previewStructure,
                this::setPreviewFeature,
                this::previewClientSubtype,
                this::setPreviewClientSubtype,
                () -> previewSubtypesExpanded
        ));
        return height;
    }

    private ClientToolSubtype previewClientSubtype() {
        return previewType.isTree()
                ? previewTreeArchetype
                : previewSplineSubtype;
    }

    private void setPreviewClientSubtype(ClientToolSubtype value) {
        if (value instanceof TreeArchetype archetype) {
            updateTreeGeneration(current -> current.withArchetype(archetype));
            getEntrance().getClientManager().getTreeEditor().start(
                    session.selectedPreset().advanced().treeGeneration()
            );
        } else if (value instanceof SplineSubtype subtype) {
            updateRoadProfile(current -> current.withSubtype(subtype));
            getEntrance().getClientManager().getRoadEditor().start(
                    session.selectedPreset().advanced().roadProfile()
            );
        }
        recreate();
    }

    private Structure previewStructure() {
        if (previewType.isClientOnly()) {
            return BuildMode.FLOOR.getDefaultStructure();
        }
        var previewMode = previewType.stockMode();
        var configured = getEntrance().getConfigStorage()
                .getStructure(previewMode);
        var result = configured == null
                ? previewMode.getDefaultStructure()
                : configured;
        return result;
    }

    private void setPreviewFeature(BuildFeature feature) {
        if (previewType.isClientOnly()) {
            return;
        }
        var player = getEntrance().getClient().getPlayer();
        var current = getEntrance().getStructureBuilder()
                .getContext(player).structure();
        var changed = current.withFeature(feature);
        if (getEntrance().getStructureBuilder().setStructure(
                player, changed
        )) {
            getEntrance().getConfigStorage().setStructure(changed);
        }
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
        y = addPanelTabs(
                innerX, y, innerWidth,
                InspectorTab.values(),
                () -> inspectorTab,
                value -> inspectorTab = value
        );

        switch (inspectorTab) {
            case FORM -> addActiveToolInspector(innerX, y, innerWidth);
            case MATERIALS -> addMaterialsInspector(innerX, y, innerWidth);
            case GRADIENT -> addGradientInspector(innerX, y, innerWidth);
            case NOISE -> addNoiseInspector(innerX, y, innerWidth);
            case RULES -> addRulesInspector(innerX, y, innerWidth);
            case MASKS -> addMasksInspector(innerX, y, innerWidth);
            case SCENE -> addSceneInspector(innerX, y, innerWidth);
            case TRANSFORMS -> addTransformsInspector(
                    innerX,
                    y,
                    innerWidth
            );
            case OUTPUT -> addOutputInspector(innerX, y, innerWidth);
        }
    }

    private ProceduralPreviewType activeBuildTool() {
        var manager = getEntrance().getClientManager();
        if (manager.getRoadEditor().isActive()) {
            return ProceduralPreviewType.ROAD;
        }
        if (manager.getTreeEditor().isActive()) {
            return ProceduralPreviewType.TREE;
        }
        var player = getEntrance().getClient().getPlayer();
        var mode = getEntrance().getStructureBuilder()
                .getContext(player).buildMode();
        return ProceduralPreviewType.ALL.stream()
                .filter(value -> !value.isClientOnly())
                .filter(value -> value.stockMode() == mode)
                .findFirst()
                .orElse(ProceduralPreviewType.SINGLE);
    }

    private void selectBuildTool(ProceduralPreviewType value) {
        previewType = value;
        session.setActiveToolId(value.persistentId());
        var player = getEntrance().getClient().getPlayer();
        var manager = getEntrance().getClientManager();
        manager.rememberActiveBuildTool(value.persistentId());
        if (value.isRoad()) {
            manager.getTreeEditor().cancel();
            manager.getRoadEditor().start(
                    session.selectedPreset().advanced().roadProfile()
            );
        } else if (value.isTree()) {
            manager.getRoadEditor().cancel();
            manager.getTreeEditor().start(
                    session.selectedPreset().advanced().treeGeneration()
            );
        } else {
            manager.getRoadEditor().cancel();
            manager.getTreeEditor().cancel();
            var structure = getEntrance().getConfigStorage()
                    .getStructure(value.stockMode());
            if (getEntrance().getStructureBuilder().setStructure(
                    player, structure
            )) {
                getEntrance().getConfigStorage().setStructure(structure);
            }
        }
        recreate();
    }

    private void addActiveToolInspector(int x, int y, int width) {
        var active = activeBuildTool();
        addWidget(new TextWidget(
                getEntrance(),
                x,
                y + 4,
                Text.text("FORM · ")
                        .append(active.displayName())
                        .withStyle(ChatFormatting.GOLD)
        ));
        y += 18;
        if (active.isRoad()) {
            addRoadInspector(x, y, width);
            return;
        }
        if (active.isTree()) {
            addTreeInspector(x, y, width);
            return;
        }
        addWidget(new TextWidget(
                getEntrance(), x, y + 4,
                Text.text("Choose the type and its variants in the toolbar "
                                + "above the live preview.")
                        .withStyle(ChatFormatting.GRAY)
        ));
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
        options.addVectorEntry(
                Text.text("Gradient center"),
                () -> {
                    var value = session.selectedPreset().advanced()
                            .gradientField();
                    return new Vector3d(
                            value.centerX(), value.centerY(), value.centerZ()
                    );
                },
                -4.0,
                4.0,
                0.05,
                false,
                value -> updateGradientField(current -> current.withCenter(
                        value.x(), value.y(), value.z()
                ))
        );
    }

    private void addGradientScaleOptions(
            ProceduralSettingOptionsList options
    ) {
        options.addVectorEntry(
                Text.text("Gradient scale"),
                () -> {
                    var value = session.selectedPreset().advanced()
                            .gradientField();
                    return new Vector3d(
                            value.scaleX(), value.scaleY(), value.scaleZ()
                    );
                },
                0.000001,
                64.0,
                0.05,
                true,
                value -> updateGradientField(current -> current.withScale(
                        value.x(), value.y(), value.z()
                ))
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
        addNoiseScaleOptions(options);
        addNoiseOffsetOptions(options);
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
            ProceduralSettingOptionsList options
    ) {
        options.addVectorEntry(
                Text.text("Noise scale"),
                () -> {
                    var value = session.selectedPreset().advanced()
                            .noiseConfig();
                    return new Vector3d(
                            value.scaleX(), value.scaleY(), value.scaleZ()
                    );
                },
                0.000001,
                64.0,
                0.05,
                true,
                value -> updateNoiseConfig(current -> current.withScale(
                        value.x(), value.y(), value.z()
                ))
        );
    }

    private void addNoiseOffsetOptions(
            ProceduralSettingOptionsList options
    ) {
        options.addVectorEntry(
                Text.text("Noise offset"),
                () -> {
                    var value = session.selectedPreset().advanced()
                            .noiseConfig();
                    return new Vector3d(
                            value.offsetX(), value.offsetY(), value.offsetZ()
                    );
                },
                -1_000_000.0,
                1_000_000.0,
                0.1,
                false,
                value -> updateNoiseConfig(current -> current.withOffset(
                        value.x(), value.y(), value.z()
                ))
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
        options.addSelectorEntry(
                Text.translate(
                        "effortless.procedural.structural_placement"
                ),
                Text.translate(
                        "effortless.procedural.tooltip.structural_placement"
                ),
                List.of(
                        Text.translate(
                                "effortless.procedural.structural.random"
                        ),
                        Text.translate(
                                "effortless.procedural.structural.smart"
                        ),
                        Text.translate(
                                "effortless.procedural.structural.rule_driven"
                        )
                ),
                List.of(StructuralPlacementMode.values()),
                preset.advanced().structuralPlacementMode(),
                value -> session.replaceSelected(current ->
                        current.withAdvanced(
                                current.advanced()
                                        .withStructuralPlacementMode(value)
                        )
                )
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
    }

    /** Inline ordered scene graph; only the scalable recipe chooser is modal. */
    private void addSceneInspector(int x, int y, int width) {
        var layers = compositionLayers();
        selectedCompositionIndex = layers.isEmpty()
                ? 0
                : Math.clamp(selectedCompositionIndex, 0, layers.size() - 1);
        int small = Math.max(24, (width - GAP * 4) / 5);
        addButton(x, y, small, Text.text("+ Layer"), button -> {
            var changed = new ArrayList<>(compositionLayers());
            changed.add(ProceduralCompositionLayer.defaultLayer());
            selectedCompositionIndex = changed.size() - 1;
            setCompositionLayers(changed);
            recreate();
        });
        addButton(x + small + GAP, y, small, Text.text("Copy"), button -> {
            var changed = new ArrayList<>(compositionLayers());
            if (!changed.isEmpty()) {
                var source = changed.get(selectedCompositionIndex);
                int insertionIndex = selectedCompositionIndex + 1;
                changed.add(
                        insertionIndex,
                        source.withName(source.name() + " copy")
                );
                if (soloCompositionIndex >= insertionIndex) {
                    soloCompositionIndex++;
                }
                selectedCompositionIndex++;
                setCompositionLayers(changed);
                recreate();
            }
        });
        addButton(x + (small + GAP) * 2, y, small, Text.text("Up"), button -> {
            moveCompositionLayer(-1);
            recreate();
        });
        addButton(x + (small + GAP) * 3, y, small, Text.text("Down"), button -> {
            moveCompositionLayer(1);
            recreate();
        });
        addButton(
                x + (small + GAP) * 4, y,
                width - (small + GAP) * 4,
                Text.text("Delete"), button -> {
                    var changed = new ArrayList<>(compositionLayers());
                    if (!changed.isEmpty()) {
                        int removedIndex = selectedCompositionIndex;
                        changed.remove(removedIndex);
                        if (soloCompositionIndex == removedIndex) {
                            soloCompositionIndex = -1;
                        } else if (soloCompositionIndex > removedIndex) {
                            soloCompositionIndex--;
                        }
                        selectedCompositionIndex = Math.max(
                                0, removedIndex - 1
                        );
                        setCompositionLayers(changed);
                        recreate();
                    }
                }
        );
        y += BUTTON_HEIGHT + GAP;

        if (!layers.isEmpty()) {
            var selectedLayer = layers.get(selectedCompositionIndex);
            int half = (width - GAP) / 2;
            addButton(
                    x, y, half,
                    Text.text(selectedLayer.enabled() ? "Mute layer" : "Unmute layer"),
                    button -> {
                        updateCompositionLayer(current -> current.withEnabled(
                                !current.enabled()
                        ));
                        recreate();
                    }
            );
            addButton(
                    x + half + GAP, y, width - half - GAP,
                    Text.text(soloCompositionIndex == selectedCompositionIndex
                            ? "Clear solo" : "Solo layer"),
                    button -> {
                        soloCompositionIndex = soloCompositionIndex
                                == selectedCompositionIndex
                                ? -1 : selectedCompositionIndex;
                        recreate();
                    }
            );
            y += BUTTON_HEIGHT + GAP;
        }

        int templateWidth = Math.max(36, (width - GAP * 2) / 3);
        addButton(
                x, y, templateWidth, Text.text("+ Tunnel"),
                button -> {
                    addTunnelTemplate();
                    recreate();
                }
        );
        addButton(
                x + templateWidth + GAP, y,
                templateWidth,
                Text.text("+ Trees"), button -> {
                    addRoadsideTreeTemplate();
                    recreate();
                }
        );
        addButton(
                x + (templateWidth + GAP) * 2, y,
                width - (templateWidth + GAP) * 2,
                Text.text("+ Damage"), button -> {
                    addRoadDamageTemplate();
                    recreate();
                }
        );
        y += BUTTON_HEIGHT + GAP;

        layers = compositionLayers();
        if (layers.isEmpty()) {
            addWidget(new TextWidget(
                    getEntrance(), x, y + 4,
                    Text.text("No scene layers. Add geometry or a template.")
                            .withStyle(ChatFormatting.GRAY)
            ));
            return;
        }
        selectedCompositionIndex = Math.clamp(
                selectedCompositionIndex, 0, layers.size() - 1
        );
        final var layerChoices = layers;
        var layer = layerChoices.get(selectedCompositionIndex);
        int layerListHeight = Math.min(
                92,
                Math.max(56, (contentTop + contentHeight - y) / 5)
        );
        compositionList = addWidget(new TextRuleList<>(
                getEntrance(), x, y, width - 8, layerListHeight,
                ProceduralCompositionLayer::name,
                value -> value.operation().name().toLowerCase()
                        + " · " + value.generator().name().toLowerCase()
                        + (value.enabled() ? "" : " · disabled")
        ));
        compositionList.setAlwaysShowScrollbar(true);
        compositionList.reset(layerChoices);
        compositionList.selectByIndex(selectedCompositionIndex);
        y += layerListHeight + GAP;
        int nameLabelWidth = Math.min(42, width / 4);
        addWidget(new TextWidget(
                getEntrance(), x, y + 6,
                Text.text("Name").withStyle(ChatFormatting.GRAY)
        ));
        var layerName = addWidget(new ReliableEditBox(
                getEntrance(), x + nameLabelWidth, y,
                width - nameLabelWidth, BUTTON_HEIGHT,
                Text.text("Scene layer name")
        ));
        layerName.setMaxLength(80);
        layerName.setValue(layer.name());
        layerName.setChangeListener(value ->
                updateCompositionLayer(current -> current.withName(value))
        );
        y += BUTTON_HEIGHT + GAP;
        var options = addOptions(
                x, y, width,
                contentTop + contentHeight - y - GAP
        );
        options.addSwitchEntry(
                Text.text("Enabled"),
                Text.translate("effortless.tooltip.composition.enabled"),
                layer.enabled(),
                value -> updateCompositionLayer(current ->
                        current.withEnabled(value))
        );
        options.addSelectorEntry(
                Text.text("Boolean operation"),
                Text.translate("effortless.tooltip.composition.operation"),
                labels(ProceduralCompositionLayer.Operation.values()),
                List.of(ProceduralCompositionLayer.Operation.values()),
                layer.operation(),
                value -> updateCompositionLayer(current ->
                        current.withOperation(value))
        );
        options.addSelectorEntry(
                Text.text("Generator"),
                Text.translate("effortless.tooltip.composition.generator"),
                labels(ProceduralCompositionLayer.Generator.values()),
                List.of(ProceduralCompositionLayer.Generator.values()),
                layer.generator(),
                value -> updateCompositionLayer(current ->
                        current.withGenerator(value))
        );
        options.addSelectorEntry(
                Text.text("Anchor"),
                Text.translate("effortless.tooltip.composition.anchor"),
                labels(ProceduralCompositionLayer.Anchor.values()),
                List.of(ProceduralCompositionLayer.Anchor.values()),
                layer.anchor(),
                value -> updateCompositionLayer(current ->
                        current.withAnchor(value))
        );
        options.addSelectorEntry(
                Text.text("Shape"),
                Text.translate("effortless.tooltip.composition.shape"),
                labels(ProceduralCompositionLayer.Primitive.values()),
                List.of(ProceduralCompositionLayer.Primitive.values()),
                layer.primitive(),
                value -> updateCompositionLayer(current ->
                        current.withPrimitive(value))
        );
        options.addTab(
                Text.text("Material / tree recipe"),
                Text.translate("effortless.tooltip.composition.recipe"),
                layer.presetId(),
                value -> updateCompositionLayer(current ->
                        current.withPresetId(value)),
                (entry, value) -> {
                    entry.getButton().setMessage(
                            Text.text(compositionRecipeName(value))
                    );
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessRecipePickerScreen(
                                    getEntrance(), "scene layer",
                                    session.library(),
                                    session.selectedPresetId(), value,
                                    entry::setItem
                            ).attach()
                    );
                }
        );
        options.addSection(Text.text("LOCAL TRANSFORM"));
        addCompositionNumber(
                options, "Offset X (sideways)",
                "effortless.tooltip.composition.offset",
                layer.offsetX(), -4096.0, 4096.0, 0.5,
                value -> updateCompositionLayer(current ->
                        current.withOffsetX(value))
        );
        addCompositionNumber(
                options, "Offset Y (vertical)",
                "effortless.tooltip.composition.offset",
                layer.offsetY(), -4096.0, 4096.0, 0.5,
                value -> updateCompositionLayer(current ->
                        current.withOffsetY(value))
        );
        addCompositionNumber(
                options, "Offset Z (forward)",
                "effortless.tooltip.composition.offset",
                layer.offsetZ(), -4096.0, 4096.0, 0.5,
                value -> updateCompositionLayer(current ->
                        current.withOffsetZ(value))
        );
        addCompositionInteger(
                options, "Size X", "effortless.tooltip.composition.size",
                layer.sizeX(), 1, 4096,
                value -> updateCompositionLayer(current ->
                        current.withSizeX(value))
        );
        addCompositionInteger(
                options, "Size Y", "effortless.tooltip.composition.size",
                layer.sizeY(), 1, 4096,
                value -> updateCompositionLayer(current ->
                        current.withSizeY(value))
        );
        addCompositionInteger(
                options, "Size Z", "effortless.tooltip.composition.size",
                layer.sizeZ(), 1, 4096,
                value -> updateCompositionLayer(current ->
                        current.withSizeZ(value))
        );
        addCompositionNumber(
                options, "Rotation",
                "effortless.tooltip.composition.rotation",
                layer.rotationDegrees(),
                -360.0, 360.0, 5.0,
                value -> updateCompositionLayer(current ->
                        current.withRotation(value))
        );
        options.addSwitchEntry(
                Text.text("Hollow shell"),
                Text.translate("effortless.tooltip.composition.hollow"),
                layer.hollow(),
                value -> updateCompositionLayer(current ->
                        current.withHollow(value))
        );
        addCompositionInteger(
                options, "Shell thickness",
                "effortless.tooltip.composition.shell",
                layer.shellThickness(),
                1, 4096,
                value -> updateCompositionLayer(current ->
                        current.withShellThickness(value))
        );
        options.addSection(Text.text("PATH REPEAT"));
        addCompositionNumber(
                options, "Spacing",
                "effortless.tooltip.composition.spacing",
                layer.spacing(),
                0.5, 4096.0, 0.5,
                value -> updateCompositionLayer(current ->
                        current.withSpacing(value))
        );
        addCompositionInteger(
                options, "Maximum instances",
                "effortless.tooltip.composition.instances",
                layer.maximumInstances(),
                1, 4096,
                value -> updateCompositionLayer(current ->
                        current.withMaximumInstances(value))
        );
        options.addSwitchEntry(
                Text.text("Safe variation"),
                Text.translate("effortless.tooltip.composition.variation"),
                layer.safeVariation(),
                value -> updateCompositionLayer(current ->
                        current.withSafeVariation(value))
        );
    }

    private void addCompositionNumber(
            ProceduralSettingOptionsList options,
            String title,
            String tooltipKey,
            double value,
            double minimum,
            double maximum,
            double step,
            java.util.function.Consumer<Double> consumer
    ) {
        options.addNumberEntry(
                Text.text(title),
                Text.translate(tooltipKey),
                value, minimum, maximum, step, consumer
        );
    }

    private void addCompositionInteger(
            ProceduralSettingOptionsList options,
            String title,
            String tooltipKey,
            int value,
            int minimum,
            int maximum,
            java.util.function.Consumer<Integer> consumer
    ) {
        options.addIntegerEntry(
                Text.text(title),
                Text.translate(tooltipKey),
                value, minimum, maximum, consumer
        );
    }

    private List<ProceduralCompositionLayer> compositionLayers() {
        return session.selectedPreset().advanced().compositionLayers();
    }

    private void setCompositionLayers(
            List<ProceduralCompositionLayer> value
    ) {
        session.replaceSelected(current -> current.withAdvanced(
                current.advanced().withCompositionLayers(value)
        ));
    }

    private void updateCompositionLayer(
            java.util.function.UnaryOperator<ProceduralCompositionLayer>
                    operation
    ) {
        var changed = new ArrayList<>(compositionLayers());
        if (changed.isEmpty()) {
            return;
        }
        int index = Math.clamp(
                selectedCompositionIndex, 0, changed.size() - 1
        );
        changed.set(index, operation.apply(changed.get(index)));
        setCompositionLayers(changed);
    }

    private void moveCompositionLayer(int direction) {
        var changed = new ArrayList<>(compositionLayers());
        if (changed.size() < 2) {
            return;
        }
        int from = Math.clamp(
                selectedCompositionIndex, 0, changed.size() - 1
        );
        int to = Math.clamp(from + direction, 0, changed.size() - 1);
        if (from == to) {
            return;
        }
        var value = changed.remove(from);
        changed.add(to, value);
        if (soloCompositionIndex == from) {
            soloCompositionIndex = to;
        } else if (soloCompositionIndex == to) {
            soloCompositionIndex = from;
        }
        selectedCompositionIndex = to;
        setCompositionLayers(changed);
    }

    private void addTunnelTemplate() {
        var changed = new ArrayList<>(compositionLayers());
        changed.addAll(ProceduralCompositionTemplates.tunnelShell());
        selectedCompositionIndex = changed.size() - 1;
        setCompositionLayers(changed);
    }

    private void addRoadsideTreeTemplate() {
        var changed = new ArrayList<>(compositionLayers());
        changed.addAll(ProceduralCompositionTemplates.roadsideTrees());
        selectedCompositionIndex = changed.size() - 1;
        setCompositionLayers(changed);
    }

    private void addRoadDamageTemplate() {
        var changed = new ArrayList<>(compositionLayers());
        changed.addAll(ProceduralCompositionTemplates.roadDamage());
        selectedCompositionIndex = changed.size() - 1;
        setCompositionLayers(changed);
    }

    private String compositionRecipeName(String presetId) {
        if (presetId == null || presetId.isBlank()) {
            return "Current recipe";
        }
        try {
            var id = UUID.fromString(presetId);
            return session.library().presets().stream()
                    .filter(value -> value.id().equals(id))
                    .map(ProceduralPatternPreset::name)
                    .findFirst()
                    .orElse("Missing recipe");
        } catch (IllegalArgumentException exception) {
            return "Invalid recipe";
        }
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
                Text.text("Import active stock recipe"),
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
        options.addSection(Text.text("RECIPE INHERITANCE"));
        options.addTab(
                Text.text("Parent recipe"),
                Text.text("Reuse another recipe without copying it."),
                preset.advanced().parentPresetId(),
                value -> session.replaceSelected(current ->
                        current.withAdvanced(current.advanced().withParent(
                                value,
                                current.advanced().inheritBlocks(),
                                current.advanced().inheritRules()
                        ))
                ),
                (entry, value) -> {
                    entry.getButton().setMessage(
                            Text.text(compositionRecipeName(value))
                    );
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessRecipePickerScreen(
                                    getEntrance(), "parent",
                                    session.library(),
                                    session.selectedPresetId(), value,
                                    entry::setItem
                            ).attach()
                    );
                }
        );
        options.addSwitchEntry(
                Text.text("Inherit parent blocks"), Text.empty(),
                preset.advanced().inheritBlocks(),
                value -> session.replaceSelected(current ->
                        current.withAdvanced(current.advanced().withParent(
                                current.advanced().parentPresetId(),
                                value,
                                current.advanced().inheritRules()
                        ))
                )
        );
        options.addSwitchEntry(
                Text.text("Inherit parent rules"), Text.empty(),
                preset.advanced().inheritRules(),
                value -> session.replaceSelected(current ->
                        current.withAdvanced(current.advanced().withParent(
                                current.advanced().parentPresetId(),
                                current.advanced().inheritBlocks(),
                                value
                        ))
                )
        );
        options.addSection(Text.text("POST PROCESS"));
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
    }

    private void addRoadInspector(int x, int y, int width) {
        var profile = session.selectedPreset().advanced().roadProfile();
        addWidget(new TextWidget(
                getEntrance(),
                x,
                y + 4,
                Text.text("SPLINE AUTHORING")
                        .withStyle(ChatFormatting.GOLD)
        ));
        y += 18;
        addWidget(new TextWidget(
                getEntrance(),
                x,
                y + 2,
                Text.translate("effortless.procedural.road.alt_hint")
                        .withStyle(ChatFormatting.GRAY)
        ));
        y += 18;

        var options = addOptions(
                x,
                y,
                width,
                contentTop + contentHeight - y - GAP
        );
        options.addSection(Text.text("CROSS-SECTION"));
        options.addIntegerEntry(
                Text.text("Road width"),
                Text.empty(),
                profile.surfaceWidth(),
                1,
                dev.huskuraft.effortless.client.road.RoadProfile
                        .MAX_SURFACE_WIDTH,
                value -> updateRoadProfile(current ->
                        current.withSurfaceWidth(value))
        );
        options.addIntegerEntry(
                Text.text("Road thickness"),
                Text.empty(),
                profile.thickness(),
                1,
                dev.huskuraft.effortless.client.road.RoadProfile
                        .MAX_THICKNESS,
                value -> updateRoadProfile(current ->
                        current.withThickness(value))
        );
        options.addIntegerEntry(
                Text.text("Shoulder width"),
                Text.empty(),
                profile.shoulderWidth(),
                0,
                dev.huskuraft.effortless.client.road.RoadProfile
                        .MAX_SHOULDER_WIDTH,
                value -> updateRoadProfile(current ->
                        current.withShoulderWidth(value))
        );
        options.addSection(Text.text("CURVE"));
        options.addRangeEntry(
                Text.text("Curve tension"),
                Text.empty(),
                profile.tension(),
                0.0,
                1.0,
                0.05,
                value -> updateRoadProfile(current ->
                        current.withTension(value))
        );
        options.addRangeEntry(
                Text.text("Curve precision"),
                Text.empty(),
                profile.sampleSpacing(),
                dev.huskuraft.effortless.client.road.RoadProfile
                        .MIN_SAMPLE_SPACING,
                dev.huskuraft.effortless.client.road.RoadProfile
                        .MAX_SAMPLE_SPACING,
                0.05,
                value -> updateRoadProfile(current ->
                        current.withSampleSpacing(value))
        );
        options.addSection(Text.text("PATTERN COORDINATES"));
        options.addSwitchEntry(
                Text.text("Path / lateral / depth"),
                Text.empty(),
                true,
                ignored -> {
                }
        ).setActive(false);
        addSplineRecipeOptions(options, profile);
    }

    private void addSplineRecipeOptions(
            ProceduralSettingOptionsList options,
            dev.huskuraft.effortless.client.road.RoadProfile profile
    ) {
        options.addSection(Text.text("MATERIALS"));
        options.addTab(
                Text.text("Spline material recipes"),
                Text.empty(),
                profile,
                value -> updateRoadProfile(ignored -> value),
                (entry, value) -> {
                    var links = value.materialLinks();
                    long linked = List.of(
                            links.surfaceRecipeId(),
                            links.shoulderRecipeId(),
                            links.foundationRecipeId(),
                            links.curbRecipeId(),
                            links.markingRecipeId(),
                            links.damageRecipeId()
                    ).stream().filter(id -> !id.isBlank()).count();
                    entry.getButton().setMessage(Text.text(
                            linked + " linked · 6 roles"
                    ));
                    entry.getButton().setOnPressListener(button -> {
                        var roles = new LinkedHashMap<String, String>();
                        roles.put("Surface", links.surfaceRecipeId());
                        roles.put("Shoulders", links.shoulderRecipeId());
                        roles.put("Foundation", links.foundationRecipeId());
                        roles.put("Curbs", links.curbRecipeId());
                        roles.put("Markings", links.markingRecipeId());
                        roles.put("Damage", links.damageRecipeId());
                        new EffortlessGeneratorRecipesScreen(
                                getEntrance(),
                                "Spline material recipes",
                                session.library(),
                                session.selectedPresetId(),
                                roles,
                                changed -> entry.setItem(
                                        value.withMaterialLinks(
                                                new SplineMaterialLinks(
                                                        changed.getOrDefault(
                                                                "Surface", ""
                                                        ),
                                                        changed.getOrDefault(
                                                                "Shoulders", ""
                                                        ),
                                                        changed.getOrDefault(
                                                                "Foundation", ""
                                                        ),
                                                        changed.getOrDefault(
                                                                "Curbs", ""
                                                        ),
                                                        changed.getOrDefault(
                                                                "Markings", ""
                                                        ),
                                                        changed.getOrDefault(
                                                                "Damage", ""
                                                        )
                                                )
                                        )
                                )
                        ).attach();
                    });
                }
        );
        options.addTab(
                Text.translate(
                        "effortless.procedural.spline.geometry.open"
                ),
                Text.translate(
                        "effortless.procedural.tooltip.spline.geometry.open"
                ),
                profile,
                value -> updateRoadProfile(ignored -> value),
                (entry, value) -> {
                    entry.getButton().setMessage(Text.text(
                            value.crossSectionBands().size() + " bands · "
                                    + (value.cutout().enabled()
                                            ? "cutouts on" : "cutouts off")
                    ));
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessSplineGeometryScreen(
                                    getEntrance(), entry::setItem, value,
                                    session.library(),
                                    session.selectedPresetId()
                            ).attach()
                    );
                }
        );
    }

    private void updateRoadProfile(
            java.util.function.UnaryOperator<
                    dev.huskuraft.effortless.client.road.RoadProfile
            > updater
    ) {
        session.replaceSelected(current -> {
            var updated = updater.apply(current.advanced().roadProfile());
            previewSplineSubtype = updated.subtype();
            return current.withAdvanced(
                    current.advanced().withRoadProfile(updated)
            );
        });
    }

    private void addTreeInspector(int x, int y, int width) {
        var tree = session.selectedPreset().advanced().treeGeneration();
        addWidget(new TextWidget(
                getEntrance(),
                x,
                y + 4,
                Text.translate("effortless.procedural.tree.title")
                        .withStyle(ChatFormatting.GOLD)
        ));
        y += 18;
        addWidget(new TextWidget(
                getEntrance(),
                x,
                y + 2,
                Text.translate("effortless.procedural.tree.alt_hint")
                        .withStyle(ChatFormatting.GRAY)
        ));
        y += 18;

        var options = addOptions(
                x,
                y,
                width,
                contentTop + contentHeight - y - GAP
        );
        options.addSection(Text.translate(
                "effortless.procedural.tree.section.variation"
        ));
        options.addSelectorEntry(
                Text.translate("effortless.procedural.tree.variation_strength"),
                Text.empty(),
                labels(TreeVariationStrength.values()),
                List.of(TreeVariationStrength.values()),
                tree.variationStrength(),
                value -> updateTreeGeneration(current ->
                        current.withVariationStrength(value))
        );
        options.addSelectorEntry(
                Text.translate("effortless.procedural.tree.variation_source"),
                Text.empty(),
                labels(TreeVariationSource.values()),
                List.of(TreeVariationSource.values()),
                tree.variationSource(),
                value -> updateTreeGeneration(current ->
                        current.withVariationSource(value))
        );
        options.addSwitchEntry(
                Text.translate("effortless.procedural.tree.style_lock"),
                Text.empty(),
                tree.styleLock(),
                value -> updateTreeGeneration(current ->
                        current.withStyleLock(value))
        );
        options.addIntegerEntry(
                Text.translate("effortless.procedural.tree.variant"),
                Text.empty(),
                tree.variant(),
                0,
                Integer.MAX_VALUE,
                value -> updateTreeGeneration(current ->
                        current.withVariant(value))
        );

        options.addSection(Text.translate(
                "effortless.procedural.tree.section.form"
        ));
        options.addIntegerEntry(
                Text.translate("effortless.procedural.tree.minimum_height"),
                Text.empty(), tree.minimumHeight(), 3,
                TreeGenerationConfig.MAX_HEIGHT,
                value -> updateTreeGeneration(current ->
                        current.withHeightRange(
                                value,
                                Math.max(value, current.maximumHeight())
                        ))
        );
        options.addIntegerEntry(
                Text.translate("effortless.procedural.tree.maximum_height"),
                Text.empty(), tree.maximumHeight(), 3,
                TreeGenerationConfig.MAX_HEIGHT,
                value -> updateTreeGeneration(current ->
                        current.withHeightRange(
                                Math.min(current.minimumHeight(), value),
                                value
                        ))
        );
        options.addIntegerEntry(
                Text.translate("effortless.procedural.tree.minimum_branches"),
                Text.empty(), tree.minimumBranches(), 0,
                TreeGenerationConfig.MAX_BRANCHES,
                value -> updateTreeGeneration(current ->
                        current.withBranchRange(
                                value,
                                Math.max(value, current.maximumBranches())
                        ))
        );
        options.addIntegerEntry(
                Text.translate("effortless.procedural.tree.maximum_branches"),
                Text.empty(), tree.maximumBranches(), 0,
                TreeGenerationConfig.MAX_BRANCHES,
                value -> updateTreeGeneration(current ->
                        current.withBranchRange(
                                Math.min(current.minimumBranches(), value),
                                value
                        ))
        );

        options.addSection(Text.translate(
                "effortless.procedural.tree.section.trunk_branches"
        ));
        options.addIntegerEntry(
                Text.translate("effortless.procedural.tree.base_radius"),
                Text.empty(), tree.baseRadius(), 1,
                TreeGenerationConfig.MAX_RADIUS,
                value -> updateTreeGeneration(current -> current.withRadii(
                        value,
                        Math.min(value, current.tipRadius()),
                        current.crownRadius()
                ))
        );
        options.addIntegerEntry(
                Text.translate("effortless.procedural.tree.tip_radius"),
                Text.empty(), tree.tipRadius(), 1,
                TreeGenerationConfig.MAX_RADIUS,
                value -> updateTreeGeneration(current -> current.withRadii(
                        current.baseRadius(),
                        Math.min(value, current.baseRadius()),
                        current.crownRadius()
                ))
        );
        options.addRangeEntry(
                Text.translate("effortless.procedural.tree.trunk_bend"),
                Text.empty(), tree.trunkBend(), 0.0, 2.0, 0.02,
                value -> updateTreeGeneration(current -> current.withShape(
                        value, current.branchLengthScale(),
                        current.branchDroop(), current.crownHeight()
                ))
        );
        options.addRangeEntry(
                Text.translate("effortless.procedural.tree.branch_length"),
                Text.empty(), tree.branchLengthScale(), 0.1, 4.0, 0.05,
                value -> updateTreeGeneration(current -> current.withShape(
                        current.trunkBend(), value,
                        current.branchDroop(), current.crownHeight()
                ))
        );
        options.addRangeEntry(
                Text.translate("effortless.procedural.tree.branch_droop"),
                Text.empty(), tree.branchDroop(), -1.0, 2.0, 0.05,
                value -> updateTreeGeneration(current -> current.withShape(
                        current.trunkBend(), current.branchLengthScale(),
                        value, current.crownHeight()
                ))
        );

        options.addSection(Text.translate(
                "effortless.procedural.tree.section.foliage"
        ));
        options.addIntegerEntry(
                Text.translate("effortless.procedural.tree.crown_radius"),
                Text.empty(), tree.crownRadius(), 1,
                TreeGenerationConfig.MAX_RADIUS,
                value -> updateTreeGeneration(current -> current.withRadii(
                        current.baseRadius(), current.tipRadius(), value
                ))
        );
        options.addIntegerEntry(
                Text.translate("effortless.procedural.tree.crown_height"),
                Text.empty(), tree.crownHeight(), 1,
                TreeGenerationConfig.MAX_HEIGHT,
                value -> updateTreeGeneration(current -> current.withShape(
                        current.trunkBend(), current.branchLengthScale(),
                        current.branchDroop(), value
                ))
        );
        options.addRangeEntry(
                Text.translate("effortless.procedural.tree.foliage_density"),
                Text.empty(), tree.foliageDensity(), 0.0, 1.0, 0.01,
                value -> updateTreeGeneration(current -> current.withFoliage(
                        value, current.foliageNoiseFrequency()
                ))
        );
        options.addRangeEntry(
                Text.translate("effortless.procedural.tree.foliage_noise"),
                Text.empty(), tree.foliageNoiseFrequency(), 0.01, 2.0, 0.01,
                value -> updateTreeGeneration(current -> current.withFoliage(
                        current.foliageDensity(), value
                ))
        );

        options.addSection(Text.translate(
                "effortless.procedural.tree.section.roots"
        ));
        options.addIntegerEntry(
                Text.translate("effortless.procedural.tree.root_count"),
                Text.empty(), tree.rootCount(), 0,
                TreeGenerationConfig.MAX_ROOTS,
                value -> updateTreeGeneration(current -> current.withRoots(
                        value, current.rootLengthScale()
                ))
        );
        options.addRangeEntry(
                Text.translate("effortless.procedural.tree.root_length"),
                Text.empty(), tree.rootLengthScale(), 0.1, 4.0, 0.05,
                value -> updateTreeGeneration(current -> current.withRoots(
                        current.rootCount(), value
                ))
        );

        addTreeRecipeOptions(options, tree);
    }

    private void addTreeRecipeOptions(
            ProceduralSettingOptionsList options,
            TreeGenerationConfig tree
    ) {
        options.addSection(Text.translate(
                "effortless.procedural.tree.section.materials"
        ));
        options.addTab(
                Text.text("Tree material recipes"),
                Text.empty(),
                tree,
                value -> updateTreeGeneration(ignored -> value),
                (entry, value) -> {
                    long linked = List.of(
                            value.trunkRecipeId(), value.branchRecipeId(),
                            value.foliageRecipeId(), value.rootRecipeId()
                    ).stream().filter(id -> !id.isBlank()).count();
                    entry.getButton().setMessage(Text.text(
                            linked + " linked · 4 roles"
                    ));
                    entry.getButton().setOnPressListener(button -> {
                        var roles = new LinkedHashMap<String, String>();
                        roles.put("Trunk", value.trunkRecipeId());
                        roles.put("Branches", value.branchRecipeId());
                        roles.put("Foliage", value.foliageRecipeId());
                        roles.put("Roots", value.rootRecipeId());
                        new EffortlessGeneratorRecipesScreen(
                                getEntrance(),
                                "Tree material recipes",
                                session.library(),
                                session.selectedPresetId(),
                                roles,
                                changed -> entry.setItem(value.withRecipes(
                                        changed.getOrDefault("Trunk", ""),
                                        changed.getOrDefault("Branches", ""),
                                        changed.getOrDefault("Foliage", ""),
                                        changed.getOrDefault("Roots", "")
                                ))
                        ).attach();
                    });
                }
        );
    }

    private void updateTreeGeneration(
            java.util.function.UnaryOperator<TreeGenerationConfig> updater
    ) {
        session.replaceSelected(current -> {
            var updated = updater.apply(current.advanced().treeGeneration());
            previewTreeArchetype = updated.archetype();
            return current.withAdvanced(
                    current.advanced().withTreeGeneration(updated)
            );
        });
    }

    private void syncPreviewSubtypesFromRecipe() {
        var advanced = session.selectedPreset().advanced();
        previewSplineSubtype = advanced.roadProfile().subtype();
        previewTreeArchetype = advanced.treeGeneration().archetype();
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
        addButton(
                margin + GAP + (buttonWidth + GAP) * 3,
                y,
                buttonWidth,
                Text.text("Clipboard"),
                button -> new EffortlessWorkbenchClipboardScreen(
                        getEntrance()
                ).attach()
        );
        addButton(
                margin + GAP + (buttonWidth + GAP) * 4,
                y,
                buttonWidth,
                Text.text("Settings"),
                button -> new EffortlessWorkbenchSettingsScreen(
                        getEntrance()
                ).attach()
        );
        if (!compactLayout) {
            int statusLeft = margin + GAP + (buttonWidth + GAP) * 5;
            int statusRight = getScreenWidth() - margin - GAP
                    - (buttonWidth + GAP) * 3;
            draftStatusWidget = addWidget(new TextWidget(
                    getEntrance(),
                    (statusLeft + statusRight) / 2,
                    y + 6,
                    Text.empty(),
                    TextWidget.Gravity.CENTER
            ));
        }
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
        var selected = selectedBlock();
        if (ProceduralMaterial.isSpecialId(selected.itemId())) {
            session.replaceSelected(current ->
                    current.withFallbackItemId(selected.itemId())
            );
            message(
                    "Fallback set to " + shortId(selected.itemId()),
                    ChatFormatting.GREEN
            );
            return;
        }
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
                            + " recipes; active: " + active,
                    ChatFormatting.GREEN
            );
            allowDetach = true;
            detach();
        } catch (IllegalArgumentException exception) {
            message(
                    "Recipe: " + exception.getMessage(),
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
            message("Exported recipe library to " + path, ChatFormatting.GREEN);
        } catch (IOException | IllegalArgumentException exception) {
            message(
                    "Could not export recipe library: "
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
                    "Imported recipe library from " + imported.path()
                            + " (press Save to keep them)",
                    ChatFormatting.GREEN
            );
            recreate();
        } catch (IOException | RuntimeException exception) {
            message(
                    "Could not import recipe library: "
                            + exception.getMessage(),
                    ChatFormatting.RED
            );
        }
    }

    private Text enabledMessage() {
        return Text.text(
                compactLayout
                        ? (session.library().enabled()
                                ? "Recipes: on"
                                : "Recipes: off")
                        : (session.library().enabled()
                                ? "Recipes: enabled"
                                : "Recipes: disabled")
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

    private void openCommandPalette() {
        new EffortlessWorkbenchCommandPaletteScreen(
                getEntrance(), workbenchCommands()
        ).attach();
    }

    private List<EffortlessWorkbenchCommandPaletteScreen.Command>
            workbenchCommands() {
        var commands = new ArrayList<
                EffortlessWorkbenchCommandPaletteScreen.Command>();
        for (var tab : InspectorTab.values()) {
            commands.add(new EffortlessWorkbenchCommandPaletteScreen.Command(
                    "Open " + tab.title(),
                    "Workspace panel",
                    () -> showPanel(tab)
            ));
        }
        for (var type : ProceduralPreviewType.ALL) {
            commands.add(new EffortlessWorkbenchCommandPaletteScreen.Command(
                    "Use " + type.displayName().getString(),
                    "Build tool",
                    () -> selectPreviewTool(type)
            ));
        }
        commands.add(new EffortlessWorkbenchCommandPaletteScreen.Command(
                "New recipe", "Recipe action", () -> {
                    session.addPreset();
                    presetSearch = "";
                    resetSelectedRecipeUi();
                    recreate();
                }
        ));
        commands.add(new EffortlessWorkbenchCommandPaletteScreen.Command(
                "Duplicate recipe", "Recipe action",
                this::duplicateSelectedRecipe
        ));
        commands.add(new EffortlessWorkbenchCommandPaletteScreen.Command(
                "Activate recipe", "Recipe action", () -> {
                    session.useSelected();
                    recreate();
                }
        ));
        commands.add(new EffortlessWorkbenchCommandPaletteScreen.Command(
                "Save library", "Ctrl+S", this::saveLibrary
        ));
        commands.add(new EffortlessWorkbenchCommandPaletteScreen.Command(
                "Undo", "Ctrl+Z", () -> {
                    session.undo();
                    recreate();
                }
        ));
        commands.add(new EffortlessWorkbenchCommandPaletteScreen.Command(
                "Redo", "Ctrl+Y", () -> {
                    session.redo();
                    recreate();
                }
        ));
        return List.copyOf(commands);
    }

    private void showPanel(InspectorTab tab) {
        inspectorTab = tab;
        compactView = switch (tab) {
            case FORM -> CompactView.FORM;
            case MATERIALS -> CompactView.PALETTE;
            case GRADIENT -> CompactView.GRADIENT;
            case NOISE -> CompactView.NOISE;
            case RULES -> CompactView.RULES;
            case MASKS -> CompactView.MASKS;
            case SCENE -> CompactView.SCENE;
            case TRANSFORMS -> CompactView.TRANSFORMS;
            case OUTPUT -> CompactView.OUTPUT;
        };
        recreate();
    }

    private void selectPreviewTool(ProceduralPreviewType type) {
        previewType = type;
        previewOrientation = PreviewOrientation.defaultFor(type);
        previewSubtypesExpanded = type.hasSubtypes();
        selectBuildTool(type);
    }

    private ProceduralPatternPreset previewPreset() {
        var preset = session.selectedPreset();
        var layers = preset.advanced().compositionLayers();
        if (soloCompositionIndex < 0 || soloCompositionIndex >= layers.size()) {
            return preset;
        }
        var previewLayers = new ArrayList<ProceduralCompositionLayer>(
                layers.size()
        );
        for (int index = 0; index < layers.size(); index++) {
            previewLayers.add(layers.get(index).withEnabled(
                    index == soloCompositionIndex
            ));
        }
        return preset.withAdvanced(
                preset.advanced().withCompositionLayers(previewLayers)
        );
    }

    private void duplicateSelectedRecipe() {
        session.duplicateSelected();
        presetSearch = "";
        resetSelectedRecipeUi();
        recreate();
    }

    private void resetSelectedRecipeUi() {
        selectedBlockIndex = 0;
        selectedCompositionIndex = 0;
        soloCompositionIndex = -1;
        syncPreviewSubtypesFromRecipe();
    }

    private <T extends Enum<T> & PanelDescriptor> int addPanelTabs(
            int x,
            int y,
            int width,
            T[] tabs,
            Supplier<T> selected,
            Consumer<T> select
    ) {
        int columns = width < 240 ? 4 : width < 420 ? 5 : tabs.length;
        int rows = (tabs.length + columns - 1) / columns;
        int tabWidth = Math.max(
                1, (width - GAP * (columns - 1)) / columns
        );
        for (int index = 0; index < tabs.length; index++) {
            var tab = tabs[index];
            int column = index % columns;
            int row = index / columns;
            int tabX = x + column * (tabWidth + GAP);
            int actualWidth = column == columns - 1
                    ? x + width - tabX : tabWidth;
            addWidget(new WorkbenchToolTab(
                    getEntrance(),
                    tabX,
                    y + row * (BUTTON_HEIGHT + GAP),
                    actualWidth,
                    BUTTON_HEIGHT,
                    Text.text(tab.label()),
                    Text.text(tab.title()),
                    Text.translate(
                            "effortless.procedural.tooltip.tab." + tab.key()
                    ),
                    tab.accent(),
                    () -> selected.get() == tab,
                    () -> {
                        select.accept(tab);
                        recreate();
                    }
            ));
        }
        return y + rows * (BUTTON_HEIGHT + GAP);
    }

    private interface PanelDescriptor {
        String label();

        String title();

        String key();

        int accent();
    }

    private enum InspectorTab implements PanelDescriptor {
        FORM("Form", "Form settings", "form", 0xFF6B959E),
        MATERIALS("Mat", "Materials", "materials", 0xFF6B8EA4),
        GRADIENT("Grad", "Gradient", "gradient", 0xFFA18450),
        NOISE("Noise", "Noise", "noise", 0xFF84709D),
        RULES("Rules", "Rules", "rules", 0xFFA66F6F),
        MASKS("Masks", "Masks", "masks", 0xFF61978E),
        SCENE("Scene", "Scene stack", "scene", 0xFF6D9484),
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

        @Override
        public String label() {
            return label;
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public int accent() {
            return accent;
        }
    }

    private enum CompactView implements PanelDescriptor {
        FORM("Form", "Form settings", "form", 0xFF6B959E),
        PALETTE("Mat", "Palette", "materials", 0xFF6B8EA4),
        PREVIEW("View", "Preview", "preview", 0xFF6B959E),
        TUNE("Tune", "Material tuning", "tuning", 0xFF748EAC),
        MIX("Mix", "Layer mixing", "mix", 0xFF7C86A6),
        GRADIENT("Grad", "Gradient", "gradient", 0xFFA18450),
        NOISE("Noise", "Noise", "noise", 0xFF84709D),
        RULES("Rules", "Rules", "rules", 0xFFA66F6F),
        MASKS("Masks", "Masks", "masks", 0xFF61978E),
        SCENE("Scene", "Scene stack", "scene", 0xFF6D9484),
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

        @Override
        public String label() {
            return label;
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public int accent() {
            return accent;
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
