package dev.huskuraft.effortless.client.editor;

import java.util.List;

import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.road.RoadInteractionMath;
import dev.huskuraft.effortless.client.road.RoadPoint;
import dev.huskuraft.universal.api.core.BlockInteraction;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.Player;
import dev.huskuraft.universal.api.math.BoundingBox3d;
import dev.huskuraft.universal.api.math.Vector3d;

/** Coordinate conversion and picking algorithms shared by in-world editors. */
public final class EditorGeometry {

    private EditorGeometry() {
    }

    public static RoadPoint playerPosition(Player player) {
        var position = player.getPosition();
        return new RoadPoint(position.x(), position.y(), position.z());
    }

    public static RoadPoint targetPoint(BlockInteraction interaction) {
        var center = interaction.getBlockPosition()
                .relative(interaction.getDirection()).getCenter();
        return new RoadPoint(center.x(), center.y(), center.z());
    }

    public static BoundingBox3d pointBox(RoadPoint point) {
        return BoundingBox3d.fromLowerCornersOf(new BlockPosition(
                (int) Math.floor(point.x()),
                (int) Math.floor(point.y()),
                (int) Math.floor(point.z())
        ).toVector3i());
    }

    public static Vector3d vector(RoadPoint point) {
        return new Vector3d(point.x(), point.y(), point.z());
    }

    public static boolean matches(
            GridPosition first,
            BlockPosition second
    ) {
        return first.x() == second.x()
                && first.y() == second.y()
                && first.z() == second.z();
    }

    public static ControlAxis nearestAxis(
            RoadPoint target,
            RoadPoint center,
            double length,
            double radius
    ) {
        ControlAxis result = ControlAxis.NONE;
        double nearest = radius * radius;
        for (var axis : ControlAxis.spatial()) {
            double distance = axis.offset(center, length)
                    .distanceSquared(target);
            if (distance <= nearest) {
                nearest = distance;
                result = axis;
            }
        }
        return result;
    }

    public static ControlAxis axisFromView(
            Player player,
            RoadPoint center,
            double length,
            double reach,
            double startOffset,
            double pickRadius
    ) {
        var ray = viewRay(player, reach);
        ControlAxis best = ControlAxis.NONE;
        double bestDistance = pickRadius * pickRadius;
        for (var axis : ControlAxis.spatial()) {
            var end = axis.offset(center, length);
            if (ray.start().distance(end) > reach) {
                continue;
            }
            double distance = RoadInteractionMath.segmentDistanceSquared(
                    ray.start(), ray.end(),
                    axis.start(center, startOffset), end
            );
            if (distance <= bestDistance) {
                best = axis;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static boolean pathFromView(
            Player player,
            List<Segment> segments,
            double reach,
            double pickRadius
    ) {
        var ray = viewRay(player, reach);
        double maximum = pickRadius * pickRadius;
        return segments.stream().anyMatch(segment ->
                RoadInteractionMath.segmentDistanceSquared(
                        ray.start(), ray.end(),
                        segment.start(), segment.end()
                ) <= maximum
        );
    }

    public static double distanceToPath(
            RoadPoint point,
            List<Segment> segments
    ) {
        return Math.sqrt(segments.stream()
                .mapToDouble(segment ->
                        RoadInteractionMath.segmentDistanceSquared(
                                point, point,
                                segment.start(), segment.end()
                        ))
                .min()
                .orElse(Double.POSITIVE_INFINITY));
    }

    private static Segment viewRay(Player player, double reach) {
        var eyeVector = player.getEyePosition();
        var directionVector = player.getEyeDirection();
        var start = new RoadPoint(
                eyeVector.x(), eyeVector.y(), eyeVector.z()
        );
        var direction = new RoadPoint(
                directionVector.x(), directionVector.y(), directionVector.z()
        ).normalize();
        return new Segment(start, start.add(direction.mul(reach)));
    }

    public record Segment(RoadPoint start, RoadPoint end) {
    }
}
