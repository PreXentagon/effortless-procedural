package dev.huskuraft.effortless.client.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SequenceCompilationPlanTest {

    @Test
    void mapsCanonicalGenerationBackToExactStockOperationOrder() {
        var first = new GridPosition(0, 0, 0);
        var second = new GridPosition(1, 0, 0);
        var third = new GridPosition(0, 1, 0);
        var placements = new LinkedHashMap<GridPosition, String>();
        placements.put(first, "A");
        placements.put(second, "B");
        placements.put(third, "C");

        var result = SequenceCompilationPlan.create(
                List.of(third, first, second),
                placements
        );

        assertTrue(result.isSuccess());
        assertEquals(List.of("C", "A", "B"), result.sequence());
    }

    @Test
    void rejectsMissingOrDuplicateStockPositions() {
        var first = new GridPosition(0, 0, 0);
        var second = new GridPosition(1, 0, 0);

        var missing = SequenceCompilationPlan.create(
                List.of(first),
                Map.of(first, "A", second, "B")
        );
        var duplicate = SequenceCompilationPlan.create(
                List.of(first, first),
                Map.of(first, "A", second, "B")
        );

        assertFalse(missing.isSuccess());
        assertFalse(duplicate.isSuccess());
    }
}
