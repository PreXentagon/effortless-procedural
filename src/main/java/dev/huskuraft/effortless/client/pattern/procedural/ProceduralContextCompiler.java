package dev.huskuraft.effortless.client.pattern.procedural;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.building.BuildState;
import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.clipboard.BlockData;
import dev.huskuraft.effortless.building.clipboard.Clipboard;
import dev.huskuraft.effortless.building.config.ProceduralSafetyConfig;
import dev.huskuraft.effortless.building.pattern.Pattern;
import dev.huskuraft.effortless.building.pattern.Transformers;
import dev.huskuraft.effortless.building.pattern.randomize.Chance;
import dev.huskuraft.effortless.building.pattern.randomize.ItemRandomizer;
import dev.huskuraft.effortless.client.pattern.procedural.config.PatternMaterialSource;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralBlockEntry;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.Item;
import dev.huskuraft.universal.api.core.ItemStack;
import dev.huskuraft.universal.api.core.Items;
import dev.huskuraft.universal.api.text.Text;

/**
 * Compiles client-only rules into a Context containing only stock protocol-13
 * transformer data.
 */
public final class ProceduralContextCompiler {

    public static final int MAX_COMPILED_POSITIONS =
            ProceduralSafetyConfig.DEFAULT.maxCompiledPositions();
    public static final long ESTIMATED_BYTES_PER_POSITION = 160L;
    public static final long MAX_ESTIMATED_MEMORY_BYTES =
            ProceduralSafetyConfig.DEFAULT.maxEstimatedMemoryBytes();
    public static final int MAX_COMPOSITION_LAYERS =
            ProceduralCompositionEngine.MAXIMUM_LAYERS;

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
        if (effectivePreset.advanced().compositionLayers().size()
                > MAX_COMPOSITION_LAYERS) {
            return PreparationResult.failure(
                    "Scene composition contains too many layers",
                    List.of(effectivePreset.advanced().compositionLayers().size()
                            + " layers > " + MAX_COMPOSITION_LAYERS
                            + " allowed")
            );
        }

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
        var safety = entrance.getConfigStorage().get()
                .proceduralSafetyConfig();
        if (operations.size() > safety.maxCompiledPositions()) {
            return PreparationResult.failure(
                    "Procedural output exceeds the client compilation limit",
                    List.of(operations.size() + " blocks > "
                            + safety.maxCompiledPositions())
            );
        }
        long estimatedBytes = operations.size() * ESTIMATED_BYTES_PER_POSITION;
        if (estimatedBytes > safety.maxEstimatedMemoryBytes()) {
            return PreparationResult.failure(
                    "Procedural output is estimated to use too much temporary memory",
                    List.of(estimatedBytes + " estimated bytes > "
                            + safety.maxEstimatedMemoryBytes())
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

        var ownerByPosition = new LinkedHashMap<
                GridPosition, ProceduralPatternPreset>();
        var geometryByPosition = new LinkedHashMap<
                GridPosition, StructuralGeometry>();
        var forcedErasers = new LinkedHashSet<GridPosition>();
        boolean forceExplicit = false;
        if (!effectivePreset.advanced().compositionLayers().isEmpty()) {
            var composition = ProceduralCompositionEngine.compose(
                    new LinkedHashSet<>(operationOrder),
                    List.of(),
                    compositionLibrary(effectivePreset),
                    effectivePreset,
                    Math.min(serverVolumeLimit, safety.maxCompiledPositions())
            );
            if (!composition.isSuccess()) {
                return PreparationResult.failure(
                        "Scene composition is invalid",
                        composition.errors()
                );
            }
            forceExplicit = true;
            operationOrder.clear();
            operationOrder.addAll(composition.positions());
            absoluteByGenerationPosition.clear();
            for (var generationPosition : operationOrder) {
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
                absoluteByGenerationPosition.put(
                        generationPosition, absolute
                );
            }
            composition.additions().forEach((position, generated) -> {
                ownerByPosition.put(position, generated.preset());
                geometryByPosition.put(position, generated.geometry());
            });
            forcedErasers.addAll(composition.erasers());

            if (operationOrder.size() > serverVolumeLimit) {
                return PreparationResult.failure(
                        "Composed output exceeds the server placement volume limit",
                        List.of(operationOrder.size() + " blocks > "
                                + serverVolumeLimit + " allowed")
                );
            }
            if (operationOrder.size() > safety.maxCompiledPositions()) {
                return PreparationResult.failure(
                        "Composed output exceeds the client compilation limit",
                        List.of(operationOrder.size() + " blocks > "
                                + safety.maxCompiledPositions())
                );
            }
            estimatedBytes = operationOrder.size()
                    * ESTIMATED_BYTES_PER_POSITION;
            if (estimatedBytes > safety.maxEstimatedMemoryBytes()) {
                return PreparationResult.failure(
                        "Composed output is estimated to use too much temporary memory",
                        List.of(estimatedBytes + " estimated bytes > "
                                + safety.maxEstimatedMemoryBytes())
                );
            }
            var composedOutOfReach = absoluteByGenerationPosition.values()
                    .stream()
                    .filter(position -> position.getCenter().distance(
                            player.getEyePosition()
                    ) > maximumReach)
                    .findFirst();
            if (composedOutOfReach.isPresent()) {
                return PreparationResult.failure(
                        "Composed output contains a block beyond the server reach limit",
                        List.of(composedOutOfReach.get() + " is farther than "
                                + maximumReach + " blocks")
                );
            }
            int composedMinX = absoluteByGenerationPosition.values().stream()
                    .mapToInt(BlockPosition::x).min().orElseThrow();
            int composedMinY = absoluteByGenerationPosition.values().stream()
                    .mapToInt(BlockPosition::y).min().orElseThrow();
            int composedMinZ = absoluteByGenerationPosition.values().stream()
                    .mapToInt(BlockPosition::z).min().orElseThrow();
            int composedMaxX = absoluteByGenerationPosition.values().stream()
                    .mapToInt(BlockPosition::x).max().orElseThrow();
            int composedMaxY = absoluteByGenerationPosition.values().stream()
                    .mapToInt(BlockPosition::y).max().orElseThrow();
            int composedMaxZ = absoluteByGenerationPosition.values().stream()
                    .mapToInt(BlockPosition::z).max().orElseThrow();
            long boundingVolume = (long) (composedMaxX - composedMinX + 1)
                    * (composedMaxY - composedMinY + 1)
                    * (composedMaxZ - composedMinZ + 1);
            int clipboardLimit = source.configs().constraintConfig()
                    .maxStructureCopyPasteVolume();
            if (boundingVolume > clipboardLimit) {
                return PreparationResult.failure(
                        "Composed bounding volume exceeds the server clipboard limit",
                        List.of(boundingVolume + " blocks > "
                                + clipboardLimit + " allowed")
                );
            }
        }

        var groupPositions = new LinkedHashMap<
                UUID, List<GridPosition>>();
        var groupPresets = new LinkedHashMap<
                UUID, ProceduralPatternPreset>();
        for (var position : operationOrder) {
            if (forcedErasers.contains(position)) {
                continue;
            }
            var recipe = ownerByPosition.getOrDefault(
                    position, effectivePreset
            );
            groupPresets.putIfAbsent(recipe.id(), recipe);
            groupPositions.computeIfAbsent(
                    recipe.id(), ignored -> new ArrayList<>()
            ).add(position);
        }
        var preparedRecipes = new ArrayList<PreparedRecipe>();
        for (var entry : groupPositions.entrySet()) {
            var configuredRecipe = groupPresets.get(entry.getKey());
            var recipe = configuredRecipe.materialSource()
                    == PatternMaterialSource.CUSTOM_PALETTE
                    ? configuredRecipe
                    : configuredRecipe.withBlocks(
                            resolveMaterials(player, configuredRecipe).blocks()
                    );
            var recipeAdaptation = ProceduralPresetAdapter.adapt(recipe);
            if (!recipeAdaptation.isSuccess()) {
                return PreparationResult.failure(
                        "Composition recipe '" + recipe.name()
                                + "' is invalid",
                        recipeAdaptation.errors()
                );
            }
            boolean recipeWorld = recipe.advanced().coordinateSpace()
                    == CoordinateSpace.WORLD;
            var recipeOrder = new ArrayList<GridPosition>();
            var recipeAbsolute = new LinkedHashMap<
                    GridPosition, BlockPosition>();
            var recipeGeometry = new LinkedHashMap<
                    GridPosition, StructuralGeometry>();
            for (var rootPosition : entry.getValue()) {
                var absolute = absoluteByGenerationPosition.get(rootPosition);
                var recipePosition = recipeWorld
                        ? new GridPosition(
                                absolute.x(), absolute.y(), absolute.z()
                        )
                        : new GridPosition(
                                absolute.x() - minX,
                                absolute.y() - minY,
                                absolute.z() - minZ
                        );
                recipeOrder.add(recipePosition);
                recipeAbsolute.put(recipePosition, absolute);
                recipeGeometry.put(
                        recipePosition,
                        geometryByPosition.getOrDefault(
                                rootPosition, StructuralGeometry.NONE
                        )
                );
            }
            ExistingNeighborLookup recipeNeighbors = recipe.inspectExistingWorld()
                    ? generationPosition -> {
                        var absolute = recipeAbsolute.get(generationPosition);
                        if (absolute == null) {
                            absolute = recipeWorld
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
                        }
                        var item = player.getWorld().getBlockState(absolute)
                                .getItem();
                        return Optional.of(item.getId().getString());
                    }
                    : ExistingNeighborLookup.NONE;
            CoordinateLookup recipeCoordinates = (coordinate, position) -> {
                var geometry = recipeGeometry.get(position);
                return geometry == null
                        ? java.util.OptionalDouble.empty()
                        : geometry.sample(coordinate);
            };
            preparedRecipes.add(new PreparedRecipe(
                    recipe,
                    recipeOrder,
                    recipeAbsolute,
                    recipeAdaptation.ruleSet().orElseThrow(),
                    recipeNeighbors,
                    effectiveSeed(
                            recipe,
                            recipeAbsolute.values(),
                            minX, minY, minZ
                    ),
                    recipeCoordinates
            ));
        }

        return PreparationResult.success(new PreparedCompilation(
                player,
                source,
                safety,
                effectivePreset,
                stockTransformers,
                operationOrder,
                absoluteByGenerationPosition,
                preparedRecipes,
                forcedErasers,
                geometryByPosition,
                forceExplicit,
                Math.min(
                        serverVolumeLimit,
                        safety.maxCompiledPositions()
                ),
                operationOrder.size(),
                estimatedBytes,
                safety.maxEstimatedWork()
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

    private ProceduralPatternLibrary compositionLibrary(
            ProceduralPatternPreset rootPreset
    ) {
        var stored = entrance.getProceduralConfigStorage().get();
        return stored.put(rootPreset);
    }

    public ResolutionResult resolve(
            PreparedCompilation prepared,
            BooleanSupplier cancelled,
            GenerationProgress progress
    ) {
        var materialsByAbsolute = new LinkedHashMap<
                BlockPosition, ProceduralMaterial>();
        int recipeIndex = 0;
        for (var recipe : prepared.recipes()) {
            if (cancelled.getAsBoolean()) {
                return ResolutionResult.failure(
                        "Procedural generation was cancelled", List.of()
                );
            }
            int currentRecipe = recipeIndex++;
            var request = new GenerationRequest<>(
                    recipe.seed(),
                    recipe.positions(),
                    recipe.ruleSet(),
                    recipe.existingNeighbors(),
                    prepared.maximumPositions(),
                    cancelled,
                    (stage, completed, total) -> progress.update(
                            stage,
                            currentRecipe * 1_000
                                    + (int) Math.round(
                                            completed * 1_000.0
                                                    / Math.max(1, total)
                                    ),
                            prepared.recipes().size() * 1_000
                    ),
                    recipe.coordinates()
            );
            var generated = ProceduralGenerator.generate(
                    request,
                    prepared.maximumEstimatedWork()
            );
            if (!generated.isSuccess()) {
                var failure = generated.failure().orElseThrow();
                return ResolutionResult.failure(
                        "Recipe '" + recipe.preset().name() + "': "
                                + failure.message(),
                        failure.details()
                );
            }
            for (var entry : generated.placements().entrySet()) {
                var absolute = recipe.absolutePositions().get(entry.getKey());
                if (absolute == null) {
                    return ResolutionResult.failure(
                            "Could not align a composed recipe with its output",
                            List.of(recipe.preset().name() + ": "
                                    + entry.getKey())
                    );
                }
                materialsByAbsolute.put(absolute, entry.getValue());
            }
        }
        for (var position : prepared.forcedErasers()) {
            var absolute = prepared.absolutePositions().get(position);
            if (absolute != null) {
                materialsByAbsolute.put(absolute, ProceduralMaterial.eraser());
            }
        }
        var sequence = new ArrayList<ProceduralMaterial>(
                prepared.operationOrder().size()
        );
        for (var position : prepared.operationOrder()) {
            var absolute = prepared.absolutePositions().get(position);
            var material = materialsByAbsolute.get(absolute);
            if (material == null) {
                return ResolutionResult.failure(
                        "Could not align procedural output with composition order",
                        List.of(String.valueOf(position))
                );
            }
            sequence.add(material);
        }
        var runs = RunLengthSequence.encode(sequence, Chance.MAX_ITEM_COUNT);
        return ResolutionResult.success(new ResolvedCompilation(
                runs,
                sequence
        ));
    }

    public CompilationResult finish(
            PreparedCompilation prepared,
            ResolvedCompilation resolved
    ) {
        if (prepared.forceExplicit() || resolved.sequence().stream()
                .anyMatch(ProceduralMaterial::isSpecial)) {
            return finishExplicitSnapshot(prepared, resolved.sequence());
        }
        var chances = resolved.runs().stream()
                .map(run -> Chance.<Item>of(
                        run.value().placeableBlock().orElseThrow(),
                        run.length()
                ))
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

    private CompilationResult finishExplicitSnapshot(
            PreparedCompilation prepared,
            List<ProceduralMaterial> sequence
    ) {
        var absolutePositions = prepared.absolutePositions();
        var air = Items.AIR.item().getBlock().getDefaultBlockState();
        var blocks = new ArrayList<BlockData>(sequence.size());
        for (int index = 0; index < sequence.size(); index++) {
            var material = sequence.get(index);
            if (material.kind() == ProceduralMaterial.Kind.SKIP) {
                continue;
            }
            var absolute = absolutePositions.get(
                    prepared.operationOrder().get(index)
            );
            if (material.kind() == ProceduralMaterial.Kind.ERASER) {
                blocks.add(new BlockData(absolute, air, null));
            } else {
                blocks.add(new BlockData(
                        absolute,
                        material.placeableBlock().orElseThrow()
                                .getBlock().getDefaultBlockState(),
                        null
                ));
            }
        }
        var absoluteGeometries = new HashMap<
                GridPosition, StructuralGeometry>();
        prepared.geometries().forEach((generation, geometry) -> {
            var absolute = absolutePositions.get(generation);
            if (absolute != null) {
                absoluteGeometries.put(
                        new GridPosition(
                                absolute.x(), absolute.y(), absolute.z()
                        ),
                        geometry
                );
            }
        });
        if (!absoluteGeometries.isEmpty()) {
            var occupied = new java.util.HashSet<GridPosition>();
            var rawStates = new HashMap<GridPosition,
                    dev.huskuraft.universal.api.core.BlockState>();
            for (var data : blocks) {
                var position = new GridPosition(
                        data.blockPosition().x(),
                        data.blockPosition().y(),
                        data.blockPosition().z()
                );
                if (data.blockState() != null
                        && !data.blockState().isAir()) {
                    occupied.add(position);
                    rawStates.put(position, data.blockState());
                }
            }
            var resolvedBlocks = new ArrayList<BlockData>(blocks.size());
            for (var data : blocks) {
                var position = new GridPosition(
                        data.blockPosition().x(),
                        data.blockPosition().y(),
                        data.blockPosition().z()
                );
                var geometry = absoluteGeometries.get(position);
                if (geometry == null || data.blockState() == null
                        || data.blockState().isAir()) {
                    resolvedBlocks.add(data);
                    continue;
                }
                resolvedBlocks.add(new BlockData(
                        data.blockPosition(),
                    StructuralBlockStateResolver.resolve(
                            data.blockState(), position, geometry,
                            occupied, absoluteGeometries, rawStates
                    ),
                    data.entityTag()
                ));
            }
            blocks = resolvedBlocks;
        }
        var assembled = ExplicitSnapshotAssembler.assemble(
                prepared.player(),
                prepared.source(),
                prepared.safety(),
                new ExplicitSnapshotAssembler.Request(
                        "Compiled procedural pattern",
                        "Procedural output",
                        blocks,
                        true
                )
        );
        if (!assembled.isSuccess()) {
            return CompilationResult.failure(
                    assembled.message(), assembled.details()
            );
        }
        var snapshot = assembled.snapshot().orElseThrow();
        var anchor = assembled.anchor().orElseThrow();
        var reference = prepared.source().getInteraction(0);
        var anchorInteraction = reference
                .withPosition(anchor.getCenter())
                .withBlockPosition(anchor);
        var explicit = prepared.source().newInteraction()
                .withBuildState(BuildState.PASTE_STRUCTURE)
                .withNoInteraction()
                .withNextInteraction(anchorInteraction)
                .withClipboard(Clipboard.of(true, snapshot))
                .withPattern(Pattern.DISABLED);
        if (!explicit.hasPermission()) {
            return CompilationResult.failure(
                    "Skip/Eraser output requires server clipboard permission",
                    List.of()
            );
        }
        if (!explicit.isVolumeInBounds()) {
            return CompilationResult.failure(
                    "Explicit Skip/Eraser output exceeds the server "
                            + "clipboard volume limit",
                    List.of(explicit.getVolume() + " blocks > "
                            + explicit.getMaxVolume() + " allowed")
            );
        }
        if (!isStockProtocolContext(explicit)) {
            return CompilationResult.failure(
                    "Compatibility guard rejected explicit procedural output",
                    List.of()
            );
        }
        return CompilationResult.success(
                explicit,
                assembled.positionCount(),
                assembled.estimatedMemoryBytes()
        );
    }

    private static long effectiveSeed(
            ProceduralPatternPreset preset,
            Collection<BlockPosition> positions,
            int minX,
            int minY,
            int minZ
    ) {
        long seed = preset.seed();
        int maxX = positions.stream()
                .mapToInt(BlockPosition::x)
                .max()
                .orElse(minX);
        int maxY = positions.stream()
                .mapToInt(BlockPosition::y)
                .max()
                .orElse(minY);
        int maxZ = positions.stream()
                .mapToInt(BlockPosition::z)
                .max()
                .orElse(minZ);
        return switch (preset.advanced().seedMode()) {
            case FIXED -> seed;
            case SHAPE -> {
                seed = StableRandom.mixSeed(seed, maxX - minX + 1L);
                seed = StableRandom.mixSeed(seed, maxY - minY + 1L);
                seed = StableRandom.mixSeed(seed, maxZ - minZ + 1L);
                yield StableRandom.mixSeed(seed, positions.size());
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
            dev.huskuraft.universal.api.core.Player player,
            Context source,
            ProceduralSafetyConfig safety,
            ProceduralPatternPreset preset,
            List<dev.huskuraft.effortless.building.pattern.Transformer> stockTransformers,
            List<GridPosition> operationOrder,
            java.util.Map<GridPosition, BlockPosition> absolutePositions,
            List<PreparedRecipe> recipes,
            Set<GridPosition> forcedErasers,
            Map<GridPosition, StructuralGeometry> geometries,
            boolean forceExplicit,
            int maximumPositions,
            int positionCount,
            long estimatedMemoryBytes,
            long maximumEstimatedWork
    ) {

        public PreparedCompilation {
            stockTransformers = List.copyOf(stockTransformers);
            operationOrder = List.copyOf(operationOrder);
            absolutePositions = java.util.Map.copyOf(absolutePositions);
            recipes = List.copyOf(recipes);
            forcedErasers = Set.copyOf(forcedErasers);
            geometries = Map.copyOf(geometries);
        }
    }

    public record PreparedRecipe(
            ProceduralPatternPreset preset,
            List<GridPosition> positions,
            Map<GridPosition, BlockPosition> absolutePositions,
            ProceduralRuleSet<ProceduralMaterial> ruleSet,
            ExistingNeighborLookup existingNeighbors,
            long seed,
            CoordinateLookup coordinates
    ) {

        public PreparedRecipe {
            positions = List.copyOf(positions);
            absolutePositions = Map.copyOf(absolutePositions);
        }
    }

    private record MaterialResolution(List<ProceduralBlockEntry> blocks) {

        private MaterialResolution {
            blocks = List.copyOf(blocks);
        }
    }

    public record ResolvedCompilation(
            List<RunLengthSequence.Run<ProceduralMaterial>> runs,
            List<ProceduralMaterial> sequence
    ) {

        public ResolvedCompilation {
            runs = List.copyOf(runs);
            sequence = List.copyOf(sequence);
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
