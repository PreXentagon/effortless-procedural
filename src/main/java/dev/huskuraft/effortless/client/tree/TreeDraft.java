package dev.huskuraft.effortless.client.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import dev.huskuraft.effortless.client.road.RoadPoint;

/**
 * Ordered tree skeleton: point 0 is the base, point 1 is the crown and every
 * later point is a branch endpoint. Branch roots are projected onto the trunk
 * deterministically, so moving the trunk keeps every branch attached.
 */
public record TreeDraft(List<RoadPoint> points, int selectedIndex) {

    public static final TreeDraft EMPTY = new TreeDraft(List.of(), -1);

    public TreeDraft {
        points = List.copyOf(points);
        if (selectedIndex < -1 || selectedIndex >= points.size()) {
            throw new IllegalArgumentException(
                    "Selected tree point index is outside the draft"
            );
        }
    }

    public TreeDraft append(RoadPoint point) {
        var changed = new ArrayList<>(points);
        changed.add(point);
        return new TreeDraft(changed, changed.size() - 1);
    }

    public TreeDraft select(int index) {
        return index < 0 || index >= points.size()
                ? new TreeDraft(points, -1)
                : new TreeDraft(points, index);
    }

    public TreeDraft clearSelection() {
        return new TreeDraft(points, -1);
    }

    public TreeDraft moveSelected(RoadPoint point) {
        if (selectedIndex < 0) {
            return this;
        }
        var changed = new ArrayList<>(points);
        changed.set(selectedIndex, point);
        return new TreeDraft(changed, selectedIndex);
    }

    public TreeDraft deleteSelected() {
        if (selectedIndex < 2) {
            return this;
        }
        var changed = new ArrayList<>(points);
        changed.remove(selectedIndex);
        return new TreeDraft(
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
