package dev.huskuraft.effortless.screen.pattern.procedural;

import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * Small visual divider used to make long inspector lists scannable.
 */
final class ProceduralSectionEntry extends SettingOptionsList.Entry<Void> {

    private final int accent;

    ProceduralSectionEntry(
            Entrance entrance,
            SettingOptionsList list,
            Text title,
            int accent
    ) {
        super(entrance, list, null);
        setMessage(title);
        this.accent = accent;
        setFocusable(false);
    }

    @Override
    public int getHeight() {
        return 18;
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        renderer.renderRect(
                getLeft() + 2,
                getTop() + 12,
                getRight() - 2,
                getTop() + 13,
                0xFF343A41
        );
        renderer.renderRect(
                getLeft() + 2,
                getTop() + 12,
                getLeft() + 30,
                getTop() + 14,
                accent
        );
        renderer.renderTextFromStart(
                getTypeface(),
                getMessage(),
                getLeft() + 4,
                getTop() + 1,
                0xFFBFC4CA,
                true
        );
    }
}
