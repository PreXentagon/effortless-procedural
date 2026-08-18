package dev.huskuraft.effortless.client.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import dev.huskuraft.effortless.client.road.RoadPoint;

/** Immutable control-point sequence shared by splines and guided generators. */
public record ControlPointDraft(List<RoadPoint> points, int selectedIndex) {

    public static final ControlPointDraft EMPTY =
            new ControlPointDraft(List.of(), -1);

    public ControlPointDraft {
        points = List.copyOf(points);
        if (selectedIndex < -1 || selectedIndex >= points.size()) {
            throw new IllegalArgumentException(
                    "Selected control point is outside the draft"
            );
        }
    }

    public ControlPointDraft append(RoadPoint point) {
        return edit(points.size(), point);
    }

    public ControlPointDraft insertAfter(int segmentIndex, RoadPoint point) {
        if (segmentIndex < 0 || segmentIndex >= points.size() - 1) {
            throw new IllegalArgumentException(
                    "Control-point segment is outside the draft"
            );
        }
        return edit(segmentIndex + 1, point);
    }

    private ControlPointDraft edit(int index, RoadPoint point) {
        var changed = new ArrayList<>(points);
        changed.add(index, point);
        return new ControlPointDraft(changed, index);
    }

    public ControlPointDraft select(int index) {
        return new ControlPointDraft(
                points,
                index >= 0 && index < points.size() ? index : -1
        );
    }

    public ControlPointDraft clearSelection() {
        return select(-1);
    }

    public ControlPointDraft moveSelected(RoadPoint point) {
        if (selectedIndex < 0) {
            return this;
        }
        var changed = new ArrayList<>(points);
        changed.set(selectedIndex, point);
        return new ControlPointDraft(changed, selectedIndex);
    }

    public ControlPointDraft deleteSelected(
            int protectedPrefix,
            int minimumPoints
    ) {
        if (selectedIndex < protectedPrefix || points.size() <= minimumPoints) {
            return this;
        }
        var changed = new ArrayList<>(points);
        changed.remove(selectedIndex);
        return new ControlPointDraft(
                changed,
                Math.min(selectedIndex, changed.size() - 1)
        );
    }

    public OptionalInt nearestPoint(
            RoadPoint target,
            double maximumDistance
    ) {
        int nearest = -1;
        double best = maximumDistance * maximumDistance;
        for (int index = 0; index < points.size(); index++) {
            double distance = points.get(index).distanceSquared(target);
            if (distance <= best) {
                best = distance;
                nearest = index;
            }
        }
        return nearest < 0 ? OptionalInt.empty() : OptionalInt.of(nearest);
    }
}
