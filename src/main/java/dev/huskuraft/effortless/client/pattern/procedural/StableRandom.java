package dev.huskuraft.effortless.client.pattern.procedural;

/**
 * Stateless deterministic random and noise helpers.
 *
 * <p>No shared PRNG state is used: every value is derived from the configured
 * seed, relative position, attempt and stream id.</p>
 */
public final class StableRandom {

    private StableRandom() {
    }

    public static double positionUnit(
            long seed,
            GridPosition position,
            int ordinal,
            int attempt,
            long stream
    ) {
        long value = seed;
        value = mix(value ^ 0x9E3779B97F4A7C15L * position.x());
        value = mix(value ^ 0xC2B2AE3D27D4EB4FL * position.y());
        value = mix(value ^ 0x165667B19E3779F9L * position.z());
        value = mix(value ^ 0x85EBCA77C2B2AE63L * ordinal);
        value = mix(value ^ 0x27D4EB2F165667C5L * attempt);
        value = mix(value ^ stream);
        return toUnit(value);
    }

    public static double valueNoise(
            long seed,
            double x,
            double y,
            double z,
            long stream
    ) {
        long x0 = fastFloor(x);
        long y0 = fastFloor(y);
        long z0 = fastFloor(z);
        long x1 = x0 + 1;
        long y1 = y0 + 1;
        long z1 = z0 + 1;

        double tx = smooth(x - x0);
        double ty = smooth(y - y0);
        double tz = smooth(z - z0);

        double n000 = latticeUnit(seed, x0, y0, z0, stream);
        double n100 = latticeUnit(seed, x1, y0, z0, stream);
        double n010 = latticeUnit(seed, x0, y1, z0, stream);
        double n110 = latticeUnit(seed, x1, y1, z0, stream);
        double n001 = latticeUnit(seed, x0, y0, z1, stream);
        double n101 = latticeUnit(seed, x1, y0, z1, stream);
        double n011 = latticeUnit(seed, x0, y1, z1, stream);
        double n111 = latticeUnit(seed, x1, y1, z1, stream);

        double nx00 = lerp(n000, n100, tx);
        double nx10 = lerp(n010, n110, tx);
        double nx01 = lerp(n001, n101, tx);
        double nx11 = lerp(n011, n111, tx);
        double nxy0 = lerp(nx00, nx10, ty);
        double nxy1 = lerp(nx01, nx11, ty);
        return lerp(nxy0, nxy1, tz);
    }

    public static long stableStringHash(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    public static long mixSeed(long seed, long value) {
        return mix(seed ^ mix(value));
    }

    private static double latticeUnit(
            long seed,
            long x,
            long y,
            long z,
            long stream
    ) {
        long value = seed ^ stream;
        value = mix(value ^ 0x9E3779B97F4A7C15L * x);
        value = mix(value ^ 0xC2B2AE3D27D4EB4FL * y);
        value = mix(value ^ 0x165667B19E3779F9L * z);
        return toUnit(value);
    }

    private static long fastFloor(double value) {
        long integer = (long) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double smooth(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static double toUnit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }
}
