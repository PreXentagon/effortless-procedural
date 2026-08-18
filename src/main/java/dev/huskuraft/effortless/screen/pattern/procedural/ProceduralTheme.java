package dev.huskuraft.effortless.screen.pattern.procedural;

import dev.huskuraft.universal.api.gui.Typeface;
import dev.huskuraft.universal.api.gui.AbstractContainerWidget;
import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.Text;

/**
 * Shared visual language for every procedural-workbench screen.
 */
final class ProceduralTheme {

    static final int PANEL = 0xC8121519;
    static final int CARD = 0x96171B20;
    static final int CARD_HOVER = 0xB022272D;
    static final int BORDER = 0xFF424850;
    static final int BORDER_HOVER = 0xFF717880;
    static final int GOLD = 0xFFA18450;
    static final int CYAN = 0xFF5D929E;
    static final int TEXT = 0xFFE7E9EC;
    static final int MUTED = 0xFF8B929A;

    private ProceduralTheme() {
    }

    static void renderWorkbenchBackdrop(
            Renderer renderer,
            int width,
            int height
    ) {
        renderer.renderGradientRect(
                0, 0, width, height, 0x68080A0D, 0x88080A0D
        );
    }

    static void renderWorkbenchFrame(
            Renderer renderer,
            Typeface typeface,
            Text title,
            WorkbenchScreenLayout layout
    ) {
        renderer.renderRect(
                layout.left(), layout.headerTop(),
                layout.right(), layout.headerBottom(),
                0xC816191D);
        renderer.renderRect(
                layout.left(), layout.tabsTop(),
                layout.right(), layout.bodyBottom(),
                0xB80C0F12);
        renderer.renderRect(
                layout.left(), layout.footerTop(),
                layout.right(), layout.footerBottom(),
                0xC816191D);
        renderer.renderTextFromStart(typeface, Text.text("EFFORTLESS"),
                layout.left() + 10, layout.headerTop() + 9,
                0xFFE2B55B, true);
        renderer.renderTextFromCenter(typeface, title,
                layout.centerX(), layout.headerTop() + 9,
                0xFFE4E7E9, true);
    }

    static void renderButton(
            Renderer renderer,
            Typeface typeface,
            Button button
    ) {
        if (!button.isVisible()) {
            return;
        }
        boolean available = button.isActive();
        boolean hovered = button.isHoveredOrFocused() && available;
        int left = button.getX();
        int top = button.getY();
        int right = button.getRight();
        int bottom = button.getBottom();
        String label = button.getMessage().getString();
        boolean stepper = label.equals("-") || label.equals("+");
        // Opaque by design: this pass is painted over Universal's stock
        // button, including its scrolling label.
        int background = !available
                ? 0xFF16181B
                : hovered ? 0xFF22272D : 0xFF171B20;
        int border = !available
                ? 0xFF30343A
                : hovered ? BORDER_HOVER : BORDER;
        int accent = stepper
                ? border
                : available ? GOLD : 0xFF4C4F53;

        renderer.renderRect(left, top, right, bottom, background);
        renderer.renderRect(left, top, right, top + 1, border);
        renderer.renderRect(left, top, left + 1, bottom, border);
        renderer.renderRect(right - 1, top, right, bottom, border);
        renderer.renderRect(left, bottom - (stepper ? 1 : hovered ? 3 : 2),
                right, bottom,
                accent);

        String icon = icon(label);
        int color = available ? TEXT : 0xFF696D72;
        if (stepper) {
            renderer.renderTextFromCenter(
                    typeface,
                    Text.text(label),
                    button.getCenterX(),
                    button.getCenterY() - 4,
                    color,
                    true
            );
            return;
        }
        if (button.getWidth() < 24) {
            return;
        }

        int iconSpace = icon.isEmpty() ? 0 : 14;
        if (!icon.isEmpty()) {
            renderer.renderRect(
                    left + 1,
                    top + 1,
                    Math.min(right - 1, left + iconSpace),
                    bottom - 2,
                    0x60101010
            );
            renderer.renderTextFromCenter(
                    typeface,
                    Text.text(icon),
                    left + iconSpace / 2,
                    button.getCenterY() - 4,
                    available ? 0xFFC4A66B : color,
                    true
            );
        }
        int textLeft = left + iconSpace + 3;
        int textRight = right - 3;
        int availableWidth = Math.max(1, textRight - textLeft);
        String fitted = fitLabel(
                typeface,
                compactLabel(label, availableWidth),
                availableWidth
        );
        renderer.renderTextFromCenter(
                typeface,
                Text.text(fitted),
                (textLeft + textRight) / 2,
                button.getCenterY() - 4,
                color,
                true
        );
    }

    private static String compactLabel(String label, int availableWidth) {
        if (availableWidth >= 78) {
            return label;
        }
        String normalized = label.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("save as asset")) {
            return "Save";
        }
        if (normalized.contains("delete asset")) {
            return "Delete";
        }
        if (normalized.startsWith("fallback")) {
            return "Fallback";
        }
        return label;
    }

    private static String fitLabel(
            Typeface typeface,
            String label,
            int availableWidth
    ) {
        if (typeface.measureWidth(label) <= availableWidth) {
            return label;
        }
        String suffix = "...";
        int length = label.length();
        while (length > 0
                && typeface.measureWidth(label.substring(0, length) + suffix)
                > availableWidth) {
            length--;
        }
        return length == 0 ? "" : label.substring(0, length) + suffix;
    }

    static void renderNestedButtons(
            Renderer renderer,
            Typeface typeface,
            Iterable<? extends AbstractWidget> widgets
    ) {
        for (var widget : widgets) {
            if (widget instanceof Button button) {
                renderButton(renderer, typeface, button);
            }
            if (widget instanceof AbstractContainerWidget container
                    && widget.isVisible()) {
                renderNestedButtons(
                        renderer,
                        typeface,
                        container.children()
                );
            }
        }
    }

    static Runnable suppressDirectButtonLabels(
            Iterable<? extends AbstractWidget> widgets
    ) {
        return suppressButtonLabels(widgets, false);
    }

    static Runnable suppressNestedButtonLabels(
            Iterable<? extends AbstractWidget> widgets
    ) {
        return suppressButtonLabels(widgets, true);
    }

    private static Runnable suppressButtonLabels(
            Iterable<? extends AbstractWidget> widgets,
            boolean recursive
    ) {
        var buttons = new java.util.ArrayList<Button>();
        var labels = new java.util.ArrayList<Text>();
        collectButtons(widgets, recursive, buttons, labels);
        for (var button : buttons) {
            button.setMessage(Text.empty());
        }
        return () -> {
            for (int index = 0; index < buttons.size(); index++) {
                buttons.get(index).setMessage(labels.get(index));
            }
        };
    }

    private static void collectButtons(
            Iterable<? extends AbstractWidget> widgets,
            boolean recursive,
            java.util.List<Button> buttons,
            java.util.List<Text> labels
    ) {
        for (var widget : widgets) {
            if (widget instanceof Button button) {
                buttons.add(button);
                labels.add(button.getMessage());
            }
            if (recursive
                    && widget instanceof AbstractContainerWidget container
                    && widget.isVisible()) {
                collectButtons(
                        container.children(),
                        true,
                        buttons,
                        labels
                );
            }
        }
    }

    private static String icon(String label) {
        String normalized = label.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("discard")
                || normalized.contains("cancel")
                || normalized.contains("delete")
                || normalized.contains("remove")
                || normalized.contains("clear")) {
            return "x";
        }
        if (normalized.contains("save") || normalized.contains("apply")) {
            return "*";
        }
        if (normalized.contains("back") || normalized.contains("previous")) {
            return "<";
        }
        if (normalized.contains("next") || normalized.contains("open")
                || normalized.contains("edit")) {
            return ">";
        }
        if (normalized.contains("import")) {
            return "<";
        }
        if (normalized.contains("export")) {
            return ">";
        }
        if (normalized.contains("duplicate") || normalized.contains("copy")) {
            return "=";
        }
        if (normalized.contains("add") || normalized.contains("new")) {
            return "+";
        }
        if (normalized.contains("up")) {
            return "^";
        }
        if (normalized.contains("down")) {
            return "v";
        }
        if (normalized.equals("-") || normalized.equals("+")) {
            return normalized;
        }
        return "";
    }
}
