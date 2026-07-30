package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.List;

import dev.huskuraft.effortless.client.pattern.procedural.GradientDistributionMode;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralBlockEntry;
import dev.huskuraft.universal.api.core.Item;
import dev.huskuraft.universal.api.core.ItemStack;
import dev.huskuraft.universal.api.core.Items;
import dev.huskuraft.universal.api.core.ResourceLocation;
import dev.huskuraft.universal.api.gui.Dimens;
import dev.huskuraft.universal.api.gui.container.EditableEntryList;
import dev.huskuraft.universal.api.gui.slot.ItemSlot;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

final class ProceduralBlockList
        extends ProceduralEntryList<ProceduralBlockEntry> {

    private final GradientDistributionMode gradientMode;

    ProceduralBlockList(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            GradientDistributionMode gradientMode
    ) {
        super(entrance, x, y, width, height);
        this.gradientMode = gradientMode;
    }

    @Override
    protected EditableEntryList.Entry<ProceduralBlockEntry> createHolder(
            ProceduralBlockEntry item
    ) {
        return new Entry(getEntrance(), this, item, gradientMode);
    }

    static final class Entry
            extends EditableEntryList.Entry<ProceduralBlockEntry> {

        private final GradientDistributionMode gradientMode;
        private TextWidget title;
        private TextWidget details;
        private ItemSlot itemSlot;

        Entry(
                Entrance entrance,
                ProceduralBlockList list,
                ProceduralBlockEntry item,
                GradientDistributionMode gradientMode
        ) {
            super(entrance, list, item);
            this.gradientMode = gradientMode;
        }

        @Override
        public void onCreate() {
            itemSlot = addWidget(new ItemSlot(
                    getEntrance(),
                    getX() + 2,
                    getY() + 5,
                    Dimens.SLOT_WIDTH,
                    Dimens.SLOT_HEIGHT,
                    resolveItem().getDefaultStack(),
                    Text.empty()
            ));
            title = addWidget(new TextWidget(
                    getEntrance(), getX() + 26, getY() + 4, Text.empty()
            ));
            details = addWidget(new TextWidget(
                    getEntrance(), getX() + 26, getY() + 16, Text.empty()
            ));
        }

        @Override
        public void onReload() {
            var item = resolveItem();
            itemSlot.setItemStack(item.getDefaultStack());
            title.setMessage(
                    Text.text("#" + (getEntryList().indexOf(this) + 1) + " ")
                            .append(item.getDefaultStack().getHoverName())
            );
            details.setMessage(
                    Text.text(summary()).withStyle(ChatFormatting.GRAY)
            );
            title.setWidth(getWidth() - 30);
            details.setWidth(getWidth() - 30);
        }

        @Override
        public List<Text> getTooltip() {
            var tooltips = new ArrayList<>(
                    resolveItem().getDefaultStack().getTooltips(
                            getEntrance().getClient().getPlayer(),
                            ItemStack.TooltipType.ADVANCED_CREATIVE
                    )
            );
            tooltips.add(Text.empty());
            tooltips.add(Text.text(
                    "Palette position: "
                            + (getEntryList().indexOf(this) + 1)
                            + " of " + getEntryList().items().size()
            ).withStyle(ChatFormatting.GOLD));
            tooltips.add(Text.text("Base weight: " + getItem().weight())
                    .withStyle(ChatFormatting.GRAY));
            if (gradientMode
                    == GradientDistributionMode.WEIGHTED_ENDPOINTS) {
                tooltips.add(Text.text(
                        "Gradient weights: " + getItem().gradientStart()
                                + " -> " + getItem().gradientEnd()
                ).withStyle(ChatFormatting.GRAY));
            }
            tooltips.add(Text.text(
                    "Noise multiplier: " + getItem().noiseMinimum()
                            + " -> " + getItem().noiseMaximum()
            ).withStyle(ChatFormatting.GRAY));
            return List.copyOf(tooltips);
        }

        @Override
        public Text getNarration() {
            return Text.text(getItem().itemId());
        }

        @Override
        public int getHeight() {
            return 30;
        }

        @Override
        public ProceduralBlockList getEntryList() {
            return (ProceduralBlockList) super.getEntryList();
        }

        private String summary() {
            return switch (gradientMode) {
                case WEIGHTED_ENDPOINTS ->
                        "Base " + getItem().weight() + " | endpoints "
                                + getItem().gradientStart() + " -> "
                                + getItem().gradientEnd();
                case ORDERED_BLEND ->
                        "Gradient stop | base " + getItem().weight();
                case ORDERED_BANDS ->
                        "Gradient band | base " + getItem().weight();
            };
        }

        private Item resolveItem() {
            try {
                return Item.fromIdOptional(
                        ResourceLocation.decompose(getItem().itemId())
                ).orElse(Items.AIR.item());
            } catch (RuntimeException exception) {
                return Items.AIR.item();
            }
        }
    }
}
