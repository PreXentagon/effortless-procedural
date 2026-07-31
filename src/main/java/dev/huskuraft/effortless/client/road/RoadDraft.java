package dev.huskuraft.effortless.client.road;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Immutable ordered control-point draft. Interaction state lives in the
 * client controller; this value is safe to snapshot for undo or tests.
 */
public record RoadDraft(List<RoadPoint> points, int selectedIndex) {

    public static final RoadDraft EMPTY = new RoadDraft(List.of(), -1);

    public RoadDraft {
        points = List.copyOf(points);
        if (selectedIndex < -1 || selectedIndex >= points.size()) {
            throw new IllegalArgumentException(
                    "Selected road point index is outside the draft"
            );
        }
    }

    public RoadDraft append(RoadPoint point) {
        var changed = new ArrayList<>(points);
        changed.add(point);
        return new RoadDraft(changed, changed.size() - 1);
    }

    public RoadDraft insertAfter(int segmentIndex, RoadPoint point) {
        if (segmentIndex < 0 || segmentIndex >= points.size() - 1) {
            throw new IllegalArgumentException(
                    "Road segment index is outside the draft"
            );
        }
        var changed = new ArrayList<>(points);
        int index = segmentIndex + 1;
        changed.add(index, point);
        return new RoadDraft(changed, index);
    }

    public RoadDraft select(int index) {
        if (index < 0 || index >= points.size()) {
            return new RoadDraft(points, -1);
        }
        return new RoadDraft(points, index);
    }

    public RoadDraft clearSelection() {
        return new RoadDraft(points, -1);
    }

    public RoadDraft moveSelected(RoadPoint point) {
        if (selectedIndex < 0) {
            return this;
        }
        var changed = new ArrayList<>(points);
        changed.set(selectedIndex, point);
        return new RoadDraft(changed, selectedIndex);
    }

    public RoadDraft deleteSelected() {
        if (selectedIndex < 0 || points.size() <= 2) {
            return this;
        }
        var changed = new ArrayList<>(points);
        changed.remove(selectedIndex);
        return new RoadDraft(
                changed,
                Math.min(selectedIndex, changed.size() - 1)
        );
    }

    public OptionalInt nearestPoint(RoadPoint target, double maximumDistance) {
        int nearest = -1;
        double best = maximumDistance * maximumDistance;
        for (int index = 0; index < points.size(); index++) {
            double distance = points.get(index).distanceSquared(target);
            if (distance <= best) {
                best = distance;
                nearest = index;
            }
        }
        return nearest < 0
                ? OptionalInt.empty()
                : OptionalInt.of(nearest);
    }
}
