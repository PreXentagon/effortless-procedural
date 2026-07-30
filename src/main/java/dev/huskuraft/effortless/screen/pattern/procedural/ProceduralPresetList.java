package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.UUID;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.universal.api.gui.container.EditableEntryList;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

final class ProceduralPresetList
        extends ProceduralEntryList<ProceduralPatternPreset> {

    private UUID activePresetId;

    ProceduralPresetList(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            UUID activePresetId
    ) {
        super(entrance, x, y, width, height);
        this.activePresetId = activePresetId;
    }

    void setActivePresetId(UUID value) {
        this.activePresetId = value;
        recreateChildren();
    }

    void selectById(UUID value) {
        for (var entry : children()) {
            if (entry.getItem().id().equals(value)) {
                setSelected(entry);
                ensureVisible(entry);
                return;
            }
        }
    }

    @Override
    protected EditableEntryList.Entry<ProceduralPatternPreset> createHolder(
            ProceduralPatternPreset item
    ) {
        return new Entry(getEntrance(), this, item);
    }

    static final class Entry extends EditableEntryList.Entry<ProceduralPatternPreset> {

        private TextWidget name;
        private TextWidget details;

        Entry(
                Entrance entrance,
                ProceduralPresetList list,
                ProceduralPatternPreset preset
        ) {
            super(entrance, list, preset);
        }

        @Override
        public void onCreate() {
            name = addWidget(new TextWidget(
                    getEntrance(),
                    getX() + 4,
                    getY() + 4,
                    Text.empty()
            ));
            details = addWidget(new TextWidget(
                    getEntrance(),
                    getX() + 4,
                    getY() + 15,
                    Text.empty()
            ));
        }

        @Override
        public void onReload() {
            boolean active = ((ProceduralPresetList) getEntryList())
                    .activePresetId.equals(getItem().id());
            name.setMessage(
                    Text.text(fit((active ? "Active - " : "") + getItem().name()))
                            .withStyle(active
                                    ? ChatFormatting.GREEN
                                    : ChatFormatting.WHITE)
            );
            details.setMessage(
                    Text.text(fit(
                            getItem().blocks().size() + " blocks, seed "
                                    + getItem().seed()
                    )).withStyle(ChatFormatting.GRAY)
            );
        }

        private String fit(String value) {
            int available = Math.max(8, getWidth() - 8);
            if (getTypeface().measureWidth(value) <= available) {
                return value;
            }
            String suffix = "...";
            int length = value.length();
            while (length > 1 && getTypeface().measureWidth(
                    value.substring(0, length) + suffix
            ) > available) {
                length--;
            }
            return value.substring(0, Math.max(1, length)) + suffix;
        }

        @Override
        public Text getNarration() {
            return Text.text(getItem().name());
        }

        @Override
        public int getHeight() {
            return 28;
        }
    }
}
