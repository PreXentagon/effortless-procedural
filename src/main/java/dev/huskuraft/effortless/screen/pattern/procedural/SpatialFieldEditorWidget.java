package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;
import dev.huskuraft.effortless.client.pattern.procedural.ExistingNeighborLookup;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationBounds;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationContext;
import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.SpatialField;
import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * Direct-manipulation 2D view of a spatial field. The center and direction/
 * primary-scale handles update the same immutable field used by generation.
 */
final class SpatialFieldEditorWidget extends AbstractWidget {

    private static final int HEADER_HEIGHT = 15;
    private static final int CELLS_X = 30;
    private static final int CELLS_Y = 16;
    private static final GenerationBounds PREVIEW_BOUNDS =
            new GenerationBounds(0, 0, 0, CELLS_X - 1, CELLS_Y - 1,
                    CELLS_X - 1);

    private final Supplier<SpatialField> field;
    private final Consumer<SpatialField> consumer;
    private DragMode dragMode = DragMode.NONE;

    SpatialFieldEditorWidget(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            Supplier<SpatialField> field,
            Consumer<SpatialField> consumer
    ) {
        super(entrance, x, y, width, height, Text.text("Gradient field canvas"));
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
        renderer.renderRect(
                getX(),
                getY(),
                getRight(),
                getBottom(),
                0xE00C0F12
        );
        renderer.renderTextFromStart(
                getTypeface(),
                Text.text("FIELD CANVAS"),
                getX() + 5,
                getY() + 3,
                0xFFE7E7E7,
                true
        );
        boolean scalarCoordinate = currentField().coordinate()
                == Coordinate.TRAVERSAL
                || currentField().coordinate() == Coordinate.DISTANCE;
        renderer.renderTextFromEnd(
                getTypeface(),
                Text.text(scalarCoordinate
                        ? "drag center + scale"
                        : "drag center + direction"),
                getRight() - 5,
                getY() + 3,
                0xFF8E949C,
                false
        );

        int left = canvasLeft();
        int top = canvasTop();
        int width = canvasWidth();
        int height = canvasHeight();
        var current = currentField();
        for (int cellY = 0; cellY < CELLS_Y; cellY++) {
            int y0 = top + cellY * height / CELLS_Y;
            int y1 = top + (cellY + 1) * height / CELLS_Y;
            for (int cellX = 0; cellX < CELLS_X; cellX++) {
                int x0 = left + cellX * width / CELLS_X;
                int x1 = left + (cellX + 1) * width / CELLS_X;
                double amount = sample(current, cellX, cellY);
                renderer.renderRect(x0, y0, x1 + 1, y1 + 1, fieldColor(amount));
            }
        }
        renderer.renderRect(left, top, left + width, top + 1, 0xFF5D6269);
        renderer.renderRect(left, top + height - 1, left + width, top + height,
                0xFF5D6269);
        renderer.renderRect(left, top, left + 1, top + height, 0xFF5D6269);
        renderer.renderRect(left + width - 1, top, left + width, top + height,
                0xFF5D6269);

        var center = centerPoint(current);
        var handle = directionPoint(current, center);
        drawLine(renderer, center.x(), center.y(), handle.x(), handle.y(),
                0xEEFFFFFF);
        renderHandle(renderer, center.x(), center.y(), 0xFFC4A66B, true);
        renderHandle(renderer, handle.x(), handle.y(), 0xFF72A8B4, false);
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isActive()
                || !containsCanvasPoint(mouseX, mouseY)) {
            return false;
        }
        beginEdit(mouseX, mouseY);
        return true;
    }

    boolean containsCanvasPoint(double mouseX, double mouseY) {
        return isVisible()
                && mouseX >= getLeft()
                && mouseX < getRight()
                && mouseY >= canvasTop()
                && mouseY < getBottom();
    }

    void beginEdit(double mouseX, double mouseY) {
        var current = currentField();
        var center = centerPoint(current);
        var direction = directionPoint(current, center);
        if (distance(mouseX, mouseY, direction.x(), direction.y()) <= 10.0) {
            dragMode = DragMode.DIRECTION;
            updateDirection(mouseX, mouseY);
        } else {
            dragMode = DragMode.CENTER;
            updateCenter(mouseX, mouseY);
        }
        getEntrance().getClient().getSoundManager().playButtonClickSound();
    }

    @Override
    public boolean onMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY
    ) {
        if (button != 0 || dragMode == DragMode.NONE || !isActive()) {
            return false;
        }
        if (dragMode == DragMode.CENTER) {
            updateCenter(mouseX, mouseY);
        } else {
            updateDirection(mouseX, mouseY);
        }
        return true;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = dragMode != DragMode.NONE;
        dragMode = DragMode.NONE;
        return consumed || super.onMouseReleased(mouseX, mouseY, button);
    }

    @Override
    public List<Text> getTooltip() {
        return List.of();
    }

    private double sample(SpatialField current, int x, int y) {
        var position = new GridPosition(x, y, x);
        var context = new GenerationContext<Object>(
                0L,
                position,
                y * CELLS_X + x,
                CELLS_X * CELLS_Y,
                PREVIEW_BOUNDS,
                Set.of(),
                Map.of(),
                ExistingNeighborLookup.NONE
        );
        return current.sample(context);
    }

    private void updateCenter(double mouseX, double mouseY) {
        var current = currentField();
        double normalizedX = normalized(mouseX, canvasLeft(), canvasWidth());
        double normalizedY = normalized(mouseY, canvasTop(), canvasHeight());
        if (current.coordinate() == Coordinate.Z) {
            consumer.accept(current.withCenter(
                    current.centerX(),
                    normalizedY,
                    normalizedX
            ));
            return;
        }
        consumer.accept(current.withCenter(
                normalizedX,
                normalizedY,
                current.centerZ()
        ));
    }

    private void updateDirection(double mouseX, double mouseY) {
        var current = currentField();
        var center = centerPoint(current);
        double dx = mouseX - center.x();
        double dy = mouseY - center.y();
        double angle = Math.atan2(dy, dx);
        boolean scalarCoordinate = current.coordinate() == Coordinate.TRAVERSAL
                || current.coordinate() == Coordinate.DISTANCE;
        double rotation = scalarCoordinate
                ? current.rotationDegrees()
                : current.coordinate() == Coordinate.Y
                        ? Math.toDegrees(angle - Math.PI / 2.0)
                        : -Math.toDegrees(angle);
        rotation = normalizeDegrees(rotation);
        double radius = Math.sqrt(dx * dx + dy * dy);
        double scale = Math.max(
                0.05,
                Math.min(4.0, radius / Math.max(1.0, minimumCanvas() * 0.22))
        );
        var rotated = current.withRotation(rotation);
        consumer.accept(switch (current.coordinate()) {
            case Y -> rotated.withScale(
                    current.scaleX(),
                    scale,
                    current.scaleZ()
            );
            case Z -> rotated.withScale(
                    current.scaleX(),
                    current.scaleY(),
                    scale
            );
            case X, DISTANCE, TRAVERSAL -> rotated.withScale(
                    scale,
                    current.scaleY(),
                    current.scaleZ()
            );
        });
    }

    private Point centerPoint(SpatialField current) {
        double x = current.coordinate() == Coordinate.Z
                ? current.centerZ()
                : current.centerX();
        return new Point(
                canvasLeft() + (int) Math.round(clamp01(x) * canvasWidth()),
                canvasTop() + (int) Math.round(
                        clamp01(current.centerY()) * canvasHeight()
                )
        );
    }

    private Point directionPoint(SpatialField current, Point center) {
        double scale = switch (current.coordinate()) {
            case X, DISTANCE, TRAVERSAL -> current.scaleX();
            case Y -> current.scaleY();
            case Z -> current.scaleZ();
        };
        double radius = minimumCanvas() * Math.max(
                0.08,
                Math.min(0.42, 0.22 * scale)
        );
        double radians = Math.toRadians(current.rotationDegrees());
        double angle = current.coordinate() == Coordinate.TRAVERSAL
                || current.coordinate() == Coordinate.DISTANCE
                ? 0.0
                : current.coordinate() == Coordinate.Y
                ? Math.PI / 2.0 + radians
                : -radians;
        return new Point(
                clamp(
                        center.x() + (int) Math.round(Math.cos(angle) * radius),
                        canvasLeft(),
                        canvasLeft() + canvasWidth()
                ),
                clamp(
                        center.y() + (int) Math.round(Math.sin(angle) * radius),
                        canvasTop(),
                        canvasTop() + canvasHeight()
                )
        );
    }

    private static int fieldColor(double value) {
        double amount = clamp01(value);
        int red = (int) Math.round(22 + amount * 213);
        int green = (int) Math.round(79 + amount * 93);
        int blue = (int) Math.round(111 - amount * 65);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void renderHandle(
            Renderer renderer,
            int x,
            int y,
            int color,
            boolean center
    ) {
        int radius = center ? 4 : 3;
        renderer.renderRect(
                x - radius - 1,
                y - radius - 1,
                x + radius + 2,
                y + radius + 2,
                0xE0101215
        );
        renderer.renderRect(
                x - radius,
                y - radius,
                x + radius + 1,
                y + radius + 1,
                color
        );
    }

    private static void drawLine(
            Renderer renderer,
            int x0,
            int y0,
            int x1,
            int y1,
            int color
    ) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            return;
        }
        for (int index = 0; index <= steps; index++) {
            double amount = (double) index / steps;
            int x = (int) Math.round(x0 + (x1 - x0) * amount);
            int y = (int) Math.round(y0 + (y1 - y0) * amount);
            renderer.renderRect(x, y, x + 1, y + 1, color);
        }
    }

    private int canvasLeft() {
        return getX() + 4;
    }

    private int canvasTop() {
        return getY() + HEADER_HEIGHT;
    }

    private int canvasWidth() {
        return Math.max(1, getWidth() - 8);
    }

    private int canvasHeight() {
        return Math.max(1, getHeight() - HEADER_HEIGHT - 4);
    }

    private int minimumCanvas() {
        return Math.min(canvasWidth(), canvasHeight());
    }

    private SpatialField currentField() {
        return field.get();
    }

    boolean isDragging() {
        return dragMode != DragMode.NONE;
    }

    private static double normalized(double value, double start, double size) {
        return clamp01((value - start) / Math.max(1.0, size));
    }

    private static double distance(
            double x0,
            double y0,
            double x1,
            double y1
    ) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double normalizeDegrees(double value) {
        double normalized = value % 360.0;
        if (normalized > 180.0) {
            normalized -= 360.0;
        } else if (normalized < -180.0) {
            normalized += 360.0;
        }
        return normalized;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Point(int x, int y) {
    }

    private enum DragMode {
        NONE,
        CENTER,
        DIRECTION
    }
}
