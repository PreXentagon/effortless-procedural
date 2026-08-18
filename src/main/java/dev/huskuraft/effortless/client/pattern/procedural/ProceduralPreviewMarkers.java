package dev.huskuraft.effortless.client.pattern.procedural;

import dev.huskuraft.effortless.building.config.RenderConfig;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.core.BlockState;
import dev.huskuraft.universal.api.core.Item;
import dev.huskuraft.universal.api.core.Items;
import dev.huskuraft.universal.api.core.ResourceLocation;

/** Client-only block models used to make destructive output visible. */
public final class ProceduralPreviewMarkers {

    private ProceduralPreviewMarkers() {
    }

    public static BlockState eraser(RenderConfig config) {
        return resolve(
                config.eraserPreviewBlockId(),
                Items.RED_WOOL.item().getBlock().getDefaultBlockState()
        );
    }

    public static BlockState cutout(RenderConfig config) {
        return resolve(
                config.cutoutPreviewBlockId(),
                Items.RED_STAINED_GLASS.item().getBlock()
                        .getDefaultBlockState()
        );
    }

    private static BlockState resolve(String itemId, BlockState fallback) {
        try {
            var item = Item.fromIdOptional(
                    ResourceLocation.decompose(itemId)
            );
            if (item.isPresent() && item.get() instanceof BlockItem blockItem) {
                return blockItem.getBlock().getDefaultBlockState();
            }
        } catch (RuntimeException ignored) {
            // Invalid or removed client-side block IDs safely use the default.
        }
        return fallback;
    }
}
