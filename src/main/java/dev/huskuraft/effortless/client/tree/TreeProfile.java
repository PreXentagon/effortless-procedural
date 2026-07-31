package dev.huskuraft.effortless.client.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-local dimensions used when voxelizing an editable tree skeleton.
 */
public record TreeProfile(
        int trunkRadius,
        int branchRadius,
        int canopyRadius,
        double sampleSpacing
) {

    public static final int MAX_TRUNK_RADIUS = 8;
    public static final int MAX_BRANCH_RADIUS = 6;
    public static final int MAX_CANOPY_RADIUS = 16;
    public static final double MIN_SAMPLE_SPACING = 0.1;
    public static final double MAX_SAMPLE_SPACING = 1.0;

    public static final TreeProfile DEFAULT =
            new TreeProfile(1, 1, 3, 0.35);

    public TreeProfile {
        if (!validate(
                trunkRadius,
                branchRadius,
                canopyRadius,
                sampleSpacing
        ).isEmpty()) {
            throw new IllegalArgumentException(
                    String.join(
                            "; ",
                            validate(
                                    trunkRadius,
                                    branchRadius,
                                    canopyRadius,
                                    sampleSpacing
                            )
                    )
            );
        }
    }

    public List<String> validate() {
        return validate(
                trunkRadius,
                branchRadius,
                canopyRadius,
                sampleSpacing
        );
    }

    private static List<String> validate(
            int trunkRadius,
            int branchRadius,
            int canopyRadius,
            double sampleSpacing
    ) {
        var errors = new ArrayList<String>();
        if (trunkRadius < 1 || trunkRadius > MAX_TRUNK_RADIUS) {
            errors.add("Tree trunk radius must be between 1 and "
                    + MAX_TRUNK_RADIUS);
        }
        if (branchRadius < 1 || branchRadius > MAX_BRANCH_RADIUS) {
            errors.add("Tree branch radius must be between 1 and "
                    + MAX_BRANCH_RADIUS);
        }
        if (canopyRadius < 0 || canopyRadius > MAX_CANOPY_RADIUS) {
            errors.add("Tree canopy radius must be between 0 and "
                    + MAX_CANOPY_RADIUS);
        }
        if (!Double.isFinite(sampleSpacing)
                || sampleSpacing < MIN_SAMPLE_SPACING
                || sampleSpacing > MAX_SAMPLE_SPACING) {
            errors.add("Tree sample spacing must be between "
                    + MIN_SAMPLE_SPACING + " and " + MAX_SAMPLE_SPACING);
        }
        return List.copyOf(errors);
    }

    public TreeProfile withTrunkRadius(int value) {
        return new TreeProfile(
                value, branchRadius, canopyRadius, sampleSpacing
        );
    }

    public TreeProfile withBranchRadius(int value) {
        return new TreeProfile(
                trunkRadius, value, canopyRadius, sampleSpacing
        );
    }

    public TreeProfile withCanopyRadius(int value) {
        return new TreeProfile(
                trunkRadius, branchRadius, value, sampleSpacing
        );
    }

    public TreeProfile withSampleSpacing(double value) {
        return new TreeProfile(
                trunkRadius, branchRadius, canopyRadius, value
        );
    }
}
