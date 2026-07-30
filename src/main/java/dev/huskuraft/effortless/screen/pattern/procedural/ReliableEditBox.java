package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.Objects;
import java.util.function.Consumer;

import dev.huskuraft.universal.api.gui.input.EditBox;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * Edit box that reports keyboard edits after the widget has processed them.
 *
 * <p>The Universal API responder is not consistently invoked for keyboard
 * input routed through nested workbench containers. Reading the resulting
 * value after each accepted key/character event keeps the visible text and
 * the workbench draft in sync.</p>
 */
final class ReliableEditBox extends EditBox {

    private Consumer<String> changeListener = value -> {
    };
    private String reportedValue = "";

    ReliableEditBox(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            Text message
    ) {
        super(entrance, x, y, width, height, message);
        setBordered(false);
        setTextColor(0xFFE7E9EC);
        setTextColorUneditable(0xFF777C82);
    }

    @Override
    public void renderWidgetBackground(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        int left = getLeft() - 3;
        int right = getRight() + 3;
        int border = isFocused()
                ? ProceduralTheme.CYAN
                : isHovered() ? ProceduralTheme.BORDER_HOVER
                : ProceduralTheme.BORDER;
        renderer.renderRect(
                left,
                getTop(),
                right,
                getBottom(),
                0xD014181D
        );
        renderer.renderRect(left, getTop(), right, getTop() + 1, border);
        renderer.renderRect(left, getTop(), left + 1, getBottom(), border);
        renderer.renderRect(
                right - 1,
                getTop(),
                right,
                getBottom(),
                border
        );
        renderer.renderRect(
                left,
                getBottom() - (isFocused() ? 2 : 1),
                right,
                getBottom(),
                border
        );
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        int originalY = getY();
        int textOffset = Math.max(
                0,
                (getHeight() - getTypeface().getLineHeight()) / 2
        );
        setY(originalY + textOffset);
        try {
            super.renderWidget(renderer, mouseX, mouseY, deltaTick);
        } finally {
            setY(originalY);
        }
    }

    void setChangeListener(Consumer<String> listener) {
        changeListener = listener == null ? value -> {
        } : listener;
        reportedValue = getValue();
    }

    void commitVisibleValue() {
        String value = getValue();
        if (Objects.equals(value, reportedValue)) {
            return;
        }
        reportedValue = value;
        changeListener.accept(value);
    }

    @Override
    public boolean onCharTyped(char character, int modifiers) {
        boolean handled = super.onCharTyped(character, modifiers);
        commitVisibleValue();
        return handled;
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.onKeyPressed(keyCode, scanCode, modifiers);
        commitVisibleValue();
        return handled;
    }

    @Override
    public void onTick() {
        super.onTick();
        commitVisibleValue();
    }
}
