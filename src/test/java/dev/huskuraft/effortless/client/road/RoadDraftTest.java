package dev.huskuraft.effortless.client.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RoadDraftTest {

    @Test
    void supportsStableInsertMoveAndDeleteEditing() {
        var first = new RoadPoint(0.5, 1.5, 0.5);
        var second = new RoadPoint(10.5, 1.5, 0.5);
        var curve = new RoadPoint(5.5, 1.5, 4.5);

        var draft = RoadDraft.EMPTY.append(first).append(second)
                .insertAfter(0, curve);
        assertEquals(List.of(first, curve, second), draft.points());
        assertEquals(1, draft.selectedIndex());

        var moved = curve.withY(3.5);
        draft = draft.moveSelected(moved);
        assertEquals(moved, draft.points().get(1));

        draft = draft.deleteSelected();
        assertEquals(List.of(first, second), draft.points());
    }

    @Test
    void nearestPointUsesConfiguredRadius() {
        var draft = new RoadDraft(List.of(
                new RoadPoint(0.5, 0.5, 0.5),
                new RoadPoint(10.5, 0.5, 0.5)
        ), -1);

        assertEquals(
                0,
                draft.nearestPoint(
                        new RoadPoint(1.0, 0.5, 0.5),
                        1.0
                ).orElseThrow()
        );
        assertTrue(draft.nearestPoint(
                new RoadPoint(5.0, 0.5, 0.5),
                1.0
        ).isEmpty());
    }

    @Test
    void deselectionRetainsEveryControlPoint() {
        var points = List.of(
                new RoadPoint(0.5, 0.5, 0.5),
                new RoadPoint(5.5, 0.5, 3.5),
                new RoadPoint(10.5, 0.5, 0.5)
        );
        var deselected = new RoadDraft(points, 1).clearSelection();

        assertEquals(points, deselected.points());
        assertEquals(-1, deselected.selectedIndex());
    }
}
