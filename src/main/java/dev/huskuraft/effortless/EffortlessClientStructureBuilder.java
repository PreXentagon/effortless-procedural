package dev.huskuraft.effortless;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.huskuraft.universal.api.core.BlockInteraction;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.BlockState;
import dev.huskuraft.universal.api.core.Interaction;
import dev.huskuraft.universal.api.core.InteractionHand;
import dev.huskuraft.universal.api.core.InteractionType;
import dev.huskuraft.universal.api.core.ItemStack;
import dev.huskuraft.universal.api.core.Items;
import dev.huskuraft.universal.api.core.Player;
import dev.huskuraft.universal.api.core.ResourceLocation;
import dev.huskuraft.universal.api.core.Tuple2;
import dev.huskuraft.universal.api.events.EventResult;
import dev.huskuraft.universal.api.events.lifecycle.ClientTick;
import dev.huskuraft.universal.api.math.BoundingBox3d;
import dev.huskuraft.universal.api.math.MathUtils;
import dev.huskuraft.universal.api.math.Vector3i;
import dev.huskuraft.universal.api.platform.Client;
import dev.huskuraft.universal.api.renderer.LightTexture;
import dev.huskuraft.universal.api.sound.SoundInstance;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;
import dev.huskuraft.effortless.building.BuildResult;
import dev.huskuraft.effortless.building.BuildStage;
import dev.huskuraft.effortless.building.BuildState;
import dev.huskuraft.effortless.building.BuildType;
import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.SingleCommand;
import dev.huskuraft.effortless.building.StructureBuilder;
import dev.huskuraft.effortless.building.clipboard.Clipboard;
import dev.huskuraft.effortless.building.clipboard.Snapshot;
import dev.huskuraft.effortless.building.clipboard.SnapshotTransform;
import dev.huskuraft.effortless.building.config.ClientConfig;
import dev.huskuraft.effortless.building.history.OperationResultStack;
import dev.huskuraft.effortless.building.operation.ItemSummary;
import dev.huskuraft.effortless.building.operation.OperationResult;
import dev.huskuraft.effortless.building.operation.OperationTooltip;
import dev.huskuraft.effortless.building.operation.batch.BatchOperationResult;
import dev.huskuraft.effortless.building.operation.block.BlockOperation;
import dev.huskuraft.effortless.building.operation.block.BlockOperationResult;
import dev.huskuraft.effortless.building.pattern.Pattern;
import dev.huskuraft.effortless.building.replace.Replace;
import dev.huskuraft.effortless.building.session.BatchBuildSession;
import dev.huskuraft.effortless.building.structure.BuildMode;
import dev.huskuraft.effortless.building.structure.builder.Structure;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralContextCompiler;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationProgress;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.networking.packets.player.PlayerBuildPacket;
import dev.huskuraft.effortless.networking.packets.player.PlayerCommandPacket;
import dev.huskuraft.effortless.renderer.opertaion.children.BlockOperationRenderer;
import dev.huskuraft.effortless.renderer.outliner.OutlineRenderLayers;
import dev.huskuraft.effortless.screen.wheel.AbstractWheelScreen;
import dev.huskuraft.effortless.session.config.ConstraintConfig;
import dev.huskuraft.effortless.session.config.SessionConfig;

public final class EffortlessClientStructureBuilder extends StructureBuilder {

    private final EffortlessClient entrance;

    private final Map<UUID, Context> contexts = new HashMap<>();
    private final Map<UUID, Context> historyContexts = new HashMap<>();
    private final Map<UUID, OperationResultStack> undoRedoStacks = new HashMap<>();
    private final AtomicReference<ResourceLocation> lastClientPlayerLevel = new AtomicReference<>();
    private final ProceduralContextCompiler proceduralCompiler;
    private final ExecutorService proceduralPreviewExecutor;
    private String lastProceduralPreviewError = "";
    private PreviewCompilationCache proceduralPreviewCache;
    private AsyncPreviewJob proceduralPreviewJob;

    public EffortlessClientStructureBuilder(EffortlessClient entrance) {
        this.entrance = entrance;
        this.proceduralCompiler = new ProceduralContextCompiler(entrance);
        this.proceduralPreviewExecutor = Executors.newSingleThreadExecutor(task -> {
            var thread = new Thread(task, "Effortless procedural preview");
            thread.setDaemon(true);
            return thread;
        });

        getEntrance().getEventRegistry().getClientTickEvent().register(this::onClientTick);
    }

    private EffortlessClient getEntrance() {
        return entrance;
    }

    private Player getPlayer() {
        return getEntrance().getClient().getPlayer();
    }

    @Override
    public BuildResult updateContext(Player player, UnaryOperator<Context> updater) {
        var context = updater.apply(getContext(player));
        if (context.isFulfilled()) {
            var finalizedContext = context.finalize(player, BuildStage.INTERACT);
            var compilation = compileProcedural(
                    player,
                    finalizedContext,
                    false
            );
            if (!compilation.isSuccess()) {
                setContext(player, context.newInteraction());
                notifyProceduralFailure(player, compilation);
                return BuildResult.CANCELED;
            }
            var outgoingContext = compilation.context().orElseThrow();
            var outgoingPacket = new PlayerBuildPacket(
                    getPlayer().getId(),
                    outgoingContext
            );
            int packetBytes;
            try {
                packetBytes = compilation.positionCount() > 0
                        ? measurePacketBytes(outgoingPacket)
                        : 0;
            } catch (RuntimeException exception) {
                setContext(player, context.newInteraction());
                notifyProceduralFailure(
                        player,
                        ProceduralContextCompiler.CompilationResult.failure(
                                "Could not serialize the compiled placement",
                                List.of(
                                        exception.getClass().getSimpleName()
                                                + ": "
                                                + exception.getMessage()
                                )
                        )
                );
                return BuildResult.CANCELED;
            }
            if (compilation.positionCount() > 0) {
                Effortless.LOGGER.info(
                        "Compiled procedural placement: {} positions, "
                                + "{} estimated temporary bytes, "
                                + "{} serialized packet bytes",
                        compilation.positionCount(),
                        compilation.estimatedMemoryBytes(),
                        packetBytes
                );
            }
            int maximumPacketBytes = getEntrance().getConfigStorage().get()
                    .proceduralSafetyConfig().maxPacketBytes();
            if (packetBytes > maximumPacketBytes) {
                setContext(player, context.newInteraction());
                notifyProceduralFailure(
                        player,
                        ProceduralContextCompiler.CompilationResult.failure(
                                "Compiled placement packet is too large",
                                List.of(
                                        packetBytes + " bytes > "
                                                + maximumPacketBytes
                                                + " safe bytes"
                                )
                        )
                );
                return BuildResult.CANCELED;
            }
            setContext(player, context.newInteraction());

            var clientContext = outgoingContext.withBuildType(BuildType.BUILD_CLIENT);
            var result = new BatchBuildSession(getEntrance(), player, clientContext).commit();
            getEntrance().getChannel().sendPacket(outgoingPacket);
            showContext(context.id(), 1024, player, outgoingContext, result);

            playSoundInBatch(player, result);
            showTooltip(context.id(), 1024, player, result.getTooltip());
            getEntrance().getClientManager().getTooltipRenderer().hideEntry(generateId(player.getId(), Context.class), 0, true);

            return BuildResult.COMPLETED;
        } else {
            setContext(player, context);
            if (context.isIdle()) {
                return BuildResult.CANCELED;
            } else {
                return BuildResult.PARTIAL;
            }
        }
    }

    record TypedBlockSound(
            SoundType soundType,
            BlockState blockState
    ) {
        enum SoundType {
            BREAK,
            PLACE,
            HIT,
            FAIL,
        }

        static TypedBlockSound breakSound(BlockState blockState) {
            return new TypedBlockSound(SoundType.BREAK, blockState);
        }

        static TypedBlockSound failSound(BlockState blockState) {
            return new TypedBlockSound(SoundType.FAIL, blockState);
        }

        static TypedBlockSound placeSound(BlockState blockState) {
            return new TypedBlockSound(SoundType.PLACE, blockState);
        }

        static TypedBlockSound hitSound(BlockState blockState) {
            return new TypedBlockSound(SoundType.HIT, blockState);
        }
    }

    private void playSoundInBatch(Player player, BatchOperationResult batchOperationResult) {
        var soundMap = new HashMap<TypedBlockSound, Integer>();
        for (var operationResult : batchOperationResult.getResults()) {
            if (soundMap.size() >= 4) {
                break;
            }
            if (operationResult instanceof BlockOperationResult blockOperationResult) {
                if (blockOperationResult.getBlockStateForRenderer() == null) {
                    continue;
                }
                switch (blockOperationResult.getOperation().getType()) {
                    case UPDATE -> {
                        if (!blockOperationResult.getBlockStateForRenderer().isAir()) {
                            if (blockOperationResult.result().success()) {
                                soundMap.compute(TypedBlockSound.placeSound(blockOperationResult.getBlockStateForRenderer()), (o, i) -> i == null ? 1 : i + 1);
                            } else {
                                soundMap.compute(TypedBlockSound.failSound(blockOperationResult.getBlockStateForRenderer()), (o, i) -> i == null ? 1 : i + 1);
                            }
                        } else {
                            if (blockOperationResult.result().success()) {
                                soundMap.compute(TypedBlockSound.breakSound(blockOperationResult.getBlockStateForRenderer()), (o, i) -> i == null ? 1 : i + 1);
                            } else {
                                soundMap.compute(TypedBlockSound.failSound(blockOperationResult.getBlockStateForRenderer()), (o, i) -> i == null ? 1 : i + 1);
                            }
                        }
                    }
                    case INTERACT -> {
                        if (blockOperationResult.result().success()) {
                            soundMap.compute(TypedBlockSound.hitSound(blockOperationResult.getBlockStateForRenderer()), (o, i) -> i == null ? 1 : i + 1);
                        } else {
                            soundMap.compute(TypedBlockSound.failSound(blockOperationResult.getBlockStateForRenderer()), (o, i) -> i == null ? 1 : i + 1);
                        }
                    }
                    case COPY -> {
                        soundMap.compute(TypedBlockSound.hitSound(blockOperationResult.getBlockStateForRenderer()), (o, i) -> i == null ? 1 : i + 1);
                    }
                }
            }
        }
        var context = batchOperationResult.getOperation().getContext();
        var nearestInteraction = context.interactions().results().stream().filter(Objects::nonNull).min(Comparator.comparing(interaction1 -> interaction1.getBlockPosition().getCenter().distance(player.getEyePosition())));
        if (nearestInteraction.isEmpty()) {
            return;
        }
        var distance = player.getEyePosition().distance(nearestInteraction.get().getBlockPosition().getCenter());
        var location = player.getEyePosition().add(player.getEyeDirection().mul(Math.min(distance, 12)));
        for (var entry : soundMap.entrySet()) {
            var typedSound = entry.getKey();
            var count = entry.getValue();
            for (int i = 0; i <= MathUtils.min(count / 2, 4); i++) {
                if (typedSound.blockState() == null) {
                    continue;
                }
                var sound = switch (typedSound.soundType()) {
                    case BREAK ->
                            SoundInstance.createBlock(typedSound.blockState().getSoundSet().breakSound(), (typedSound.blockState().getSoundSet().volume() + 1.0F) / 2.0F, typedSound.blockState().getSoundSet().pitch() * 0.8F, location);
                    case PLACE ->
                            SoundInstance.createBlock(typedSound.blockState().getSoundSet().placeSound(), (typedSound.blockState().getSoundSet().volume() + 1.0F) / 2.0F, typedSound.blockState().getSoundSet().pitch() * 0.8F, location);
                    case HIT ->
                            SoundInstance.createBlock(typedSound.blockState().getSoundSet().hitSound(), (typedSound.blockState().getSoundSet().volume() + 1.0F) / 2.0F, typedSound.blockState().getSoundSet().pitch() * 0.8F, location);
                    case FAIL ->
                            SoundInstance.createBlock(typedSound.blockState().getSoundSet().hitSound(), (typedSound.blockState().getSoundSet().volume() + 1.0F) / 3.0F, typedSound.blockState().getSoundSet().pitch() * 0.5F, location);
                };
                getPlayer().getClient().getSoundManager().playDelayed(sound, i);
            }

        }

    }

    public void onSessionConfig(SessionConfig sessionConfig) {
        for (var uuid : getAllContexts().keySet()) {
            var config = sessionConfig.getByPlayer(uuid);
            getAllContexts().computeIfPresent(uuid, (uuid1, context) -> context.withConstraintConfig(config));
        }
    }

    @Override
    public Context getDefaultContext(Player player) {
        var constraintConfig = getEntrance().getSessionManager().getServerSessionConfig();
        var builderConfig = getEntrance().getConfigStorage().get().builderConfig();
        if (constraintConfig == null) {
            return Context.defaultSet().withConstraintConfig(ConstraintConfig.EMPTY).withBuilderConfig(builderConfig);
        }
        return Context.defaultSet().withConstraintConfig(constraintConfig.getByPlayer(player)).withBuilderConfig(builderConfig);
    }

    @Override
    public Context getContext(Player player) {
        return contexts.computeIfAbsent(player.getId(), uuid -> getDefaultContext(player));
    }

    private Context getHistoryContext(Player player) {
        return historyContexts.computeIfAbsent(player.getId(), uuid -> getDefaultContext(player));
    }

    private Context putHistoryContext(Player player, Context context) {
        return historyContexts.put(player.getId(), context);
    }

    @Override
    public Context getContextTraced(Player player) {
        var context = getContext(player).finalize(player, BuildStage.INTERACT);
        if (context.isInteractionEmpty()) {
            if (context.clipboard().enabled()) {
                if (context.clipboard().isEmpty()) {
                    context = context.withBuildState(BuildState.COPY_STRUCTURE);
                } else {
                    context = context.withBuildState(BuildState.PASTE_STRUCTURE);
                }
            } else {
                if (player.getItemStack(InteractionHand.MAIN).isBlock()) {
                    context = context.withBuildState(BuildState.PLACE_BLOCK);
                } else if (player.getItemStack(InteractionHand.MAIN).isDamageableItem()) {
                    context = context.withBuildState(BuildState.BREAK_BLOCK);
                } else {
                    context = context.withBuildState(BuildState.INTERACT_BLOCK);
                }
            }
        }
        return context.withNextInteraction(context.trace(player));
    }

    @Override
    public Map<UUID, Context> getAllContexts() {
        return contexts;
    }

    @Override
    public boolean setContext(Player player, Context context) {
        contexts.put(player.getId(), context);
        return true;
    }

    public boolean checkPermission(Player player) {
        if (!isSessionValid(player)) {
            getEntrance().getSessionManager().notifyPlayer();
            return false;
        }
        if (!isPermissionGranted(player)) {
            player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.no_permission")));
            return false;
        }
        return true;
    }

    @Override
    public boolean setStructure(Player player, Structure structure) {
        if (!checkPermission(player)) {
            return false;
        }
        updateContext(player, context -> context.withNoInteraction().withStructure(structure).withEmptyClipboard());
        if (structure.getMode().isDisabled()) {
            getEntrance().getClientManager().getTooltipRenderer().hideAllEntries(false);
            updateContext(player, context -> context);
        }
        return true;
    }

    @Override
    public boolean setClipboard(Player player, Clipboard clipboard) {
        if (!checkPermission(player)) {
            return false;
        }
        updateContext(player, context -> context.newInteraction().withClipboard(clipboard));
        return true;
    }

    @Override
    public boolean setPattern(Player player, Pattern pattern) {
        if (!checkPermission(player)) {
            return false;
        }
        updateContext(player, context -> context.withPattern(pattern).finalize(player, BuildStage.SET_PATTERN));
        return true;
    }

    @Override
    public boolean setReplace(Player player, Replace replace) {
        if (!checkPermission(player)) {
            return false;
        }
        updateContext(player, context -> context.withReplace(replace));
        return true;
    }

    @Override
    public void resetAll() {
        invalidateProceduralPreview();
        lastProceduralPreviewError = "";
        lastClientPlayerLevel.set(null);
        contexts.clear();
        undoRedoStacks.clear();
    }

    public EventResult onPlayerInteract(Player player, InteractionType type, InteractionHand hand) {
        if (getEntrance().getConfigStorage().get().builderConfig().passiveMode())
            if (!EffortlessKeys.PASSIVE_BUILD_MODIFIER.getKeyBinding().isDown() && !getContext(player).isBuilding()) {
                return EventResult.pass();
            }

        if (type == InteractionType.UNKNOWN) {
            return EventResult.pass();
        }

        var buildResult = updateContext(player, context -> {

            var state = switch (type) {
                case ATTACK -> {
                    if (context.clipboard().enabled()) {
                        yield BuildState.COPY_STRUCTURE;
                    } else {
                        yield BuildState.BREAK_BLOCK;
                    }
                }
                case USE_ITEM -> {
                    if (context.clipboard().enabled()) {
                        yield BuildState.PASTE_STRUCTURE;
                    } else {
                        if (player.getItemStack(hand).isEmpty() || !player.getItemStack(hand).isBlock()) {
                            yield BuildState.INTERACT_BLOCK;
                        }
                        yield BuildState.PLACE_BLOCK;
                    }
                }
                case UNKNOWN -> BuildState.IDLE;
            };

            var interaction = context.withBuildState(state).trace(player);
            var nextContext = context.withBuildState(state).withNextInteraction(interaction);

            if (interaction == null) {
                return context.newInteraction();
            }
            if (interaction.getTarget() == Interaction.Target.MISS) {
                var traced = player.raytrace(Short.MAX_VALUE, 0f, false);
                var message = Text.empty().append(" (").append(Text.text(String.valueOf(MathUtils.round(traced.getPosition().distance(player.getEyePosition())))).withStyle(ChatFormatting.RED)).append(Text.text("/")).append(Text.text(String.valueOf(context.configs().constraintConfig().maxReachDistance()))).append(Text.text(")"));
                player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.cannot_reach_target").append(message)));
//                player.sendClientMessage(Text.translate("effortless.message.building.client.cannot_reach_target").append(message), true);
                return context.newInteraction();
            }
            if (interaction.getTarget() == Interaction.Target.ENTITY) {
                player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.cannot_reach_entity")));
//                player.sendClientMessage(Text.translate("effortless.message.building.client.cannot_reach_entity"), true);
                return context.newInteraction();
            }
            if (context.isBuilding() && context.buildState() != state) {
                switch (context.buildState()) {
                    case BREAK_BLOCK -> player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.block_breaking_canceled")));
                    case PLACE_BLOCK -> player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.block_placing_canceled")));
                    case INTERACT_BLOCK -> player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.block_interacting_canceled")));
                    case COPY_STRUCTURE -> player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.structure_copying_canceled")));
                    case PASTE_STRUCTURE -> player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.structure_pasting_canceled")));
                }
                return context.newInteraction();
            }
            if (context.buildState() == BuildState.IDLE && state == BuildState.COPY_STRUCTURE && !context.clipboard().snapshot().isEmpty()) {
                player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.structure_pasting_canceled")));
//                player.sendClientMessage(Text.translate("effortless.message.building.client.structure_pasting_canceled"), true);
                return context.newInteraction().withEmptyClipboard();
            }

            if (!context.withBuildState(state).hasPermission()) {
                if (state == BuildState.BREAK_BLOCK) {
                    player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.no_block_break_permission")));
//                    player.sendClientMessage(Text.translate("effortless.message.building.client.no_block_break_permission"), true);
                }
                if (state == BuildState.PLACE_BLOCK) {
                    player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.no_block_place_permission")));
//                    player.sendClientMessage(Text.translate("effortless.message.building.client.no_block_place_permission"), true);
                }
                if (state == BuildState.INTERACT_BLOCK) {
                    player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.no_block_interact_permission")));
//                    player.sendClientMessage(Text.translate("effortless.message.building.client.no_block_interact_permission"), true);
                }
                if (state == BuildState.COPY_STRUCTURE) {
                    player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.no_structure_copy_permission")));
//                    player.sendClientMessage(Text.translate("effortless.message.building.client.no_structure_copy_permission"), true);
                }
                if (state == BuildState.PASTE_STRUCTURE) {
                    player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.no_structure_paste_permission")));
//                    player.sendClientMessage(Text.translate("effortless.message.building.client.no_structure_paste_permission"), true);
                }
                return context.newInteraction();
            }

            if (!nextContext.isVolumeInBounds()) {
                if (nextContext.buildState() == BuildState.BREAK_BLOCK) {
                    player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.block_break_volume_too_large").append(" (").append(Text.text(String.valueOf(nextContext.getVolume())).withStyle(ChatFormatting.RED)).append("/").append(String.valueOf(nextContext.getMaxVolume())).append(")")));
//                    player.sendClientMessage(Text.translate("effortless.message.building.client.block_break_volume_too_large").append(" (").append(Text.text(String.valueOf(nextContext.getBoxVolume())).withStyle(ChatFormatting.RED)).append("/").append(String.valueOf(nextContext.getMaxBoxVolume())).append(")"), true);
                }
                if (nextContext.buildState() == BuildState.PLACE_BLOCK) {
                    player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.block_place_volume_too_large").append(" (").append(Text.text(String.valueOf(nextContext.getVolume())).withStyle(ChatFormatting.RED)).append("/").append(String.valueOf(nextContext.getMaxVolume())).append(")")));
//                    player.sendClientMessage(Text.translate("effortless.message.building.client.block_place_volume_too_large").append(" (").append(Text.text(String.valueOf(nextContext.getBoxVolume())).withStyle(ChatFormatting.RED)).append("/").append(String.valueOf(nextContext.getMaxBoxVolume())).append(")"), true);
                }
                if (nextContext.buildState() == BuildState.INTERACT_BLOCK) {
                    player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.block_interact_volume_too_large").append(" (").append(Text.text(String.valueOf(nextContext.getVolume())).withStyle(ChatFormatting.RED)).append("/").append(String.valueOf(nextContext.getMaxVolume())).append(")")));
//                    player.sendClientMessage(Text.translate("effortless.message.building.client.block_interact_volume_too_large").append(" (").append(Text.text(String.valueOf(nextContext.getBoxVolume())).withStyle(ChatFormatting.RED)).append("/").append(String.valueOf(nextContext.getMaxBoxVolume())).append(")"), true);
                }
                if (nextContext.buildState() == BuildState.COPY_STRUCTURE) {
                    player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.structure_copy_volume_too_large").append(" (").append(Text.text(String.valueOf(nextContext.getVolume())).withStyle(ChatFormatting.RED)).append("/").append(String.valueOf(nextContext.getMaxVolume())).append(")")));
//                    player.sendClientMessage(Text.translate("effortless.message.building.client.structure_copy_volume_too_large").append(" (").append(Text.text(String.valueOf(nextContext.getBoxVolume())).withStyle(ChatFormatting.RED)).append("/").append(String.valueOf(nextContext.getMaxBoxVolume())).append(")"), true);
                }
                if (nextContext.buildState() == BuildState.PASTE_STRUCTURE) {
                    player.sendMessage(Effortless.getSystemMessage(Text.translate("effortless.message.building.server.structure_paste_volume_too_large").append(" (").append(Text.text(String.valueOf(nextContext.getVolume())).withStyle(ChatFormatting.RED)).append("/").append(String.valueOf(nextContext.getMaxVolume())).append(")")));
//                    player.sendClientMessage(Text.translate("effortless.message.building.client.structure_paste_volume_too_large").append(" (").append(Text.text(String.valueOf(nextContext.getBoxVolume())).withStyle(ChatFormatting.RED)).append("/").append(String.valueOf(nextContext.getMaxBoxVolume())).append(")"), true);
                }
                return context.newInteraction();
            }

            return nextContext;
        });

        if (buildResult.isSuccess()) {
            player.swing(hand);
        }

        return EventResult.interrupt(buildResult.isSuccess());
    }

    @Override
    public void onContextReceived(Player player, Context context) {
        if (context.isBuildType()) {
            return; // handle on server, will never happen
        }
        var result = new BatchBuildSession(getEntrance(), player, context).commit();

        showContext(player.getId(), 1024, player, context, result);
        showTooltip(context.id(), 1024, player, result.getTooltip());

        if (context.isBuildClientType()) {
            playSoundInBatch(player, result);
        }
    }

    public void onTooltipReceived(Player player, OperationTooltip operationTooltip) {
        switch (operationTooltip.type()) {
            case BUILD -> {
                showTooltip(operationTooltip.context().id(), 1024, player, operationTooltip);
            }
            default -> {
                if (operationTooltip.context().buildMode() == BuildMode.DISABLED) { // nothing
                    var entries = new ArrayList<>();
                    entries.add(operationTooltip.itemSummary().values().stream().flatMap(List::stream).toList());
                    entries.add(Text.translate("effortless.history." + operationTooltip.type().getName()));
                    entries.add(operationTooltip.context().buildMode().getIcon());
                    getEntrance().getClientManager().getTooltipRenderer().showGroupEntry(UUID.randomUUID(), 1024 + 1, entries, true);
                } else {
                    showTooltip(operationTooltip.context().id(), 1024, player, operationTooltip);
                }
            }
        }
    }

    public void onSnapshotCaptured(Player player, Snapshot snapshot) {
        updateContext(player, context -> context.withClipboard(context.clipboard().withSnapshot(snapshot)));
    }

    public void updateClipboard(Player player, SnapshotTransform action) {
        updateContext(player, context -> context.withClipboard(context.clipboard().withSnapshot(context.clipboard().snapshot().update(action))));
    }


    @Override
    public OperationResultStack getOperationResultStack(Player player) {
        return null;
    }

    @Override
    public void undo(Player player) {
        if (!checkPermission(player)) {
            return;
        }
        getEntrance().getChannel().sendPacket(new PlayerCommandPacket(SingleCommand.UNDO));
    }

    @Override
    public void redo(Player player) {
        if (!checkPermission(player)) {
            return;
        }
        getEntrance().getChannel().sendPacket(new PlayerCommandPacket(SingleCommand.REDO));
    }

    public void onClientTick(Client client, ClientTick.Phase phase) {
        if (phase == ClientTick.Phase.END) {
            return;
        }
        if (getEntrance().getClient() == null || getPlayer() == null) {
            resetAll();
            return;
        }

        var player = getPlayer();

        if (!isSessionValid(player)) {
            invalidateProceduralPreview();
            resetContext(player);
            return;
        }

        if (!isPermissionGranted(player)) {
            invalidateProceduralPreview();
            resetContext(player);
            return;
        }

        if (player.isDeadOrDying()) {
            invalidateProceduralPreview();
            resetInteractions(player);
            return;
        }

        if (!player.getWorld().getDimensionId().location().equals(lastClientPlayerLevel.get())) {
            invalidateProceduralPreview();
            resetInteractions(player);
            lastClientPlayerLevel.set(player.getWorld().getDimensionId().location());
            return;
        }

        if (getContext(player).isDisabled()) {
            invalidateProceduralPreview();
            clearBuildMessage(player);
            return;
        }

        if (getEntrance().getClientManager().getRoadEditor().isActive()
                || getEntrance().getClientManager().getTreeEditor()
                        .isActive()) {
            invalidateProceduralPreview();
            clearBuildMessage(player);
            return;
        }

        if (getEntrance().getConfigStorage().get().builderConfig().passiveMode() && !EffortlessKeys.PASSIVE_BUILD_MODIFIER.getKeyBinding().isDown() && !getContext(player).isBuilding()) {
            cancelProceduralPreview();
            getEntrance().getClientManager().getTooltipRenderer().hideEntry(generateId(player.getId(), Context.class), 0, false);
            return;
        }

        reloadContext(player);

        var context1 = getContextTraced(player);
        var context = context1.withBuildType(BuildType.PREVIEW);
        var localPreviewContext = context;
        boolean previewReady = true;
        boolean renderWithinLimit = context.getVolume()
                <= getEntrance().getConfigStorage().get().renderConfig().maxRenderVolume();
        if (renderWithinLimit) {
            var compilation = compileProcedural(player, context, true);
            if (compilation.isSuccess()) {
                previewReady = !compilation.pending();
                if (previewReady) {
                    localPreviewContext = compilation.context().orElseThrow()
                            .withBuildType(BuildType.PREVIEW);
                }
                lastProceduralPreviewError = "";
            } else {
                previewReady = false;
                if (!compilation.message().isEmpty()
                        && !compilation.message().equals(
                                lastProceduralPreviewError
                        )) {
                    lastProceduralPreviewError = compilation.message();
                    notifyProceduralFailure(player, compilation);
                }
            }
        }

        if (!renderWithinLimit || !previewReady) {
            if (!renderWithinLimit) {
                invalidateProceduralPreview();
            }
            showContext(player.getId(), 0, player, localPreviewContext, null);
            showTooltip(player.getId(), 0, player, OperationTooltip.build(localPreviewContext));
        } else {
            var result = new BatchBuildSession(getEntrance(), player, localPreviewContext).commit();
            showContext(player.getId(), 0, player, localPreviewContext, result);
            showTooltip(player.getId(), 0, player, result.getTooltip());
        }

        showBuildMessage(player, context);

        if (getHistoryContext(player).getVolume() != context.getVolume()) {
            putHistoryContext(player, context);
            var nearestInteraction = context.interactions().results().stream().filter(Objects::nonNull).min(Comparator.comparing(interaction1 -> interaction1.getBlockPosition().getCenter().distance(player.getEyePosition())));
            if (nearestInteraction.isEmpty()) {
                return;
            }
            var blockState = Items.AIR.item().getBlock().getDefaultBlockState();
            var distance = player.getEyePosition().distance(nearestInteraction.get().getBlockPosition().getCenter());
            var location = player.getEyePosition().add(player.getEyeDirection().mul(Math.min(distance, 3)));
            var sound = SoundInstance.createBlock(blockState.getSoundSet().hitSound(), (blockState.getSoundSet().volume() + 1.0F) / 2.0F * 0.1F, blockState.getSoundSet().pitch() * 0.2F, location);
            getEntrance().getClient().getSoundManager().play(sound);
        }

        getEntrance().getChannel().sendPacket(new PlayerBuildPacket(getPlayer().getId(), context));
    }

    private ProceduralContextCompiler.CompilationResult compileProcedural(
            Player player,
            Context context,
            boolean allowAsyncPreview
    ) {
        var library = getEntrance().getProceduralConfigStorage().get();
        if (!library.enabled()) {
            invalidateProceduralPreview();
            return ProceduralContextCompiler.CompilationResult.success(context, 0, 0L);
        }
        var resolution = library.resolvedActivePreset();
        if (!resolution.isSuccess()) {
            invalidateProceduralPreview();
            return ProceduralContextCompiler.CompilationResult.failure(
                    "The active procedural pattern could not be composed",
                    resolution.errors()
            );
        }
        if (context.buildState() != BuildState.PLACE_BLOCK) {
            invalidateProceduralPreview();
            return ProceduralContextCompiler.CompilationResult.success(context, 0, 0L);
        }
        if (!context.tracingResult().isSuccess()) {
            invalidateProceduralPreview();
            return ProceduralContextCompiler.CompilationResult.success(context, 0, 0L);
        }
        var preset = resolution.preset().orElseThrow();
        if (preset.inspectExistingWorld()) {
            // Existing neighbor state is a declared determinism input and can
            // change without the Context changing, so it is never cached.
            invalidateProceduralPreview();
            return proceduralCompiler.compile(player, context, preset);
        }
        var key = new PreviewCompilationKey(
                context.id(),
                context.buildState(),
                context.interactions(),
                context.structure(),
                context.pattern(),
                context.configs(),
                preset,
                ProceduralContextCompiler.materialSourceFingerprint(
                        player,
                        preset
                )
        );
        if (proceduralPreviewCache != null && proceduralPreviewCache.key().equals(key)) {
            var cached = proceduralPreviewCache.result();
            if (!cached.isSuccess()) {
                return cached;
            }
            return ProceduralContextCompiler.CompilationResult.success(
                    context.withPattern(cached.context().orElseThrow().pattern()),
                    cached.positionCount(),
                    cached.estimatedMemoryBytes()
            );
        }

        var completed = completeAsyncPreviewIfReady(player, context, key);
        if (completed != null) {
            return completed;
        }

        if (!allowAsyncPreview) {
            cancelProceduralPreview();
            var result = proceduralCompiler.compile(player, context, preset);
            proceduralPreviewCache = new PreviewCompilationCache(key, result);
            return result;
        }

        if (proceduralPreviewJob != null
                && proceduralPreviewJob.key.equals(key)) {
            reportProceduralPreviewProgress(player, proceduralPreviewJob);
            return ProceduralContextCompiler.CompilationResult.pending(
                    context,
                    proceduralPreviewJob.prepared.positionCount(),
                    proceduralPreviewJob.prepared.estimatedMemoryBytes()
            );
        }

        cancelProceduralPreview();
        var preparation = proceduralCompiler.prepare(player, context, preset);
        if (!preparation.isSuccess()) {
            return preparation.failure().orElseThrow();
        }
        var prepared = preparation.prepared().orElseThrow();
        int asyncThreshold = getEntrance().getConfigStorage().get()
                .proceduralSafetyConfig()
                .asyncPreviewPositionThreshold();
        if (prepared.positionCount() < asyncThreshold) {
            var resolutionResult = proceduralCompiler.resolve(
                    prepared,
                    Thread.currentThread()::isInterrupted,
                    GenerationProgress.NONE
            );
            var result = resolutionResult.isSuccess()
                    ? proceduralCompiler.finish(
                            prepared,
                            resolutionResult.resolved().orElseThrow()
                    )
                    : ProceduralContextCompiler.CompilationResult.failure(
                            resolutionResult.message(),
                            resolutionResult.details()
                    );
            proceduralPreviewCache = new PreviewCompilationCache(key, result);
            return result;
        }

        startAsyncPreview(player, key, prepared);
        return ProceduralContextCompiler.CompilationResult.pending(
                context,
                prepared.positionCount(),
                prepared.estimatedMemoryBytes()
        );
    }

    private void startAsyncPreview(
            Player player,
            PreviewCompilationKey key,
            ProceduralContextCompiler.PreparedCompilation prepared
    ) {
        var job = new AsyncPreviewJob(key, prepared);
        job.future = proceduralPreviewExecutor.submit(() ->
                proceduralCompiler.resolve(
                        prepared,
                        () -> job.cancelled.get()
                                || Thread.currentThread().isInterrupted(),
                        (stage, completed, total) -> job.progress.set(
                                new PreviewProgress(stage, completed, total)
                        )
                )
        );
        proceduralPreviewJob = job;
        if (getEntrance().getConfigStorage().get()
                .proceduralSafetyConfig().showPreparationMessages()) {
            player.sendMessage(Effortless.getSystemMessage(
                    Text.text(
                            "Compiling procedural preview for "
                                    + prepared.positionCount() + " blocks..."
                    ).withStyle(ChatFormatting.GRAY)
            ));
        }
    }

    private ProceduralContextCompiler.CompilationResult
    completeAsyncPreviewIfReady(
            Player player,
            Context context,
            PreviewCompilationKey key
    ) {
        var job = proceduralPreviewJob;
        if (job == null || !job.key.equals(key) || !job.future.isDone()) {
            return null;
        }
        proceduralPreviewJob = null;
        ProceduralContextCompiler.CompilationResult result;
        try {
            var resolution = job.future.get();
            result = resolution.isSuccess()
                    ? proceduralCompiler.finish(
                            job.prepared,
                            resolution.resolved().orElseThrow()
                    )
                    : ProceduralContextCompiler.CompilationResult.failure(
                            resolution.message(),
                            resolution.details()
                    );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            result = ProceduralContextCompiler.CompilationResult.failure(
                    "Procedural preview compilation was interrupted",
                    List.of()
            );
        } catch (ExecutionException exception) {
            var cause = exception.getCause() == null
                    ? exception
                    : exception.getCause();
            result = ProceduralContextCompiler.CompilationResult.failure(
                    "Procedural preview worker failed",
                    List.of(
                            cause.getClass().getSimpleName() + ": "
                                    + cause.getMessage()
                    )
            );
        }
        proceduralPreviewCache = new PreviewCompilationCache(key, result);
        if (result.isSuccess()) {
            if (getEntrance().getConfigStorage().get()
                    .proceduralSafetyConfig().showPreparationMessages()) {
                player.sendMessage(Effortless.getSystemMessage(
                        Text.text("Procedural preview ready")
                                .withStyle(ChatFormatting.GREEN)
                ));
            }
            return ProceduralContextCompiler.CompilationResult.success(
                    context.withPattern(
                            result.context().orElseThrow().pattern()
                    ),
                    result.positionCount(),
                    result.estimatedMemoryBytes()
            );
        }
        return result;
    }

    private void reportProceduralPreviewProgress(
            Player player,
            AsyncPreviewJob job
    ) {
        if (!getEntrance().getConfigStorage().get()
                .proceduralSafetyConfig().showPreparationMessages()) {
            return;
        }
        var progress = job.progress.get();
        if (progress.total() <= 0) {
            return;
        }
        int bucket = Math.min(
                4,
                progress.completed() * 4 / progress.total()
        );
        if (progress.stage() == job.reportedStage
                && bucket == job.reportedBucket) {
            return;
        }
        job.reportedStage = progress.stage();
        job.reportedBucket = bucket;
        player.sendMessage(Effortless.getSystemMessage(
                Text.text(
                        "Procedural preview "
                                + progress.stage().name().toLowerCase(Locale.ROOT)
                                + ": " + bucket * 25 + "%"
                ).withStyle(ChatFormatting.GRAY)
        ));
    }

    private void cancelProceduralPreview() {
        var job = proceduralPreviewJob;
        proceduralPreviewJob = null;
        if (job != null) {
            job.cancelled.set(true);
            if (job.future != null) {
                job.future.cancel(true);
            }
        }
    }

    /**
     * Places an already materialized client snapshot through the unchanged
     * stock clipboard protocol. The previous build tool, clipboard and pattern
     * are restored immediately after the one-shot request is emitted.
     */
    public BuildResult placeCompiledSnapshot(
            Player player,
            Snapshot snapshot,
            BlockPosition anchor,
            BlockInteraction referenceInteraction
    ) {
        return applyCompiledSnapshot(
                player,
                snapshot,
                anchor,
                referenceInteraction,
                "Road placement"
        );
    }

    /**
     * Executes an already materialized client snapshot through the stock
     * clipboard request. This is also used for explicit air snapshots, whose
     * entries follow the server's normal block-breaking path.
     */
    public BuildResult applyCompiledSnapshot(
            Player player,
            Snapshot snapshot,
            BlockPosition anchor,
            BlockInteraction referenceInteraction,
            String operationName
    ) {
        var label = operationName == null || operationName.isBlank()
                ? "Compiled operation"
                : operationName;
        if (snapshot == null || snapshot.isEmpty()) {
            player.sendMessage(Effortless.getSystemMessage(
                    Text.text(label + " contains no blocks")
                            .withStyle(ChatFormatting.RED)
            ));
            return BuildResult.CANCELED;
        }
        var previous = getContext(player);
        var structure = previous.isDisabled()
                ? getEntrance().getConfigStorage()
                        .getStructure(BuildMode.SINGLE)
                : previous.structure();
        var anchorInteraction = referenceInteraction
                .withPosition(anchor.getCenter())
                .withBlockPosition(anchor);
        var outgoing = previous.newInteraction()
                .withStructure(structure)
                .withBuildState(BuildState.PASTE_STRUCTURE)
                .withBuildType(BuildType.BUILD)
                .withNoInteraction()
                .withNextInteraction(anchorInteraction)
                .withClipboard(Clipboard.of(true, snapshot))
                .withPattern(Pattern.DISABLED)
                .withPlayerExtras(player);

        if (!outgoing.hasPermission()) {
            player.sendMessage(Effortless.getSystemMessage(
                    Text.text("The server does not allow clipboard operations")
                            .withStyle(ChatFormatting.RED)
            ));
            return BuildResult.CANCELED;
        }
        if (!outgoing.isVolumeInBounds()) {
            player.sendMessage(Effortless.getSystemMessage(
                    Text.text(label + " bounding volume exceeds the server limit ("
                            + outgoing.getVolume() + "/"
                            + outgoing.getMaxVolume() + ")")
                            .withStyle(ChatFormatting.RED)
            ));
            return BuildResult.CANCELED;
        }

        int packetBytes;
        try {
            packetBytes = measurePacketBytes(
                    new PlayerBuildPacket(player.getId(), outgoing)
            );
        } catch (RuntimeException exception) {
            player.sendMessage(Effortless.getSystemMessage(
                    Text.text("Could not serialize " + label.toLowerCase(Locale.ROOT)
                            + ": "
                            + exception.getMessage())
                            .withStyle(ChatFormatting.RED)
            ));
            return BuildResult.CANCELED;
        }
        int maximumPacketBytes = getEntrance().getConfigStorage().get()
                .proceduralSafetyConfig().maxPacketBytes();
        if (packetBytes > maximumPacketBytes) {
            player.sendMessage(Effortless.getSystemMessage(
                    Text.text(label + " packet is too large (" + packetBytes
                            + " bytes > " + maximumPacketBytes + ")")
                            .withStyle(ChatFormatting.RED)
            ));
            return BuildResult.CANCELED;
        }

        Effortless.LOGGER.info(
                "{}: {} blocks, {} bounding volume, "
                        + "{} serialized packet bytes",
                label,
                snapshot.blockData().size(),
                snapshot.volume(),
                packetBytes
        );
        var result = updateContext(player, ignored -> outgoing);
        setContext(player, previous.newInteraction());
        return result;
    }

    private void invalidateProceduralPreview() {
        cancelProceduralPreview();
        proceduralPreviewCache = null;
    }

    private int measurePacketBytes(PlayerBuildPacket packet) {
        var buffer = getEntrance().getChannel().createBuffer(packet);
        try {
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }

    private void notifyProceduralFailure(
            Player player,
            ProceduralContextCompiler.CompilationResult failure
    ) {
        var message = Text.text("Procedural pattern: " + failure.message())
                .withStyle(ChatFormatting.RED);
        if (!failure.details().isEmpty()) {
            message = message.append(
                    Text.text(" (" + failure.details().get(0) + ")")
                            .withStyle(ChatFormatting.GRAY)
            );
        }
        player.sendMessage(Effortless.getSystemMessage(message));
    }

    private record PreviewCompilationKey(
            UUID contextId,
            BuildState buildState,
            Context.Interactions interactions,
            Structure structure,
            Pattern pattern,
            Context.Configs configs,
            ProceduralPatternPreset preset,
            long materialSourceFingerprint
    ) {
    }

    private record PreviewCompilationCache(
            PreviewCompilationKey key,
            ProceduralContextCompiler.CompilationResult result
    ) {
    }

    private record PreviewProgress(
            GenerationProgress.Stage stage,
            int completed,
            int total
    ) {

        private static final PreviewProgress NONE =
                new PreviewProgress(GenerationProgress.Stage.GENERATING, 0, 0);
    }

    private static final class AsyncPreviewJob {

        private final PreviewCompilationKey key;
        private final ProceduralContextCompiler.PreparedCompilation prepared;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<PreviewProgress> progress =
                new AtomicReference<>(PreviewProgress.NONE);
        private Future<ProceduralContextCompiler.ResolutionResult> future;
        private GenerationProgress.Stage reportedStage;
        private int reportedBucket = -1;

        private AsyncPreviewJob(
                PreviewCompilationKey key,
                ProceduralContextCompiler.PreparedCompilation prepared
        ) {
            this.key = key;
            this.prepared = prepared;
        }
    }

    private void reloadContext(Player player) {
        setContext(player, getContext(player).finalize(player, BuildStage.TICK));


//        if (Keys.KEY_LEFT_CONTROL.getKeyBinding().isKeyDown()) {
//            setContext(player, getContext(player).withBuildFeature(PlaneLength.EQUAL));
//        } else {
//            setContext(player, getContext(player).withBuildFeature(PlaneLength.VARIABLE));
//        }
    }

    private boolean isSessionValid(Player player) {
        return getEntrance().getSessionManager().isSessionValid();
    }

    private boolean isPermissionGranted(Player player) {
        return getEntrance().getSessionManager().getServerSessionConfig().getByPlayer(player).allowUseMod();
    }

    private UUID generateId(UUID uuid, Object tag) {
        return new UUID(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits() + tag.hashCode());
    }

    public void showContext(UUID uuid, int priority, Player player, Context context, OperationResult result) {
        if (player.getId() != getPlayer().getId()) {
            if (!getEntrance().getConfigStorage().get().renderConfig().showOtherPlayersBuild()) {
                return;
            }
        }
        getEntrance().getClientManager().getPatternRenderer().showPattern(uuid, context);

        if (context.interactions().isEmpty()) {
            getEntrance().getClientManager().getOutlineRenderer().remove(generateId(uuid, BoundingBox3d.class));
        } else {
            var box = BoundingBox3d.fromLowerCornersOf(context.interactions().results().stream().filter(Objects::nonNull).map(BlockInteraction::getBlockPosition).map(BlockPosition::toVector3i).toArray(Vector3i[]::new));
            getEntrance().getClientManager().getOutlineRenderer().showBoundingBox(generateId(uuid, BoundingBox3d.class), box)
                    .texture(OutlineRenderLayers.CHECKERED_THIN_TEXTURE_LOCATION)
                    .lightMap(LightTexture.FULL_BLOCK)
                    .disableNormals()
                    .colored(Color.DARK_GRAY)
                    .stroke(1 / 32f);
        }

        if (result instanceof BatchOperationResult batchOperationResult) {
            getEntrance().getClientManager().getOperationsRenderer().showResult(uuid, result);

            var resultMap = batchOperationResult.getResults().stream().filter(BlockOperationResult.class::isInstance).map(BlockOperationResult.class::cast).filter(blockOperationResult -> BlockOperationRenderer.getColorByOpResult(blockOperationResult) != null).collect(Collectors.groupingBy(BlockOperationRenderer::getColorByOpResult));

            for (var allColor : BlockOperationRenderer.getAllColors()) {
                if (resultMap.get(allColor) == null) {
                    getEntrance().getClientManager().getOutlineRenderer().remove(generateId(uuid, allColor));
                    continue;
                }
                var locations = resultMap.get(allColor).stream().map(BlockOperationResult::getOperation).map(BlockOperation::getBlockPosition).filter(Objects::nonNull).toList();
                getEntrance().getClientManager().getOutlineRenderer().showCluster(generateId(uuid, allColor), locations)
                        .texture(OutlineRenderLayers.CHECKERED_THIN_TEXTURE_LOCATION)
                        .lightMap(LightTexture.FULL_BLOCK)
                        .disableNormals()
                        .colored(allColor)
                        .stroke(1 / 32f);
            }
        } else {
            getEntrance().getClientManager().getOperationsRenderer().remove(uuid);
            for (var allColor : BlockOperationRenderer.getAllColors()) {
                getEntrance().getClientManager().getOutlineRenderer().remove(generateId(uuid, allColor));
            }
        }
    }

    public void showTooltip(UUID id, int priority, Player player, OperationTooltip tooltip) {
        var context = tooltip.context();

        if (player == null) {
            player = getPlayer();
        }

        if (player.getId() != getPlayer().getId()) {
            if (!getEntrance().getConfigStorage().get().renderConfig().showOtherPlayersBuildTooltips()) {
                return;
            }
        }
        if (player.getGameMode().isSpectator()) {
            getEntrance().getClientManager().getTooltipRenderer().hideEntry(generateId(id, Context.class), priority, false);
            return;
        }
        var entries = new ArrayList<>();

        var blockStateSummary = tooltip.itemSummary();
        if (!blockStateSummary.isEmpty()) {
            var allProducts = new ArrayList<ItemStack>();
            for (var summary : ItemSummary.values()) {
                var items = blockStateSummary.getOrDefault(summary, List.of());
                if (items.isEmpty()) {
                    continue;
                }
                var color = switch (summary) {
                    case BLOCKS_PLACED -> ChatFormatting.WHITE;
                    case BLOCKS_DESTROYED -> ChatFormatting.RED;
                    case BLOCKS_INTERACTED -> ChatFormatting.YELLOW;
                    case BLOCKS_COPIED -> ChatFormatting.GREEN;
                    case BLOCKS_NOT_REPLACEABLE -> ChatFormatting.GRAY;
                    case BLOCKS_NOT_BREAKABLE -> ChatFormatting.GRAY;
                    case BLOCKS_NOT_INTERACTABLE -> ChatFormatting.GRAY;
                    case BLOCKS_NOT_COPYABLE -> ChatFormatting.GRAY;
                    case BLOCKS_ITEMS_INSUFFICIENT -> ChatFormatting.RED;
                    case BLOCKS_TOOLS_INSUFFICIENT -> ChatFormatting.GRAY;
                    case BLOCKS_BLACKLISTED -> ChatFormatting.GRAY;
                    case BLOCKS_NO_PERMISSION -> ChatFormatting.GRAY;

                    case CONTAINER_CONSUMED -> ChatFormatting.WHITE;
                    case CONTAINER_DROPPED -> ChatFormatting.WHITE;
                };
                entries.add(new Tuple2<>(items, color.getColor()));
                entries.add(Text.translate("effortless.build.summary." + summary.name().toLowerCase(Locale.ROOT)).withStyle(color));
                allProducts.addAll(items);
            }
            if (allProducts.isEmpty()) {
                entries.add(Text.translate("effortless.build.summary.no_item_summary").withStyle(ChatFormatting.GRAY));
            }
        } else {
            entries.add(Text.translate("effortless.build.summary.pending_item_summary").withStyle(ChatFormatting.GRAY));
        }


        var texts = new ArrayList<Tuple2<Text, Text>>();
        texts.add(new Tuple2<>(Text.translate("effortless.build.summary.structure").withStyle(ChatFormatting.WHITE), context.buildMode().getDisplayName().withStyle(ChatFormatting.GOLD)));
        texts.add(new Tuple2<>(AbstractWheelScreen.button(context.replaceStrategy()).getCategory().withStyle(ChatFormatting.WHITE), AbstractWheelScreen.button(context.replaceStrategy()).getName().withStyle(ChatFormatting.GOLD)));

        for (var supportedFeature : context.structure().getSupportedFeatures()) {
            var option = context.buildFeatures().stream().filter(feature -> Objects.equals(feature.getCategory(), supportedFeature.getName())).findFirst();
            if (option.isEmpty()) continue;
            var button = AbstractWheelScreen.button(option.get());
            texts.add(new Tuple2<>(button.getCategory().withStyle(ChatFormatting.WHITE), button.getName().withStyle(ChatFormatting.GOLD)));
        }
        if (context.pattern().enabled()) {
            texts.add(new Tuple2<>(Text.translate("effortless.build.summary.pattern").withStyle(ChatFormatting.WHITE), (context.pattern().enabled() ? Text.translate("effortless.build.summary.pattern_enabled") : Text.translate("effortless.build.summary.pattern_disabled")).withStyle(ChatFormatting.GOLD)));
        }

        entries.add(texts);

        entries.add(context.buildMode().getIcon());
        getEntrance().getClientManager().getTooltipRenderer().showGroupEntry(generateId(id, Context.class), priority, entries, context.isBuildType());

    }

    private boolean isBuildMessageVisible = false;

    public void clearBuildMessage(Player player) {
        if (this.isBuildMessageVisible) {
            player.sendMessage(Text.empty(), true);
            this.isBuildMessageVisible = false;
        }

    }

    public void showBuildMessage(Player player, Context context) {
        var dimensions = Stream.of(context.getInteractionBox().x(), context.getInteractionBox().y(), context.getInteractionBox().z()).filter(i -> i > 1).toList();
        if (dimensions.isEmpty()) {
            dimensions = List.of(1);
        }
        var message = Text.empty();
        if (context.tracingResult().isSuccess()) {
            message = message.append(context.buildState().getDisplayName(context.buildMode()))
                    .append(" ")
                    .append("(")
                    .append(dimensions.stream().map(String::valueOf).collect(Collectors.joining("x")))
                    .append("=")
                    .append(Text.text(String.valueOf(context.getVolume())).withStyle(!context.isVolumeInBounds() ? ChatFormatting.RED : ChatFormatting.WHITE))
                    .append(")");
        } else {
            message = Text.empty();
//            switch (context.tracingResult()) {
//                case SUCCESS_FULFILLED -> {
//                }
//                case SUCCESS_PARTIAL -> {
//                }
//                case PASS -> {
//                }
//                case FAILED -> {
//                    message = message.append(Text.translate("effortless.message.building.client.cannot_reach_target").withStyle(ChatFormatting.WHITE));
//                    var interaction = context.interactions().results().stream().filter(result -> result != null && result.getTarget() == Interaction.Target.MISS).findAny();
//                    if (interaction.isPresent()) {
//                        message = message.append(" (").append(Text.text(String.valueOf(MathUtils.round(interaction.get().getBlockPosition().toVector3i().distance(player.getPosition().toVector3i())))).withStyle(ChatFormatting.RED)).append("/").append(String.valueOf(context.configs().constraintConfig().maxReachDistance())).append(")");
//                    }
//                }
//            }
        }

        player.sendMessage(message, true);
        this.isBuildMessageVisible = true;


    }

}
