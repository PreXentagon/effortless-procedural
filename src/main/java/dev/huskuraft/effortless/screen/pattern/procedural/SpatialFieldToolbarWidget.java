package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;
import dev.huskuraft.effortless.client.pattern.procedural.SpatialField;
import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.gui.tooltip.TooltipHelper;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/**
 * Compact Alt-menu-inspired selectors for a spatial field's shape and axis.
 * The whole strip is one widget so it stays usable in the compact workbench.
 */
final class SpatialFieldToolbarWidget extends AbstractWidget {

    static final int HEIGHT = 42;
    private static final int LABEL_WIDTH = 42;
    private static final int ROW_HEIGHT = 19;
    private static final int ROW_GAP = 3;
    private static final int SHAPE_ACCENT = 0xFFA18450;
    private static final int AXIS_ACCENT = 0xFF5D929E;

    private static final SpatialField.Shape[] SHAPES =
            SpatialField.Shape.values();
    private static final Coordinate[] COORDINATES = Coordinate.values();

    private final Supplier<SpatialField> field;
    private final Consumer<SpatialField> consumer;
    private HoverTarget hovered = HoverTarget.NONE;
    private int hoveredIndex = -1;

    SpatialFieldToolbarWidget(
            Entrance entrance,
            int x,
            int y,
            int width,
            Supplier<SpatialField> field,
            Consumer<SpatialField> consumer
    ) {
        super(
                entrance,
                x,
                y,
                width,
                HEIGHT,
                Text.translate("effortless.procedural.field.toolbar")
        );
        this.field = field;
        this.consumer = consumer;
        this.focusable = true;
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        hovered = HoverTarget.NONE;
        hoveredIndex = -1;
        renderRow(
                renderer,
                mouseX,
                mouseY,
                getY(),
                Text.translate("effortless.procedural.field.shape"),
                SHAPES.length,
                shapeIndex(field.get().shape()),
                SHAPE_ACCENT,
                true
        );
        renderRow(
                renderer,
                mouseX,
                mouseY,
                getY() + ROW_HEIGHT + ROW_GAP,
                Text.translate("effortless.procedural.field.axis"),
                COORDINATES.length,
                coordinateIndex(field.get().coordinate()),
                AXIS_ACCENT,
                false
        );
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isActive()
                || !super.onMouseClicked(mouseX, mouseY, button)) {
            return false;
        }
        int row = rowAt(mouseY);
        if (row < 0) {
            return false;
        }
        int count = row == 0 ? SHAPES.length : COORDINATES.length;
        int index = cellAt(mouseX, count);
        if (index < 0) {
            return false;
        }
        var current = field.get();
        consumer.accept(row == 0
                ? current.withShape(SHAPES[index])
                : current.withCoordinate(COORDINATES[index]));
        getEntrance().getClient().getSoundManager().playButtonClickSound();
        return true;
    }

    @Override
    public List<Text> getTooltip() {
        if (hovered == HoverTarget.NONE || hoveredIndex < 0) {
            return List.of();
        }
        String category = hovered == HoverTarget.SHAPE
                ? "gradient_shape"
                : "gradient_coordinate";
        String value = hovered == HoverTarget.SHAPE
                ? SHAPES[hoveredIndex].name().toLowerCase()
                : COORDINATES[hoveredIndex].name().toLowerCase();
        var lines = new ArrayList<Text>();
        lines.add(Text.translate(
                "effortless.procedural.field."
                        + (hovered == HoverTarget.SHAPE ? "shape." : "axis.")
                        + value
        ).withStyle(ChatFormatting.WHITE));
        lines.add(TooltipHelper.holdShiftForSummary());
        if (TooltipHelper.isSummaryButtonDown()) {
            lines.add(Text.empty());
            lines.addAll(TooltipHelper.wrapLines(
                    getTypeface(),
                    Text.translate(
                            "effortless.procedural.tooltip.value."
                                    + category + "." + value
                    ).withStyle(ChatFormatting.GRAY)
            ));
        }
        return List.copyOf(lines);
    }

    private void renderRow(
            Renderer renderer,
            int mouseX,
            int mouseY,
            int rowY,
            Text title,
            int count,
            int selected,
            int accent,
            boolean shapeRow
    ) {
        renderer.renderRect(
                getX(),
                rowY,
                getX() + LABEL_WIDTH - 2,
                rowY + ROW_HEIGHT,
                0xB20B0D10
        );
        renderer.renderRect(
                getX(),
                rowY + ROW_HEIGHT - 2,
                getX() + LABEL_WIDTH - 2,
                rowY + ROW_HEIGHT,
                accent
        );
        renderer.renderTextFromCenter(
                getTypeface(),
                title,
                getX() + (LABEL_WIDTH - 2) / 2,
                rowY + 6,
                0xFFE7E7E7,
                true
        );

        int available = getWidth() - LABEL_WIDTH;
        for (int index = 0; index < count; index++) {
            int left = getX() + LABEL_WIDTH + index * available / count;
            int right = getX() + LABEL_WIDTH
                    + (index + 1) * available / count;
            boolean inside = mouseX >= left && mouseX < right
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean activeValue = index == selected;
            int background = activeValue
                    ? 0xA35C5C5C
                    : inside ? 0x805C5C5C : 0x6B000000;
            int border = activeValue
                    ? 0xFFD6D6D6
                    : inside ? 0xFF8B9098 : 0xFF44484E;
            renderer.renderRect(left, rowY, right, rowY + ROW_HEIGHT,
                    background);
            renderer.renderRect(left, rowY, right, rowY + 1, border);
            renderer.renderRect(left, rowY, left + 1, rowY + ROW_HEIGHT,
                    border);
            renderer.renderRect(
                    right - 1,
                    rowY,
                    right,
                    rowY + ROW_HEIGHT,
                    border
            );
            renderer.renderRect(
                    left,
                    rowY + ROW_HEIGHT - (activeValue ? 3 : 2),
                    right,
                    rowY + ROW_HEIGHT,
                    accent
            );
            renderer.renderTextFromCenter(
                    getTypeface(),
                    Text.translate(
                            "effortless.procedural.field."
                                    + (shapeRow ? "shape." : "axis.")
                                    + (shapeRow
                                    ? SHAPES[index].name().toLowerCase()
                                    : COORDINATES[index].name().toLowerCase())
                    ),
                    (left + right) / 2,
                    rowY + 6,
                    activeValue ? 0xFFFFFFFF : 0xFFE7E7E7,
                    activeValue
            );
            if (inside) {
                hovered = shapeRow ? HoverTarget.SHAPE : HoverTarget.AXIS;
                hoveredIndex = index;
            }
        }
    }

    private int rowAt(double mouseY) {
        if (mouseY >= getY() && mouseY < getY() + ROW_HEIGHT) {
            return 0;
        }
        int second = getY() + ROW_HEIGHT + ROW_GAP;
        return mouseY >= second && mouseY < second + ROW_HEIGHT ? 1 : -1;
    }

    private int cellAt(double mouseX, int count) {
        if (mouseX < getX() + LABEL_WIDTH || mouseX >= getRight()) {
            return -1;
        }
        int available = getWidth() - LABEL_WIDTH;
        int index = (int) ((mouseX - getX() - LABEL_WIDTH) * count
                / Math.max(1, available));
        return Math.max(0, Math.min(count - 1, index));
    }

    private static int shapeIndex(SpatialField.Shape value) {
        for (int index = 0; index < SHAPES.length; index++) {
            if (SHAPES[index] == value) {
                return index;
            }
        }
        return 0;
    }

    private static int coordinateIndex(Coordinate value) {
        for (int index = 0; index < COORDINATES.length; index++) {
            if (COORDINATES[index] == value) {
                return index;
            }
        }
        return 0;
    }

    private enum HoverTarget {
        NONE,
        SHAPE,
        AXIS
    }
}
