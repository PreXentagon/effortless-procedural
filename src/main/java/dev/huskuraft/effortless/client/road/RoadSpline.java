package dev.huskuraft.effortless.client.road;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic cardinal spline with Catmull-Rom style tangents.
 *
 * <p>Tension zero is the familiar smooth Catmull-Rom curve. Increasing
 * tension reduces overshoot and approaches straight, eased segments. Samples
 * include both endpoints and carry normalized arc-length progress.</p>
 */
public final class RoadSpline {

    private RoadSpline() {
    }

    public static SampleResult sample(
            List<RoadPoint> controlPoints,
            double tension,
            double spacing
    ) {
        if (controlPoints.size() < 2) {
            return SampleResult.failure(
                    "A spline requires at least two control points"
            );
        }
        if (!Double.isFinite(tension) || tension < 0.0 || tension > 1.0) {
            return SampleResult.failure(
                    "Spline curve tension must be between 0 and 1"
            );
        }
        if (!Double.isFinite(spacing) || spacing <= 0.0) {
            return SampleResult.failure(
                    "Spline sample spacing must be greater than zero"
            );
        }

        var raw = new ArrayList<RawSample>();
        for (int segment = 0; segment < controlPoints.size() - 1; segment++) {
            var p0 = controlPoints.get(Math.max(0, segment - 1));
            var p1 = controlPoints.get(segment);
            var p2 = controlPoints.get(segment + 1);
            var p3 = controlPoints.get(
                    Math.min(controlPoints.size() - 1, segment + 2)
            );
            double estimate = Math.max(
                    p1.distance(p2),
                    (p0.distance(p1) + p1.distance(p2)
                            + p2.distance(p3)) / 3.0
            );
            int steps = Math.max(1, (int) Math.ceil(estimate / spacing));
            int firstStep = segment == 0 ? 0 : 1;
            for (int step = firstStep; step <= steps; step++) {
                double t = (double) step / (double) steps;
                raw.add(evaluate(p0, p1, p2, p3, segment, t, tension));
            }
        }

        if (raw.size() < 2) {
            return SampleResult.failure("Spline produced too few samples");
        }
        var cumulative = new double[raw.size()];
        double total = 0.0;
        for (int index = 1; index < raw.size(); index++) {
            total += raw.get(index - 1).point()
                    .distance(raw.get(index).point());
            cumulative[index] = total;
        }
        if (total <= 1.0e-9) {
            return SampleResult.failure(
                    "Spline endpoints must not occupy the same position"
            );
        }

        var result = new ArrayList<Sample>(raw.size());
        for (int index = 0; index < raw.size(); index++) {
            var sample = raw.get(index);
            result.add(new Sample(
                    sample.point(),
                    sample.tangent(),
                    cumulative[index] / total,
                    sample.segmentIndex(),
                    sample.segmentT()
            ));
        }
        return SampleResult.success(result, total);
    }

    public static Optional<ClosestSample> closest(
            List<Sample> samples,
            RoadPoint target
    ) {
        if (samples.size() < 2) {
            return Optional.empty();
        }
        ClosestSample closest = null;
        for (int index = 0; index < samples.size() - 1; index++) {
            var first = samples.get(index);
            var second = samples.get(index + 1);
            var delta = second.point().sub(first.point());
            double denominator = delta.lengthSquared();
            double local = denominator <= 1.0e-12
                    ? 0.0
                    : clamp01(target.sub(first.point()).dot(delta)
                            / denominator);
            var point = first.point().add(delta.mul(local));
            double distance = point.distance(target);
            if (closest == null || distance < closest.distance()) {
                closest = new ClosestSample(
                        point,
                        distance,
                        first.segmentIndex(),
                        first.progress() * (1.0 - local)
                                + second.progress() * local
                );
            }
        }
        return Optional.ofNullable(closest);
    }

    private static RawSample evaluate(
            RoadPoint p0,
            RoadPoint p1,
            RoadPoint p2,
            RoadPoint p3,
            int segment,
            double t,
            double tension
    ) {
        double tangentScale = (1.0 - tension) * 0.5;
        var m1 = p2.sub(p0).mul(tangentScale);
        var m2 = p3.sub(p1).mul(tangentScale);
        double t2 = t * t;
        double t3 = t2 * t;
        double h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
        double h10 = t3 - 2.0 * t2 + t;
        double h01 = -2.0 * t3 + 3.0 * t2;
        double h11 = t3 - t2;
        var point = p1.mul(h00).add(m1.mul(h10))
                .add(p2.mul(h01)).add(m2.mul(h11));

        double dh00 = 6.0 * t2 - 6.0 * t;
        double dh10 = 3.0 * t2 - 4.0 * t + 1.0;
        double dh01 = -6.0 * t2 + 6.0 * t;
        double dh11 = 3.0 * t2 - 2.0 * t;
        var tangent = p1.mul(dh00).add(m1.mul(dh10))
                .add(p2.mul(dh01)).add(m2.mul(dh11))
                .normalize();
        if (tangent.lengthSquared() <= 1.0e-12) {
            tangent = p2.sub(p1).normalize();
        }
        return new RawSample(point, tangent, segment, t);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record RawSample(
            RoadPoint point,
            RoadPoint tangent,
            int segmentIndex,
            double segmentT
    ) {
    }

    public record Sample(
            RoadPoint point,
            RoadPoint tangent,
            double progress,
            int segmentIndex,
            double segmentT
    ) {
    }

    public record ClosestSample(
            RoadPoint point,
            double distance,
            int segmentIndex,
            double progress
    ) {
    }

    public record SampleResult(
            List<Sample> samples,
            double length,
            String error
    ) {

        public SampleResult {
            samples = List.copyOf(samples);
            error = error == null ? "" : error;
        }

        public static SampleResult success(
                List<Sample> samples,
                double length
        ) {
            return new SampleResult(samples, length, "");
        }

        public static SampleResult failure(String error) {
            return new SampleResult(List.of(), 0.0, error);
        }

        public boolean isSuccess() {
            return error.isEmpty();
        }
    }
}
