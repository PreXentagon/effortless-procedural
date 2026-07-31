package dev.huskuraft.effortless.client.road;

/**
 * Small deterministic geometry helpers used by in-world road tools.
 */
public final class RoadInteractionMath {

    private static final double EPSILON = 1.0e-9;

    private RoadInteractionMath() {
    }

    public static double segmentDistanceSquared(
            RoadPoint firstStart,
            RoadPoint firstEnd,
            RoadPoint secondStart,
            RoadPoint secondEnd
    ) {
        var firstDirection = firstEnd.sub(firstStart);
        var secondDirection = secondEnd.sub(secondStart);
        var offset = firstStart.sub(secondStart);
        double firstLength = firstDirection.dot(firstDirection);
        double secondLength = secondDirection.dot(secondDirection);
        double directions = secondDirection.dot(offset);
        double firstParameter;
        double secondParameter;

        if (firstLength <= EPSILON && secondLength <= EPSILON) {
            return firstStart.distanceSquared(secondStart);
        }
        if (firstLength <= EPSILON) {
            firstParameter = 0.0;
            secondParameter = clamp01(directions / secondLength);
        } else {
            double firstOffset = firstDirection.dot(offset);
            if (secondLength <= EPSILON) {
                secondParameter = 0.0;
                firstParameter = clamp01(-firstOffset / firstLength);
            } else {
                double directionDot =
                        firstDirection.dot(secondDirection);
                double denominator = firstLength * secondLength
                        - directionDot * directionDot;
                firstParameter = denominator <= EPSILON
                        ? 0.0
                        : clamp01(
                                (directionDot * directions
                                        - firstOffset * secondLength)
                                        / denominator
                        );
                secondParameter = (
                        directionDot * firstParameter + directions
                ) / secondLength;
                if (secondParameter < 0.0) {
                    secondParameter = 0.0;
                    firstParameter = clamp01(
                            -firstOffset / firstLength
                    );
                } else if (secondParameter > 1.0) {
                    secondParameter = 1.0;
                    firstParameter = clamp01(
                            (directionDot - firstOffset) / firstLength
                    );
                }
            }
        }

        var firstClosest = firstStart.add(
                firstDirection.mul(firstParameter)
        );
        var secondClosest = secondStart.add(
                secondDirection.mul(secondParameter)
        );
        return firstClosest.distanceSquared(secondClosest);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
