package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.List;

import dev.huskuraft.effortless.building.structure.BuildMode;

/**
 * Explicit synthetic placement orientation used by the workbench preview.
 *
 * <p>A build mode such as wall is not tied to one plane until the player
 * places it in the world. Keeping that choice visible prevents an XY preview
 * from being mistaken for a YZ placement.</p>
 */
enum PreviewOrientation {
    X("X"),
    Y("Y"),
    Z("Z"),
    XY("XY"),
    XZ("XZ"),
    YZ("YZ"),
    XYZ("XYZ");

    private final String label;

    PreviewOrientation(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    static PreviewOrientation defaultFor(BuildMode mode) {
        return choices(mode).get(0);
    }

    static PreviewOrientation next(BuildMode mode, PreviewOrientation current) {
        var choices = choices(mode);
        int index = choices.indexOf(current);
        return choices.get((Math.max(0, index) + 1) % choices.size());
    }

    static List<PreviewOrientation> choices(BuildMode mode) {
        return switch (mode) {
            case SINGLE -> List.of(XYZ);
            case LINE -> List.of(X, Y, Z);
            case WALL -> List.of(YZ, XY);
            case FLOOR -> List.of(XZ);
            case CUBOID, SPHERE, PYRAMID, CONE -> List.of(XYZ);
            case DIAGONAL_LINE -> List.of(XY, XZ, YZ, XYZ);
            case DIAGONAL_WALL -> List.of(XZ);
            case SLOPE_FLOOR -> List.of(XZ);
            case CIRCLE -> List.of(YZ, XZ, XY);
            case CYLINDER -> List.of(Y, X, Z);
            case DISABLED -> List.of(XYZ);
        };
    }

    static String kind(BuildMode mode) {
        return switch (mode) {
            case LINE -> "Axis";
            case WALL, FLOOR, CIRCLE -> "Plane";
            case CYLINDER -> "Axis";
            case DIAGONAL_LINE -> "Path";
            case DIAGONAL_WALL, SLOPE_FLOOR -> "Base";
            default -> "Space";
        };
    }
}
