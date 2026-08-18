package dev.huskuraft.effortless.screen.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class PreviewSamplingTest {

    @Test
    void keepsSmallPreviewsAndSamplesLargeOnesDeterministically() {
        var source = IntStream.range(0, 100).boxed().toList();

        assertEquals(source, PreviewSampling.evenlySpaced(source, 100));
        assertEquals(
                List.of(0, 25, 50, 74, 99),
                PreviewSampling.evenlySpaced(source, 5)
        );
        assertEquals(
                PreviewSampling.evenlySpaced(source, 7),
                PreviewSampling.evenlySpaced(source, 7)
        );
    }

    @Test
    void handlesDisabledAndSingleCellRendering() {
        var source = List.of(1, 2, 3, 4, 5);

        assertEquals(List.of(), PreviewSampling.evenlySpaced(source, 0));
        assertEquals(List.of(3), PreviewSampling.evenlySpaced(source, 1));
    }
}
