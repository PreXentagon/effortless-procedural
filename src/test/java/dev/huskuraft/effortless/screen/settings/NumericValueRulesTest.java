package dev.huskuraft.effortless.screen.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NumericValueRulesTest {

    @Test
    void decrementClampsAtStrictlyPositiveMinimumWithoutRecursion() {
        assertEquals(
                0.000001,
                NumericValueRules.step(0.1, -1.0, 0.000001, 1024.0)
        );
        assertEquals(
                "0.000001",
                NumericValueRules.format(0.000001, NumericValueRules.TYPE_DOUBLE)
        );
    }

    @Test
    void incompleteAndOutOfRangeTextNeverCommits() {
        assertTrue(NumericValueRules.isPotentialInput(
                "0.", NumericValueRules.TYPE_DOUBLE
        ));
        assertFalse(NumericValueRules.parseCommitted(
                "0.", NumericValueRules.TYPE_DOUBLE, 0.000001, 1024.0
        ).isPresent());
        assertFalse(NumericValueRules.parseCommitted(
                "", NumericValueRules.TYPE_DOUBLE, 0.000001, 1024.0
        ).isPresent());
        assertFalse(NumericValueRules.parseCommitted(
                "0", NumericValueRules.TYPE_DOUBLE, 0.000001, 1024.0
        ).isPresent());
        assertFalse(NumericValueRules.parseCommitted(
                "2048", NumericValueRules.TYPE_DOUBLE, 0.000001, 1024.0
        ).isPresent());
    }

    @Test
    void generatedHighPrecisionValuesRoundTripThroughTheInputFilter() {
        double value = 0.10526315789473684;
        String formatted = NumericValueRules.format(
                value,
                NumericValueRules.TYPE_DOUBLE
        );
        assertTrue(NumericValueRules.isPotentialInput(
                formatted,
                NumericValueRules.TYPE_DOUBLE
        ));
        assertEquals(
                value,
                NumericValueRules.parseCommitted(
                        formatted,
                        NumericValueRules.TYPE_DOUBLE,
                        -4.0,
                        4.0
                ).orElseThrow()
        );
    }

    @Test
    void invalidBoundsFailImmediately() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NumericValueRules.requireRange(2.0, 1.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> NumericValueRules.requireRange(
                        Double.NaN, 1.0
                )
        );
    }
}
