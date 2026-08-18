package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.function.BooleanSupplier;

import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;
import dev.huskuraft.effortless.screen.common.EffortlessScreen;

/**
 * Workbench-styled subview used by all focused pattern editors.
 *
 * <p>These screens deliberately retain the familiar panel coordinate helpers
 * used by the original editors, but no longer inherit the vanilla-looking
 * demo panel renderer. They are modal workbench subviews over the same
 * in-memory draft; only the root workbench persists that draft.</p>
 */
public abstract class EffortlessProceduralScreen extends EffortlessScreen {

    protected static final int PANEL_WIDTH_60 =
            AbstractPanelScreen.PANEL_WIDTH_60;
    protected static final int PANEL_HEIGHT_FULL =
            AbstractPanelScreen.PANEL_HEIGHT_FULL;
    protected static final int PANEL_BUTTON_ROW_HEIGHT_1 =
            AbstractPanelScreen.PANEL_BUTTON_ROW_HEIGHT_1;
    protected static final int PANEL_BUTTON_ROW_HEIGHT_2 =
            AbstractPanelScreen.PANEL_BUTTON_ROW_HEIGHT_2;
    protected static final int PANEL_BUTTON_ROW_HEIGHT_3 =
            AbstractPanelScreen.PANEL_BUTTON_ROW_HEIGHT_3;
    protected static final int PANEL_BUTTON_ROW_HEIGHT_4 =
            AbstractPanelScreen.PANEL_BUTTON_ROW_HEIGHT_4;
    protected static final int PANEL_BUTTON_ROW_HEIGHT_5 =
            AbstractPanelScreen.PANEL_BUTTON_ROW_HEIGHT_5;
    protected static final int PANEL_BUTTON_ROW_HEIGHT_6 =
            AbstractPanelScreen.PANEL_BUTTON_ROW_HEIGHT_6;
    protected static final int PANEL_TITLE_HEIGHT_1 =
            AbstractPanelScreen.PANEL_TITLE_HEIGHT_1;
    protected static final int PANEL_TITLE_HEIGHT_2 =
            AbstractPanelScreen.PANEL_TITLE_HEIGHT_2;
    protected static final int PADDINGS_H = AbstractPanelScreen.PADDINGS_H;
    protected static final int PADDINGS_V = AbstractPanelScreen.PADDINGS_V;
    protected static final int INNER_PADDINGS_H =
            AbstractPanelScreen.INNER_PADDINGS_H;
    protected static final int INNER_PADDINGS_V =
            AbstractPanelScreen.INNER_PADDINGS_V;

    private BooleanSupplier draftCommit = () -> true;
    private final ProceduralTooltipDelay tooltipDelay =
            new ProceduralTooltipDelay();
    private final int preferredWidth;
    private final int preferredHeight;
    private boolean discardDraft;
    private boolean closing;

    protected EffortlessProceduralScreen(
            Entrance entrance,
            Text title,
            int width,
            int height
    ) {
        super(entrance, title);
        preferredWidth = width;
        preferredHeight = height;
    }

    @Override
    public void init(int screenWidth, int screenHeight) {
        setScreenWidth(screenWidth);
        setScreenHeight(screenHeight);
        int width = Math.min(preferredWidth, Math.max(160, screenWidth - 16));
        int height = Math.min(
                preferredHeight,
                Math.max(120, screenHeight - 16)
        );
        setWidth(width);
        setHeight(height);
        setX((screenWidth - width) / 2);
        setY((screenHeight - height) / 2);
        recreate();
    }

    /**
     * Makes nested editors behave as one draft: Back/Escape applies to the
     * parent draft, while only the library screen writes the draft to disk.
     */
    protected final void setDraftCommit(BooleanSupplier value) {
        draftCommit = value;
    }

    protected final void discardAndDetach() {
        discardDraft = true;
        detach();
    }

    protected final Text workbenchTitle() {
        return getScreenTitle().withStyle(ChatFormatting.GOLD);
    }

    @Override
    public void detach() {
        if (closing) {
            return;
        }
        if (!discardDraft && !draftCommit.getAsBoolean()) {
            return;
        }
        closing = true;
        super.detach();
    }

    @Override
    public void renderWidgetBackground(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        renderer.renderGradientRect(
                0,
                0,
                getScreenWidth(),
                getScreenHeight(),
                0x68080A0D,
                0x88080A0D
        );
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        renderer.renderRect(
                getLeft() - 2,
                getTop() - 2,
                getRight() + 2,
                getBottom() + 2,
                0xFF55472B
        );
        renderer.renderRect(
                getLeft(),
                getTop(),
                getRight(),
                getBottom(),
                0xC0121519
        );
        renderer.renderRect(
                getLeft(),
                getTop(),
                getRight(),
                Math.min(getBottom(), getTop() + PANEL_TITLE_HEIGHT_1),
                0xC81A1E23
        );
        Runnable restoreLabels =
                ProceduralTheme.suppressDirectButtonLabels(children());
        try {
            super.renderWidget(renderer, mouseX, mouseY, deltaTick);
        } finally {
            restoreLabels.run();
        }
        for (var child : children()) {
            if (child instanceof Button button) {
                ProceduralTheme.renderButton(
                        renderer,
                        getTypeface(),
                        button
                );
            }
        }
    }

    @Override
    public void renderWidgetOverlay(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        for (var child : children()) {
            if (child instanceof Button button) {
                button.setTooltip(ProceduralTooltips.action(
                        getTypeface(),
                        button.getMessage()
                ));
            }
        }
        if (tooltipDelay.isReady(mouseX, mouseY)) {
            super.renderWidgetOverlay(renderer, mouseX, mouseY, deltaTick);
        }
    }
}
