package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralNoiseConfig;

/**
 * Smooth value-noise multiplier. Each candidate has an independent, stable
 * stream derived from its id, so adding UI redraws or unrelated random calls
 * cannot perturb the result.
 */
public final class SeededNoiseSource<T> implements WeightSource<T> {

    private final double frequency;
    private final long salt;
    private final Map<String, MultiplierRange> multipliers;
    private final ProceduralNoiseConfig config;

    public SeededNoiseSource(
            double frequency,
            long salt,
            Map<String, MultiplierRange> multipliers
    ) {
        this(
                frequency,
                salt,
                multipliers,
                ProceduralNoiseConfig.DEFAULT
        );
    }

    public SeededNoiseSource(
            double frequency,
            long salt,
            Map<String, MultiplierRange> multipliers,
            ProceduralNoiseConfig config
    ) {
        this.frequency = frequency;
        this.salt = salt;
        this.multipliers = Map.copyOf(new LinkedHashMap<>(multipliers));
        this.config = config;
    }

    @Override
    public void apply(GenerationContext<T> context, List<Candidate<T>> candidates, double[] weights) {
        var position = context.position();
        double x = (position.x() + config.offsetX()) / config.scaleX();
        double y = (position.y() + config.offsetY()) / config.scaleY();
        double z = (position.z() + config.offsetZ()) / config.scaleZ();
        double radians = Math.toRadians(config.rotationDegrees());
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double rotatedX = x * cosine - z * sine;
        double rotatedZ = x * sine + z * cosine;
        x = rotatedX;
        z = rotatedZ;
        if (config.warpStrength() > 0.0) {
            double warpX = StableRandom.valueNoise(
                    context.seed(),
                    x * config.warpFrequency(),
                    y * config.warpFrequency(),
                    z * config.warpFrequency(),
                    config.warpSalt() ^ 0x6e6f6973652d7778L
            );
            double warpY = StableRandom.valueNoise(
                    context.seed(),
                    x * config.warpFrequency(),
                    y * config.warpFrequency(),
                    z * config.warpFrequency(),
                    config.warpSalt() ^ 0x6e6f6973652d7779L
            );
            double warpZ = StableRandom.valueNoise(
                    context.seed(),
                    x * config.warpFrequency(),
                    y * config.warpFrequency(),
                    z * config.warpFrequency(),
                    config.warpSalt() ^ 0x6e6f6973652d777aL
            );
            x += (warpX - 0.5) * 2.0 * config.warpStrength();
            y += (warpY - 0.5) * 2.0 * config.warpStrength();
            z += (warpZ - 0.5) * 2.0 * config.warpStrength();
        }
        for (int i = 0; i < candidates.size(); i++) {
            var candidate = candidates.get(i);
            var range = multipliers.get(candidate.id());
            if (range == null) {
                continue;
            }
            long stream = salt ^ StableRandom.stableStringHash(candidate.id());
            double noise = fractalNoise(context.seed(), x, y, z, stream);
            weights[i] *= range.minimum() + (range.maximum() - range.minimum()) * noise;
        }
    }

    @Override
    public List<String> validate(Set<String> candidateIds) {
        var errors = new java.util.ArrayList<String>();
        if (!Double.isFinite(frequency) || frequency <= 0.0) {
            errors.add("Noise frequency must be finite and greater than zero");
        }
        if (config == null) {
            errors.add("Noise configuration must be selected");
        } else {
            errors.addAll(config.validate());
        }
        for (var entry : multipliers.entrySet()) {
            if (!candidateIds.contains(entry.getKey())) {
                errors.add("Noise source references unknown candidate '" + entry.getKey() + "'");
            }
            if (!entry.getValue().isValid()) {
                errors.add(
                        "Noise multipliers for '" + entry.getKey()
                                + "' must satisfy 0 <= minimum <= maximum"
                );
            }
        }
        return List.copyOf(errors);
    }

    private double fractalNoise(
            long seed,
            double x,
            double y,
            double z,
            long stream
    ) {
        double total = 0.0;
        double amplitude = 1.0;
        double totalAmplitude = 0.0;
        double octaveFrequency = frequency;
        for (int octave = 0; octave < config.octaves(); octave++) {
            long octaveStream = octave == 0
                    ? stream
                    : StableRandom.mixSeed(stream, octave);
            total += StableRandom.valueNoise(
                    seed,
                    x * octaveFrequency,
                    y * octaveFrequency,
                    z * octaveFrequency,
                    octaveStream
            ) * amplitude;
            totalAmplitude += amplitude;
            amplitude *= config.persistence();
            octaveFrequency *= config.lacunarity();
        }
        return totalAmplitude == 0.0 ? 0.0 : total / totalAmplitude;
    }

    public record MultiplierRange(double minimum, double maximum) {

        private boolean isValid() {
            return Double.isFinite(minimum) && minimum >= 0.0
                    && Double.isFinite(maximum) && maximum >= minimum;
        }
    }
}
