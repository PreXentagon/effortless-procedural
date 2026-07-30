package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;
import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralVerticalRule;
import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

final class EffortlessVerticalRuleEditScreen
        extends EffortlessProceduralScreen {

    private final Consumer<ProceduralVerticalRule> consumer;
    private ProceduralVerticalRule rule;

    EffortlessVerticalRuleEditScreen(
            Entrance entrance,
            Consumer<ProceduralVerticalRule> consumer,
            ProceduralVerticalRule rule
    ) {
        super(entrance, Text.text("Edit vertical rule"), PANEL_WIDTH_60, PANEL_HEIGHT_FULL);
        this.consumer = consumer;
        this.rule = rule;
        setDraftCommit(() -> {
            consumer.accept(this.rule);
            return true;
        });
    }

    @Override
    protected EffortlessClient getEntrance() {
        return (EffortlessClient) super.getEntrance();
    }

    @Override
    public void onCreate() {
        addWidget(new TextWidget(
                getEntrance(), getLeft() + getWidth() / 2,
                getTop() + PANEL_TITLE_HEIGHT_1 - 10,
                workbenchTitle(),
                TextWidget.Gravity.CENTER
        ));
        var options = addWidget(new ProceduralSettingOptionsList(
                getEntrance(), getLeft() + PADDINGS_H,
                getTop() + PANEL_TITLE_HEIGHT_1,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - PANEL_TITLE_HEIGHT_1 - PANEL_BUTTON_ROW_HEIGHT_1,
                false, false
        ));
        options.addSelectorEntry(
                Text.text("Required neighbor direction"),
                Text.empty(),
                List.of(Text.text("below"), Text.text("above")),
                List.of(
                        ProceduralVerticalRule.Direction.BELOW,
                        ProceduralVerticalRule.Direction.ABOVE
                ),
                rule.direction(),
                value -> rule = copy(value, rule.allowedItemIds(), rule.rejectUnresolved())
        );
        options.addSwitchEntry(
                Text.text("Reject unresolved future neighbor"),
                Text.empty(),
                rule.rejectUnresolved(),
                value -> rule = copy(rule.direction(), rule.allowedItemIds(), value)
        );
        options.addTab(
                Text.text("Allowed neighbor blocks"),
                Text.empty(),
                rule.allowedItemIds(),
                value -> rule = copy(rule.direction(), value, rule.rejectUnresolved()),
                (entry, value) -> {
                    entry.getButton().setMessage(Text.text(value.size() + " blocks"));
                    entry.getButton().setOnPressListener(b -> new EffortlessAllowedItemsScreen(
                            getEntrance(),
                            entry::setItem,
                            value
                    ).attach());
                }
        );
        addWidget(Button.builder(getEntrance(), Text.text("Discard"), b ->
                discardAndDetach()
        ).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 0f, 0.5f).build());
        addWidget(Button.builder(getEntrance(), Text.text("Back"), b ->
                detach()
        ).setBoundsGrid(getLeft(), getTop(), getWidth(), getHeight(), 0f, 0.5f, 0.5f).build());
    }

    private ProceduralVerticalRule copy(
            ProceduralVerticalRule.Direction direction,
            List<String> allowed,
            boolean rejectUnresolved
    ) {
        return new ProceduralVerticalRule(
                rule.itemId(), direction, allowed, rejectUnresolved
        );
    }
}
