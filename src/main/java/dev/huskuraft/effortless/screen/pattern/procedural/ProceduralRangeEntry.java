package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import dev.huskuraft.effortless.screen.settings.BoundedNumberField;
import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * A precise typed number field and a draggable range track in one row.
 */
final class ProceduralRangeEntry
        extends SettingOptionsList.SettingsEntry<Double> {

    private final double minimum;
    private final double maximum;
    private final double step;
    private final boolean logarithmic;
    private BoundedNumberField numberField;
    private ProceduralRangeTrack track;
    private DoubleSupplier valueSupplier;

    ProceduralRangeEntry(
            Entrance entrance,
            SettingOptionsList entryList,
            Text title,
            Text symbol,
            double value,
            double minimum,
            double maximum,
            double step,
            boolean logarithmic,
            Consumer<Double> consumer
    ) {
        super(entrance, entryList, title, symbol, value, consumer);
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.logarithmic = logarithmic;
        this.valueSupplier = this::getItem;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        int fieldWidth = Math.min(72, Math.max(48, getWidth() / 3));
        numberField = addWidget(new BoundedNumberField(
                getEntrance(),
                getInnerRight() - fieldWidth,
                getTop(),
                fieldWidth,
                20,
                BoundedNumberField.TYPE_DOUBLE,
                getItem(),
                minimum,
                maximum,
                step,
                true
        ));
        numberField.setValueChangeListener(number ->
                setItem(number.doubleValue())
        );
        titleTextWidget.setWidth(
                getInnerRight() - getInnerLeft() - fieldWidth - 8
        );
        track = addWidget(new ProceduralRangeTrack(
                getEntrance(),
                getInnerLeft() + 4,
                getTop() + 22,
                Math.max(1, getInnerRight() - getInnerLeft() - 8),
                minimum,
                maximum,
                step,
                logarithmic,
                this::currentValue,
                this::setItem
        ));
    }

    @Override
    public void onTick() {
        super.onTick();
        if (numberField == null || numberField.isEditing()) {
            return;
        }
        double current = currentValue();
        if (Double.compare(
                numberField.getNumber().doubleValue(),
                current
        ) != 0) {
            numberField.setValue(current);
        }
    }

    @Override
    public int getHeight() {
        return 34;
    }

    @Override
    public void renderWidgetBackground(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        ProceduralEntryBackground.render(renderer, this,
                ProceduralTheme.GOLD);
    }

    @Override
    public void setItem(Double item) {
        super.setItem(item);
        if (numberField != null) {
            numberField.setValue(item);
        }
    }

    @Override
    public void setActive(boolean active) {
        super.setActive(active);
        if (numberField != null) {
            numberField.setActive(active);
        }
        if (track != null) {
            track.setActive(active);
        }
    }

    @Override
    public ProceduralRangeEntry setSummary(Text summary) {
        super.setSummary(summary);
        return this;
    }

    ProceduralRangeEntry setValueSupplier(DoubleSupplier supplier) {
        valueSupplier = supplier == null ? this::getItem : supplier;
        return this;
    }

    private double currentValue() {
        return valueSupplier.getAsDouble();
    }
}
