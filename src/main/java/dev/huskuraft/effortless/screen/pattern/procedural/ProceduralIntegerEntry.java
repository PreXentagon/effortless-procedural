package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.function.Consumer;

import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * Integer input with the same compact range treatment as decimal settings.
 */
final class ProceduralIntegerEntry extends SettingOptionsList.IntegerEntry {

    private ProceduralRangeTrack track;

    ProceduralIntegerEntry(
            Entrance entrance,
            SettingOptionsList entryList,
            Text title,
            Text symbol,
            int value,
            int min,
            int max,
            int step,
            Consumer<Integer> consumer
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
                () -> getItem().doubleValue(),
                value -> setItem((int) Math.round(value))
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
