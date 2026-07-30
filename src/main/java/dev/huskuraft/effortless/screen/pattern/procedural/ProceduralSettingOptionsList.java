package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

final class ProceduralSettingOptionsList extends SettingOptionsList {

    ProceduralSettingOptionsList(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            boolean showIcon,
            boolean showButton
    ) {
        super(entrance, x, y, width, height, showIcon, showButton);
        setRenderSelection(false);
    }

    ProceduralSectionEntry addSection(Text title) {
        return addEntry(new ProceduralSectionEntry(
                getEntrance(),
                this,
                title,
                ProceduralTheme.GOLD
        ));
    }

    ProceduralTripleRangeEntry addTripleRangeEntry(
            Text title,
            Text symbol,
            List<DoubleSupplier> values,
            double minimum,
            double maximum,
            double step,
            boolean logarithmic,
            List<Consumer<Double>> consumers
    ) {
        var entry = addEntry(new ProceduralTripleRangeEntry(
                getEntrance(),
                this,
                title,
                symbol,
                values,
                minimum,
                maximum,
                step,
                logarithmic,
                consumers
        ));
        entry.setSummary(ProceduralTooltips.setting(title));
        return entry;
    }

    public ProceduralRangeEntry addRangeEntry(
            Text title,
            Text symbol,
            double value,
            double minimum,
            double maximum,
            double step,
            Consumer<Double> consumer
    ) {
        return addRangeEntry(
                title,
                symbol,
                value,
                minimum,
                maximum,
                step,
                false,
                consumer
        );
    }

    public ProceduralRangeEntry addRangeEntry(
            Text title,
            Text symbol,
            DoubleSupplier value,
            double minimum,
            double maximum,
            double step,
            Consumer<Double> consumer
    ) {
        return addRangeEntry(
                title,
                symbol,
                value.getAsDouble(),
                minimum,
                maximum,
                step,
                false,
                consumer
        ).setValueSupplier(value);
    }

    public ProceduralRangeEntry addLogRangeEntry(
            Text title,
            Text symbol,
            double value,
            double minimum,
            double maximum,
            double step,
            Consumer<Double> consumer
    ) {
        return addRangeEntry(
                title,
                symbol,
                value,
                minimum,
                maximum,
                step,
                true,
                consumer
        );
    }

    public ProceduralRangeEntry addLogRangeEntry(
            Text title,
            Text symbol,
            DoubleSupplier value,
            double minimum,
            double maximum,
            double step,
            Consumer<Double> consumer
    ) {
        return addRangeEntry(
                title,
                symbol,
                value.getAsDouble(),
                minimum,
                maximum,
                step,
                true,
                consumer
        ).setValueSupplier(value);
    }

    private ProceduralRangeEntry addRangeEntry(
            Text title,
            Text symbol,
            double value,
            double minimum,
            double maximum,
            double step,
            boolean logarithmic,
            Consumer<Double> consumer
    ) {
        var entry = addEntry(new ProceduralRangeEntry(
                getEntrance(),
                this,
                title,
                symbol,
                value,
                minimum,
                maximum,
                step,
                logarithmic,
                consumer
        ));
        entry.setSummary(ProceduralTooltips.setting(title));
        return entry;
    }

    @Override
    public PositionEntry addPositionEntry(
            Text title,
            Text symbol,
            Double value,
            Double min,
            Double max,
            Consumer<Double> consumer
    ) {
        var entry = super.addPositionEntry(
                title, symbol, value, min, max, consumer
        );
        entry.setSummary(ProceduralTooltips.setting(title));
        return entry;
    }

    @Override
    public NumberEntry addNumberEntry(
            Text title,
            Text symbol,
            Double value,
            Double min,
            Double max,
            Consumer<Double> consumer
    ) {
        var entry = addEntry(new ProceduralNumberEntry(
                getEntrance(),
                this,
                title,
                symbol,
                value,
                min,
                max,
                1.0,
                consumer
        ));
        entry.setSummary(ProceduralTooltips.setting(title));
        return entry;
    }

    @Override
    public NumberEntry addNumberEntry(
            Text title,
            Text symbol,
            Double value,
            Double min,
            Double max,
            double step,
            Consumer<Double> consumer
    ) {
        var entry = addEntry(new ProceduralNumberEntry(
                getEntrance(),
                this,
                title,
                symbol,
                value,
                min,
                max,
                step,
                consumer
        ));
        entry.setSummary(ProceduralTooltips.setting(title));
        return entry;
    }

    @Override
    public IntegerEntry addIntegerEntry(
            Text title,
            Text symbol,
            int value,
            int min,
            int max,
            Consumer<Integer> consumer
    ) {
        var entry = addEntry(new ProceduralIntegerEntry(
                getEntrance(),
                this,
                title,
                symbol,
                value,
                min,
                max,
                1,
                consumer
        ));
        entry.setSummary(ProceduralTooltips.setting(title));
        return entry;
    }

    @Override
    public IntegerEntry addIntegerEntry(
            Text title,
            Text symbol,
            int value,
            int min,
            int max,
            int step,
            Consumer<Integer> consumer
    ) {
        var entry = addEntry(new ProceduralIntegerEntry(
                getEntrance(),
                this,
                title,
                symbol,
                value,
                min,
                max,
                step,
                consumer
        ));
        entry.setSummary(ProceduralTooltips.setting(title));
        return entry;
    }

    @Override
    public SelectorEntry<Boolean> addSwitchEntry(
            Text title,
            Text symbol,
            Boolean value,
            Consumer<Boolean> consumer
    ) {
        var entry = addEntry(new ProceduralSelectorEntry<>(
                getEntrance(),
                this,
                title,
                symbol,
                List.of(
                        Text.translate("effortless.option.on")
                                .withStyle(ChatFormatting.GREEN),
                        Text.translate("effortless.option.off")
                                .withStyle(ChatFormatting.RED)
                ),
                List.of(Boolean.TRUE, Boolean.FALSE),
                value,
                consumer
        ));
        entry.setSummary(ProceduralTooltips.setting(title));
        entry.setValueSummaries(List.of(
                ProceduralTooltips.value(title, true),
                ProceduralTooltips.value(title, false)
        ));
        return entry;
    }

    @Override
    public <T> SelectorEntry<T> addSelectorEntry(
            Text title,
            Text symbol,
            List<Text> messages,
            List<T> values,
            T value,
            Consumer<T> consumer
    ) {
        var entry = addEntry(new ProceduralSelectorEntry<>(
                getEntrance(),
                this,
                title,
                symbol,
                messages,
                values,
                value,
                consumer
        ));
        entry.setSummary(ProceduralTooltips.setting(title));
        entry.setValueSummaries(
                values.stream()
                        .map(option -> ProceduralTooltips.value(title, option))
                        .toList()
        );
        return entry;
    }

    @Override
    public <T> ButtonEntry<T> addTab(
            Text title,
            Text symbol,
            T value,
            Consumer<T> consumer,
            BiConsumer<ButtonEntry<T>, T> buttonConsumer
    ) {
        var entry = super.addTab(
                title, symbol, value, consumer, buttonConsumer
        );
        entry.setSummary(ProceduralTooltips.setting(title));
        return entry;
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
                0xB80C0F12
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
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        Runnable restoreLabels =
                ProceduralTheme.suppressNestedButtonLabels(children());
        try {
            super.renderWidget(renderer, mouseX, mouseY, deltaTick);
        } finally {
            restoreLabels.run();
        }
        for (var entry : children()) {
            if (!entry.isVisible()) {
                continue;
            }
            ProceduralTheme.renderNestedButtons(
                    renderer,
                    getTypeface(),
                    entry.children()
            );
        }
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
                ProceduralTheme.CYAN
        );
    }
}
