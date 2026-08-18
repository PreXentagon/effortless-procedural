package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.clipboard.BlockData;
import dev.huskuraft.effortless.building.clipboard.Snapshot;
import dev.huskuraft.effortless.building.config.ProceduralSafetyConfig;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.Player;

/**
 * The single compatibility boundary for client-generated explicit geometry.
 * It validates the same server constraints for every procedural tool and
 * converts absolute cells into an ordinary stock clipboard snapshot.
 */
public final class ExplicitSnapshotAssembler {

    private ExplicitSnapshotAssembler() {
    }

    public static Result assemble(
            Player player,
            Context source,
            ProceduralSafetyConfig safety,
            Request request
    ) {
        if (request.absoluteBlocks().isEmpty()) {
            return Result.failure(
                    "Every " + request.subject().toLowerCase()
                            + " cell was skipped",
                    List.of("Nothing would be sent to the server")
            );
        }

        var ordered = new LinkedHashMap<GridPosition, BlockData>();
        request.absoluteBlocks().stream()
                .sorted((first, second) -> GridPosition.TRAVERSAL_ORDER.compare(
                        grid(first.blockPosition()),
                        grid(second.blockPosition())
                ))
                .forEach(data -> ordered.put(grid(data.blockPosition()), data));
        if (ordered.size() != request.absoluteBlocks().size()) {
            return Result.failure(
                    request.subject() + " contains duplicate explicit cells",
                    List.of()
            );
        }

        var bounds = GenerationBounds.enclosing(ordered.keySet());
        int serverLimit = source.configs().constraintConfig()
                .maxStructureCopyPasteVolume();
        if (bounds.volume() > serverLimit) {
            return Result.failure(
                    request.subject()
                            + " bounding volume exceeds the server clipboard limit",
                    List.of(bounds.volume() + " blocks > "
                            + serverLimit + " allowed")
            );
        }
        if (ordered.size() > safety.maxCompiledPositions()) {
            return Result.failure(
                    request.subject() + " contains too many explicit blocks",
                    List.of(ordered.size() + " blocks > "
                            + safety.maxCompiledPositions())
            );
        }
        long estimatedBytes = ordered.size()
                * ProceduralContextCompiler.ESTIMATED_BYTES_PER_POSITION;
        if (estimatedBytes > safety.maxEstimatedMemoryBytes()) {
            return Result.failure(
                    request.subject()
                            + " compilation is estimated to use too much memory",
                    List.of(estimatedBytes + " estimated bytes > "
                            + safety.maxEstimatedMemoryBytes())
            );
        }

        if (request.enforceReach()) {
            double reach = source.maxReachDistance();
            var outOfReach = ordered.values().stream()
                    .map(BlockData::blockPosition)
                    .filter(position -> position.getCenter()
                            .distance(player.getEyePosition()) > reach)
                    .findFirst();
            if (outOfReach.isPresent()) {
                return Result.failure(
                        request.subject()
                                + " contains a block beyond the server reach limit",
                        List.of(outOfReach.get() + " is farther than "
                                + reach + " blocks")
                );
            }
        }

        boolean containsPlacement = ordered.values().stream()
                .map(BlockData::blockState)
                .anyMatch(state -> state != null && !state.isAir());
        boolean containsErase = ordered.values().stream()
                .map(BlockData::blockState)
                .anyMatch(state -> state != null && state.isAir());
        var constraints = source.configs().constraintConfig();
        if (containsPlacement && !constraints.allowPlaceBlocks()) {
            return Result.failure(
                    "The server does not allow block placement",
                    List.of()
            );
        }
        if (containsErase && !constraints.allowBreakBlocks()) {
            return Result.failure(
                    "The server does not allow block breaking",
                    List.of()
            );
        }

        var anchor = new BlockPosition(
                bounds.minX(), bounds.minY(), bounds.minZ()
        );
        var relative = new ArrayList<BlockData>(ordered.size());
        ordered.values().forEach(data -> relative.add(new BlockData(
                new BlockPosition(
                        data.blockPosition().x() - anchor.x(),
                        data.blockPosition().y() - anchor.y(),
                        data.blockPosition().z() - anchor.z()
                ),
                data.blockState(),
                data.entityTag()
        )));
        return Result.success(
                new Snapshot(
                        request.snapshotName(),
                        System.currentTimeMillis(),
                        relative
                ),
                anchor,
                relative.size(),
                bounds.volume(),
                estimatedBytes
        );
    }

    private static GridPosition grid(BlockPosition position) {
        return new GridPosition(position.x(), position.y(), position.z());
    }

    public record Request(
            String snapshotName,
            String subject,
            Collection<BlockData> absoluteBlocks,
            boolean enforceReach
    ) {
        public Request {
            snapshotName = snapshotName == null || snapshotName.isBlank()
                    ? "Procedural output" : snapshotName;
            subject = subject == null || subject.isBlank()
                    ? "Procedural output" : subject;
            absoluteBlocks = absoluteBlocks == null
                    ? List.of() : List.copyOf(absoluteBlocks);
        }
    }

    public record Result(
            Optional<Snapshot> snapshot,
            Optional<BlockPosition> anchor,
            int positionCount,
            long boundingVolume,
            long estimatedMemoryBytes,
            String message,
            List<String> details
    ) {
        public static Result success(
                Snapshot snapshot,
                BlockPosition anchor,
                int positionCount,
                long boundingVolume,
                long estimatedMemoryBytes
        ) {
            return new Result(
                    Optional.of(snapshot),
                    Optional.of(anchor),
                    positionCount,
                    boundingVolume,
                    estimatedMemoryBytes,
                    "",
                    List.of()
            );
        }

        public static Result failure(String message, List<String> details) {
            return new Result(
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    0L,
                    0L,
                    message,
                    List.copyOf(details)
            );
        }

        public boolean isSuccess() {
            return snapshot.isPresent();
        }
    }

}
