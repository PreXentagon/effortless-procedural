package dev.huskuraft.effortless.screen.settings;

import java.util.function.Consumer;

import dev.huskuraft.universal.api.gui.AbstractContainerWidget;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.input.EditBox;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * A non-recursive bounded number input. Button changes are always clamped.
 * Incomplete typed values never enter the backing model and are restored to
 * the last valid value when the field loses focus.
 */
public final class BoundedNumberField extends AbstractContainerWidget {

    public static final int TYPE_INTEGER = NumericValueRules.TYPE_INTEGER;
    public static final int TYPE_DOUBLE = NumericValueRules.TYPE_DOUBLE;

    private static final int BUTTON_WIDTH = 10;

    private final EditBox textField;
    private final Button minusButton;
    private final Button plusButton;
    private final int type;
    private final double minimum;
    private final double maximum;
    private final double step;
    private final boolean workbenchStyle;

    private Consumer<Number> valueChangeListener = value -> {
    };
    private double lastValidValue;
    private boolean updatingText;
    private boolean textWasFocused;

    public BoundedNumberField(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            int type,
            Number value,
            Number minimum,
            Number maximum,
            double step
    ) {
        this(
                entrance,
                x,
                y,
                width,
                height,
                type,
                value,
                minimum,
                maximum,
                step,
                false
        );
    }

    public BoundedNumberField(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            int type,
            Number value,
            Number minimum,
            Number maximum,
            double step,
            boolean workbenchStyle
    ) {
        super(entrance, x, y, width, height, Text.empty());
        if (type != TYPE_DOUBLE && type != TYPE_INTEGER) {
            throw new IllegalArgumentException("Invalid number type: " + type);
        }
        this.type = type;
        this.minimum = minimum.doubleValue();
        this.maximum = maximum.doubleValue();
        this.workbenchStyle = workbenchStyle;
        NumericValueRules.requireRange(this.minimum, this.maximum);
        if (!Double.isFinite(step) || step <= 0.0) {
            throw new IllegalArgumentException(
                    "Numeric step must be finite and greater than zero"
            );
        }
        this.step = step;
        this.focusable = true;

        int textInset = workbenchStyle ? 3 : 0;
        textField = addWidget(workbenchStyle
                ? new CenteredEditBox(
                        entrance,
                        x + BUTTON_WIDTH + textInset,
                        y + 1,
                        width - BUTTON_WIDTH * 2 - textInset * 2,
                        height - 2
                )
                : new EditBox(
                        entrance,
                        x + BUTTON_WIDTH,
                        y + 1,
                        width - BUTTON_WIDTH * 2,
                        height - 2,
                        Text.empty()
                ));
        if (workbenchStyle) {
            textField.setBordered(false);
            textField.setTextColor(0xFFE7E9EC);
            textField.setTextColorUneditable(0xFF777C82);
        }
        textField.setMaxLength(32);
        textField.setFilter(valueText ->
                NumericValueRules.isPotentialInput(valueText, type)
        );
        textField.setResponder(this::onTextChanged);

        minusButton = addWidget(new Button(
                entrance,
                x,
                y,
                BUTTON_WIDTH,
                height,
                Text.text("-"),
                button -> changeBy(-scaledStep())
        ));
        plusButton = addWidget(new Button(
                entrance,
                x + width - BUTTON_WIDTH,
                y,
                BUTTON_WIDTH,
                height,
                Text.text("+"),
                button -> changeBy(scaledStep())
        ));

        setValue(value);
    }

    @Override
    public void renderWidgetBackground(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        if (!workbenchStyle) {
            return;
        }
        int left = getLeft() + BUTTON_WIDTH;
        int right = getRight() - BUTTON_WIDTH;
        boolean invalid = !textField.getValue().isBlank()
                && currentTypedValue().isEmpty();
        int border = invalid
                ? 0xFF9A6262
                : textField.isFocused() ? 0xFF5D929E
                : textField.isHovered() ? 0xFF717880
                : 0xFF424850;
        renderer.renderRect(left, getTop(), right, getBottom(), 0xD014181D);
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
                getBottom() - (textField.isFocused() ? 2 : 1),
                right,
                getBottom(),
                border
        );
    }

    @Override
    public void onTick() {
        super.onTick();
        // Do not rely solely on EditBox#setResponder: nested entry lists can
        // route keyboard events without Universal API invoking that callback.
        // Polling the visible value keeps valid typed input synchronized while
        // the range parser still rejects zero/out-of-range/incomplete drafts.
        synchronizeTypedValue();
        boolean focused = textField.isFocused();
        if (textWasFocused && !focused && currentTypedValue().isEmpty()) {
            writeText(lastValidValue);
        }
        textWasFocused = focused;
    }

    @Override
    public boolean onCharTyped(char character, int modifiers) {
        boolean handled = super.onCharTyped(character, modifiers);
        synchronizeTypedValue();
        return handled;
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.onKeyPressed(keyCode, scanCode, modifiers);
        synchronizeTypedValue();
        return handled;
    }

    @Override
    public void setActive(boolean active) {
        super.setActive(active);
        textField.setActive(active);
        minusButton.setActive(active);
        plusButton.setActive(active);
    }

    public Number getNumber() {
        return number(lastValidValue);
    }

    public boolean isEditing() {
        return textField.isFocused();
    }

    public void setValue(Number value) {
        double clamped = NumericValueRules.clamp(
                value == null ? minimum : value.doubleValue(),
                minimum,
                maximum
        );
        if (type == TYPE_INTEGER) {
            clamped = Math.rint(clamped);
        }
        lastValidValue = clamped;
        writeText(clamped);
    }

    public void setValueChangeListener(Consumer<Number> listener) {
        valueChangeListener = listener == null ? value -> {
        } : listener;
    }

    private void onTextChanged(String value) {
        if (updatingText) {
            return;
        }
        synchronizeTypedValue();
    }

    private void synchronizeTypedValue() {
        var parsed = NumericValueRules.parseCommitted(
                textField.getValue(),
                type,
                minimum,
                maximum
        );
        if (parsed.isEmpty()) {
            return;
        }
        double parsedValue = parsed.getAsDouble();
        if (Double.compare(parsedValue, lastValidValue) == 0) {
            return;
        }
        lastValidValue = parsedValue;
        valueChangeListener.accept(number(lastValidValue));
    }

    private void changeBy(double amount) {
        double next = NumericValueRules.step(
                lastValidValue,
                amount,
                minimum,
                maximum
        );
        if (type == TYPE_INTEGER) {
            next = Math.rint(next);
        }
        lastValidValue = next;
        writeText(next);
        valueChangeListener.accept(number(next));
    }

    private double scaledStep() {
        if (getEntrance().getClient().getWindow().isShiftDown()) {
            return step * 10.0;
        }
        if (getEntrance().getClient().getWindow().isControlDown()) {
            return step * 5.0;
        }
        return step;
    }

    private java.util.OptionalDouble currentTypedValue() {
        return NumericValueRules.parseCommitted(
                textField.getValue(),
                type,
                minimum,
                maximum
        );
    }

    private Number number(double value) {
        return type == TYPE_INTEGER ? (int) Math.round(value) : value;
    }

    private void writeText(double value) {
        updatingText = true;
        try {
            textField.setValue(NumericValueRules.format(value, type));
        } finally {
            updatingText = false;
        }
    }

    private static final class CenteredEditBox extends EditBox {

        private CenteredEditBox(
                Entrance entrance,
                int x,
                int y,
                int width,
                int height
        ) {
            super(entrance, x, y, width, height, Text.empty());
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
    }
}
