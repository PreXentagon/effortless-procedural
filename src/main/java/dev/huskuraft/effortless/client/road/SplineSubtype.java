package dev.huskuraft.effortless.client.road;

import dev.huskuraft.effortless.Effortless;
import dev.huskuraft.effortless.client.generator.ClientToolSubtype;
import dev.huskuraft.universal.api.core.ResourceLocation;

/** Named client-only cross-section presets for the spline authoring tool. */
public enum SplineSubtype implements ClientToolSubtype {
    PATH("path", "textures/mode/line.png"),
    FLAT_ROAD("flat_road", "textures/mode/floor.png"),
    CROWNED_ROAD("crowned_road", "textures/mode/slope_floor.png"),
    BANKED_ROAD("banked_road", "textures/mode/diagonal_wall.png"),
    EMBANKMENT("embankment", "textures/mode/pyramid.png"),
    TRENCH("trench", "textures/mode/wall.png"),
    BRIDGE_DECK("bridge_deck", "textures/mode/cuboid.png"),
    RAIL_BED("rail_bed", "textures/mode/diagonal_line.png"),
    CUSTOM("custom_spline", "textures/mode/diagonal_line.png");

    private final String id;
    private final String icon;

    SplineSubtype(String id, String icon) {
        this.id = id;
        this.icon = icon;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String getCategory() {
        return "spline_subtype";
    }

    @Override
    public ResourceLocation getIcon() {
        return ResourceLocation.of(Effortless.MOD_ID, icon);
    }
}
