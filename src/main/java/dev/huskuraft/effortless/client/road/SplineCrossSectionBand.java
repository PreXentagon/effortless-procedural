package dev.huskuraft.effortless.client.road;

import java.util.ArrayList;
import java.util.List;

/**
 * One ordered, client-only strip appended to a spline cross-section.
 *
 * <p>Bands are measured from the legacy road edge outwards. Their order is
 * significant and therefore deliberately stored in a list.</p>
 */
public record SplineCrossSectionBand(
        String name,
        int width,
        int heightOffset,
        int depth,
        Side side,
        Placement placement,
        String recipeId
) {

    public static final int MAX_WIDTH = 32;
    public static final int MAX_DEPTH = 16;
    public static final int MAX_HEIGHT_OFFSET = 16;

    public static final SplineCrossSectionBand DEFAULT =
            new SplineCrossSectionBand(
                    "Cross-section band", 1, 0, 1,
                    Side.BOTH, Placement.AUTO, ""
            );

    public SplineCrossSectionBand {
        name = name == null || name.isBlank()
                ? "Cross-section band"
                : name.strip();
        side = side == null ? Side.BOTH : side;
        placement = placement == null ? Placement.AUTO : placement;
        recipeId = recipeId == null ? "" : recipeId.strip();
    }

    public List<String> validate() {
        var errors = new ArrayList<String>();
        if (width < 1 || width > MAX_WIDTH) {
            errors.add("Band width must be between 1 and " + MAX_WIDTH);
        }
        if (heightOffset < -MAX_HEIGHT_OFFSET
                || heightOffset > MAX_HEIGHT_OFFSET) {
            errors.add("Band height must be between -"
                    + MAX_HEIGHT_OFFSET + " and " + MAX_HEIGHT_OFFSET);
        }
        if (depth < 1 || depth > MAX_DEPTH) {
            errors.add("Band depth must be between 1 and " + MAX_DEPTH);
        }
        return List.copyOf(errors);
    }

    public SplineCrossSectionBand withName(String value) {
        return new SplineCrossSectionBand(
                value, width, heightOffset, depth, side, placement, recipeId
        );
    }

    public SplineCrossSectionBand withWidth(int value) {
        return new SplineCrossSectionBand(
                name, value, heightOffset, depth, side, placement, recipeId
        );
    }

    public SplineCrossSectionBand withHeightOffset(int value) {
        return new SplineCrossSectionBand(
                name, width, value, depth, side, placement, recipeId
        );
    }

    public SplineCrossSectionBand withDepth(int value) {
        return new SplineCrossSectionBand(
                name, width, heightOffset, value, side, placement, recipeId
        );
    }

    public SplineCrossSectionBand withSide(Side value) {
        return new SplineCrossSectionBand(
                name, width, heightOffset, depth, value, placement, recipeId
        );
    }

    public SplineCrossSectionBand withPlacement(Placement value) {
        return new SplineCrossSectionBand(
                name, width, heightOffset, depth, side, value, recipeId
        );
    }

    public SplineCrossSectionBand withRecipeId(String value) {
        return new SplineCrossSectionBand(
                name, width, heightOffset, depth, side, placement, value
        );
    }

    public enum Side {
        BOTH,
        LEFT,
        RIGHT
    }

    public enum Placement {
        AUTO,
        FULL_BLOCK,
        STAIR_INWARD,
        STAIR_OUTWARD,
        SLAB_TOP,
        SLAB_BOTTOM
    }
}
