package dev.huskuraft.effortless.client.tree;

import dev.huskuraft.effortless.client.generator.ClientToolSubtype;
import dev.huskuraft.universal.api.core.ResourceLocation;

/**
 * Stable tree families. These values are persisted only in the client-local
 * pattern library and are never serialized to the server.
 */
public enum TreeArchetype implements ClientToolSubtype {
    OAK("oak", "oak_sapling"),
    BIRCH("birch", "birch_sapling"),
    SPRUCE("spruce", "spruce_sapling"),
    PINE("pine", "spruce_sapling"),
    JUNGLE("jungle", "jungle_sapling"),
    DARK_OAK("dark_oak", "dark_oak_sapling"),
    ACACIA("acacia", "acacia_sapling"),
    MANGROVE("mangrove", "mangrove_propagule"),
    CHERRY("cherry", "cherry_sapling"),
    WILLOW("willow", "mangrove_propagule"),
    PALM("palm", "jungle_sapling"),
    GIANT_FANTASY("giant_fantasy", "azalea_leaves"),
    CUSTOM("custom_tree", "flowering_azalea_leaves");

    private final String id;
    private final String icon;

    TreeArchetype(String id, String icon) {
        this.id = id;
        this.icon = icon;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String getCategory() {
        return "tree_archetype";
    }

    @Override
    public ResourceLocation getIcon() {
        return ResourceLocation.of(
                "minecraft",
                "textures/block/" + icon + ".png"
        );
    }

    public boolean isConifer() {
        return this == SPRUCE || this == PINE;
    }

    public boolean isWeeping() {
        return this == WILLOW;
    }

    public boolean isUmbrella() {
        return this == ACACIA || this == CHERRY;
    }

    public boolean isPalm() {
        return this == PALM;
    }
}
