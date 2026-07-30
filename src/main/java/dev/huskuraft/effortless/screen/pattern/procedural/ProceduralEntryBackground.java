package dev.huskuraft.effortless.screen.pattern.procedural;

import dev.huskuraft.universal.api.gui.Widget;
import dev.huskuraft.universal.api.renderer.Renderer;

final class ProceduralEntryBackground {

    private ProceduralEntryBackground() {
    }

    static void render(Renderer renderer, Widget widget, int accent) {
        renderer.renderRect(
                widget.getLeft(),
                widget.getTop() + 1,
                widget.getRight(),
                widget.getBottom() - 2,
                0x70101418
        );
        renderer.renderRect(
                widget.getLeft(),
                widget.getTop() + 1,
                widget.getLeft() + 2,
                widget.getBottom() - 2,
                accent
        );
    }
}
