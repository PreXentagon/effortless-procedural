package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import dev.huskuraft.effortless.screen.settings.BoundedNumberField;
import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * Groups X/Y/Z values into one compact card instead of three disconnected
 * rows.
 */
final class ProceduralTripleRangeEntry
        extends SettingOptionsList.SettingsEntry<Void> {

    private static final List<Text> AXES = List.of(
            Text.text("X"),
            Text.text("Y"),
            Text.text("Z")
    );

    private final List<DoubleSupplier> values;
    private final List<Consumer<Double>> consumers;
    private final double minimum;
    private final double maximum;
    private final double step;
    private final boolean logarithmic;
    private final BoundedNumberField[] fields = new BoundedNumberField[3];
    private final ProceduralRangeTrack[] tracks = new ProceduralRangeTrack[3];

    ProceduralTripleRangeEntry(
            Entrance entrance,
            SettingOptionsList list,
            Text title,
            Text symbol,
            List<DoubleSupplier> values,
            double minimum,
            double maximum,
            double step,
            boolean logarithmic,
            List<Consumer<Double>> consumers
    ) {
        super(entrance, list, title, symbol, null, null);
        if (values.size() != 3 || consumers.size() != 3) {
            throw new IllegalArgumentException(
                    "A vector range requires exactly three axes"
            );
        }
        this.values = List.copyOf(values);
        this.consumers = List.copyOf(consumers);
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.logarithmic = logarithmic;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        titleTextWidget.setWidth(getInnerRight() - getInnerLeft() - 8);
        int available = getInnerRight() - getInnerLeft() - 8;
        for (int axis = 0; axis < 3; axis++) {
            int left = getInnerLeft() + 4 + axis * available / 3;
            int right = getInnerLeft() + 4 + (axis + 1) * available / 3;
            int fieldLeft = left + 10;
            int fieldWidth = Math.max(32, right - fieldLeft - 3);
            int currentAxis = axis;
            fields[axis] = addWidget(new BoundedNumberField(
                    getEntrance(),
                    fieldLeft,
                    getTop() + 17,
                    fieldWidth,
                    18,
                    BoundedNumberField.TYPE_DOUBLE,
                    values.get(axis).getAsDouble(),
                    minimum,
                    maximum,
                    step,
                    true
            ));
            fields[axis].setValueChangeListener(number ->
                    consumers.get(currentAxis).accept(number.doubleValue())
            );
            tracks[axis] = addWidget(new ProceduralRangeTrack(
                    getEntrance(),
                    left,
                    getTop() + 39,
                    Math.max(1, right - left - 3),
                    minimum,
                    maximum,
                    step,
                    logarithmic,
                    values.get(axis),
                    consumers.get(axis)
            ));
        }
    }

    @Override
    public void onTick() {
        super.onTick();
        for (int axis = 0; axis < fields.length; axis++) {
            if (fields[axis] == null || fields[axis].isEditing()) {
                continue;
            }
            double current = values.get(axis).getAsDouble();
            if (Double.compare(
                    fields[axis].getNumber().doubleValue(),
                    current
            ) != 0) {
                fields[axis].setValue(current);
            }
        }
    }

    @Override
    public int getHeight() {
        return 52;
    }

    @Override
    public void renderWidgetBackground(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        ProceduralEntryBackground.render(renderer, this,
                ProceduralTheme.CYAN);
        int available = getInnerRight() - getInnerLeft() - 8;
        for (int axis = 0; axis < 3; axis++) {
            int left = getInnerLeft() + 4 + axis * available / 3;
            renderer.renderTextFromStart(
                    getTypeface(),
                    AXES.get(axis),
                    left + 1,
                    getTop() + 21,
                    switch (axis) {
                        case 0 -> 0xFFE66A6A;
                        case 1 -> 0xFF72D477;
                        default -> 0xFF70A8E8;
                    },
                    true
            );
        }
    }

    @Override
    public void setActive(boolean active) {
        super.setActive(active);
        for (int axis = 0; axis < fields.length; axis++) {
            if (fields[axis] != null) {
                fields[axis].setActive(active);
            }
            if (tracks[axis] != null) {
                tracks[axis].setActive(active);
            }
        }
    }

    @Override
    public ProceduralTripleRangeEntry setSummary(Text summary) {
        super.setSummary(summary);
        return this;
    }
}
