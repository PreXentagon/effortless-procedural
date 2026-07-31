package dev.huskuraft.effortless.client.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable client-only tree recipe. Generated trees use bounded ranges so a
 * visible variant can safely mutate the dimensions without leaving the chosen
 * archetype. Skeleton mode retains the original point authoring profile.
 */
public record TreeGenerationConfig(
        TreeMode mode,
        TreeArchetype archetype,
        TreeVariationStrength variationStrength,
        TreeVariationSource variationSource,
        boolean styleLock,
        int variant,
        int minimumHeight,
        int maximumHeight,
        int minimumBranches,
        int maximumBranches,
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
        String trunkRecipeId,
        String branchRecipeId,
        String foliageRecipeId,
        String rootRecipeId,
        TreeProfile skeletonProfile
) {

    public static final int MAX_HEIGHT = 128;
    public static final int MAX_BRANCHES = 256;
    public static final int MAX_RADIUS = 24;
    public static final int MAX_ROOTS = 32;

    public static final TreeGenerationConfig DEFAULT = forArchetype(
            TreeArchetype.OAK
    );

    public TreeGenerationConfig {
        // Generated and Skeleton are accepted by the local serializer only as
        // legacy migration values. Tree authoring now has one predictable
        // workflow: archetype-guided geometry.
        mode = TreeMode.GUIDED;
        archetype = archetype == null ? TreeArchetype.OAK : archetype;
        variationStrength = variationStrength == null
                ? TreeVariationStrength.NATURAL
                : variationStrength;
        variationSource = variationSource == null
                ? TreeVariationSource.PLACEMENT_SEQUENCE
                : variationSource;
        trunkRecipeId = normalizeRecipe(trunkRecipeId);
        branchRecipeId = normalizeRecipe(branchRecipeId);
        foliageRecipeId = normalizeRecipe(foliageRecipeId);
        rootRecipeId = normalizeRecipe(rootRecipeId);
        skeletonProfile = skeletonProfile == null
                ? TreeProfile.DEFAULT
                : skeletonProfile;
        var errors = validateValues(
                minimumHeight, maximumHeight, minimumBranches,
                maximumBranches, baseRadius, tipRadius, trunkBend,
                branchLengthScale, branchDroop, crownRadius, crownHeight,
                foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale
        );
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    public static TreeGenerationConfig forArchetype(TreeArchetype value) {
        return switch (value) {
            case OAK -> create(value, 14, 22, 8, 14, 2, 1,
                    0.22, 1.12, 0.06, 7, 7, 0.84, 0.19, 6, 1.15);
            case BIRCH -> create(value, 15, 24, 6, 10, 1, 1,
                    0.10, 0.78, 0.02, 4, 8, 0.76, 0.23, 3, 0.65);
            case SPRUCE -> create(value, 21, 36, 18, 32, 2, 1,
                    0.06, 1.02, 0.22, 8, 23, 0.84, 0.24, 5, 0.8);
            case PINE -> create(value, 22, 38, 11, 20, 2, 1,
                    0.10, 1.18, 0.14, 8, 16, 0.76, 0.21, 4, 0.85);
            case JUNGLE -> create(value, 26, 44, 14, 28, 4, 1,
                    0.27, 1.48, 0.13, 10, 11, 0.82, 0.17, 10, 1.55);
            case DARK_OAK -> create(value, 15, 24, 14, 26, 4, 1,
                    0.25, 1.36, 0.10, 10, 9, 0.88, 0.19, 10, 1.45);
            case ACACIA -> create(value, 13, 21, 6, 11, 2, 1,
                    0.38, 1.58, -0.12, 9, 4, 0.76, 0.17, 5, 1.0);
            case MANGROVE -> create(value, 16, 27, 11, 21, 3, 1,
                    0.30, 1.38, 0.22, 9, 9, 0.80, 0.16, 14, 1.8);
            case CHERRY -> create(value, 13, 21, 9, 17, 2, 1,
                    0.23, 1.42, 0.14, 9, 5, 0.82, 0.18, 5, 1.0);
            case WILLOW -> create(value, 17, 29, 14, 25, 3, 1,
                    0.32, 1.52, 0.72, 10, 12, 0.78, 0.14, 9, 1.45);
            case PALM -> create(value, 17, 30, 8, 14, 2, 1,
                    0.34, 1.72, 0.55, 8, 4, 0.82, 0.16, 4, 0.75);
            case GIANT_FANTASY -> create(value, 34, 62, 24, 52, 6, 1,
                    0.40, 1.82, 0.26, 16, 19, 0.86, 0.12, 20, 2.0);
            case CUSTOM -> create(value, 15, 25, 9, 20, 2, 1,
                    0.24, 1.1, 0.10, 8, 9, 0.82, 0.18, 7, 1.1);
        };
    }

    public static TreeGenerationConfig legacySkeleton(TreeProfile profile) {
        var base = DEFAULT;
        return new TreeGenerationConfig(
                TreeMode.GUIDED, TreeArchetype.CUSTOM,
                TreeVariationStrength.OFF, TreeVariationSource.LOCKED,
                true, 0, base.minimumHeight, base.maximumHeight,
                base.minimumBranches, base.maximumBranches,
                profile.trunkRadius(), Math.min(profile.trunkRadius(), 1),
                0.0, 1.0, 0.0, profile.canopyRadius(),
                Math.max(1, profile.canopyRadius() * 2), 1.0, 0.2,
                0, 1.0, "", "", "", "", profile
        );
    }

    private static TreeGenerationConfig create(
            TreeArchetype archetype,
            int minHeight,
            int maxHeight,
            int minBranches,
            int maxBranches,
            int baseRadius,
            int tipRadius,
            double trunkBend,
            double branchLength,
            double branchDroop,
            int crownRadius,
            int crownHeight,
            double foliageDensity,
            double foliageNoise,
            int roots,
            double rootLength
    ) {
        return new TreeGenerationConfig(
                TreeMode.GUIDED, archetype,
                TreeVariationStrength.NATURAL,
                TreeVariationSource.PLACEMENT_SEQUENCE,
                true, 0, minHeight, maxHeight, minBranches, maxBranches,
                baseRadius, tipRadius, trunkBend, branchLength, branchDroop,
                crownRadius, crownHeight, foliageDensity, foliageNoise,
                roots, rootLength, "", "", "", "", TreeProfile.DEFAULT
        );
    }

    public List<String> validate() {
        return validateValues(
                minimumHeight, maximumHeight, minimumBranches,
                maximumBranches, baseRadius, tipRadius, trunkBend,
                branchLengthScale, branchDroop, crownRadius, crownHeight,
                foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale
        );
    }

    private static List<String> validateValues(
            int minHeight,
            int maxHeight,
            int minBranches,
            int maxBranches,
            int baseRadius,
            int tipRadius,
            double trunkBend,
            double branchLength,
            double branchDroop,
            int crownRadius,
            int crownHeight,
            double foliageDensity,
            double foliageNoise,
            int roots,
            double rootLength
    ) {
        var errors = new ArrayList<String>();
        range(errors, "Tree minimum height", minHeight, 3, MAX_HEIGHT);
        range(errors, "Tree maximum height", maxHeight, 3, MAX_HEIGHT);
        if (maxHeight < minHeight) {
            errors.add("Tree maximum height must be at least the minimum");
        }
        range(errors, "Tree minimum branches", minBranches, 0, MAX_BRANCHES);
        range(errors, "Tree maximum branches", maxBranches, 0, MAX_BRANCHES);
        if (maxBranches < minBranches) {
            errors.add("Tree maximum branches must be at least the minimum");
        }
        range(errors, "Tree base radius", baseRadius, 1, MAX_RADIUS);
        range(errors, "Tree tip radius", tipRadius, 1, MAX_RADIUS);
        if (tipRadius > baseRadius) {
            errors.add("Tree tip radius cannot exceed the base radius");
        }
        finiteRange(errors, "Tree trunk bend", trunkBend, 0.0, 2.0);
        finiteRange(errors, "Tree branch length scale", branchLength, 0.1, 4.0);
        finiteRange(errors, "Tree branch droop", branchDroop, -1.0, 2.0);
        range(errors, "Tree crown radius", crownRadius, 1, MAX_RADIUS);
        range(errors, "Tree crown height", crownHeight, 1, MAX_HEIGHT);
        finiteRange(errors, "Tree foliage density", foliageDensity, 0.0, 1.0);
        finiteRange(errors, "Tree foliage noise", foliageNoise, 0.01, 2.0);
        range(errors, "Tree root count", roots, 0, MAX_ROOTS);
        finiteRange(errors, "Tree root length", rootLength, 0.1, 4.0);
        return List.copyOf(errors);
    }

    private static void range(
            List<String> errors,
            String name,
            int value,
            int minimum,
            int maximum
    ) {
        if (value < minimum || value > maximum) {
            errors.add(name + " must be between " + minimum + " and " + maximum);
        }
    }

    private static void finiteRange(
            List<String> errors,
            String name,
            double value,
            double minimum,
            double maximum
    ) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            errors.add(name + " must be between " + minimum + " and " + maximum);
        }
    }

    private static String normalizeRecipe(String value) {
        return value == null ? "" : value.trim();
    }

    public TreeGenerationConfig withMode(TreeMode value) {
        return copy(TreeMode.GUIDED, archetype, variationStrength, variationSource,
                styleLock, variant, minimumHeight, maximumHeight,
                minimumBranches, maximumBranches, baseRadius, tipRadius,
                trunkBend, branchLengthScale, branchDroop, crownRadius,
                crownHeight, foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale, trunkRecipeId, branchRecipeId,
                foliageRecipeId, rootRecipeId, skeletonProfile);
    }

    public TreeGenerationConfig withArchetype(TreeArchetype value) {
        var themed = forArchetype(value);
        return new TreeGenerationConfig(
                mode, value, variationStrength, variationSource, styleLock,
                variant, themed.minimumHeight, themed.maximumHeight,
                themed.minimumBranches, themed.maximumBranches,
                themed.baseRadius, themed.tipRadius, themed.trunkBend,
                themed.branchLengthScale, themed.branchDroop,
                themed.crownRadius, themed.crownHeight,
                themed.foliageDensity, themed.foliageNoiseFrequency,
                themed.rootCount, themed.rootLengthScale, trunkRecipeId,
                branchRecipeId, foliageRecipeId, rootRecipeId, skeletonProfile
        );
    }

    /**
     * Changes the species-specific generation style while retaining every
     * tuned dimension, range, material link, and variation setting. This is
     * used by preview-only subtype selection; normal recipe editing continues
     * to use {@link #withArchetype(TreeArchetype)} to load useful defaults.
     */
    public TreeGenerationConfig withArchetypePreservingGeometry(
            TreeArchetype value
    ) {
        return copy(
                mode,
                value == null ? TreeArchetype.CUSTOM : value,
                variationStrength, variationSource, styleLock, variant,
                minimumHeight, maximumHeight, minimumBranches,
                maximumBranches, baseRadius, tipRadius, trunkBend,
                branchLengthScale, branchDroop, crownRadius, crownHeight,
                foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale, trunkRecipeId, branchRecipeId,
                foliageRecipeId, rootRecipeId, skeletonProfile
        );
    }

    public TreeGenerationConfig withVariationStrength(
            TreeVariationStrength value
    ) {
        return copy(mode, archetype, value, variationSource, styleLock,
                variant, minimumHeight, maximumHeight, minimumBranches,
                maximumBranches, baseRadius, tipRadius, trunkBend,
                branchLengthScale, branchDroop, crownRadius, crownHeight,
                foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale, trunkRecipeId, branchRecipeId,
                foliageRecipeId, rootRecipeId, skeletonProfile);
    }

    public TreeGenerationConfig withVariationSource(TreeVariationSource value) {
        return copy(mode, archetype, variationStrength, value, styleLock,
                variant, minimumHeight, maximumHeight, minimumBranches,
                maximumBranches, baseRadius, tipRadius, trunkBend,
                branchLengthScale, branchDroop, crownRadius, crownHeight,
                foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale, trunkRecipeId, branchRecipeId,
                foliageRecipeId, rootRecipeId, skeletonProfile);
    }

    public TreeGenerationConfig withStyleLock(boolean value) {
        return copy(mode, archetype, variationStrength, variationSource,
                value, variant, minimumHeight, maximumHeight, minimumBranches,
                maximumBranches, baseRadius, tipRadius, trunkBend,
                branchLengthScale, branchDroop, crownRadius, crownHeight,
                foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale, trunkRecipeId, branchRecipeId,
                foliageRecipeId, rootRecipeId, skeletonProfile);
    }

    public TreeGenerationConfig withVariant(int value) {
        return copy(mode, archetype, variationStrength, variationSource,
                styleLock, Math.max(0, value), minimumHeight, maximumHeight,
                minimumBranches, maximumBranches, baseRadius, tipRadius,
                trunkBend, branchLengthScale, branchDroop, crownRadius,
                crownHeight, foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale, trunkRecipeId, branchRecipeId,
                foliageRecipeId, rootRecipeId, skeletonProfile);
    }

    public TreeGenerationConfig withHeightRange(int minimum, int maximum) {
        return copy(mode, archetype, variationStrength, variationSource,
                styleLock, variant, minimum, maximum, minimumBranches,
                maximumBranches, baseRadius, tipRadius, trunkBend,
                branchLengthScale, branchDroop, crownRadius, crownHeight,
                foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale, trunkRecipeId, branchRecipeId,
                foliageRecipeId, rootRecipeId, skeletonProfile);
    }

    public TreeGenerationConfig withBranchRange(int minimum, int maximum) {
        return copy(mode, archetype, variationStrength, variationSource,
                styleLock, variant, minimumHeight, maximumHeight, minimum,
                maximum, baseRadius, tipRadius, trunkBend, branchLengthScale,
                branchDroop, crownRadius, crownHeight, foliageDensity,
                foliageNoiseFrequency, rootCount, rootLengthScale,
                trunkRecipeId, branchRecipeId, foliageRecipeId, rootRecipeId,
                skeletonProfile);
    }

    public TreeGenerationConfig withRadii(int base, int tip, int crown) {
        return copy(mode, archetype, variationStrength, variationSource,
                styleLock, variant, minimumHeight, maximumHeight,
                minimumBranches, maximumBranches, base, tip, trunkBend,
                branchLengthScale, branchDroop, crown, crownHeight,
                foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale, trunkRecipeId, branchRecipeId,
                foliageRecipeId, rootRecipeId, skeletonProfile);
    }

    public TreeGenerationConfig withShape(
            double bend,
            double branchLength,
            double droop,
            int crownHeight
    ) {
        return copy(mode, archetype, variationStrength, variationSource,
                styleLock, variant, minimumHeight, maximumHeight,
                minimumBranches, maximumBranches, baseRadius, tipRadius,
                bend, branchLength, droop, crownRadius, crownHeight,
                foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale, trunkRecipeId, branchRecipeId,
                foliageRecipeId, rootRecipeId, skeletonProfile);
    }

    public TreeGenerationConfig withFoliage(double density, double noise) {
        return copy(mode, archetype, variationStrength, variationSource,
                styleLock, variant, minimumHeight, maximumHeight,
                minimumBranches, maximumBranches, baseRadius, tipRadius,
                trunkBend, branchLengthScale, branchDroop, crownRadius,
                crownHeight, density, noise, rootCount, rootLengthScale,
                trunkRecipeId, branchRecipeId, foliageRecipeId, rootRecipeId,
                skeletonProfile);
    }

    public TreeGenerationConfig withRoots(int count, double length) {
        return copy(mode, archetype, variationStrength, variationSource,
                styleLock, variant, minimumHeight, maximumHeight,
                minimumBranches, maximumBranches, baseRadius, tipRadius,
                trunkBend, branchLengthScale, branchDroop, crownRadius,
                crownHeight, foliageDensity, foliageNoiseFrequency, count,
                length, trunkRecipeId, branchRecipeId, foliageRecipeId,
                rootRecipeId, skeletonProfile);
    }

    public TreeGenerationConfig withRecipes(
            String trunk,
            String branch,
            String foliage,
            String root
    ) {
        return copy(mode, archetype, variationStrength, variationSource,
                styleLock, variant, minimumHeight, maximumHeight,
                minimumBranches, maximumBranches, baseRadius, tipRadius,
                trunkBend, branchLengthScale, branchDroop, crownRadius,
                crownHeight, foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale, trunk, branch, foliage, root,
                skeletonProfile);
    }

    public TreeGenerationConfig withSkeletonProfile(TreeProfile value) {
        return copy(mode, archetype, variationStrength, variationSource,
                styleLock, variant, minimumHeight, maximumHeight,
                minimumBranches, maximumBranches, baseRadius, tipRadius,
                trunkBend, branchLengthScale, branchDroop, crownRadius,
                crownHeight, foliageDensity, foliageNoiseFrequency, rootCount,
                rootLengthScale, trunkRecipeId, branchRecipeId,
                foliageRecipeId, rootRecipeId, value);
    }

    private static TreeGenerationConfig copy(
            TreeMode mode,
            TreeArchetype archetype,
            TreeVariationStrength strength,
            TreeVariationSource source,
            boolean styleLock,
            int variant,
            int minHeight,
            int maxHeight,
            int minBranches,
            int maxBranches,
            int baseRadius,
            int tipRadius,
            double bend,
            double branchLength,
            double droop,
            int crownRadius,
            int crownHeight,
            double density,
            double noise,
            int roots,
            double rootLength,
            String trunkRecipe,
            String branchRecipe,
            String foliageRecipe,
            String rootRecipe,
            TreeProfile skeleton
    ) {
        return new TreeGenerationConfig(
                mode, archetype, strength, source, styleLock, variant,
                minHeight, maxHeight, minBranches, maxBranches, baseRadius,
                tipRadius, bend, branchLength, droop, crownRadius, crownHeight,
                density, noise, roots, rootLength, trunkRecipe, branchRecipe,
                foliageRecipe, rootRecipe, skeleton
        );
    }
}
