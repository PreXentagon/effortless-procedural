package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;
import java.util.function.Consumer;

import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * Workbench selector entry with visible radio-style choices.
 */
final class ProceduralSelectorEntry<T>
        extends SettingOptionsList.SelectorEntry<T> {

    private ProceduralChoiceStrip<T> choices;

    ProceduralSelectorEntry(
            Entrance entrance,
            SettingOptionsList entryList,
            Text title,
            Text symbol,
            List<Text> messages,
            List<T> values,
            T value,
            Consumer<T> consumer
    ) {
        super(
                entrance,
                entryList,
                title,
                symbol,
                messages,
                values,
                value,
                consumer
        );
    }

    @Override
    public void onCreate() {
        super.onCreate();
        button.setVisible(false);
        titleTextWidget.setWidth(getInnerRight() - getInnerLeft() - 8);
        choices = addWidget(new ProceduralChoiceStrip<>(
                getEntrance(),
                getInnerLeft() + 4,
                getTop() + 18,
                Math.max(1, getInnerRight() - getInnerLeft() - 8),
                messages,
                values,
                this::getItem,
                this::setItem
        ));
    }

    @Override
    public int getHeight() {
        return 22 + ProceduralChoiceStrip.heightFor(values.size());
    }

    @Override
    public void renderWidgetBackground(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        renderer.renderRect(
                getInnerLeft(),
                getTop() + 1,
                getInnerRight(),
                getBottom() - 2,
                0x70101418
        );
        renderer.renderRect(
                getInnerLeft(),
                getTop() + 1,
                getInnerLeft() + 2,
                getBottom() - 2,
                ProceduralTheme.CYAN
        );
    }

    @Override
    public void setActive(boolean active) {
        super.setActive(active);
        if (choices != null) {
            choices.setActive(active);
        }
    }
}
