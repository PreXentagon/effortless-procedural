package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;
import java.util.Locale;

import dev.huskuraft.effortless.client.pattern.procedural.NeighborDirection;
import dev.huskuraft.universal.api.gui.Typeface;
import dev.huskuraft.universal.api.gui.tooltip.TooltipHelper;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/**
 * Resolves workbench tooltip copy from the language bundle. Java only decides
 * which stable key belongs to a control; all player-facing prose lives in
 * {@code assets/effortless/lang/*.json}.
 */
final class ProceduralTooltips {

    private static final String PREFIX = "effortless.procedural.tooltip.";

    private ProceduralTooltips() {
    }

    static List<Text> action(Typeface typeface, Text label) {
        String key = PREFIX + "action." + actionKey(label.getString());
        return TooltipHelper.makeSummary(
                typeface,
                label.withStyle(ChatFormatting.WHITE),
                localized(key, PREFIX + "action.generic")
                        .withStyle(ChatFormatting.GRAY)
        );
    }

    static Text setting(Text title) {
        return localized(
                PREFIX + "setting." + key(title.getString()),
                PREFIX + "setting.generic"
        );
    }

    static Text value(Text title, Object value) {
        String option;
        if (value instanceof NeighborDirection direction) {
            option = direction.name();
        } else if (value instanceof Enum<?> enumValue) {
            option = enumValue.name();
        } else {
            option = String.valueOf(value);
        }
        String translationKey = PREFIX + "value." + key(title.getString())
                + "." + key(option);
        return localized(
                translationKey,
                PREFIX + "value.generic",
                humanize(option)
        );
    }

    private static Text localized(
            String translationKey,
            String fallbackKey,
            Object... fallbackArguments
    ) {
        var translated = Text.translate(translationKey);
        if (!translated.getString().equals(translationKey)) {
            return translated;
        }
        return Text.translate(fallbackKey, fallbackArguments);
    }

    private static String actionKey(String label) {
        String plain = stripSelection(label);
        if (plain.startsWith("Procedural rules:")
                || plain.startsWith("Procedural:")) {
            return "pattern_toggle";
        }
        if (plain.startsWith("Fallback:")) {
            return "fallback";
        }
        if (plain.startsWith("Materials:")) {
            return "materials";
        }
        if (plain.endsWith(" rules") || plain.endsWith(" entries")) {
            return "open_editor";
        }
        return key(plain);
    }

    private static String stripSelection(String value) {
        if (value.length() >= 2
                && value.charAt(0) == '['
                && value.charAt(value.length() - 1) == ']') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String key(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace("+", "add_")
                .replace("<", "move_left")
                .replace(">", "move_right")
                .replace("0 = off", "optional")
                .replace("0..1", "fraction")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? "generic" : normalized;
    }

    private static String humanize(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.isEmpty()
                ? lower
                : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
