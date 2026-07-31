package dev.huskuraft.effortless.client.road;

import java.util.ArrayList;
import java.util.List;

/** Client-only extrusion settings for eraser-selected spline damage. */
public record SplineCutoutConfig(
        boolean enabled,
        int depth,
        int wallThickness,
        double taperPerLayer,
        boolean lineWalls,
        boolean lineFloor,
        String wallRecipeId,
        String floorRecipeId
) {

    public static final int MAX_DEPTH = 64;
    public static final int MAX_WALL_THICKNESS = 8;

    public static final SplineCutoutConfig DEFAULT = new SplineCutoutConfig(
            false, 4, 1, 0.0, true, true, "", ""
    );

    public SplineCutoutConfig {
        wallRecipeId = wallRecipeId == null ? "" : wallRecipeId.strip();
        floorRecipeId = floorRecipeId == null ? "" : floorRecipeId.strip();
    }

    public List<String> validate() {
        var errors = new ArrayList<String>();
        if (depth < 1 || depth > MAX_DEPTH) {
            errors.add("Cutout depth must be between 1 and " + MAX_DEPTH);
        }
        if (wallThickness < 0 || wallThickness > MAX_WALL_THICKNESS) {
            errors.add("Cutout wall thickness must be between 0 and "
                    + MAX_WALL_THICKNESS);
        }
        if (!Double.isFinite(taperPerLayer)
                || taperPerLayer < 0.0 || taperPerLayer > 1.0) {
            errors.add("Cutout taper must be between 0 and 1");
        }
        return List.copyOf(errors);
    }

    public SplineCutoutConfig withEnabled(boolean value) {
        return new SplineCutoutConfig(
                value, depth, wallThickness, taperPerLayer,
                lineWalls, lineFloor, wallRecipeId, floorRecipeId
        );
    }

    public SplineCutoutConfig withDepth(int value) {
        return new SplineCutoutConfig(
                enabled, value, wallThickness, taperPerLayer,
                lineWalls, lineFloor, wallRecipeId, floorRecipeId
        );
    }

    public SplineCutoutConfig withWallThickness(int value) {
        return new SplineCutoutConfig(
                enabled, depth, value, taperPerLayer,
                lineWalls, lineFloor, wallRecipeId, floorRecipeId
        );
    }

    public SplineCutoutConfig withTaperPerLayer(double value) {
        return new SplineCutoutConfig(
                enabled, depth, wallThickness, value,
                lineWalls, lineFloor, wallRecipeId, floorRecipeId
        );
    }

    public SplineCutoutConfig withLineWalls(boolean value) {
        return new SplineCutoutConfig(
                enabled, depth, wallThickness, taperPerLayer,
                value, lineFloor, wallRecipeId, floorRecipeId
        );
    }

    public SplineCutoutConfig withLineFloor(boolean value) {
        return new SplineCutoutConfig(
                enabled, depth, wallThickness, taperPerLayer,
                lineWalls, value, wallRecipeId, floorRecipeId
        );
    }

    public SplineCutoutConfig withRecipes(String walls, String floor) {
        return new SplineCutoutConfig(
                enabled, depth, wallThickness, taperPerLayer,
                lineWalls, lineFloor, walls, floor
        );
    }
}
