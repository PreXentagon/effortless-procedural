package dev.huskuraft.effortless.client.pattern.procedural;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.building.BuildState;
import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.pattern.Pattern;
import dev.huskuraft.effortless.building.pattern.Transformers;
import dev.huskuraft.effortless.building.pattern.randomize.Chance;
import dev.huskuraft.effortless.building.pattern.randomize.ItemRandomizer;
import dev.huskuraft.effortless.client.pattern.procedural.config.PatternMaterialSource;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralBlockEntry;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.Item;
import dev.huskuraft.universal.api.core.ItemStack;
import dev.huskuraft.universal.api.text.Text;

/**
 * Compiles client-only rules into a Context containing only stock protocol-13
 * transformer data.
 */
public final class ProceduralContextCompiler {

    public static final int MAX_COMPILED_POSITIONS = 250_000;
    public static final long ESTIMATED_BYTES_PER_POSITION = 160L;
    public static final long MAX_ESTIMATED_MEMORY_BYTES = 64L * 1024L * 1024L;

    private final EffortlessClient entrance;

    public ProceduralContextCompiler(EffortlessClient entrance) {
        this.entrance = entrance;
    }

    public CompilationResult compile(
            dev.huskuraft.universal.api.core.Player player,
            Context source,
            ProceduralPatternPreset preset
    ) {
        var preparation = prepare(player, source, preset);
        if (!preparation.isSuccess()) {
            return preparation.failure().orElseThrow();
        }
        var prepared = preparation.prepared().orElseThrow();
        var resolution = resolve(
                prepared,
                Thread.currentThread()::isInterrupted,
                GenerationProgress.NONE
        );
        if (!resolution.isSuccess()) {
            return CompilationResult.failure(
                    resolution.message(),
                    resolution.details()
            );
        }
        return finish(prepared, resolution.resolved().orElseThrow());
    }

    public PreparationResult prepare(
            dev.huskuraft.universal.api.core.Player player,
            Context source,
            ProceduralPatternPreset preset
    ) {
        if (source.buildState() != BuildState.PLACE_BLOCK) {
            return PreparationResult.failure(
                    "Procedural patterns currently support block placement only",
                    List.of("Build state was " + source.buildState())
            );
        }

        var materialResolution = resolveMaterials(player, preset);
        if (materialResolution.blocks().isEmpty()) {
            return PreparationResult.failure(
                    "The selected material source contains no placeable blocks",
                    List.of(preset.materialSource().displayName())
            );
        }
        var effectivePreset = preset.materialSource()
                == PatternMaterialSource.CUSTOM_PALETTE
                ? preset
                : preset.withBlocks(materialResolution.blocks());

        var adaptation = ProceduralPresetAdapter.adapt(effectivePreset);
        if (!adaptation.isSuccess()) {
            return PreparationResult.failure(
                    "The active procedural preset is invalid",
                    adaptation.errors()
            );
        }

        // A disabled stock pattern contributes no transformers. The compiled
        // sequence itself still requires Pattern.enabled on the wire.
        var stockTransformers = effectivePreset.stockTransformers().isEmpty()
                && source.pattern().enabled()
                ? source.pattern().transformers().stream()
                        .filter(transformer ->
                                transformer.getType() != Transformers.RANDOMIZER
                        )
                        .toList()
                : effectivePreset.stockTransformers();
        var stockPattern = new Pattern(!stockTransformers.isEmpty(), stockTransformers);
        var stockContext = source.withPattern(stockPattern);
        List<dev.huskuraft.effortless.building.operation.block.BlockOperation> operations;
        try {
            operations = new ClientOperationCollector(entrance, player, stockContext).collect();
        } catch (RuntimeException exception) {
            return PreparationResult.failure(
                    "Could not resolve stock placement operations",
                    List.of(exception.getClass().getSimpleName() + ": " + exception.getMessage())
            );
        }
        if (operations.isEmpty()) {
            return PreparationResult.failure(
                    "The selected structure produced no placeable blocks",
                    List.of()
            );
        }

        int serverVolumeLimit = source.getMaxVolume();
        if (operations.size() > serverVolumeLimit) {
            return PreparationResult.failure(
                    "Procedural output exceeds the server placement volume limit",
                    List.of(operations.size() + " blocks > " + serverVolumeLimit + " allowed")
            );
        }
        if (operations.size() > MAX_COMPILED_POSITIONS) {
            return PreparationResult.failure(
                    "Procedural output exceeds the client compilation limit",
                    List.of(operations.size() + " blocks > " + MAX_COMPILED_POSITIONS)
            );
        }
        long estimatedBytes = operations.size() * ESTIMATED_BYTES_PER_POSITION;
        if (estimatedBytes > MAX_ESTIMATED_MEMORY_BYTES) {
            return PreparationResult.failure(
                    "Procedural output is estimated to use too much temporary memory",
                    List.of(estimatedBytes + " estimated bytes > " + MAX_ESTIMATED_MEMORY_BYTES)
            );
        }

        double maximumReach = source.maxReachDistance();
        var outOfReach = operations.stream()
                .map(operation -> operation.getBlockPosition())
                .filter(position -> position.getCenter().distance(player.getEyePosition()) > maximumReach)
                .findFirst();
        if (outOfReach.isPresent()) {
            return PreparationResult.failure(
                    "Procedural output contains a block beyond the server reach limit",
                    List.of(outOfReach.get() + " is farther than " + maximumReach + " blocks")
            );
        }

        int minX = operations.stream().mapToInt(operation -> operation.getBlockPosition().x()).min()
                .orElseThrow();
        int minY = operations.stream().mapToInt(operation -> operation.getBlockPosition().y()).min()
                .orElseThrow();
        int minZ = operations.stream().mapToInt(operation -> operation.getBlockPosition().z()).min()
                .orElseThrow();

        var operationOrder = new ArrayList<GridPosition>(operations.size());
        var absoluteByGenerationPosition =
                new LinkedHashMap<GridPosition, BlockPosition>();
        boolean worldCoordinates =
                effectivePreset.advanced().coordinateSpace()
                        == CoordinateSpace.WORLD;
        for (var operation : operations) {
            var absolute = operation.getBlockPosition();
            var generationPosition = worldCoordinates
                    ? new GridPosition(absolute.x(), absolute.y(), absolute.z())
                    : new GridPosition(
                            absolute.x() - minX,
                            absolute.y() - minY,
                            absolute.z() - minZ
                    );
            operationOrder.add(generationPosition);
            absoluteByGenerationPosition.put(generationPosition, absolute);
        }
        if (absoluteByGenerationPosition.size() != operations.size()) {
            return PreparationResult.failure(
                    "Stock operation collection produced duplicate positions",
                    List.of()
            );
        }

        ExistingNeighborLookup existingNeighbors =
                effectivePreset.inspectExistingWorld()
                ? generationPosition -> {
                    var absolute = worldCoordinates
                            ? new BlockPosition(
                                    generationPosition.x(),
                                    generationPosition.y(),
                                    generationPosition.z()
                            )
                            : new BlockPosition(
                                    generationPosition.x() + minX,
                                    generationPosition.y() + minY,
                                    generationPosition.z() + minZ
                            );
                    var item = player.getWorld().getBlockState(absolute).getItem();
                    return Optional.of(item.getId().getString());
                }
                : ExistingNeighborLookup.NONE;

        return PreparationResult.success(new PreparedCompilation(
                source,
                effectivePreset,
                stockTransformers,
                operationOrder,
                adaptation.ruleSet().orElseThrow(),
                existingNeighbors,
                effectiveSeed(
                        effectivePreset,
                        operations,
                        minX,
                        minY,
                        minZ
                ),
                Math.min(serverVolumeLimit, MAX_COMPILED_POSITIONS),
                operations.size(),
                estimatedBytes
        ));
    }

    /**
     * Stable fingerprint used to invalidate previews when a live material pool
     * changes without the placement context changing.
     */
    public static long materialSourceFingerprint(
            dev.huskuraft.universal.api.core.Player player,
            ProceduralPatternPreset preset
    ) {
        if (preset.materialSource() == PatternMaterialSource.CUSTOM_PALETTE) {
            return 0L;
        }
        long result = 0xCBF29CE484222325L;
        for (var entry : resolveMaterials(player, preset).blocks()) {
            for (int index = 0; index < entry.itemId().length(); index++) {
                result ^= entry.itemId().charAt(index);
                result *= 0x100000001B3L;
            }
            result ^= Double.doubleToLongBits(entry.weight());
            result *= 0x100000001B3L;
        }
        return result;
    }

    /**
     * Resolves live material sources for a client-only workbench preview.
     * The returned preset is never serialized or sent to the server.
     */
    public static ProceduralPatternPreset resolvePreviewMaterials(
            dev.huskuraft.universal.api.core.Player player,
            ProceduralPatternPreset preset
    ) {
        if (preset.materialSource() == PatternMaterialSource.CUSTOM_PALETTE) {
            return preset;
        }
        return preset.withBlocks(resolveMaterials(player, preset).blocks());
    }

    private static MaterialResolution resolveMaterials(
            dev.huskuraft.universal.api.core.Player player,
            ProceduralPatternPreset preset
    ) {
        if (preset.materialSource() == PatternMaterialSource.CUSTOM_PALETTE) {
            return new MaterialResolution(preset.blocks());
        }
        List<ItemStack> stacks = switch (preset.materialSource()) {
            case INVENTORY -> java.util.stream.Stream.of(
                            player.getInventory().getBagItems(),
                            player.getInventory().getOffhandItems()
                    )
                    .flatMap(List::stream)
                    .toList();
            case HOTBAR -> player.getInventory().getHotbarItems();
            case HANDS -> List.of(
                    player.getInventory().getSelectedItem(),
                    player.getInventory().getOffhandItem()
            );
            case CUSTOM_PALETTE -> throw new IllegalStateException(
                    "Custom palette was handled above"
            );
        };
        var counts = new LinkedHashMap<String, Integer>();
        for (var stack : stacks) {
            if (stack.getItem() instanceof BlockItem && stack.getCount() > 0) {
                counts.merge(
                        stack.getItem().getId().getString(),
                        stack.getCount(),
                        Integer::sum
                );
            }
        }
        return new MaterialResolution(counts.entrySet().stream()
                .map(entry -> ProceduralBlockEntry.weighted(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList());
    }

    public ResolutionResult resolve(
            PreparedCompilation prepared,
            BooleanSupplier cancelled,
            GenerationProgress progress
    ) {
        var request = new GenerationRequest<>(
                prepared.seed(),
                prepared.operationOrder(),
                prepared.ruleSet(),
                prepared.existingNeighbors(),
                prepared.maximumPositions(),
                cancelled,
                progress
        );
        var generated = ProceduralGenerator.generate(request);
        if (!generated.isSuccess()) {
            var failure = generated.failure().orElseThrow();
            return ResolutionResult.failure(failure.message(), failure.details());
        }

        var plan = SequenceCompilationPlan.create(
                prepared.operationOrder(),
                generated.placements()
        );
        if (!plan.isSuccess()) {
            return ResolutionResult.failure(
                    "Could not align procedural output with stock operation order",
                    List.of(plan.error())
            );
        }

        var runs = RunLengthSequence.encode(
                plan.sequence(),
                Chance.MAX_ITEM_COUNT
        );
        return ResolutionResult.success(new ResolvedCompilation(runs));
    }

    public CompilationResult finish(
            PreparedCompilation prepared,
            ResolvedCompilation resolved
    ) {
        var chances = resolved.runs().stream()
                .map(run -> Chance.of(run.value(), run.length()))
                .toList();
        var randomizerId = UUID.nameUUIDFromBytes(
                ("effortless:procedural:" + prepared.preset().id() + ":"
                        + prepared.source().id())
                        .getBytes(StandardCharsets.UTF_8)
        );
        var compiledRandomizer = new ItemRandomizer(
                randomizerId,
                Text.text("Procedural: " + prepared.preset().name()),
                ItemRandomizer.Order.SEQUENCE,
                ItemRandomizer.Target.SINGLE,
                ItemRandomizer.Source.CUSTOMIZE,
                chances
        );
        var compiledTransformers =
                new ArrayList<dev.huskuraft.effortless.building.pattern.Transformer>(
                        prepared.stockTransformers().size() + 1
                );
        compiledTransformers.addAll(prepared.stockTransformers());
        compiledTransformers.add(compiledRandomizer);
        var compiledContext = prepared.source().withPattern(
                new Pattern(true, compiledTransformers)
        );

        if (!isStockProtocolContext(compiledContext)) {
            return CompilationResult.failure(
                    "Internal compatibility guard rejected a non-stock transformer",
                    List.of()
            );
        }
        return CompilationResult.success(
                compiledContext,
                prepared.positionCount(),
                prepared.estimatedMemoryBytes()
        );
    }

    private static long effectiveSeed(
            ProceduralPatternPreset preset,
            List<dev.huskuraft.effortless.building.operation.block.BlockOperation> operations,
            int minX,
            int minY,
            int minZ
    ) {
        long seed = preset.seed();
        int maxX = operations.stream()
                .mapToInt(operation -> operation.getBlockPosition().x())
                .max()
                .orElse(minX);
        int maxY = operations.stream()
                .mapToInt(operation -> operation.getBlockPosition().y())
                .max()
                .orElse(minY);
        int maxZ = operations.stream()
                .mapToInt(operation -> operation.getBlockPosition().z())
                .max()
                .orElse(minZ);
        return switch (preset.advanced().seedMode()) {
            case FIXED -> seed;
            case SHAPE -> {
                seed = StableRandom.mixSeed(seed, maxX - minX + 1L);
                seed = StableRandom.mixSeed(seed, maxY - minY + 1L);
                seed = StableRandom.mixSeed(seed, maxZ - minZ + 1L);
                yield StableRandom.mixSeed(seed, operations.size());
            }
            case WORLD_ANCHORED -> {
                seed = StableRandom.mixSeed(seed, minX);
                seed = StableRandom.mixSeed(seed, minY);
                seed = StableRandom.mixSeed(seed, minZ);
                seed = StableRandom.mixSeed(seed, maxX);
                seed = StableRandom.mixSeed(seed, maxY);
                yield StableRandom.mixSeed(seed, maxZ);
            }
        };
    }

    static boolean isStockProtocolContext(Context context) {
        return context.pattern().transformers().stream().allMatch(transformer -> switch (
                transformer.getType()
        ) {
            case ARRAY, MIRROR, RADIAL, RANDOMIZER -> true;
        });
    }

    public record PreparedCompilation(
            Context source,
            ProceduralPatternPreset preset,
            List<dev.huskuraft.effortless.building.pattern.Transformer> stockTransformers,
            List<GridPosition> operationOrder,
            ProceduralRuleSet<Item> ruleSet,
            ExistingNeighborLookup existingNeighbors,
            long seed,
            int maximumPositions,
            int positionCount,
            long estimatedMemoryBytes
    ) {

        public PreparedCompilation {
            stockTransformers = List.copyOf(stockTransformers);
            operationOrder = List.copyOf(operationOrder);
        }
    }

    private record MaterialResolution(List<ProceduralBlockEntry> blocks) {

        private MaterialResolution {
            blocks = List.copyOf(blocks);
        }
    }

    public record ResolvedCompilation(
            List<RunLengthSequence.Run<Item>> runs
    ) {

        public ResolvedCompilation {
            runs = List.copyOf(runs);
        }
    }

    public record PreparationResult(
            Optional<PreparedCompilation> prepared,
            Optional<CompilationResult> failure
    ) {

        public static PreparationResult success(PreparedCompilation prepared) {
            return new PreparationResult(
                    Optional.of(prepared),
                    Optional.empty()
            );
        }

        public static PreparationResult failure(
                String message,
                List<String> details
        ) {
            return new PreparationResult(
                    Optional.empty(),
                    Optional.of(CompilationResult.failure(message, details))
            );
        }

        public boolean isSuccess() {
            return prepared.isPresent();
        }
    }

    public record ResolutionResult(
            Optional<ResolvedCompilation> resolved,
            String message,
            List<String> details
    ) {

        public ResolutionResult {
            details = List.copyOf(details);
        }

        public static ResolutionResult success(ResolvedCompilation resolved) {
            return new ResolutionResult(
                    Optional.of(resolved),
                    "",
                    List.of()
            );
        }

        public static ResolutionResult failure(
                String message,
                List<String> details
        ) {
            return new ResolutionResult(Optional.empty(), message, details);
        }

        public boolean isSuccess() {
            return resolved.isPresent();
        }
    }

    public record CompilationResult(
            Optional<Context> context,
            String message,
            List<String> details,
            int positionCount,
            long estimatedMemoryBytes,
            boolean pending
    ) {

        public CompilationResult {
            details = List.copyOf(details);
        }

        public static CompilationResult success(
                Context context,
                int positionCount,
                long estimatedMemoryBytes
        ) {
            return new CompilationResult(
                    Optional.of(context),
                    "",
                    List.of(),
                    positionCount,
                    estimatedMemoryBytes,
                    false
            );
        }

        public static CompilationResult pending(
                Context context,
                int positionCount,
                long estimatedMemoryBytes
        ) {
            return new CompilationResult(
                    Optional.of(context),
                    "",
                    List.of(),
                    positionCount,
                    estimatedMemoryBytes,
                    true
            );
        }

        public static CompilationResult failure(String message, List<String> details) {
            return new CompilationResult(
                    Optional.empty(),
                    message,
                    details,
                    0,
                    0L,
                    false
            );
        }

        public boolean isSuccess() {
            return context.isPresent();
        }
    }
}
