package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RunLengthSequenceTest {

    @Test
    void preservesExactSequenceAndSplitsRunsAtStockChanceLimit() {
        var original = new ArrayList<String>();
        original.addAll(java.util.Collections.nCopies(130, "stone"));
        original.addAll(java.util.Collections.nCopies(2, "cobblestone"));
        original.add("stone");

        var encoded = RunLengthSequence.encode(original, 127);

        assertEquals(
                List.of(
                        new RunLengthSequence.Run<>("stone", 127),
                        new RunLengthSequence.Run<>("stone", 3),
                        new RunLengthSequence.Run<>("cobblestone", 2),
                        new RunLengthSequence.Run<>("stone", 1)
                ),
                encoded
        );
        var restored = encoded.stream()
                .flatMap(run -> java.util.Collections.nCopies(
                        run.length(),
                        run.value()
                ).stream())
                .toList();
        assertEquals(original, restored);
    }

    @Test
    void rejectsInvalidMaximumRunLength() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RunLengthSequence.encode(List.of("stone"), 0)
        );
    }
}
