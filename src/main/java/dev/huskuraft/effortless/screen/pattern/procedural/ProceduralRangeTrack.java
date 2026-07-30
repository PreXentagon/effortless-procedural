package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * Direct-manipulation range track paired with an exact numeric input.
 */
final class ProceduralRangeTrack extends AbstractWidget {

    private final double minimum;
    private final double maximum;
    private final double step;
    private final boolean logarithmic;
    private final DoubleSupplier value;
    private final Consumer<Double> consumer;

    ProceduralRangeTrack(
            Entrance entrance,
            int x,
            int y,
            int width,
            double minimum,
            double maximum,
            double step,
            boolean logarithmic,
            DoubleSupplier value,
            Consumer<Double> consumer
    ) {
        super(entrance, x, y, width, 9, Text.empty());
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.logarithmic = logarithmic;
        this.value = value;
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
        int trackY = getY() + 3;
        renderer.renderRect(
                getX(),
                trackY,
                getRight(),
                trackY + 3,
                0xFF22262B
        );
        int filled = (int) Math.round(normalized(value.getAsDouble())
                * getWidth());
        renderer.renderRect(
                getX(),
                trackY,
                getX() + filled,
                trackY + 3,
                isActive() ? ProceduralTheme.GOLD : 0xFF55585B
        );
        int knobX = getX() + filled;
        renderer.renderRect(
                knobX - 2,
                getY(),
                knobX + 3,
                getBottom(),
                isHoveredOrFocused() && isActive()
                        ? 0xFFFFFFFF
                        : 0xFFDADDE1
        );
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isActive()
                || !super.onMouseClicked(mouseX, mouseY, button)) {
            return false;
        }
        update(mouseX);
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
        update(mouseX);
        return true;
    }

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isActive()) {
            return false;
        }
        if (keyCode == 263) {
            consumer.accept(clamp(value.getAsDouble() - step));
            return true;
        }
        if (keyCode == 262) {
            consumer.accept(clamp(value.getAsDouble() + step));
            return true;
        }
        return false;
    }

    private void update(double mouseX) {
        double amount = Math.max(
                0.0,
                Math.min(1.0, (mouseX - getX()) / Math.max(1.0, getWidth()))
        );
        double raw = logarithmic
                ? minimum * Math.pow(maximum / minimum, amount)
                : minimum + amount * (maximum - minimum);
        double snapped = logarithmic && raw < step
                ? raw
                : minimum + Math.round((raw - minimum) / step) * step;
        consumer.accept(clamp(snapped));
    }

    private double normalized(double current) {
        if (maximum <= minimum) {
            return 0.0;
        }
        if (logarithmic) {
            return Math.max(
                    0.0,
                    Math.min(
                            1.0,
                            Math.log(clamp(current) / minimum)
                                    / Math.log(maximum / minimum)
                    )
            );
        }
        return maximum <= minimum
                ? 0.0
                : Math.max(
                        0.0,
                        Math.min(1.0, (current - minimum)
                                / (maximum - minimum))
                );
    }

    private double clamp(double current) {
        return Math.max(minimum, Math.min(maximum, current));
    }
}
