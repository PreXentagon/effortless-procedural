package dev.huskuraft.effortless.screen.pattern.procedural;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import dev.huskuraft.effortless.Effortless;
import dev.huskuraft.effortless.building.structure.BuildMode;
import dev.huskuraft.universal.api.core.ResourceLocation;
import dev.huskuraft.universal.api.text.Text;

/**
 * Placement tools offered by the authoritative workbench selector.
 *
 * <p>Road deliberately lives outside {@link BuildMode}: build modes are sent
 * to the server by ordinal, while a road is compiled locally into a stock
 * clipboard snapshot.</p>
 */
enum ProceduralPreviewType {
    DISABLED(BuildMode.DISABLED),
    SINGLE(BuildMode.SINGLE),
    LINE(BuildMode.LINE),
    WALL(BuildMode.WALL),
    FLOOR(BuildMode.FLOOR),
    CUBOID(BuildMode.CUBOID),
    DIAGONAL_LINE(BuildMode.DIAGONAL_LINE),
    DIAGONAL_WALL(BuildMode.DIAGONAL_WALL),
    SLOPE_FLOOR(BuildMode.SLOPE_FLOOR),
    CIRCLE(BuildMode.CIRCLE),
    CYLINDER(BuildMode.CYLINDER),
    SPHERE(BuildMode.SPHERE),
    PYRAMID(BuildMode.PYRAMID),
    CONE(BuildMode.CONE),
    ROAD(null),
    TREE(null);

    static final List<ProceduralPreviewType> ALL = List.copyOf(
            Arrays.asList(values())
    );

    private static final Color ROAD_TINT =
            new Color(0.31f, 0.62f, 0.67f, 0.72f);
    private static final Color TREE_TINT =
            new Color(0.37f, 0.64f, 0.40f, 0.72f);

    private final BuildMode stockMode;

    ProceduralPreviewType(BuildMode stockMode) {
        this.stockMode = stockMode;
    }

    boolean isRoad() {
        return this == ROAD;
    }

    boolean isTree() {
        return this == TREE;
    }

    boolean isClientOnly() {
        return stockMode == null;
    }

    String persistentId() {
        return name().toLowerCase(Locale.ROOT);
    }

    BuildMode stockMode() {
        if (stockMode == null) {
            throw new IllegalStateException(
                    "This is a client-only preview type"
            );
        }
        return stockMode;
    }

    Text displayName() {
        return switch (this) {
            case ROAD -> Text.translate("effortless.mode.road");
            case TREE -> Text.translate("effortless.mode.tree");
            default -> stockMode.getDisplayName();
        };
    }

    ResourceLocation icon() {
        // Reuse the curved-line silhouette without introducing a new
        // server-visible mode or duplicating an existing texture asset.
        return switch (this) {
            case ROAD -> ResourceLocation.of(
                    Effortless.MOD_ID,
                    "textures/mode/diagonal_line.png"
            );
            case TREE -> ResourceLocation.of(
                    Effortless.MOD_ID,
                    "textures/mode/cone.png"
            );
            default -> stockMode.getIcon();
        };
    }

    Color tintColor() {
        return switch (this) {
            case ROAD -> ROAD_TINT;
            case TREE -> TREE_TINT;
            default -> stockMode.getTintColor();
        };
    }

    boolean hasSubtypes() {
        if (isClientOnly()) {
            return true;
        }
        return PreviewOrientation.choices(this).size() > 1
                || stockMode.getSupportedFeatures().length > 0;
    }
}
