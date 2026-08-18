package dev.huskuraft.effortless.client.road;

import static dev.huskuraft.effortless.client.editor.EditorGeometry.matches;
import static dev.huskuraft.effortless.client.editor.EditorGeometry.distanceToPath;
import static dev.huskuraft.effortless.client.editor.EditorGeometry.axisFromView;
import static dev.huskuraft.effortless.client.editor.EditorGeometry.pathFromView;
import static dev.huskuraft.effortless.client.editor.EditorGeometry.nearestAxis;
import static dev.huskuraft.effortless.client.editor.EditorGeometry.playerPosition;
import static dev.huskuraft.effortless.client.editor.EditorGeometry.pointBox;
import static dev.huskuraft.effortless.client.editor.EditorGeometry.targetPoint;
import static dev.huskuraft.effortless.client.editor.EditorGeometry.vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.huskuraft.effortless.Effortless;
import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.building.BuildResult;
import dev.huskuraft.effortless.building.clipboard.Snapshot;
import dev.huskuraft.effortless.building.config.ProceduralSafetyConfig;
import dev.huskuraft.effortless.client.editor.ControlPointDraft;
import dev.huskuraft.effortless.client.editor.ControlAxis;
import dev.huskuraft.effortless.client.editor.EditorGeometry.Segment;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralContextCompiler;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralPreviewMarkers;
import dev.huskuraft.effortless.client.pattern.procedural.config.PatternMaterialSource;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.renderer.opertaion.BlockRenderLayers;
import dev.huskuraft.universal.api.core.BlockInteraction;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.Interaction;
import dev.huskuraft.universal.api.core.InteractionHand;
import dev.huskuraft.universal.api.core.InteractionType;
import dev.huskuraft.universal.api.core.Item;
import dev.huskuraft.universal.api.core.ItemStack;
import dev.huskuraft.universal.api.core.ResourceKey;
import dev.huskuraft.universal.api.core.Tuple2;
import dev.huskuraft.universal.api.core.World;
import dev.huskuraft.universal.api.events.EventResult;
import dev.huskuraft.universal.api.input.Keys;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/**
 * Client-only in-world control-point editor for procedural roads.
 */
public final class RoadEditor {

    private static final double POINT_PICK_RADIUS = 1.35;
    private static final double CURVE_PICK_RADIUS = 1.75;
    private static final double GIZMO_PICK_RADIUS = 1.1;
    private static final double GIZMO_LENGTH = 2.0;
    private static final double GIZMO_RAY_PICK_RADIUS = 0.32;
    private static final double GIZMO_RAY_REACH = 8.0;
    private static final double GIZMO_RAY_START_OFFSET = 0.35;
    private static final double ROAD_RAY_PICK_RADIUS = 0.55;
    private static final double ROAD_RAY_REACH = 16.0;
    private static final Object CELL_OUTLINE = "effortless:road/cells";
    private static final Object ROAD_TOOLTIP = "effortless:road/tooltip";
    private static final int ROAD_TOOLTIP_PRIORITY = 512;
    private static final int PREVIEW_COLOR = 0x72FFFFFF;

    private final EffortlessClient entrance;
    private final Set<Object> visibleOutlineIds = new HashSet<>();
    private final ExecutorService materialPreviewExecutor;

    private ControlPointDraft draft = ControlPointDraft.EMPTY;
    private RoadProfile profile = RoadProfile.DEFAULT;
    private RoadVoxelizer.Result preview =
            RoadVoxelizer.Result.failure(java.util.List.of("No spline"));
    private RoadPatternCompiler.CompilationResult materialPreview =
            RoadPatternCompiler.CompilationResult.failure(
                    "Pending",
                    java.util.List.of()
            );
    private ProceduralPatternPreset materialPreviewPreset;
    private ProceduralSafetyConfig materialPreviewSafety;
    private Future<RoadPatternCompiler.CompilationResult>
            materialPreviewFuture;
    private boolean materialPreviewDirty = true;
    private int tooltipRefresh;
    private boolean active;
    private boolean moveArmed;
    private ControlAxis axisMove = ControlAxis.NONE;
    private ResourceKey<World> draftDimension;

    public RoadEditor(EffortlessClient entrance) {
        this.entrance = entrance;
        this.materialPreviewExecutor = Executors.newSingleThreadExecutor(
                task -> {
                    var thread = new Thread(
                            task,
                            "Effortless road preview"
                    );
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    public boolean isActive() {
        return active;
    }

    public ControlPointDraft draft() {
        return draft;
    }

    public RoadProfile profile() {
        return profile;
    }

    public RoadVoxelizer.Result preview() {
        return preview;
    }

    public void start(RoadProfile roadProfile) {
        clearOutlines();
        var dimension = entrance.getClient().getPlayer()
                .getWorld()
                .getDimensionId();
        if (draftDimension != null
                && !Objects.equals(draftDimension, dimension)) {
            clearDraftState();
        }
        draftDimension = dimension;
        profile = roadProfile == null ? RoadProfile.DEFAULT : roadProfile;
        invalidatePreview();
        moveArmed = false;
        axisMove = ControlAxis.NONE;
        active = true;
        message(
                draft.points().isEmpty()
                        ? "Spline editor: use item for P1/P2, click points or "
                                + "the curve to edit, Shift + use item to place"
                        : "Spline draft restored: " + draft.points().size()
                                + " points; Shift + use item to place",
                ChatFormatting.GOLD
        );
    }

    public boolean startFromActivePattern() {
        return startFromActivePattern(null);
    }

    public boolean startFromActivePattern(SplineSubtype subtype) {
        var resolved = entrance.getProceduralConfigStorage().get()
                .resolvedActivePreset();
        if (!resolved.isSuccess()) {
            message(
                    "Cannot start spline editor: "
                            + String.join("; ", resolved.errors()),
                    ChatFormatting.RED
            );
            return false;
        }
        var configured = resolved.preset().orElseThrow()
                .advanced().roadProfile();
        start(subtype == null ? configured : configured.withSubtype(subtype));
        return true;
    }

    public void toggle() {
        if (active) {
            cancel();
            return;
        }
        startFromActivePattern();
    }

    public void cancel() {
        if (!active) {
            return;
        }
        active = false;
        moveArmed = false;
        axisMove = ControlAxis.NONE;
        message("Spline editor deselected; draft retained", ChatFormatting.GRAY);
    }

    public EventResult onInteraction(
            InteractionType type,
            InteractionHand hand
    ) {
        if (!active) {
            return EventResult.pass();
        }
        boolean shift = Keys.KEY_LEFT_SHIFT.isDown()
                || Keys.KEY_RIGHT_SHIFT.isDown();
        boolean control = Keys.KEY_LEFT_CONTROL.isDown()
                || Keys.KEY_RIGHT_CONTROL.isDown();
        var interaction = entrance.getClient().getLastInteraction();
        var blockInteraction = interaction instanceof BlockInteraction block
                && interaction.getTarget() == Interaction.Target.BLOCK
                        ? block
                        : null;
        if (type == InteractionType.USE_ITEM && control) {
            if (isRoadHit(blockInteraction) || roadFromView()) {
                clearRoad();
                entrance.getClient().getPlayer().swing(hand);
            } else {
                message(
                        "Aim at the spline before pressing Ctrl + use item",
                        ChatFormatting.RED
                );
            }
            return EventResult.interruptTrue();
        }
        if (type == InteractionType.USE_ITEM && !shift && !control) {
            var viewAxis = gizmoFromView();
            if (viewAxis != ControlAxis.NONE) {
                selectAxis(viewAxis);
                entrance.getClient().getPlayer().swing(hand);
                return EventResult.interruptTrue();
            }
        }
        if (blockInteraction == null) {
            message("Aim at a block to edit the spline", ChatFormatting.RED);
            return EventResult.interruptFalse();
        }

        if (type == InteractionType.ATTACK && shift) {
            destroy(blockInteraction, hand);
            return EventResult.interruptTrue();
        }
        if (type == InteractionType.ATTACK) {
            onAttack();
            return EventResult.interruptTrue();
        }
        if (type != InteractionType.USE_ITEM) {
            return EventResult.interruptFalse();
        }

        if (shift) {
            confirm(blockInteraction, hand);
            return EventResult.interruptTrue();
        }

        var target = targetPoint(blockInteraction);
        editAt(target);
        entrance.getClient().getPlayer().swing(hand);
        return EventResult.interruptTrue();
    }

    public void tick() {
        var player = entrance.getClient().getPlayer();
        if (player == null) {
            return;
        }
        if (draft.points().isEmpty()
                || draftDimension == null
                || !Objects.equals(
                        draftDimension,
                        player.getWorld().getDimensionId()
                )) {
            clearOutlines();
            hideRoadTooltip();
            return;
        }
        renderOutlines();
        refreshMaterialPreview();
        if (++tooltipRefresh >= 20) {
            tooltipRefresh = 0;
            refreshRoadTooltip();
        }
    }

    /**
     * Draws the exact compiled palette output. Control points and curve
     * outlines remain visible when this material preview is distance-hidden.
     */
    public void render(Renderer renderer, float deltaTick) {
        var player = entrance.getClient().getPlayer();
        if (player == null
                || draft.points().isEmpty()
                || !materialPreview.isSuccess()
                || draftDimension == null
                || !Objects.equals(
                        draftDimension,
                        player.getWorld().getDimensionId()
                )) {
            return;
        }
        var renderConfig = entrance.getConfigStorage().get().renderConfig();
        if (!renderConfig.showBlockPreview()
                || materialPreview.positionCount()
                        > renderConfig.maxRenderVolume()
                || distanceToRoad(playerPosition(
                        entrance.getClient().getPlayer()))
                        > entrance.getConfigStorage().get()
                                .proceduralSafetyConfig()
                                .roadPreviewDistance()) {
            return;
        }
        Snapshot snapshot = materialPreview.snapshot().orElseThrow();
        BlockPosition anchor = materialPreview.anchor().orElseThrow();
        var camera = renderer.getCamera().position();
        var world = player.getWorld();
        float scale = 129f / 128f;
        for (var data : snapshot.blockData()) {
            if (data.blockState() == null) {
                continue;
            }
            var previewState = data.blockState().isAir()
                    ? ProceduralPreviewMarkers.cutout(renderConfig)
                    : data.blockState();
            var position = anchor.add(data.blockPosition());
            renderer.pushPose();
            renderer.translate(position.toVector3d().sub(camera));
            renderer.translate(
                    (scale - 1) / -2f,
                    (scale - 1) / -2f,
                    (scale - 1) / -2f
            );
            renderer.scale(scale, scale, scale);
            renderer.renderBlockState(
                    BlockRenderLayers.block(PREVIEW_COLOR),
                    world,
                    position,
                    previewState
            );
            renderer.popPose();
        }
    }

    private void editAt(RoadPoint target) {
        if (draft.points().size() < 2) {
            draft = draft.append(target);
            invalidatePreview();
            message(
                    draft.points().size() == 1
                            ? "Spline P1 set; choose P2"
                            : "Spline P2 set; click the curve to add control points",
                    ChatFormatting.GREEN
            );
            return;
        }

        if (axisMove != ControlAxis.NONE && draft.selectedIndex() >= 0) {
            var selected = draft.points().get(draft.selectedIndex());
            var moved = switch (axisMove) {
                case X -> selected.withX(target.x());
                case Y -> selected.withY(target.y());
                case Z -> selected.withZ(target.z());
                case NONE -> selected;
            };
            draft = draft.moveSelected(moved);
            axisMove = ControlAxis.NONE;
            moveArmed = false;
            invalidatePreview();
            message("Spline point moved on one axis", ChatFormatting.GREEN);
            return;
        }
        if (moveArmed && draft.selectedIndex() >= 0) {
            draft = draft.moveSelected(target);
            moveArmed = false;
            invalidatePreview();
            message("Spline point moved", ChatFormatting.GREEN);
            return;
        }

        var selectedAxis = gizmoAt(target);
        if (selectedAxis != ControlAxis.NONE) {
            selectAxis(selectedAxis);
            return;
        }

        var nearest = draft.nearestPoint(target, POINT_PICK_RADIUS);
        if (nearest.isPresent()) {
            int index = nearest.getAsInt();
            if (draft.selectedIndex() == index) {
                moveArmed = true;
                message(
                        "Point " + (index + 1)
                                + " ready to move; click its new position",
                        ChatFormatting.GOLD
                );
            } else {
                draft = draft.select(index);
                message(
                        "Selected spline point " + (index + 1)
                                + "; click again to move or use an axis handle",
                        ChatFormatting.AQUA
                );
            }
            return;
        }

        ensurePreview();
        var closest = RoadSpline.closest(preview.samples(), target);
        if (closest.isPresent()
                && closest.get().distance() <= CURVE_PICK_RADIUS) {
            draft = draft.insertAfter(
                    closest.get().segmentIndex(),
                    target
            );
            invalidatePreview();
            message(
                    "Inserted curve point " + (draft.selectedIndex() + 1),
                    ChatFormatting.GREEN
            );
            return;
        }

        draft = draft.append(target);
        invalidatePreview();
        message(
                "Extended spline to point " + draft.points().size(),
                ChatFormatting.GREEN
        );
    }

    private void onAttack() {
        if (axisMove != ControlAxis.NONE || moveArmed) {
            axisMove = ControlAxis.NONE;
            moveArmed = false;
            message("Point move canceled", ChatFormatting.GRAY);
            return;
        }
        if (draft.selectedIndex() >= 0 && draft.points().size() > 2) {
            int deleted = draft.selectedIndex();
            draft = draft.deleteSelected(0, 2);
            invalidatePreview();
            message(
                    "Deleted spline point " + (deleted + 1),
                    ChatFormatting.GRAY
            );
            return;
        }
        draft = draft.clearSelection();
        message(
                "Spline retained; Ctrl + use item on it clears all points",
                ChatFormatting.GRAY
        );
    }

    private void confirm(
            BlockInteraction reference,
            InteractionHand hand
    ) {
        ensurePreview();
        if (!preview.isSuccess()) {
            message(
                    "Spline: " + String.join("; ", preview.errors()),
                    ChatFormatting.RED
            );
            return;
        }
        var library = entrance.getProceduralConfigStorage().get();
        var resolution = library.resolvedActivePreset();
        if (!resolution.isSuccess()) {
            message(
                    "Spline pattern: " + String.join("; ", resolution.errors()),
                    ChatFormatting.RED
            );
            return;
        }
        ProceduralPatternPreset preset = resolution.preset().orElseThrow();
        var player = entrance.getClient().getPlayer();
        var context = entrance.getStructureBuilder().getContext(player);
        var compilation = RoadPatternCompiler.compile(
                player,
                context,
                library,
                preset,
                preview,
                entrance.getConfigStorage().get()
                        .proceduralSafetyConfig()
        );
        if (!compilation.isSuccess()) {
            String detail = compilation.details().isEmpty()
                    ? ""
                    : " (" + compilation.details().get(0) + ")";
            message(
                    "Spline: " + compilation.message() + detail,
                    ChatFormatting.RED
            );
            return;
        }
        BuildResult result = entrance.getStructureBuilder()
                .placeCompiledSnapshot(
                        player,
                        compilation.snapshot().orElseThrow(),
                        compilation.anchor().orElseThrow(),
                        reference
                );
        if (!result.isSuccess()) {
            message("Spline placement was canceled", ChatFormatting.RED);
            return;
        }
        player.swing(hand);
        message(
                "Placed procedural spline: " + compilation.positionCount()
                        + " blocks; draft retained for survival retries",
                ChatFormatting.GREEN
        );
    }

    private void destroy(
            BlockInteraction reference,
            InteractionHand hand
    ) {
        ensurePreview();
        if (!preview.isSuccess()) {
            message(
                    "Spline: " + String.join("; ", preview.errors()),
                    ChatFormatting.RED
            );
            return;
        }
        var player = entrance.getClient().getPlayer();
        var context = entrance.getStructureBuilder().getContext(player);
        var compilation = RoadPatternCompiler.compileDestruction(
                player,
                context,
                preview,
                entrance.getConfigStorage().get()
                        .proceduralSafetyConfig()
        );
        if (!compilation.isSuccess()) {
            String detail = compilation.details().isEmpty()
                    ? ""
                    : " (" + compilation.details().get(0) + ")";
            message(
                    "Spline clearing: " + compilation.message() + detail,
                    ChatFormatting.RED
            );
            return;
        }
        BuildResult result = entrance.getStructureBuilder()
                .applyCompiledSnapshot(
                        player,
                        compilation.snapshot().orElseThrow(),
                        compilation.anchor().orElseThrow(),
                        reference,
                        "Spline clearing"
                );
        if (!result.isSuccess()) {
            message("Spline clearing was canceled", ChatFormatting.RED);
            return;
        }
        player.swing(hand);
        message(
                "Cleared " + compilation.positionCount()
                        + " spline cells; draft retained",
                ChatFormatting.GREEN
        );
    }

    private void invalidatePreview() {
        cancelMaterialPreview();
        preview = RoadVoxelizer.Result.failure(java.util.List.of("Pending"));
        materialPreview = RoadPatternCompiler.CompilationResult.failure(
                "Pending",
                java.util.List.of()
        );
        materialPreviewPreset = null;
        materialPreviewSafety = null;
        materialPreviewDirty = true;
        tooltipRefresh = 20;
    }

    private void ensurePreview() {
        if (draft.points().size() < 2) {
            return;
        }
        if (!preview.isSuccess()) {
            preview = RoadVoxelizer.voxelize(
                    draft.points(),
                    profile,
                    entrance.getConfigStorage().get()
                            .proceduralSafetyConfig()
                            .maxCompiledPositions()
            );
        }
    }

    private void refreshMaterialPreview() {
        ensurePreview();
        if (!preview.isSuccess()) {
            return;
        }
        var resolution = entrance.getProceduralConfigStorage().get()
                .resolvedActivePreset();
        if (!resolution.isSuccess()) {
            materialPreview = RoadPatternCompiler.CompilationResult.failure(
                    "The active spline pattern is invalid",
                    resolution.errors()
            );
            materialPreviewPreset = null;
            materialPreviewDirty = false;
            return;
        }
        var preset = resolution.preset().orElseThrow();
        var safety = entrance.getConfigStorage().get()
                .proceduralSafetyConfig();
        if (materialPreviewFuture != null) {
            if (!Objects.equals(materialPreviewPreset, preset)
                    || !Objects.equals(materialPreviewSafety, safety)) {
                cancelMaterialPreview();
                materialPreviewDirty = true;
            } else if (!materialPreviewFuture.isDone()) {
                return;
            } else {
                try {
                    materialPreview = materialPreviewFuture.get();
                    if (safety.showPreparationMessages()
                            && materialPreview.isSuccess()) {
                        message(
                                "Spline preview ready",
                                ChatFormatting.GREEN
                        );
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    materialPreview =
                            RoadPatternCompiler.CompilationResult.failure(
                                    "Spline preview was interrupted",
                                    java.util.List.of()
                            );
                } catch (ExecutionException exception) {
                    var cause = exception.getCause() == null
                            ? exception
                            : exception.getCause();
                    materialPreview =
                            RoadPatternCompiler.CompilationResult.failure(
                                    "Spline preview worker failed",
                                    java.util.List.of(
                                            cause.getClass().getSimpleName()
                                                    + ": "
                                                    + cause.getMessage()
                                    )
                            );
                }
                materialPreviewFuture = null;
                materialPreviewDirty = false;
                tooltipRefresh = 20;
                return;
            }
        }
        if (!materialPreviewDirty
                && Objects.equals(materialPreviewPreset, preset)
                && Objects.equals(materialPreviewSafety, safety)) {
            return;
        }
        var player = entrance.getClient().getPlayer();
        if (!preset.inspectExistingWorld()
                && !profile.materialLinks().hasLinks()
                && preview.cells().size()
                        >= safety.asyncPreviewPositionThreshold()) {
            var workerPreset = ProceduralContextCompiler
                    .resolvePreviewMaterials(player, preset)
                    .withMaterialSource(
                            PatternMaterialSource.CUSTOM_PALETTE
                    );
            var context = entrance.getStructureBuilder()
                    .getContext(player);
            var road = preview;
            materialPreview = RoadPatternCompiler.CompilationResult.failure(
                    "Preparing spline preview",
                    java.util.List.of()
            );
            materialPreviewPreset = preset;
            materialPreviewSafety = safety;
            materialPreviewDirty = false;
            materialPreviewFuture = materialPreviewExecutor.submit(() ->
                    RoadPatternCompiler.compilePreview(
                            player,
                            context,
                            workerPreset,
                            road,
                            safety
                    )
            );
            if (safety.showPreparationMessages()) {
                message(
                        "Preparing spline preview for "
                                + preview.cells().size() + " blocks...",
                        ChatFormatting.GRAY
                );
            }
            return;
        }
        materialPreview = RoadPatternCompiler.compilePreview(
                player,
                entrance.getStructureBuilder().getContext(player),
                entrance.getProceduralConfigStorage().get(),
                preset,
                preview,
                entrance.getConfigStorage().get()
                        .proceduralSafetyConfig()
        );
        materialPreviewPreset = preset;
        materialPreviewSafety = safety;
        materialPreviewDirty = false;
        tooltipRefresh = 20;
    }

    private void cancelMaterialPreview() {
        if (materialPreviewFuture != null) {
            materialPreviewFuture.cancel(true);
            materialPreviewFuture = null;
        }
    }

    private void refreshRoadTooltip() {
        if (!materialPreview.isSuccess()) {
            hideRoadTooltip();
            return;
        }
        var counts = new LinkedHashMap<Item, Integer>();
        for (var data : materialPreview.snapshot().orElseThrow().blockData()) {
            if (data.blockState() == null || data.blockState().isAir()) {
                continue;
            }
            counts.merge(data.blockState().getItem(), 1, Integer::sum);
        }
        var stacks = new ArrayList<ItemStack>(counts.size());
        counts.forEach((item, count) ->
                stacks.add(item.getDefaultStack().withCount(count))
        );
        var pairs = java.util.List.of(
                new Tuple2<>(
                        Text.text("Spline cells").withStyle(
                                ChatFormatting.WHITE
                        ),
                        Text.text(String.valueOf(
                                materialPreview.positionCount()
                        )).withStyle(ChatFormatting.GOLD)
                ),
                new Tuple2<>(
                        Text.text("Preview").withStyle(ChatFormatting.WHITE),
                        Text.text(
                                distanceToRoad(playerPosition(
                                        entrance.getClient().getPlayer()))
                                        <= entrance.getConfigStorage().get()
                                                .proceduralSafetyConfig()
                                                .roadPreviewDistance()
                                        ? "Visible"
                                        : "Distance hidden"
                        ).withStyle(ChatFormatting.GOLD)
                )
        );
        var entries = new ArrayList<Object>();
        entries.add(new Tuple2<>(
                stacks,
                ChatFormatting.WHITE.getColor()
        ));
        entries.add(Text.text("Procedural spline").withStyle(
                ChatFormatting.GOLD
        ));
        entries.add(pairs);
        entrance.getClientManager().getTooltipRenderer().showGroupEntry(
                ROAD_TOOLTIP,
                ROAD_TOOLTIP_PRIORITY,
                entries,
                false
        );
    }

    private void hideRoadTooltip() {
        entrance.getClientManager().getTooltipRenderer().hideEntry(
                ROAD_TOOLTIP,
                ROAD_TOOLTIP_PRIORITY,
                false
        );
    }

    private double distanceToRoad(RoadPoint point) {
        ensurePreview();
        if (!preview.isSuccess() || preview.samples().isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        return distanceToPath(point, previewSegments());
    }

    private void renderOutlines() {
        var outline = entrance.getClientManager().getOutlineRenderer();
        var next = new HashSet<Object>();
        ensurePreview();
        if (preview.isSuccess()) {
            for (int index = 0; index < preview.samples().size() - 1; index++) {
                Object id = "effortless:road/curve/" + index;
                next.add(id);
                outline.showLine(
                        id,
                        vector(preview.samples().get(index).point()),
                        vector(preview.samples().get(index + 1).point())
                ).colored(183, 164, 112, 210).stroke(0.055f);
            }
            int renderLimit = entrance.getConfigStorage().get()
                    .renderConfig().maxRenderVolume();
            if (preview.cells().size() <= renderLimit) {
                next.add(CELL_OUTLINE);
                outline.showCluster(
                        CELL_OUTLINE,
                        preview.cells().stream()
                                .map(RoadCell::position)
                                .map(position -> new BlockPosition(
                                        position.x(),
                                        position.y(),
                                        position.z()
                                ))
                                .toList()
                ).colored(117, 157, 161, 110).stroke(0.025f);
            }
        }

        for (int index = 0; index < draft.points().size(); index++) {
            Object id = "effortless:road/point/" + index;
            next.add(id);
            int color = index == draft.selectedIndex()
                    ? 0xFFE0B85C
                    : 0xFF79A6B2;
            outline.showBoundingBox(
                    id,
                    pointBox(draft.points().get(index))
            ).colored(color).stroke(index == draft.selectedIndex()
                    ? 0.075f
                    : 0.045f);
        }
        if (draft.selectedIndex() >= 0) {
            renderGizmo(next);
        }
        for (var id : visibleOutlineIds) {
            if (!next.contains(id)) {
                outline.remove(id);
            }
        }
        visibleOutlineIds.clear();
        visibleOutlineIds.addAll(next);
    }

    private void renderGizmo(Set<Object> next) {
        var outline = entrance.getClientManager().getOutlineRenderer();
        var center = draft.points().get(draft.selectedIndex());
        for (var axis : ControlAxis.spatial()) {
            var end = axis.offset(center, GIZMO_LENGTH);
            Object lineId = "effortless:road/gizmo/" + axis + "/line";
            Object handleId = "effortless:road/gizmo/" + axis + "/handle";
            next.add(lineId);
            next.add(handleId);
            outline.showLine(lineId, vector(center), vector(end))
                    .colored(axis.red(), axis.green(), axis.blue(), 150)
                    .stroke(0.065f);
            outline.showBoundingBox(handleId, pointBox(end))
                    .colored(axis.red(), axis.green(), axis.blue(), 135)
                    .stroke(0.055f);
        }
    }

    private ControlAxis gizmoAt(RoadPoint target) {
        if (draft.selectedIndex() < 0) {
            return ControlAxis.NONE;
        }
        return nearestAxis(
                target,
                draft.points().get(draft.selectedIndex()),
                GIZMO_LENGTH,
                GIZMO_PICK_RADIUS
        );
    }

    private ControlAxis gizmoFromView() {
        if (draft.selectedIndex() < 0) {
            return ControlAxis.NONE;
        }
        return axisFromView(
                entrance.getClient().getPlayer(),
                draft.points().get(draft.selectedIndex()),
                GIZMO_LENGTH,
                GIZMO_RAY_REACH,
                GIZMO_RAY_START_OFFSET,
                GIZMO_RAY_PICK_RADIUS
        );
    }

    private boolean roadFromView() {
        ensurePreview();
        if (!preview.isSuccess() || preview.samples().isEmpty()) {
            return false;
        }
        return pathFromView(
                entrance.getClient().getPlayer(),
                previewSegments(),
                ROAD_RAY_REACH,
                ROAD_RAY_PICK_RADIUS
        );
    }

    private java.util.List<Segment> previewSegments() {
        if (preview.samples().size() == 1) {
            var point = preview.samples().getFirst().point();
            return java.util.List.of(new Segment(point, point));
        }
        return java.util.stream.IntStream.range(
                0, preview.samples().size() - 1
        ).mapToObj(index -> new Segment(
                preview.samples().get(index).point(),
                preview.samples().get(index + 1).point()
        )).toList();
    }

    private boolean isRoadHit(BlockInteraction interaction) {
        if (interaction == null) {
            return false;
        }
        ensurePreview();
        if (!preview.isSuccess()) {
            return false;
        }
        var clicked = interaction.getBlockPosition();
        var adjacent = clicked.relative(interaction.getDirection());
        return preview.cells().stream().anyMatch(cell -> {
            var position = cell.position();
            return matches(position, clicked) || matches(position, adjacent);
        });
    }

    private void clearRoad() {
        clearDraftState();
        clearOutlines();
        message(
                "Spline draft cleared; choose P1",
                ChatFormatting.GRAY
        );
    }

    private void clearDraftState() {
        cancelMaterialPreview();
        draft = ControlPointDraft.EMPTY;
        preview = RoadVoxelizer.Result.failure(java.util.List.of("No spline"));
        materialPreview = RoadPatternCompiler.CompilationResult.failure(
                "No spline",
                java.util.List.of()
        );
        materialPreviewPreset = null;
        materialPreviewSafety = null;
        materialPreviewDirty = true;
        moveArmed = false;
        axisMove = ControlAxis.NONE;
        hideRoadTooltip();
    }

    private void selectAxis(ControlAxis selectedAxis) {
        axisMove = selectedAxis;
        moveArmed = false;
        message(
                "Axis " + selectedAxis.name()
                        + " selected; click the new coordinate",
                selectedAxis.textColor()
        );
    }

    private void clearOutlines() {
        var outline = entrance.getClientManager().getOutlineRenderer();
        for (var id : visibleOutlineIds) {
            outline.remove(id);
        }
        outline.remove(CELL_OUTLINE);
        visibleOutlineIds.clear();
    }

    private void message(String value, ChatFormatting formatting) {
        var player = entrance.getClient().getPlayer();
        if (player != null) {
            player.sendMessage(Effortless.getSystemMessage(
                    Text.text(value).withStyle(formatting)
            ));
        }
    }

}
