package dev.huskuraft.effortless.screen.pattern.procedural;

import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.gui.tooltip.TooltipHelper;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/** A compact workbench-native status/error surface. */
final class EffortlessWorkbenchMessageScreen
        extends EffortlessProceduralScreen {

    private final Text message;

    EffortlessWorkbenchMessageScreen(
            Entrance entrance,
            Text title,
            Text message
    ) {
        super(entrance, title, PANEL_WIDTH_60, 170);
        this.message = message;
    }

    @Override
    public void onCreate() {
        addWidget(new TextWidget(
                getEntrance(),
                getLeft() + getWidth() / 2,
                getTop() + 10,
                workbenchTitle(),
                TextWidget.Gravity.CENTER
        ));
        int y = getTop() + PANEL_TITLE_HEIGHT_1 + 12;
        for (var line : TooltipHelper.wrapLines(
                getTypeface(), message, getWidth() - PADDINGS_H * 2
        )) {
            addWidget(new TextWidget(
                    getEntrance(), getLeft() + PADDINGS_H, y,
                    line.withStyle(ChatFormatting.GRAY)
            ));
            y += 11;
        }
        addWidget(Button.builder(
                getEntrance(), Text.text("Back"), button -> detach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                0f, 0f, 1f
        ).build());
    }
}
