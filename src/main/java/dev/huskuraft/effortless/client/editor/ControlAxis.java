package dev.huskuraft.effortless.client.editor;

import dev.huskuraft.effortless.client.road.RoadPoint;
import dev.huskuraft.universal.api.text.ChatFormatting;

/** Shared axis semantics for every in-world procedural control-point gizmo. */
public enum ControlAxis {
    NONE(180, 180, 180, ChatFormatting.GRAY),
    X(205, 92, 92, ChatFormatting.RED),
    Y(92, 184, 112, ChatFormatting.GREEN),
    Z(92, 132, 205, ChatFormatting.BLUE);

    private final int red;
    private final int green;
    private final int blue;
    private final ChatFormatting textColor;

    ControlAxis(int red, int green, int blue, ChatFormatting textColor) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.textColor = textColor;
    }

    public RoadPoint offset(RoadPoint center, double length) {
        return switch (this) {
            case X -> center.add(length, 0.0, 0.0);
            case Y -> center.add(0.0, length, 0.0);
            case Z -> center.add(0.0, 0.0, length);
            case NONE -> center;
        };
    }

    public RoadPoint start(RoadPoint center, double offset) {
        return offset(center, offset);
    }

    public int red() {
        return red;
    }

    public int green() {
        return green;
    }

    public int blue() {
        return blue;
    }

    public ChatFormatting textColor() {
        return textColor;
    }

    public static ControlAxis[] spatial() {
        return new ControlAxis[]{X, Y, Z};
    }
}
