package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;
import java.util.function.BooleanSupplier;

import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.gui.tooltip.TooltipHelper;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/**
 * Flat, compact tool selector inspired by the Alt wheel visual language.
 * It avoids the scrolling label used by stock Minecraft buttons.
 */
final class WorkbenchToolTab extends AbstractWidget {

    private final Text shortLabel;
    private final Text title;
    private final Text summary;
    private final int accent;
    private final BooleanSupplier selected;
    private final Runnable action;

    WorkbenchToolTab(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            Text shortLabel,
            Text title,
            Text summary,
            int accent,
            BooleanSupplier selected,
            Runnable action
    ) {
        super(entrance, x, y, width, height, title);
        this.shortLabel = shortLabel;
        this.title = title;
        this.summary = summary;
        this.accent = accent;
        this.selected = selected;
        this.action = action;
        this.focusable = true;
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        boolean activeTool = selected.getAsBoolean();
        int background = activeTool
                ? 0xA35C5C5C
                : isHoveredOrFocused() ? 0x805C5C5C : 0x6B000000;
        int border = activeTool
                ? 0xFFD6D6D6
                : isHoveredOrFocused() ? 0xFF8B9098 : 0xFF4A4E54;
        renderer.renderRect(getX(), getY(), getRight(), getBottom(), background);
        renderer.renderRect(getX(), getY(), getRight(), getY() + 1, border);
        renderer.renderRect(
                getX(),
                getBottom() - (activeTool ? 3 : 2),
                getRight(),
                getBottom(),
                accent
        );
        renderer.renderRect(getX(), getY(), getX() + 1, getBottom(), border);
        renderer.renderRect(
                getRight() - 1,
                getY(),
                getRight(),
                getBottom(),
                border
        );
        renderer.renderTextFromCenter(
                getTypeface(),
                shortLabel,
                getCenterX(),
                getCenterY() - 4,
                activeTool ? 0xFFFFFFFF : 0xFFE8E8E8,
                true
        );
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isActive()
                || !super.onMouseClicked(mouseX, mouseY, button)) {
            return false;
        }
        action.run();
        getEntrance().getClient().getSoundManager().playButtonClickSound();
        return true;
    }

    @Override
    public List<Text> getTooltip() {
        var lines = new java.util.ArrayList<Text>();
        lines.add(title.withStyle(ChatFormatting.WHITE));
        lines.add(TooltipHelper.holdShiftForSummary());
        if (TooltipHelper.isSummaryButtonDown()) {
            lines.add(Text.empty());
            lines.addAll(TooltipHelper.wrapLines(
                    getTypeface(),
                    summary.withStyle(ChatFormatting.GRAY)
            ));
        }
        return List.copyOf(lines);
    }
}
