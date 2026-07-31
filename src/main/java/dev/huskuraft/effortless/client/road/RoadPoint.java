package dev.huskuraft.effortless.client.road;

/**
 * Loader-independent double precision point used by the client-only road
 * editor. Control points are normally stored at block centers.
 */
public record RoadPoint(double x, double y, double z) {

    public static final RoadPoint ZERO = new RoadPoint(0.0, 0.0, 0.0);

    public RoadPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z)) {
            throw new IllegalArgumentException(
                    "Road point coordinates must be finite"
            );
        }
    }

    public RoadPoint add(RoadPoint other) {
        return new RoadPoint(x + other.x, y + other.y, z + other.z);
    }

    public RoadPoint add(double dx, double dy, double dz) {
        return new RoadPoint(x + dx, y + dy, z + dz);
    }

    public RoadPoint sub(RoadPoint other) {
        return new RoadPoint(x - other.x, y - other.y, z - other.z);
    }

    public RoadPoint mul(double scale) {
        return new RoadPoint(x * scale, y * scale, z * scale);
    }

    public double dot(RoadPoint other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public double lengthSquared() {
        return dot(this);
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double distanceSquared(RoadPoint other) {
        return sub(other).lengthSquared();
    }

    public double distance(RoadPoint other) {
        return Math.sqrt(distanceSquared(other));
    }

    public RoadPoint normalize() {
        double length = length();
        return length <= 1.0e-12 ? ZERO : mul(1.0 / length);
    }

    public RoadPoint withX(double value) {
        return new RoadPoint(value, y, z);
    }

    public RoadPoint withY(double value) {
        return new RoadPoint(x, value, z);
    }

    public RoadPoint withZ(double value) {
        return new RoadPoint(x, y, value);
    }

    public static RoadPoint lerp(RoadPoint first, RoadPoint second, double t) {
        return first.mul(1.0 - t).add(second.mul(t));
    }
}
