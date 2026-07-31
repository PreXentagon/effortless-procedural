package dev.huskuraft.effortless.client.tree;

import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.StableRandom;
import dev.huskuraft.effortless.client.road.RoadPoint;

/** One validated, deterministic mutation of a saved tree recipe. */
public record TreeVariant(
        long seed,
        int variant,
        int height,
        int branchCount,
        int baseRadius,
        int tipRadius,
        double trunkBend,
        double branchLengthScale,
        double branchDroop,
        int crownRadius,
        int crownHeight,
        double foliageDensity,
        double foliageNoiseFrequency,
        int rootCount,
        double rootLengthScale,
        double rotation
) {

    private static final long STREAM_VARIATION = 0x545245455641524CL;

    public static TreeVariant resolve(
            TreeGenerationConfig config,
            long presetSeed,
            RoadPoint anchor,
            int visibleVariant
    ) {
        int variant = Math.max(0, visibleVariant);
        long seed = StableRandom.mixSeed(
                presetSeed,
                config.archetype().ordinal()
        );
        seed = StableRandom.mixSeed(seed, variant);
        if (config.variationSource() == TreeVariationSource.WORLD_POSITION) {
            seed = StableRandom.mixSeed(seed, Double.doubleToLongBits(anchor.x()));
            seed = StableRandom.mixSeed(seed, Double.doubleToLongBits(anchor.y()));
            seed = StableRandom.mixSeed(seed, Double.doubleToLongBits(anchor.z()));
        }
        double scale = config.variationStrength().scale();
        int height = rangedInt(
                config.minimumHeight(), config.maximumHeight(),
                unit(seed, 0), scale
        );
        int branches = rangedInt(
                config.minimumBranches(), config.maximumBranches(),
                unit(seed, 1), scale
        );
        int baseRadius = mutateInt(
                config.baseRadius(),
                config.baseRadius() >= 3 ? 1 : 0,
                unit(seed, 2),
                scale,
                1,
                TreeGenerationConfig.MAX_RADIUS
        );
        int tipRadius = Math.min(
                baseRadius,
                mutateInt(
                        config.tipRadius(), 1, unit(seed, 3), scale,
                        1, TreeGenerationConfig.MAX_RADIUS
                )
        );
        double bend = mutate(
                config.trunkBend(), 0.28, unit(seed, 4), scale, 0.0, 2.0
        );
        double length = mutate(
                config.branchLengthScale(), 0.13, unit(seed, 5), scale,
                0.1, 4.0
        );
        double droop = mutate(
                config.branchDroop(), 0.16, unit(seed, 6), scale,
                -1.0, 2.0
        );
        int crownRadius = mutateInt(
                config.crownRadius(),
                Math.max(1, (int) Math.round(config.crownRadius() * 0.10)),
                unit(seed, 7), scale, 1, TreeGenerationConfig.MAX_RADIUS
        );
        int crownHeight = mutateInt(
                config.crownHeight(),
                Math.max(1, (int) Math.round(config.crownHeight() * 0.10)),
                unit(seed, 8), scale, 1, TreeGenerationConfig.MAX_HEIGHT
        );
        double density = mutate(
                config.foliageDensity(), 0.055, unit(seed, 9), scale,
                0.05, 1.0
        );
        double noise = mutate(
                config.foliageNoiseFrequency(), 0.10, unit(seed, 10), scale,
                0.01, 2.0
        );
        int roots = mutateInt(
                config.rootCount(), config.rootCount() > 0 ? 1 : 0,
                unit(seed, 11), scale, 0, TreeGenerationConfig.MAX_ROOTS
        );
        double rootLength = mutate(
                config.rootLengthScale(), 0.12, unit(seed, 12), scale,
                0.1, 4.0
        );
        return new TreeVariant(
                seed, variant, height, branches, baseRadius, tipRadius, bend,
                length, droop, crownRadius, crownHeight, density, noise,
                roots, rootLength, unit(seed, 13) * Math.PI * 2.0
        );
    }

    public double unit(int stream) {
        return unit(seed, 100 + stream);
    }

    private static double unit(long seed, int stream) {
        return StableRandom.positionUnit(
                seed,
                new GridPosition(stream, stream * 31, stream * -17),
                stream,
                0,
                STREAM_VARIATION
        );
    }

    private static int rangedInt(
            int minimum,
            int maximum,
            double unit,
            double scale
    ) {
        if (minimum >= maximum || scale <= 0.0) {
            return (minimum + maximum) / 2;
        }
        double centered = clamp((unit - 0.5) * scale, -0.5, 0.5);
        double value = (minimum + maximum) * 0.5
                + centered * (maximum - minimum);
        return Math.max(minimum, Math.min(maximum, (int) Math.round(value)));
    }

    private static int mutateInt(
            int value,
            int amount,
            double unit,
            double scale,
            int minimum,
            int maximum
    ) {
        if (amount <= 0 || scale <= 0.0) {
            return value;
        }
        int changed = (int) Math.round(
                value + (unit * 2.0 - 1.0) * amount * scale
        );
        return Math.max(minimum, Math.min(maximum, changed));
    }

    private static double mutate(
            double value,
            double fraction,
            double unit,
            double scale,
            double minimum,
            double maximum
    ) {
        if (scale <= 0.0) {
            return value;
        }
        double changed = value * (1.0
                + (unit * 2.0 - 1.0) * fraction * scale);
        if (Math.abs(value) < 1.0e-9) {
            changed = (unit * 2.0 - 1.0) * fraction * scale;
        }
        return clamp(changed, minimum, maximum);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
