package dev.huskuraft.effortless.building.config;

public record RenderConfig(
        boolean showBlockPreview,
        boolean showOtherPlayersBuild,
        boolean showOtherPlayersBuildTooltips,
        int maxRenderVolume,
        int maxRenderDistance,
        String eraserPreviewBlockId,
        String cutoutPreviewBlockId
) {
    public static final int MAX_RENDER_VOLUME_DEFAULT = 1024;
    public static final int MAX_RENDER_VOLUME_MIN = 0;
    public static final int MAX_RENDER_VOLUME_MAX = 1_000_000;

    public RenderConfig() {
        this(
                true,
                true,
                false,
                MAX_RENDER_VOLUME_DEFAULT,
                128,
                "minecraft:red_wool",
                "minecraft:red_stained_glass"
        );
    }

    public RenderConfig(
            boolean showBlockPreview,
            boolean showOtherPlayersBuild,
            boolean showOtherPlayersBuildTooltips,
            int maxRenderVolume,
            int maxRenderDistance
    ) {
        this(
                showBlockPreview,
                showOtherPlayersBuild,
                showOtherPlayersBuildTooltips,
                maxRenderVolume,
                maxRenderDistance,
                "minecraft:red_wool",
                "minecraft:red_stained_glass"
        );
    }

    public RenderConfig withEraserPreviewBlockId(String value) {
        return new RenderConfig(
                showBlockPreview, showOtherPlayersBuild,
                showOtherPlayersBuildTooltips, maxRenderVolume,
                maxRenderDistance, value, cutoutPreviewBlockId
        );
    }

    public RenderConfig withCutoutPreviewBlockId(String value) {
        return new RenderConfig(
                showBlockPreview, showOtherPlayersBuild,
                showOtherPlayersBuildTooltips, maxRenderVolume,
                maxRenderDistance, eraserPreviewBlockId, value
        );
    }

    public boolean showOtherPlayersBuildTooltips() {
        return false;
    }

    public static RenderConfig DEFAULT = new RenderConfig();

}
