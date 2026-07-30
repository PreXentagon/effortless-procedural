package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;

import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.operation.block.BlockOperation;
import dev.huskuraft.effortless.building.session.BatchBuildSession;
import dev.huskuraft.universal.api.core.Player;
import dev.huskuraft.universal.api.platform.Entrance;

/**
 * Exposes the protected stock operation construction path without committing
 * anything to the client world.
 */
final class ClientOperationCollector extends BatchBuildSession {

    private final Context sourceContext;

    ClientOperationCollector(Entrance entrance, Player player, Context context) {
        super(entrance, player, context);
        this.sourceContext = context;
    }

    List<BlockOperation> collect() {
        return create(getWorld(), getPlayer(), sourceContext)
                .operations()
                .filter(BlockOperation.class::isInstance)
                .map(BlockOperation.class::cast)
                .toList();
    }
}
