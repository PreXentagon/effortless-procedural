package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * A compact grid of radio-style choices. It replaces long cycling buttons and
 * makes every available value visible at once.
 */
final class ProceduralChoiceStrip<T> extends AbstractWidget {

    static final int CELL_HEIGHT = 18;
    static final int CELL_GAP = 2;

    private final List<Text> messages;
    private final List<T> values;
    private final Supplier<T> value;
    private final Consumer<T> consumer;
    private final int columns;

    ProceduralChoiceStrip(
            Entrance entrance,
            int x,
            int y,
            int width,
            List<Text> messages,
            List<T> values,
            Supplier<T> value,
            Consumer<T> consumer
    ) {
        super(
                entrance,
                x,
                y,
                width,
                heightFor(values.size()),
                Text.empty()
        );
        this.messages = List.copyOf(messages);
        this.values = List.copyOf(values);
        this.value = value;
        this.consumer = consumer;
        this.columns = columnsFor(values.size());
        this.focusable = true;
    }

    static int heightFor(int count) {
        int columns = columnsFor(count);
        int rows = Math.max(1, (count + columns - 1) / columns);
        return rows * CELL_HEIGHT + Math.max(0, rows - 1) * CELL_GAP;
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        int selected = values.indexOf(value.get());
        for (int index = 0; index < values.size(); index++) {
            var bounds = bounds(index);
            boolean inside = mouseX >= bounds.left()
                    && mouseX < bounds.right()
                    && mouseY >= bounds.top()
                    && mouseY < bounds.bottom();
            boolean chosen = index == selected;
            int background = chosen
                    ? 0xB2474E55
                    : inside ? ProceduralTheme.CARD_HOVER
                    : ProceduralTheme.CARD;
            int border = chosen
                    ? 0xFFD9DDE2
                    : inside ? ProceduralTheme.BORDER_HOVER
                    : ProceduralTheme.BORDER;
            int accent = chosen
                    ? ProceduralTheme.CYAN
                    : inside ? ProceduralTheme.GOLD
                    : 0xFF555A61;

            renderer.renderRect(
                    bounds.left(),
                    bounds.top(),
                    bounds.right(),
                    bounds.bottom(),
                    background
            );
            renderer.renderRect(
                    bounds.left(),
                    bounds.top(),
                    bounds.right(),
                    bounds.top() + 1,
                    border
            );
            renderer.renderRect(
                    bounds.left(),
                    bounds.top(),
                    bounds.left() + 1,
                    bounds.bottom(),
                    border
            );
            renderer.renderRect(
                    bounds.right() - 1,
                    bounds.top(),
                    bounds.right(),
                    bounds.bottom(),
                    border
            );
            renderer.renderRect(
                    bounds.left(),
                    bounds.bottom() - (chosen ? 3 : 2),
                    bounds.right(),
                    bounds.bottom(),
                    accent
            );
            String label = getTypeface().subtractByWidth(
                    messages.get(index).getString(),
                    Math.max(1, bounds.right() - bounds.left() - 6),
                    false
            );
            renderer.renderTextFromCenter(
                    getTypeface(),
                    Text.text(label),
                    (bounds.left() + bounds.right()) / 2,
                    bounds.top() + 5,
                    isActive()
                            ? chosen ? 0xFFFFFFFF : ProceduralTheme.TEXT
                            : 0xFF686C71,
                    chosen
            );
        }
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isActive() || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int index = indexAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        consumer.accept(values.get(index));
        getEntrance().getClient().getSoundManager().playButtonClickSound();
        return true;
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isActive() || values.isEmpty()) {
            return false;
        }
        int direction = switch (keyCode) {
            case 263, 265 -> -1;
            case 262, 264 -> 1;
            default -> 0;
        };
        if (direction == 0) {
            return false;
        }
        int selected = Math.max(0, values.indexOf(value.get()));
        consumer.accept(values.get(Math.floorMod(
                selected + direction,
                values.size()
        )));
        return true;
    }

    private int indexAt(double mouseX, double mouseY) {
        for (int index = 0; index < values.size(); index++) {
            var bounds = bounds(index);
            if (mouseX >= bounds.left() && mouseX < bounds.right()
                    && mouseY >= bounds.top() && mouseY < bounds.bottom()) {
                return index;
            }
        }
        return -1;
    }

    private Bounds bounds(int index) {
        int column = index % columns;
        int row = index / columns;
        int usable = getWidth() - Math.max(0, columns - 1) * CELL_GAP;
        int left = getX() + column * usable / columns
                + column * CELL_GAP;
        int right = getX() + (column + 1) * usable / columns
                + column * CELL_GAP;
        int top = getY() + row * (CELL_HEIGHT + CELL_GAP);
        return new Bounds(left, top, right, top + CELL_HEIGHT);
    }

    private static int columnsFor(int count) {
        if (count <= 0) {
            return 1;
        }
        if (count <= 4) {
            return count;
        }
        if (count <= 6) {
            return 3;
        }
        return 4;
    }

    private record Bounds(int left, int top, int right, int bottom) {
    }
}
