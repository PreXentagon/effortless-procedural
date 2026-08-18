package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.function.Consumer;

import dev.huskuraft.effortless.client.pattern.procedural.ProceduralMaterial;
import dev.huskuraft.effortless.screen.item.EffortlessItemPickerScreen;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

/**
 * Selects either a registry block or one of the client-only procedural
 * materials. The special values stay local until compilation turns them into
 * an omitted operation (Skip) or an ordinary stock air update (Eraser).
 */
final class EffortlessCandidatePickerScreen extends EffortlessProceduralScreen {

    private final Consumer<String> consumer;

    EffortlessCandidatePickerScreen(
            Entrance entrance,
            String title,
            Consumer<String> consumer
    ) {
        super(
                entrance,
                Text.text(title),
                PANEL_WIDTH_60,
                PANEL_TITLE_HEIGHT_1 + PANEL_BUTTON_ROW_HEIGHT_2
        );
        this.consumer = consumer;
    }

    @Override
    public void onCreate() {
        addWidget(new TextWidget(
                getEntrance(),
                getLeft() + getWidth() / 2,
                getTop() + PANEL_TITLE_HEIGHT_1 - 10,
                workbenchTitle(),
                TextWidget.Gravity.CENTER
        ));
        addWidget(Button.builder(
                getEntrance(), Text.text("Block"), button ->
                        new EffortlessItemPickerScreen(
                                getEntrance(),
                                item -> item instanceof BlockItem,
                                item -> choose(item.getId().getString())
                        ).attach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                1f, 0f, 1f / 3f
        ).build());
        addWidget(Button.builder(
                getEntrance(), Text.text("Skip"), button -> choose(
                        ProceduralMaterial.SKIP_ID
                )
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                1f, 1f / 3f, 1f / 3f
        ).build());
        addWidget(Button.builder(
                getEntrance(), Text.text("Eraser"), button -> choose(
                        ProceduralMaterial.ERASER_ID
                )
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                1f, 2f / 3f, 1f / 3f
        ).build());
        addWidget(Button.builder(
                getEntrance(), Text.text("Cancel"), button -> detach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(),
                0f, 0f, 1f
        ).build());
    }

    private void choose(String itemId) {
        consumer.accept(itemId);
        detach();
    }
}
