package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.gui.tooltip.TooltipHelper;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/**
 * Compact workbench slider with an exact numeric readout.
 */
final class ProceduralSlider extends AbstractWidget {

    private static final int TRACK_HEIGHT = 4;
    private static final int KNOB_WIDTH = 6;

    private final Text label;
    private final Text summary;
    private final double minimum;
    private final double maximum;
    private final double step;
    private final DoubleSupplier value;
    private final Consumer<Double> consumer;
    private final DoubleFunction<String> formatter;

    ProceduralSlider(
            Entrance entrance,
            int x,
            int y,
            int width,
            Text label,
            Text summary,
            double minimum,
            double maximum,
            double step,
            DoubleSupplier value,
            Consumer<Double> consumer,
            DoubleFunction<String> formatter
    ) {
        super(entrance, x, y, width, 22, label);
        this.label = label;
        this.summary = summary;
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.value = value;
        this.consumer = consumer;
        this.formatter = formatter;
        this.focusable = true;
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        double current = clamped(value.getAsDouble());
        renderer.renderTextFromStart(
                getTypeface(),
                label,
                getX(),
                getY() + 1,
                isActive() ? 0xFFE0E0E0 : 0xFF777777,
                true
        );
        renderer.renderTextFromEnd(
                getTypeface(),
                Text.text(formatter.apply(current)),
                getRight(),
                getY() + 1,
                isActive() ? 0xFFC4A66B : 0xFF777777,
                true
        );

        int trackY = getY() + 15;
        renderer.renderRect(
                getX(),
                trackY,
                getRight(),
                trackY + TRACK_HEIGHT,
                0xCC1B1B1B
        );
        int filled = (int) Math.round(normalized(current) * getWidth());
        renderer.renderRect(
                getX(),
                trackY,
                getX() + filled,
                trackY + TRACK_HEIGHT,
                isActive() ? ProceduralTheme.GOLD : 0xFF555555
        );
        int knobX = getX() + filled - KNOB_WIDTH / 2;
        renderer.renderRect(
                knobX,
                trackY - 2,
                knobX + KNOB_WIDTH,
                trackY + TRACK_HEIGHT + 2,
                isHoveredOrFocused() && isActive()
                        ? 0xFFFFFFFF
                        : 0xFFD8D8D8
        );
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isActive()
                || !super.onMouseClicked(mouseX, mouseY, button)) {
            return false;
        }
        updateFromMouse(mouseX);
        getEntrance().getClient().getSoundManager().playButtonClickSound();
        return true;
    }

    @Override
    public boolean onMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY
    ) {
        if (button != 0 || !isActive()) {
            return false;
        }
        updateFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isActive()) {
            return false;
        }
        if (keyCode == 263) {
            consumer.accept(clamped(value.getAsDouble() - step));
            return true;
        }
        if (keyCode == 262) {
            consumer.accept(clamped(value.getAsDouble() + step));
            return true;
        }
        return false;
    }

    @Override
    public List<Text> getTooltip() {
        var lines = new java.util.ArrayList<Text>();
        lines.add(label.withStyle(
                ChatFormatting.WHITE
        ));
        lines.add(Text.text(formatter.apply(value.getAsDouble()))
                .withStyle(ChatFormatting.GOLD));
        lines.add(Text.empty());
        lines.add(TooltipHelper.holdShiftForSummary());
        if (TooltipHelper.isSummaryButtonDown()) {
            lines.add(Text.empty());
            lines.addAll(TooltipHelper.wrapLines(
                    getTypeface(),
                    summary.withStyle(ChatFormatting.GRAY)
            ));
            lines.add(Text.translate(
                    "effortless.procedural.tooltip.range",
                    formatter.apply(minimum),
                    formatter.apply(maximum)
            ).withStyle(ChatFormatting.DARK_GRAY));
        }
        return List.copyOf(lines);
    }

    private void updateFromMouse(double mouseX) {
        double normalized = Math.max(
                0.0,
                Math.min(1.0, (mouseX - getX()) / Math.max(1.0, getWidth()))
        );
        double raw = minimum + normalized * (maximum - minimum);
        double stepped = step <= 0.0
                ? raw
                : minimum + Math.round((raw - minimum) / step) * step;
        consumer.accept(clamped(stepped));
    }

    private double normalized(double current) {
        if (maximum <= minimum) {
            return 0.0;
        }
        return (current - minimum) / (maximum - minimum);
    }

    private double clamped(double current) {
        return Math.max(minimum, Math.min(maximum, current));
    }
}
