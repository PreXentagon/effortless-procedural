package dev.huskuraft.effortless.screen.pattern.procedural;

import dev.huskuraft.universal.api.gui.container.EditableEntryList;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;

/**
 * Workbench-styled list surface shared by presets, blocks, and rule rows.
 */
abstract class ProceduralEntryList<T> extends EditableEntryList<T> {

    ProceduralEntryList(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height
    ) {
        super(entrance, x, y, width, height);
    }

    @Override
    public void renderWidgetBackground(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        renderer.renderRect(
                getLeft(),
                getTop(),
                getRight(),
                getBottom(),
                0xC00C0F12
        );
        renderer.renderRect(
                getLeft(),
                getTop(),
                getRight(),
                getTop() + 1,
                ProceduralTheme.BORDER
        );
    }

    @Override
    protected void renderSelection(
            Renderer renderer,
            EditableEntryList.Entry<T> entry,
            int outerColor,
            int innerColor
    ) {
        boolean selected = getSelected() == entry;
        int accent = selected ? ProceduralTheme.GOLD
                : ProceduralTheme.BORDER_HOVER;
        renderer.renderRect(
                entry.getLeft() - 1,
                entry.getTop(),
                entry.getRight() + 1,
                entry.getBottom() - 2,
                selected ? 0x9A2A3036 : 0x7021272D
        );
        renderer.renderRect(
                entry.getLeft() - 1,
                entry.getTop(),
                entry.getLeft() + 1,
                entry.getBottom() - 2,
                accent
        );
        renderer.renderRect(
                entry.getLeft() - 1,
                entry.getBottom() - 3,
                entry.getRight() + 1,
                entry.getBottom() - 1,
                accent
        );
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        super.renderWidget(renderer, mouseX, mouseY, deltaTick);
        renderWorkbenchScrollbar(renderer);
    }

    private void renderWorkbenchScrollbar(Renderer renderer) {
        if (!isScrollbarVisible()) {
            return;
        }
        int left = getScrollbarPosition();
        int width = getScrollbarWidth();
        int maxScroll = getMaxScroll();
        int viewport = Math.max(1, y1 - y0);
        int content = Math.max(1, getMaxPosition());
        int thumb = Math.max(32, Math.min(
                viewport,
                viewport * viewport / content
        ));
        int top = maxScroll == 0
                ? y0
                : y0 + (int) Math.round(
                        getScrollAmount() * (viewport - thumb) / maxScroll
                );
        renderer.renderRect(left, y0, left + width, y1, 0xFF0B0D10);
        renderer.renderRect(
                left + 1,
                top,
                left + width - 1,
                top + thumb,
                0xFF384048
        );
        renderer.renderRect(
                left + 1,
                top,
                left + width - 1,
                top + Math.min(3, thumb),
                ProceduralTheme.GOLD
        );
    }
}
