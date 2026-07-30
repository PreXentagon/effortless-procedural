package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-local transform and fractal controls for the seeded noise field.
 */
public record ProceduralNoiseConfig(
        double scaleX,
        double scaleY,
        double scaleZ,
        double offsetX,
        double offsetY,
        double offsetZ,
        double rotationDegrees,
        int octaves,
        double persistence,
        double lacunarity,
        double warpStrength,
        double warpFrequency,
        long warpSalt
) {

    public static final ProceduralNoiseConfig DEFAULT =
            new ProceduralNoiseConfig(
                    1.0,
                    1.0,
                    1.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    1,
                    0.5,
                    2.0,
                    0.0,
                    0.1,
                    0L
            );

    public ProceduralNoiseConfig withScale(double x, double y, double z) {
        return copy(x, y, z, offsetX, offsetY, offsetZ, rotationDegrees,
                octaves, persistence, lacunarity, warpStrength, warpFrequency,
                warpSalt);
    }

    public ProceduralNoiseConfig withOffset(double x, double y, double z) {
        return copy(scaleX, scaleY, scaleZ, x, y, z, rotationDegrees,
                octaves, persistence, lacunarity, warpStrength, warpFrequency,
                warpSalt);
    }

    public ProceduralNoiseConfig withRotation(double value) {
        return copy(scaleX, scaleY, scaleZ, offsetX, offsetY, offsetZ, value,
                octaves, persistence, lacunarity, warpStrength, warpFrequency,
                warpSalt);
    }

    public ProceduralNoiseConfig withFractal(
            int octaveCount,
            double persistenceValue,
            double lacunarityValue
    ) {
        return copy(scaleX, scaleY, scaleZ, offsetX, offsetY, offsetZ,
                rotationDegrees, octaveCount, persistenceValue,
                lacunarityValue, warpStrength, warpFrequency, warpSalt);
    }

    public ProceduralNoiseConfig withWarp(
            double strength,
            double frequency,
            long salt
    ) {
        return copy(scaleX, scaleY, scaleZ, offsetX, offsetY, offsetZ,
                rotationDegrees, octaves, persistence, lacunarity, strength,
                frequency, salt);
    }

    public List<String> validate() {
        var errors = new ArrayList<String>();
        validatePositive(errors, "scale X", scaleX, 64.0);
        validatePositive(errors, "scale Y", scaleY, 64.0);
        validatePositive(errors, "scale Z", scaleZ, 64.0);
        validateFinite(errors, "offset X", offsetX);
        validateFinite(errors, "offset Y", offsetY);
        validateFinite(errors, "offset Z", offsetZ);
        validateFinite(errors, "rotation", rotationDegrees);
        if (octaves < 1 || octaves > 8) {
            errors.add("Noise octaves must be between 1 and 8");
        }
        if (!Double.isFinite(persistence)
                || persistence < 0.0
                || persistence > 1.0) {
            errors.add("Noise persistence must be between 0 and 1");
        }
        if (!Double.isFinite(lacunarity)
                || lacunarity < 1.0
                || lacunarity > 8.0) {
            errors.add("Noise lacunarity must be between 1 and 8");
        }
        if (!Double.isFinite(warpStrength)
                || warpStrength < 0.0
                || warpStrength > 64.0) {
            errors.add("Noise warp strength must be between 0 and 64");
        }
        validatePositive(errors, "warp frequency", warpFrequency, 1024.0);
        return List.copyOf(errors);
    }

    private ProceduralNoiseConfig copy(
            double scaleX,
            double scaleY,
            double scaleZ,
            double offsetX,
            double offsetY,
            double offsetZ,
            double rotationDegrees,
            int octaves,
            double persistence,
            double lacunarity,
            double warpStrength,
            double warpFrequency,
            long warpSalt
    ) {
        return new ProceduralNoiseConfig(
                scaleX, scaleY, scaleZ, offsetX, offsetY, offsetZ,
                rotationDegrees, octaves, persistence, lacunarity,
                warpStrength, warpFrequency, warpSalt
        );
    }

    private static void validatePositive(
            List<String> errors,
            String name,
            double value,
            double maximum
    ) {
        if (!Double.isFinite(value) || value <= 0.0 || value > maximum) {
            errors.add("Noise " + name
                    + " must be greater than zero and at most " + maximum);
        }
    }

    private static void validateFinite(
            List<String> errors,
            String name,
            double value
    ) {
        if (!Double.isFinite(value)) {
            errors.add("Noise " + name + " must be finite");
        }
    }
}
