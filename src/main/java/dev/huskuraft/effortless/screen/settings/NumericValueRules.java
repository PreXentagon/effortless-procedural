package dev.huskuraft.effortless.screen.settings;

import java.math.BigDecimal;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * Pure numeric parsing/bounding rules shared by the settings number field and
 * its regression tests.
 */
final class NumericValueRules {

    static final int TYPE_INTEGER = 0;
    static final int TYPE_DOUBLE = 1;

    private static final Pattern INTEGER_INPUT = Pattern.compile("-?\\d*");
    private static final Pattern DOUBLE_INPUT =
            Pattern.compile("-?(?:\\d+(?:\\.\\d{0,17})?|\\.\\d{0,17})?");

    private NumericValueRules() {
    }

    static void requireRange(double minimum, double maximum) {
        if (!Double.isFinite(minimum)
                || !Double.isFinite(maximum)
                || minimum > maximum) {
            throw new IllegalArgumentException(
                    "Numeric range must be finite and minimum <= maximum"
            );
        }
    }

    static double clamp(double value, double minimum, double maximum) {
        requireRange(minimum, maximum);
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    static double step(
            double value,
            double amount,
            double minimum,
            double maximum
    ) {
        return clamp(value + amount, minimum, maximum);
    }

    static boolean isPotentialInput(String value, int type) {
        if (value == null) {
            return false;
        }
        return switch (type) {
            case TYPE_INTEGER -> INTEGER_INPUT.matcher(value).matches();
            case TYPE_DOUBLE -> DOUBLE_INPUT.matcher(value).matches();
            default -> false;
        };
    }

    static OptionalDouble parseCommitted(
            String value,
            int type,
            double minimum,
            double maximum
    ) {
        requireRange(minimum, maximum);
        if (!isPotentialInput(value, type)
                || value.isEmpty()
                || value.equals("-")
                || value.equals(".")
                || value.equals("-.")) {
            return OptionalDouble.empty();
        }
        try {
            double parsed = type == TYPE_INTEGER
                    ? Integer.parseInt(value)
                    : Double.parseDouble(value);
            if (!Double.isFinite(parsed)
                    || parsed < minimum
                    || parsed > maximum) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(parsed);
        } catch (NumberFormatException exception) {
            return OptionalDouble.empty();
        }
    }

    static String format(double value, int type) {
        if (type == TYPE_INTEGER) {
            return Integer.toString((int) Math.round(value));
        }
        return BigDecimal.valueOf(value)
                .stripTrailingZeros()
                .toPlainString();
    }
}
