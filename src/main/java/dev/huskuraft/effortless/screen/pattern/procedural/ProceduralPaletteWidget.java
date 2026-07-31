package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.BiConsumer;

import dev.huskuraft.effortless.client.pattern.procedural.GradientDistributionMode;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralMaterial;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralBlockEntry;
import dev.huskuraft.universal.api.core.Item;
import dev.huskuraft.universal.api.core.ItemStack;
import dev.huskuraft.universal.api.core.Items;
import dev.huskuraft.universal.api.core.ResourceLocation;
import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/**
 * Visual, ordered block palette used as both a gradient preview and selector.
 */
final class ProceduralPaletteWidget extends AbstractWidget {

    private static final int MIN_CARD_WIDTH = 42;
    private static final int HEADER_HEIGHT = 18;

    private final Supplier<List<ProceduralBlockEntry>> entries;
    private final Supplier<GradientDistributionMode> mode;
    private final IntSupplier selectedIndex;
    private final IntConsumer selectionConsumer;
    private final BiConsumer<Integer, Double> stopConsumer;
    private int hoveredIndex = -1;
    private int draggedStop = -1;

    ProceduralPaletteWidget(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            Supplier<List<ProceduralBlockEntry>> entries,
            Supplier<GradientDistributionMode> mode,
            IntSupplier selectedIndex,
            IntConsumer selectionConsumer
    ) {
        this(
                entrance,
                x,
                y,
                width,
                height,
                entries,
                mode,
                selectedIndex,
                selectionConsumer,
                null
        );
    }

    ProceduralPaletteWidget(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            Supplier<List<ProceduralBlockEntry>> entries,
            Supplier<GradientDistributionMode> mode,
            IntSupplier selectedIndex,
            IntConsumer selectionConsumer,
            BiConsumer<Integer, Double> stopConsumer
    ) {
        super(entrance, x, y, width, height, Text.text("Block palette"));
        this.entries = entries;
        this.mode = mode;
        this.selectedIndex = selectedIndex;
        this.selectionConsumer = selectionConsumer;
        this.stopConsumer = stopConsumer;
        this.focusable = true;
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        var blocks = entries.get();
        var distributionMode = mode.get();
        renderer.renderRect(
                getX(),
                getY(),
                getRight(),
                getBottom(),
                0xD20D0F12
        );
        renderer.renderRect(
                getX(),
                getY(),
                getRight(),
                getY() + 1,
                0xFF8A8A8A
        );
        renderer.renderTextFromStart(
                getTypeface(),
                Text.text("PALETTE  /  " + modeLabel(distributionMode)),
                getX() + 6,
                getY() + 5,
                0xFFE7E7E7,
                true
        );

        if (blocks.isEmpty()) {
            renderer.renderTextFromCenter(
                    getTypeface(),
                    Text.text("No block entries"),
                    getCenterX(),
                    getCenterY(),
                    0xFFAAAAAA,
                    true
            );
            hoveredIndex = -1;
            return;
        }

        int capacity = Math.max(1, getWidth() / MIN_CARD_WIDTH);
        int selected = Math.max(
                0,
                Math.min(selectedIndex.getAsInt(), blocks.size() - 1)
        );
        int first = Math.max(
                0,
                Math.min(
                        selected - capacity / 2,
                        Math.max(0, blocks.size() - capacity)
                )
        );
        int shown = Math.min(capacity, blocks.size() - first);
        int cardWidth = Math.max(1, getWidth() / shown);
        int cardY = getY() + HEADER_HEIGHT;
        int cardHeight = Math.max(
                44,
                getHeight() - HEADER_HEIGHT - 16
        );
        hoveredIndex = -1;

        for (int visible = 0; visible < shown; visible++) {
            int index = first + visible;
            var block = blocks.get(index);
            int left = getX() + visible * cardWidth;
            int right = visible == shown - 1
                    ? getRight()
                    : left + cardWidth;
            boolean isSelected = index == selected;
            boolean isHovered = mouseX >= left && mouseX < right
                    && mouseY >= cardY && mouseY < cardY + cardHeight;
            if (isHovered) {
                hoveredIndex = index;
            }

            int color = isSelected
                    ? 0xE0524630
                    : isHovered ? 0xD8363A40 : 0xC51B1E22;
            renderer.renderRect(left, cardY, right, cardY + cardHeight, color);
            renderer.renderRect(
                    left,
                    cardY,
                    right,
                    cardY + 3,
                    paletteAccent(distributionMode, index, blocks.size())
            );
            if (visible > 0) {
                renderer.renderRect(
                        left,
                        cardY + 3,
                        left + 1,
                        cardY + cardHeight,
                        0xFF55585D
                );
            }
            if (isSelected) {
                renderBorder(
                        renderer,
                        left,
                        cardY,
                        right - left,
                        cardHeight,
                        0xFFC4A66B
                );
            }

            if (ProceduralMaterial.isSpecialId(block.itemId())) {
                renderer.renderTextFromCenter(
                        getTypeface(),
                        Text.text(
                                ProceduralMaterial.SKIP_ID.equals(
                                        block.itemId()
                                ) ? "∅" : "×"
                        ),
                        (left + right) / 2,
                        cardY + 11,
                        ProceduralMaterial.SKIP_ID.equals(block.itemId())
                                ? 0xFF9AB8C2
                                : 0xFFD98282,
                        true
                );
            } else {
                var stack = resolveItem(block.itemId()).getDefaultStack();
                renderer.renderItem(
                        stack,
                        left + Math.max(1, (right - left - 18) / 2),
                        cardY + 6
                );
            }
            String label = fitLabel(
                    shortId(block.itemId()),
                    Math.max(8, right - left - 6)
            );
            renderer.renderScrollingText(
                    getTypeface(),
                    Text.text(label),
                    left + 3,
                    cardY + 27,
                    right - 3,
                    cardY + 39,
                    0xFFFFFFFF
            );
            if (cardHeight >= 52) {
                renderer.renderTextFromCenter(
                        getTypeface(),
                        Text.text(Integer.toString(index + 1)),
                        (left + right) / 2,
                        cardY + 43,
                        0xFFAAAAAA,
                        false
                );
            }
        }

        renderStopRail(renderer, mouseX, mouseY, blocks, selected);
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || hoveredIndex < 0
                || !super.onMouseClicked(mouseX, mouseY, button)) {
            return false;
        }
        selectionConsumer.accept(hoveredIndex);
        if (stopConsumer != null && mouseY >= getBottom() - 16) {
            draggedStop = hoveredIndex;
            updateStop(mouseX);
        }
        getEntrance().getClient().getSoundManager().playButtonClickSound();
        return true;
    }

    @Override
    public boolean onMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY
    ) {
        if (button != 0 || draggedStop < 0 || stopConsumer == null) {
            return false;
        }
        updateStop(mouseX);
        return true;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = draggedStop >= 0;
        draggedStop = -1;
        return consumed || super.onMouseReleased(mouseX, mouseY, button);
    }

    @Override
    public List<Text> getTooltip() {
        var blocks = entries.get();
        if (hoveredIndex < 0 || hoveredIndex >= blocks.size()) {
            return List.of();
        }
        var entry = blocks.get(hoveredIndex);
        if (ProceduralMaterial.isSpecialId(entry.itemId())) {
            var tooltip = new ArrayList<Text>();
            boolean skip = ProceduralMaterial.SKIP_ID.equals(entry.itemId());
            tooltip.add(Text.text(skip ? "Skip" : "Eraser").withStyle(
                    skip ? ChatFormatting.AQUA : ChatFormatting.RED
            ));
            tooltip.add(Text.text(
                    skip
                            ? "Leaves this generated position unchanged."
                            : "Compiles this position to a stock air update."
            ).withStyle(ChatFormatting.GRAY));
            tooltip.add(Text.text(
                    skip
                            ? "No server operation is emitted."
                            : "Server break permissions and tools still apply."
            ).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Text.empty());
            tooltip.add(Text.text("Base weight " + entry.weight())
                    .withStyle(ChatFormatting.GRAY));
            return List.copyOf(tooltip);
        }
        var stack = resolveItem(entry.itemId()).getDefaultStack();
        var tooltip = new ArrayList<>(stack.getTooltips(
                getEntrance().getClient().getPlayer(),
                ItemStack.TooltipType.ADVANCED_CREATIVE
        ));
        tooltip.add(Text.empty());
        tooltip.add(Text.text(
                "Palette position " + (hoveredIndex + 1) + " of " + blocks.size()
        ).withStyle(ChatFormatting.GOLD));
        tooltip.add(Text.text("Base weight " + entry.weight())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Text.text(
                "Gradient " + entry.gradientStart() + " -> "
                        + entry.gradientEnd()
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Text.text(
                "Noise " + entry.noiseMinimum() + " -> "
                        + entry.noiseMaximum()
        ).withStyle(ChatFormatting.GRAY));
        return List.copyOf(tooltip);
    }

    private static int paletteAccent(
            GradientDistributionMode mode,
            int index,
            int size
    ) {
        if (mode == GradientDistributionMode.WEIGHTED_ENDPOINTS) {
            return 0xFF7B8798;
        }
        double fraction = size <= 1 ? 0.0 : (double) index / (size - 1);
        int red = (int) Math.round(70 + 170 * fraction);
        int green = (int) Math.round(190 - 80 * fraction);
        int blue = (int) Math.round(220 - 130 * fraction);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private void renderStopRail(
            Renderer renderer,
            int mouseX,
            int mouseY,
            List<ProceduralBlockEntry> blocks,
            int selected
    ) {
        int left = getX() + 9;
        int right = getRight() - 9;
        int y = getBottom() - 8;
        renderer.renderRect(left, y, right, y + 2, 0xFF50545A);
        if (blocks.size() > 1) {
            renderer.renderGradientRect(
                    left,
                    y,
                    right,
                    y + 2,
                    0xFF6798A3,
                    0xFFA8815C
            );
        }
        int nearest = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (int index = 0; index < blocks.size(); index++) {
            double position = effectiveStop(blocks, index);
            int markerX = left + (int) Math.round((right - left) * position);
            int color = index == selected ? 0xFFC4A66B : 0xFFCACDD0;
            renderer.renderRect(
                    markerX - 2,
                    y - 4,
                    markerX + 3,
                    y + 6,
                    0xFF101216
            );
            renderer.renderRect(
                    markerX - 1,
                    y - 3,
                    markerX + 2,
                    y + 5,
                    color
            );
            double distance = Math.abs(mouseX - markerX);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = index;
            }
        }
        if (mouseY >= getBottom() - 16 && mouseY < getBottom()
                && mouseX >= getX() && mouseX < getRight()
                && nearest >= 0) {
            hoveredIndex = nearest;
        }
    }

    private void updateStop(double mouseX) {
        var blocks = entries.get();
        if (draggedStop < 0 || draggedStop >= blocks.size()) {
            return;
        }
        double amount = (mouseX - (getX() + 9))
                / Math.max(1.0, getWidth() - 18.0);
        double minimum = draggedStop == 0
                ? 0.0
                : effectiveStop(blocks, draggedStop - 1) + 0.001;
        double maximum = draggedStop == blocks.size() - 1
                ? 1.0
                : effectiveStop(blocks, draggedStop + 1) - 0.001;
        stopConsumer.accept(
                draggedStop,
                Math.max(minimum, Math.min(maximum, amount))
        );
    }

    static double effectiveStop(
            List<ProceduralBlockEntry> blocks,
            int index
    ) {
        double configured = blocks.get(index).gradientPosition();
        if (configured >= 0.0 && configured <= 1.0) {
            return configured;
        }
        return blocks.size() <= 1
                ? 0.0
                : (double) index / (blocks.size() - 1);
    }

    private static String modeLabel(GradientDistributionMode value) {
        return value.name().toLowerCase().replace('_', ' ');
    }

    private static String shortId(String value) {
        int separator = value.indexOf(':');
        return separator < 0 ? value : value.substring(separator + 1);
    }

    private String fitLabel(String value, int availableWidth) {
        if (getTypeface().measureWidth(value) <= availableWidth) {
            return value;
        }
        String suffix = "...";
        int length = value.length();
        while (length > 1 && getTypeface().measureWidth(
                value.substring(0, length) + suffix
        ) > availableWidth) {
            length--;
        }
        return value.substring(0, Math.max(1, length)) + suffix;
    }

    private static Item resolveItem(String itemId) {
        try {
            return Item.fromIdOptional(ResourceLocation.decompose(itemId))
                    .orElse(Items.AIR.item());
        } catch (RuntimeException exception) {
            return Items.AIR.item();
        }
    }

    private static void renderBorder(
            Renderer renderer,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        renderer.renderRect(x, y, x + width, y + 1, color);
        renderer.renderRect(x, y + height - 1, x + width, y + height, color);
        renderer.renderRect(x, y + 1, x + 1, y + height - 1, color);
        renderer.renderRect(
                x + width - 1,
                y + 1,
                x + width,
                y + height - 1,
                color
        );
    }
}
