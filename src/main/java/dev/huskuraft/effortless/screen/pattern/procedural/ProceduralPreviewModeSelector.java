package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.huskuraft.effortless.building.structure.BuildFeature;
import dev.huskuraft.effortless.building.structure.BuildFeatures;
import dev.huskuraft.effortless.building.structure.BuildMode;
import dev.huskuraft.effortless.building.structure.builder.Structure;
import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * Preview-local shape toolbar. Every build mode remains visible; clicking the
 * selected mode toggles one compact horizontal rail containing its preview
 * orientation and stock feature variants.
 */
final class ProceduralPreviewModeSelector extends AbstractWidget {

    private static final int TOOLBAR_HEIGHT = 29;
    private static final int SUBTYPE_HEIGHT = 34;
    private static final int OPTION_CELL_WIDTH = 29;
    private static final int GROUP_GAP = 9;
    private static final List<BuildMode> MODES = Arrays.stream(
            BuildMode.values()
    ).filter(BuildMode::isEnabled).toList();

    private final Supplier<BuildMode> mode;
    private final Consumer<BuildMode> modeConsumer;
    private final Supplier<PreviewOrientation> orientation;
    private final Consumer<PreviewOrientation> orientationConsumer;
    private final Supplier<Structure> structure;
    private final Consumer<BuildFeature> featureConsumer;
    private final Supplier<Boolean> expanded;
    private BuildMode hoveredMode;
    private BuildFeature hoveredFeature;

    ProceduralPreviewModeSelector(
            Entrance entrance,
            int x,
            int y,
            int width,
            Supplier<BuildMode> mode,
            Consumer<BuildMode> modeConsumer,
            Supplier<PreviewOrientation> orientation,
            Consumer<PreviewOrientation> orientationConsumer,
            Supplier<Structure> structure,
            Consumer<BuildFeature> featureConsumer,
            Supplier<Boolean> expanded
    ) {
        super(
                entrance,
                x,
                y,
                width,
                heightFor(expanded.get()),
                Text.text("Preview shape and subtype toolbar")
        );
        this.mode = mode;
        this.modeConsumer = modeConsumer;
        this.orientation = orientation;
        this.orientationConsumer = orientationConsumer;
        this.structure = structure;
        this.featureConsumer = featureConsumer;
        this.expanded = expanded;
        this.focusable = true;
    }

    static int heightFor(boolean expanded) {
        return TOOLBAR_HEIGHT + (expanded ? SUBTYPE_HEIGHT : 0);
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        hoveredMode = null;
        hoveredFeature = null;
        renderModeToolbar(renderer, mouseX, mouseY);
        if (expanded.get()) {
            renderSubtypeRail(renderer, mouseX, mouseY);
        }
    }

    private void renderModeToolbar(Renderer renderer, int mouseX, int mouseY) {
        var selected = mode.get();
        renderer.renderRect(
                getX(),
                getY(),
                getRight(),
                getY() + TOOLBAR_HEIGHT,
                0xE0181C20
        );
        for (int index = 0; index < MODES.size(); index++) {
            var candidate = MODES.get(index);
            int left = modeLeft(index);
            int right = modeLeft(index + 1);
            boolean hovered = contains(
                    mouseX,
                    mouseY,
                    left,
                    getY(),
                    right,
                    getY() + TOOLBAR_HEIGHT
            );
            if (hovered) {
                hoveredMode = candidate;
            }
            if (candidate == selected || hovered) {
                renderer.renderRect(
                        left + 1,
                        getY() + 1,
                        right - 1,
                        getY() + TOOLBAR_HEIGHT - 2,
                        candidate == selected ? 0xE04A535C : 0xC0353C43
                );
            }
            int iconX = left + Math.max(1, (right - left - 16) / 2);
            renderer.renderTexture(
                    candidate.getIcon(),
                    iconX,
                    getY() + 4,
                    16,
                    16,
                    0f,
                    0f,
                    18,
                    18,
                    18,
                    18
            );
            renderer.renderRect(
                    left + 3,
                    getY() + TOOLBAR_HEIGHT - 4,
                    right - 3,
                    getY() + TOOLBAR_HEIGHT - 2,
                    candidate.getTintColor().getRGB() | 0xFF000000
            );
        }
    }

    private void renderSubtypeRail(
            Renderer renderer,
            int mouseX,
            int mouseY
    ) {
        int top = getY() + TOOLBAR_HEIGHT;
        renderer.renderRect(
                getX(),
                top,
                getRight(),
                top + SUBTYPE_HEIGHT,
                0xEB14181C
        );
        renderer.renderRect(
                getX(),
                top + SUBTYPE_HEIGHT - 1,
                getRight(),
                top + SUBTYPE_HEIGHT,
                mode.get().getTintColor().getRGB() | 0xFF000000
        );
        int cursor = getX() + 7;
        var orientations = PreviewOrientation.choices(mode.get());
        if (orientations.size() > 1) {
            cursor = renderLabel(renderer, "VIEW", cursor, top);
            for (var value : orientations) {
                int width = Math.max(
                        25,
                        getTypeface().measureWidth(value.label()) + 12
                );
                boolean hovered = contains(
                        mouseX,
                        mouseY,
                        cursor,
                        top + 3,
                        cursor + width,
                        top + SUBTYPE_HEIGHT - 4
                );
                renderChoiceBackground(
                        renderer,
                        cursor,
                        top,
                        width,
                        value == orientation.get(),
                        hovered
                );
                renderer.renderTextFromCenter(
                        getTypeface(),
                        Text.text(value.label()),
                        cursor + width / 2,
                        top + 10,
                        value == orientation.get()
                                ? 0xFFE8F5F7
                                : 0xFFC6CBD0,
                        false
                );
                cursor += width + 2;
            }
            cursor += GROUP_GAP;
        }

        var selectedStructure = structure.get();
        for (var type : featureTypes(mode.get())) {
            cursor = renderLabel(
                    renderer,
                    shortCategory(type),
                    cursor,
                    top
            );
            for (var entry : type.getEntries()) {
                boolean selected = selectedStructure.getFeatures()
                        .contains(entry);
                boolean hovered = contains(
                        mouseX,
                        mouseY,
                        cursor,
                        top + 3,
                        cursor + OPTION_CELL_WIDTH,
                        top + SUBTYPE_HEIGHT - 4
                );
                if (hovered) {
                    hoveredFeature = entry;
                }
                renderChoiceBackground(
                        renderer,
                        cursor,
                        top,
                        OPTION_CELL_WIDTH,
                        selected,
                        hovered
                );
                renderer.renderTexture(
                        entry.getIcon(),
                        cursor + 6,
                        top + 6,
                        16,
                        16,
                        0f,
                        0f,
                        18,
                        18,
                        18,
                        18
                );
                cursor += OPTION_CELL_WIDTH + 2;
            }
            cursor += GROUP_GAP;
        }

        if (hoveredFeature != null) {
            renderer.renderTextFromEnd(
                    getTypeface(),
                    hoveredFeature.getNameText(),
                    getRight() - 7,
                    top + 10,
                    0xFFE4E7E9,
                    true
            );
        } else if (hoveredMode != null) {
            renderer.renderTextFromEnd(
                    getTypeface(),
                    hoveredMode.getDisplayName(),
                    getRight() - 7,
                    top + 10,
                    0xFFE4E7E9,
                    true
            );
        }
    }

    private int renderLabel(
            Renderer renderer,
            String label,
            int cursor,
            int top
    ) {
        int width = getTypeface().measureWidth(label) + 7;
        renderer.renderTextFromStart(
                getTypeface(),
                Text.text(label),
                cursor,
                top + 10,
                0xFF8F979F,
                false
        );
        return cursor + width;
    }

    private void renderChoiceBackground(
            Renderer renderer,
            int left,
            int top,
            int width,
            boolean selected,
            boolean hovered
    ) {
        if (!selected && !hovered) {
            return;
        }
        renderer.renderRect(
                left + 1,
                top + 3,
                left + width - 1,
                top + SUBTYPE_HEIGHT - 4,
                selected ? 0xD54A535C : 0xB8353C43
        );
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isActive() || !isVisible()
                || mouseX < getX() || mouseX >= getRight()) {
            return false;
        }
        if (mouseY >= getY() && mouseY < getY() + TOOLBAR_HEIGHT) {
            int index = Math.min(
                    MODES.size() - 1,
                    Math.max(
                            0,
                            (int) ((mouseX - getX()) * MODES.size()
                                    / getWidth())
                    )
            );
            click();
            modeConsumer.accept(MODES.get(index));
            return true;
        }
        if (!expanded.get()
                || mouseY < getY() + TOOLBAR_HEIGHT
                || mouseY >= getBottom()) {
            return false;
        }
        int cursor = getX() + 7;
        var orientations = PreviewOrientation.choices(mode.get());
        if (orientations.size() > 1) {
            cursor += getTypeface().measureWidth("VIEW") + 7;
            for (var value : orientations) {
                int width = Math.max(
                        25,
                        getTypeface().measureWidth(value.label()) + 12
                );
                if (mouseX >= cursor && mouseX < cursor + width) {
                    click();
                    orientationConsumer.accept(value);
                    return true;
                }
                cursor += width + 2;
            }
            cursor += GROUP_GAP;
        }
        for (var type : featureTypes(mode.get())) {
            cursor += getTypeface().measureWidth(shortCategory(type)) + 7;
            for (var entry : type.getEntries()) {
                if (mouseX >= cursor
                        && mouseX < cursor + OPTION_CELL_WIDTH) {
                    click();
                    featureConsumer.accept(entry);
                    return true;
                }
                cursor += OPTION_CELL_WIDTH + 2;
            }
            cursor += GROUP_GAP;
        }
        return false;
    }

    @Override
    public List<Text> getTooltip() {
        return List.of();
    }

    private int modeLeft(int index) {
        return getX() + index * getWidth() / MODES.size();
    }

    private void click() {
        getEntrance().getClient().getSoundManager().playButtonClickSound();
    }

    private static List<BuildFeatures> featureTypes(BuildMode mode) {
        var supported = mode.getDefaultStructure().getSupportedFeatures();
        return Arrays.stream(BuildFeatures.values())
                .filter(supported::contains)
                .toList();
    }

    private static String shortCategory(BuildFeatures type) {
        return switch (type) {
            case CIRCLE_START -> "START";
            case CUBE_FILLING, PLANE_FILLING -> "FILL";
            case CUBE_LENGTH -> "SIZE";
            case PLANE_FACING -> "FACE";
            case PLANE_LENGTH -> "LENGTH";
            case LINE_DIRECTION -> "DIR";
            case RAISED_EDGE -> "EDGE";
        };
    }

    private static boolean contains(
            double mouseX,
            double mouseY,
            int left,
            int top,
            int right,
            int bottom
    ) {
        return mouseX >= left && mouseX < right
                && mouseY >= top && mouseY < bottom;
    }
}
