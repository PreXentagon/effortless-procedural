package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.function.Function;
import java.util.function.Predicate;

import dev.huskuraft.universal.api.gui.container.EditableEntryList;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

final class TextRuleList<T> extends ProceduralEntryList<T> {

    private final Function<T, String> title;
    private final Function<T, String> details;

    TextRuleList(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            Function<T, String> title,
            Function<T, String> details
    ) {
        super(entrance, x, y, width, height);
        this.title = title;
        this.details = details;
    }

    void selectByIndex(int index) {
        if (index >= 0 && index < children().size()) {
            var entry = children().get(index);
            setSelected(entry);
            ensureVisible(entry);
        }
    }

    void moveSelection(int direction) {
        if (children().isEmpty() || direction == 0) {
            return;
        }
        int current = indexOfSelected();
        int next = current < 0
                ? 0
                : Math.clamp(current + direction, 0, children().size() - 1);
        selectByIndex(next);
    }

    void selectFirst(Predicate<T> predicate) {
        for (var entry : children()) {
            if (predicate.test(entry.getItem())) {
                setSelected(entry);
                ensureVisible(entry);
                return;
            }
        }
    }

    @Override
    protected EditableEntryList.Entry<T> createHolder(T item) {
        return new Entry<>(getEntrance(), this, item);
    }

    private static final class Entry<T> extends EditableEntryList.Entry<T> {

        private TextWidget titleWidget;
        private TextWidget detailsWidget;

        private Entry(Entrance entrance, TextRuleList<T> list, T item) {
            super(entrance, list, item);
        }

        @Override
        public void onCreate() {
            titleWidget = addWidget(new TextWidget(
                    getEntrance(), getX() + 4, getY() + 4, Text.empty()
            ));
            detailsWidget = addWidget(new TextWidget(
                    getEntrance(), getX() + 4, getY() + 15, Text.empty()
            ));
        }

        @Override
        public void onReload() {
            var list = (TextRuleList<T>) getEntryList();
            titleWidget.setMessage(Text.text(list.title.apply(getItem())));
            detailsWidget.setMessage(
                    Text.text(list.details.apply(getItem())).withStyle(ChatFormatting.GRAY)
            );
        }

        @Override
        public Text getNarration() {
            return Text.text(((TextRuleList<T>) getEntryList()).title.apply(getItem()));
        }

        @Override
        public int getHeight() {
            return 28;
        }
    }
}
