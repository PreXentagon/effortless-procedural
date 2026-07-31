package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.Optional;

import dev.huskuraft.universal.api.core.BlockItem;

/**
 * Client-only generated value. Special values are consumed by an explicit
 * stock-snapshot compiler and are never serialized as registry item ids.
 */
public record ProceduralMaterial(
        String id,
        Kind kind,
        BlockItem blockItem
) {

    public static final String SKIP_ID = "effortless:skip";
    public static final String ERASER_ID = "effortless:eraser";

    public ProceduralMaterial {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Procedural material id must not be blank"
            );
        }
        if (kind == Kind.BLOCK && blockItem == null) {
            throw new IllegalArgumentException(
                    "Block material requires a block item"
            );
        }
        if (kind != Kind.BLOCK && blockItem != null) {
            throw new IllegalArgumentException(
                    "Special material cannot carry a block item"
            );
        }
    }

    public static ProceduralMaterial block(String id, BlockItem item) {
        return new ProceduralMaterial(id, Kind.BLOCK, item);
    }

    public static ProceduralMaterial skip() {
        return new ProceduralMaterial(SKIP_ID, Kind.SKIP, null);
    }

    public static ProceduralMaterial eraser() {
        return new ProceduralMaterial(ERASER_ID, Kind.ERASER, null);
    }

    public Optional<BlockItem> placeableBlock() {
        return Optional.ofNullable(blockItem);
    }

    public boolean isSpecial() {
        return kind != Kind.BLOCK;
    }

    public static boolean isSpecialId(String id) {
        return SKIP_ID.equals(id) || ERASER_ID.equals(id);
    }

    public enum Kind {
        BLOCK,
        SKIP,
        ERASER
    }
}
