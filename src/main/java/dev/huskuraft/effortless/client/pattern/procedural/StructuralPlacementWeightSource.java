package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;
import java.util.Set;

/**
 * Optional generator-independent material placement heuristic.
 *
 * <p>It never invents a block or expands a palette. It only adjusts existing
 * candidate weights from structural metadata supplied by a generator.</p>
 */
public final class StructuralPlacementWeightSource
        implements WeightSource<ProceduralMaterial> {

    private final StructuralPlacementMode mode;

    public StructuralPlacementWeightSource(StructuralPlacementMode mode) {
        this.mode = mode;
    }

    @Override
    public void apply(
            GenerationContext<ProceduralMaterial> context,
            List<Candidate<ProceduralMaterial>> candidates,
            double[] weights
    ) {
        if (mode != StructuralPlacementMode.SMART) {
            return;
        }
        var lookup = context.coordinateLookup();
        var position = context.position();
        var depth = lookup.sample(Coordinate.DEPTH, position);
        var thickness = lookup.sample(Coordinate.THICKNESS, position);
        var slope = lookup.sample(Coordinate.SLOPE, position);
        var tip = lookup.sample(Coordinate.TIP, position);
        var junction = lookup.sample(Coordinate.JUNCTION, position);
        if (depth.isEmpty() || thickness.isEmpty() || slope.isEmpty()
                || tip.isEmpty() || junction.isEmpty()) {
            return;
        }

        double radialDepth = depth.getAsDouble();
        double localThickness = thickness.getAsDouble();
        double localSlope = slope.getAsDouble();
        double tipProximity = tip.getAsDouble();
        double junctionProximity = junction.getAsDouble();
        var kinds = candidates.stream().map(
                StructuralPlacementWeightSource::kind
        ).toList();
        boolean hasStructural = kinds.stream().anyMatch(kind -> {
            return kind == StructuralMaterialClassifier.Kind.SOLID
                    || kind == StructuralMaterialClassifier.Kind.PILLAR;
        });
        if (!hasStructural) {
            // A deliberately all-detail palette remains usable.
            return;
        }

        adjustWeights(
                kinds, weights, radialDepth, localThickness, localSlope,
                tipProximity, junctionProximity
        );
    }

    static void adjustWeights(
            List<StructuralMaterialClassifier.Kind> kinds,
            double[] weights,
            double radialDepth,
            double localThickness,
            double localSlope,
            double tipProximity,
            double junctionProximity
    ) {
        for (int index = 0; index < kinds.size(); index++) {
            var kind = kinds.get(index);
            switch (kind) {
                case SOLID, PILLAR -> {
                    if (radialDepth < 0.58 || junctionProximity > 0.2) {
                        weights[index] *= 2.5;
                    }
                }
                case STAIR -> {
                    if (radialDepth < 0.58 || junctionProximity > 0.35) {
                        weights[index] = 0.0;
                    } else {
                        weights[index] *= 1.0
                                + localSlope * 5.0
                                + tipProximity * 1.5;
                    }
                }
                case SLAB -> {
                    if (radialDepth < 0.52 || junctionProximity > 0.35) {
                        weights[index] = 0.0;
                    } else {
                        weights[index] *= 1.0
                                + localSlope * 3.5
                                + tipProximity * 2.0;
                    }
                }
                case CONNECTOR -> {
                    boolean thinTip = tipProximity >= 0.62
                            && localThickness <= 0.34
                            && radialDepth <= 0.42;
                    if (!thinTip || junctionProximity > 0.15) {
                        weights[index] = 0.0;
                    } else {
                        weights[index] *= 2.0 + tipProximity * 4.0;
                    }
                }
                case FOLIAGE, SPECIAL -> {
                    // Foliage and skip/eraser materials keep their authored
                    // weights. Their suitability is generator-specific.
                }
            }
        }
    }

    @Override
    public List<String> validate(Set<String> availableCandidateIds) {
        return mode == null
                ? List.of("Structural placement mode must be selected")
                : List.of();
    }

    private static StructuralMaterialClassifier.Kind kind(
            Candidate<ProceduralMaterial> candidate
    ) {
        var material = candidate.value();
        if (material == null || material.isSpecial()) {
            return StructuralMaterialClassifier.Kind.SPECIAL;
        }
        var state = material.blockItem()
                .getBlock().getDefaultBlockState();
        return StructuralMaterialClassifier.classify(state);
    }
}
