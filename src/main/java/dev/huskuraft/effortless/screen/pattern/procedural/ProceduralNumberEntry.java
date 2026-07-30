package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.function.Consumer;

import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * Exact numeric input paired with a direct-manipulation range.
 */
final class ProceduralNumberEntry extends SettingOptionsList.NumberEntry {

    private ProceduralRangeTrack track;

    ProceduralNumberEntry(
            Entrance entrance,
            SettingOptionsList entryList,
            Text title,
            Text symbol,
            Double value,
            Double min,
            Double max,
            double step,
            Consumer<Double> consumer
    ) {
        super(
                entrance,
                entryList,
                title,
                symbol,
                value,
                min,
                max,
                step,
                consumer
        );
    }

    @Override
    public void onCreate() {
        super.onCreate();
        track = addWidget(new ProceduralRangeTrack(
                getEntrance(),
                getInnerLeft() + 4,
                getTop() + 22,
                Math.max(1, getInnerRight() - getInnerLeft() - 8),
                min,
                max,
                step,
                false,
                this::getItem,
                this::setItem
        ));
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
    public void setActive(boolean active) {
        super.setActive(active);
        if (track != null) {
            track.setActive(active);
        }
    }
}
