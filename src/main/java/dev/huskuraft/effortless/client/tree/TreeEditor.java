package dev.huskuraft.effortless.client.tree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
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
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralContextCompiler;
import dev.huskuraft.effortless.client.pattern.procedural.config.PatternMaterialSource;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.road.RoadInteractionMath;
import dev.huskuraft.effortless.client.road.RoadPatternCompiler;
import dev.huskuraft.effortless.client.road.RoadPoint;
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
import dev.huskuraft.universal.api.math.BoundingBox3d;
import dev.huskuraft.universal.api.math.Vector3d;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/**
 * Persistent client-only tree skeleton editor. Tree geometry and procedural
 * materials are fully resolved to an ordinary stock clipboard snapshot.
 */
public final class TreeEditor {

    private static final double POINT_PICK_RADIUS = 1.35;
    private static final double GIZMO_LENGTH = 2.0;
    private static final double GIZMO_RAY_PICK_RADIUS = 0.32;
    private static final double GIZMO_RAY_REACH = 8.0;
    private static final double GIZMO_RAY_START_OFFSET = 0.35;
    private static final double TREE_RAY_PICK_RADIUS = 0.7;
    private static final double TREE_RAY_REACH = 20.0;
    private static final Object CELL_OUTLINE = "effortless:tree/cells";
    private static final Object TREE_TOOLTIP = "effortless:tree/tooltip";
    private static final int TREE_TOOLTIP_PRIORITY = 513;
    private static final int PREVIEW_COLOR = 0x72FFFFFF;

    private final EffortlessClient entrance;
    private final Set<Object> visibleOutlineIds = new HashSet<>();
    private final ExecutorService materialPreviewExecutor;

    private TreeDraft draft = TreeDraft.EMPTY;
    private TreeGenerationConfig generation = TreeGenerationConfig.DEFAULT;
    private TreeProfile profile = TreeProfile.DEFAULT;
    private int visibleVariant;
    private TreeVoxelizer.Result preview =
            TreeVoxelizer.Result.failure(java.util.List.of("No tree"));
    private RoadPatternCompiler.CompilationResult materialPreview =
            RoadPatternCompiler.CompilationResult.failure(
                    "Pending",
                    java.util.List.of()
            );
    private ProceduralPatternPreset materialPreviewPreset;
    private Map<TreeCell.Role, ProceduralPatternPreset>
            materialPreviewRecipes = Map.of();
    private ProceduralSafetyConfig materialPreviewSafety;
    private Future<RoadPatternCompiler.CompilationResult>
            materialPreviewFuture;
    private boolean materialPreviewDirty = true;
    private boolean active;
    private boolean moveArmed;
    private AxisMove axisMove = AxisMove.NONE;
    private ResourceKey<World> draftDimension;
    private int tooltipRefresh;

    public TreeEditor(EffortlessClient entrance) {
        this.entrance = entrance;
        materialPreviewExecutor = Executors.newSingleThreadExecutor(task -> {
            var thread = new Thread(task, "Effortless tree preview");
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean isActive() {
        return active;
    }

    public TreeDraft draft() {
        return draft;
    }

    public TreeProfile profile() {
        return profile;
    }

    public TreeGenerationConfig generation() {
        return generation;
    }

    public int visibleVariant() {
        return visibleVariant;
    }

    public TreeVoxelizer.Result preview() {
        return preview;
    }

    public void start(TreeProfile treeProfile) {
        start(TreeGenerationConfig.legacySkeleton(treeProfile));
    }

    public void start(TreeGenerationConfig treeGeneration) {
        clearOutlines();
        var dimension = entrance.getClient().getPlayer()
                .getWorld().getDimensionId();
        if (draftDimension != null
                && !Objects.equals(draftDimension, dimension)) {
            clearDraftState();
        }
        draftDimension = dimension;
        generation = treeGeneration == null
                ? TreeGenerationConfig.DEFAULT
                : treeGeneration;
        profile = generation.skeletonProfile();
        visibleVariant = generation.variant();
        invalidatePreview();
        moveArmed = false;
        axisMove = AxisMove.NONE;
        active = true;
        message(
                draft.points().isEmpty()
                        ? generation.archetype().name()
                                + " tree: set base, crown, then optional "
                                + "branch guides; Shift + use item places"
                        : generation.archetype().name()
                                + " tree restored: "
                                + Math.max(0, draft.points().size() - 2)
                                + " branch guides; Shift + use item places",
                ChatFormatting.GOLD
        );
    }

    public boolean startFromActivePattern() {
        return startFromActivePattern(null);
    }

    public boolean startFromActivePattern(TreeArchetype archetype) {
        var resolved = entrance.getProceduralConfigStorage().get()
                .resolvedActivePreset();
        if (!resolved.isSuccess()) {
            message(
                    "Cannot start tree editor: "
                            + String.join("; ", resolved.errors()),
                    ChatFormatting.RED
            );
            return false;
        }
        var configured = resolved.preset().orElseThrow()
                .advanced().treeGeneration();
        start(archetype == null
                ? configured
                : configured.withArchetype(archetype));
        return true;
    }

    public void cancel() {
        if (!active) {
            return;
        }
        active = false;
        moveArmed = false;
        axisMove = AxisMove.NONE;
        message("Tree editor deselected; draft retained", ChatFormatting.GRAY);
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
            if (isTreeHit(blockInteraction) || treeFromView()) {
                clearTree();
                entrance.getClient().getPlayer().swing(hand);
            } else {
                message(
                        "Aim at the tree before pressing Ctrl + use item",
                        ChatFormatting.RED
                );
            }
            return EventResult.interruptTrue();
        }
        if (type == InteractionType.USE_ITEM && !shift && !control) {
            var viewAxis = gizmoFromView();
            if (viewAxis != AxisMove.NONE) {
                selectAxis(viewAxis);
                entrance.getClient().getPlayer().swing(hand);
                return EventResult.interruptTrue();
            }
        }
        if (blockInteraction == null) {
            message("Aim at a block to edit the tree", ChatFormatting.RED);
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
        editAt(targetPoint(blockInteraction));
        entrance.getClient().getPlayer().swing(hand);
        return EventResult.interruptTrue();
    }

    public void tick() {
        var player = entrance.getClient().getPlayer();
        if (player == null) {
            return;
        }
        if (!hasTree()
                || draftDimension == null
                || !Objects.equals(
                        draftDimension,
                        player.getWorld().getDimensionId()
                )) {
            clearOutlines();
            hideTooltip();
            return;
        }
        renderOutlines();
        refreshMaterialPreview();
        if (++tooltipRefresh >= 20) {
            tooltipRefresh = 0;
            refreshTooltip();
        }
    }

    public void render(Renderer renderer, float deltaTick) {
        var player = entrance.getClient().getPlayer();
        if (player == null
                || !hasTree()
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
                || distanceToTree(playerPosition())
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
            if (data.blockState() == null || data.blockState().isAir()) {
                continue;
            }
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
                    data.blockState()
            );
            renderer.popPose();
        }
    }

    private boolean hasTree() {
        return !draft.points().isEmpty();
    }

    private void editAt(RoadPoint target) {
        if (draft.points().size() < 2) {
            draft = draft.append(target);
            invalidatePreview();
            message(
                    draft.points().size() == 1
                            ? "Tree base set; choose the crown"
                            : "Tree crown set; add branch endpoints",
                    ChatFormatting.GREEN
            );
            return;
        }
        if (axisMove != AxisMove.NONE && draft.selectedIndex() >= 0) {
            var selected = draft.points().get(draft.selectedIndex());
            draft = draft.moveSelected(switch (axisMove) {
                case X -> selected.withX(target.x());
                case Y -> selected.withY(target.y());
                case Z -> selected.withZ(target.z());
                case NONE -> selected;
            });
            axisMove = AxisMove.NONE;
            moveArmed = false;
            invalidatePreview();
            message("Tree point moved on one axis", ChatFormatting.GREEN);
            return;
        }
        if (moveArmed && draft.selectedIndex() >= 0) {
            draft = draft.moveSelected(target);
            moveArmed = false;
            invalidatePreview();
            message("Tree point moved", ChatFormatting.GREEN);
            return;
        }
        var nearest = draft.nearestPoint(target, POINT_PICK_RADIUS);
        if (nearest.isPresent()) {
            int index = nearest.getAsInt();
            if (draft.selectedIndex() == index) {
                moveArmed = true;
                message(
                        pointName(index)
                                + " ready to move; click its new position",
                        ChatFormatting.GOLD
                );
            } else {
                draft = draft.select(index);
                message(
                        "Selected " + pointName(index)
                                + "; click again to move or use an axis handle",
                        ChatFormatting.AQUA
                );
            }
            return;
        }
        draft = draft.append(target);
        invalidatePreview();
        message(
                "Added branch " + (draft.points().size() - 2),
                ChatFormatting.GREEN
        );
    }

    private void onAttack() {
        if (axisMove != AxisMove.NONE || moveArmed) {
            axisMove = AxisMove.NONE;
            moveArmed = false;
            message("Point move canceled", ChatFormatting.GRAY);
            return;
        }
        if (draft.selectedIndex() >= 2) {
            int branch = draft.selectedIndex() - 1;
            draft = draft.deleteSelected();
            invalidatePreview();
            message("Deleted branch " + branch, ChatFormatting.GRAY);
            return;
        }
        draft = draft.clearSelection();
        message(
                "Tree retained; Ctrl + use item on it clears the draft",
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
                    "Tree: " + String.join("; ", preview.errors()),
                    ChatFormatting.RED
            );
            return;
        }
        var resolution = entrance.getProceduralConfigStorage().get()
                .resolvedActivePreset();
        if (!resolution.isSuccess()) {
            message(
                    "Tree pattern: " + String.join("; ", resolution.errors()),
                    ChatFormatting.RED
            );
            return;
        }
        var player = entrance.getClient().getPlayer();
        var materialResolution = TreeMaterialResolver.resolve(
                entrance.getProceduralConfigStorage().get(),
                resolution.preset().orElseThrow()
        );
        if (!materialResolution.isSuccess()) {
            message(
                    "Tree materials: "
                            + String.join("; ", materialResolution.errors()),
                    ChatFormatting.RED
            );
            return;
        }
        var compilation = TreePatternCompiler.compile(
                player,
                entrance.getStructureBuilder().getContext(player),
                resolution.preset().orElseThrow(),
                materialResolution.recipes(),
                preview,
                safety()
        );
        if (!compilation.isSuccess()) {
            compilationError("Tree", compilation);
            return;
        }
        BuildResult result = entrance.getStructureBuilder()
                .applyCompiledSnapshot(
                        player,
                        compilation.snapshot().orElseThrow(),
                        compilation.anchor().orElseThrow(),
                        reference,
                        "Tree placement"
                );
        if (!result.isSuccess()) {
            message("Tree placement was canceled", ChatFormatting.RED);
            return;
        }
        player.swing(hand);
        if (result == BuildResult.COMPLETED
                && generation.variationSource()
                        == TreeVariationSource.PLACEMENT_SEQUENCE) {
            visibleVariant = visibleVariant == Integer.MAX_VALUE
                    ? 0
                    : visibleVariant + 1;
            persistCompletedVariant();
            invalidatePreview();
        }
        message(
                (result == BuildResult.PARTIAL
                        ? "Partially placed procedural tree: "
                        : "Placed procedural tree: ")
                        + compilation.positionCount()
                        + " blocks; "
                        + (result == BuildResult.PARTIAL
                                ? "same variant retained for survival retry"
                                : "preview retained"),
                ChatFormatting.GREEN
        );
    }

    /**
     * A completed placement is the only event that advances and persists a
     * placement-sequence variant. Partial survival placements therefore keep
     * the exact same geometry after deselecting or restarting the client.
     */
    private void persistCompletedVariant() {
        generation = generation.withVariant(visibleVariant);
        var storage = entrance.getProceduralConfigStorage();
        var library = storage.get();
        var active = library.activePreset();
        if (active.isEmpty()) {
            return;
        }
        var preset = active.orElseThrow();
        storage.set(library.put(preset.withAdvanced(
                preset.advanced().withTreeGeneration(
                        preset.advanced().treeGeneration()
                                .withVariant(visibleVariant)
                )
        )));
    }

    private void destroy(
            BlockInteraction reference,
            InteractionHand hand
    ) {
        ensurePreview();
        if (!preview.isSuccess()) {
            message(
                    "Tree: " + String.join("; ", preview.errors()),
                    ChatFormatting.RED
            );
            return;
        }
        var player = entrance.getClient().getPlayer();
        var compilation = TreePatternCompiler.compileDestruction(
                player,
                entrance.getStructureBuilder().getContext(player),
                preview,
                safety()
        );
        if (!compilation.isSuccess()) {
            compilationError("Tree clearing", compilation);
            return;
        }
        BuildResult result = entrance.getStructureBuilder()
                .applyCompiledSnapshot(
                        player,
                        compilation.snapshot().orElseThrow(),
                        compilation.anchor().orElseThrow(),
                        reference,
                        "Tree clearing"
                );
        if (!result.isSuccess()) {
            message("Tree clearing was canceled", ChatFormatting.RED);
            return;
        }
        player.swing(hand);
        message(
                "Cleared " + compilation.positionCount()
                        + " tree cells; draft retained",
                ChatFormatting.GREEN
        );
    }

    private void compilationError(
            String prefix,
            RoadPatternCompiler.CompilationResult result
    ) {
        String detail = result.details().isEmpty()
                ? ""
                : " (" + result.details().get(0) + ")";
        message(
                prefix + ": " + result.message() + detail,
                ChatFormatting.RED
        );
    }

    private void invalidatePreview() {
        cancelMaterialPreview();
        preview = TreeVoxelizer.Result.failure(java.util.List.of("Pending"));
        materialPreview = RoadPatternCompiler.CompilationResult.failure(
                "Pending",
                java.util.List.of()
        );
        materialPreviewPreset = null;
        materialPreviewRecipes = Map.of();
        materialPreviewSafety = null;
        materialPreviewDirty = true;
        tooltipRefresh = 20;
    }

    private void ensurePreview() {
        if (preview.isSuccess()) {
            return;
        }
        if (draft.points().size() < 2) {
            return;
        }
        var resolution = entrance.getProceduralConfigStorage().get()
                .resolvedActivePreset();
        if (!resolution.isSuccess()) {
            preview = TreeVoxelizer.Result.failure(resolution.errors());
            return;
        }
        preview = TreeVoxelizer.generateGuided(
                draft.points(),
                generation,
                resolution.preset().orElseThrow().seed(),
                visibleVariant,
                safety().maxCompiledPositions()
        );
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
                    "The active tree pattern is invalid",
                    resolution.errors()
            );
            materialPreviewPreset = null;
            materialPreviewDirty = false;
            return;
        }
        var preset = resolution.preset().orElseThrow();
        var roleResolution = TreeMaterialResolver.resolve(
                entrance.getProceduralConfigStorage().get(),
                preset
        );
        if (!roleResolution.isSuccess()) {
            materialPreview = RoadPatternCompiler.CompilationResult.failure(
                    "The tree component recipes are invalid",
                    roleResolution.errors()
            );
            materialPreviewPreset = null;
            materialPreviewRecipes = Map.of();
            materialPreviewDirty = false;
            return;
        }
        var recipes = roleResolution.recipes();
        var safety = safety();
        if (materialPreviewFuture != null) {
            if (!Objects.equals(materialPreviewPreset, preset)
                    || !Objects.equals(materialPreviewRecipes, recipes)
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
                        message("Tree preview ready", ChatFormatting.GREEN);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    materialPreview =
                            RoadPatternCompiler.CompilationResult.failure(
                                    "Tree preview was interrupted",
                                    java.util.List.of()
                            );
                } catch (ExecutionException exception) {
                    var cause = exception.getCause() == null
                            ? exception
                            : exception.getCause();
                    materialPreview =
                            RoadPatternCompiler.CompilationResult.failure(
                                    "Tree preview worker failed",
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
                && Objects.equals(materialPreviewRecipes, recipes)
                && Objects.equals(materialPreviewSafety, safety)) {
            return;
        }
        var player = entrance.getClient().getPlayer();
        if (recipes.values().stream()
                        .noneMatch(ProceduralPatternPreset::inspectExistingWorld)
                && preview.cells().size()
                        >= safety.asyncPreviewPositionThreshold()) {
            var workerRecipes = new java.util.EnumMap<
                    TreeCell.Role,
                    ProceduralPatternPreset
            >(TreeCell.Role.class);
            recipes.forEach((role, recipe) -> workerRecipes.put(
                    role,
                    ProceduralContextCompiler.resolvePreviewMaterials(
                            player,
                            recipe
                    ).withMaterialSource(PatternMaterialSource.CUSTOM_PALETTE)
            ));
            var workerPreset = workerRecipes.getOrDefault(
                    TreeCell.Role.TRUNK,
                    ProceduralContextCompiler.resolvePreviewMaterials(
                            player,
                            preset
                    ).withMaterialSource(PatternMaterialSource.CUSTOM_PALETTE)
            );
            var context = entrance.getStructureBuilder().getContext(player);
            var tree = preview;
            materialPreview = RoadPatternCompiler.CompilationResult.failure(
                    "Preparing tree preview",
                    java.util.List.of()
            );
            materialPreviewPreset = preset;
            materialPreviewRecipes = Map.copyOf(recipes);
            materialPreviewSafety = safety;
            materialPreviewDirty = false;
            materialPreviewFuture = materialPreviewExecutor.submit(() ->
                    TreePatternCompiler.compilePreview(
                            player,
                            context,
                            workerPreset,
                            workerRecipes,
                            tree,
                            safety
                    )
            );
            if (safety.showPreparationMessages()) {
                message(
                        "Preparing tree preview for "
                                + preview.cells().size() + " blocks...",
                        ChatFormatting.GRAY
                );
            }
            return;
        }
        materialPreview = TreePatternCompiler.compilePreview(
                player,
                entrance.getStructureBuilder().getContext(player),
                preset,
                recipes,
                preview,
                safety
        );
        materialPreviewPreset = preset;
        materialPreviewRecipes = Map.copyOf(recipes);
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

    private void refreshTooltip() {
        if (!materialPreview.isSuccess()) {
            hideTooltip();
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
                        Text.text("Tree cells").withStyle(
                                ChatFormatting.WHITE
                        ),
                        Text.text(String.valueOf(
                                materialPreview.positionCount()
                        )).withStyle(ChatFormatting.GOLD)
                ),
                new Tuple2<>(
                        Text.text("Guides").withStyle(ChatFormatting.WHITE),
                        Text.text(String.valueOf(Math.max(
                                0,
                                draft.points().size() - 2
                        ))).withStyle(ChatFormatting.GOLD)
                ),
                new Tuple2<>(
                        Text.text("Preview").withStyle(ChatFormatting.WHITE),
                        Text.text(
                                distanceToTree(playerPosition())
                                        <= safety().roadPreviewDistance()
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
        entries.add(Text.text("Procedural tree").withStyle(
                ChatFormatting.GOLD
        ));
        entries.add(pairs);
        entrance.getClientManager().getTooltipRenderer().showGroupEntry(
                TREE_TOOLTIP,
                TREE_TOOLTIP_PRIORITY,
                entries,
                false
        );
    }

    private void hideTooltip() {
        entrance.getClientManager().getTooltipRenderer().hideEntry(
                TREE_TOOLTIP,
                TREE_TOOLTIP_PRIORITY,
                false
        );
    }

    private void renderOutlines() {
        var outline = entrance.getClientManager().getOutlineRenderer();
        var next = new HashSet<Object>();
        ensurePreview();
        if (preview.isSuccess()) {
            for (int index = 0; index < preview.limbs().size(); index++) {
                var limb = preview.limbs().get(index);
                Object id = "effortless:tree/limb/" + index;
                next.add(id);
                int[] color = limb.role() == TreeCell.Role.CANOPY
                        ? new int[]{104, 158, 92}
                        : limb.role() == TreeCell.Role.ROOT
                                ? new int[]{126, 92, 64}
                                : new int[]{156, 116, 78};
                outline.showLine(
                        id,
                        vector(limb.start()),
                        vector(limb.end())
                ).colored(color[0], color[1], color[2], 220)
                        .stroke(0.06f);
            }
            if (preview.cells().size() <= entrance.getConfigStorage().get()
                    .renderConfig().maxRenderVolume()) {
                next.add(CELL_OUTLINE);
                outline.showCluster(
                        CELL_OUTLINE,
                        preview.cells().stream()
                                .map(TreeCell::position)
                                .map(position -> new BlockPosition(
                                        position.x(),
                                        position.y(),
                                        position.z()
                                ))
                                .toList()
                ).colored(104, 150, 99, 100).stroke(0.025f);
            }
        }
        for (int index = 0; index < draft.points().size(); index++) {
                Object id = "effortless:tree/point/" + index;
                next.add(id);
                int color = index == draft.selectedIndex()
                        ? 0xFFE0B85C
                        : index == 0
                                ? 0xFF9C744E
                                : index == 1
                                        ? 0xFF78B26A
                                        : 0xFF6AA6A0;
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
        for (var axis : new AxisMove[]{AxisMove.X, AxisMove.Y, AxisMove.Z}) {
            var end = gizmoEnd(center, axis);
            Object lineId = "effortless:tree/gizmo/" + axis + "/line";
            Object handleId = "effortless:tree/gizmo/" + axis + "/handle";
            next.add(lineId);
            next.add(handleId);
            int[] rgb = axisRgb(axis);
            outline.showLine(lineId, vector(center), vector(end))
                    .colored(rgb[0], rgb[1], rgb[2], 150)
                    .stroke(0.065f);
            outline.showBoundingBox(handleId, pointBox(end))
                    .colored(rgb[0], rgb[1], rgb[2], 135)
                    .stroke(0.055f);
        }
    }

    private AxisMove gizmoFromView() {
        if (draft.selectedIndex() < 0) {
            return AxisMove.NONE;
        }
        var player = entrance.getClient().getPlayer();
        var eyeVector = player.getEyePosition();
        var directionVector = player.getEyeDirection();
        var eye = new RoadPoint(
                eyeVector.x(), eyeVector.y(), eyeVector.z()
        );
        var viewEnd = eye.add(new RoadPoint(
                directionVector.x(),
                directionVector.y(),
                directionVector.z()
        ).normalize().mul(GIZMO_RAY_REACH));
        var center = draft.points().get(draft.selectedIndex());
        AxisMove best = AxisMove.NONE;
        double bestDistance = GIZMO_RAY_PICK_RADIUS
                * GIZMO_RAY_PICK_RADIUS;
        for (var axis : new AxisMove[]{
                AxisMove.X, AxisMove.Y, AxisMove.Z
        }) {
            var end = gizmoEnd(center, axis);
            if (eye.distance(end) > GIZMO_RAY_REACH) {
                continue;
            }
            var start = switch (axis) {
                case X -> center.add(GIZMO_RAY_START_OFFSET, 0.0, 0.0);
                case Y -> center.add(0.0, GIZMO_RAY_START_OFFSET, 0.0);
                case Z -> center.add(0.0, 0.0, GIZMO_RAY_START_OFFSET);
                case NONE -> center;
            };
            double distance = RoadInteractionMath.segmentDistanceSquared(
                    eye, viewEnd, start, end
            );
            if (distance <= bestDistance) {
                best = axis;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean treeFromView() {
        ensurePreview();
        if (!preview.isSuccess()) {
            return false;
        }
        var player = entrance.getClient().getPlayer();
        var eyeVector = player.getEyePosition();
        var directionVector = player.getEyeDirection();
        var eye = new RoadPoint(
                eyeVector.x(), eyeVector.y(), eyeVector.z()
        );
        var viewEnd = eye.add(new RoadPoint(
                directionVector.x(),
                directionVector.y(),
                directionVector.z()
        ).normalize().mul(TREE_RAY_REACH));
        double maximumDistance = TREE_RAY_PICK_RADIUS
                * TREE_RAY_PICK_RADIUS;
        return preview.limbs().stream().anyMatch(limb ->
                RoadInteractionMath.segmentDistanceSquared(
                        eye,
                        viewEnd,
                        limb.start(),
                        limb.end()
                ) <= maximumDistance
        );
    }

    private boolean isTreeHit(BlockInteraction interaction) {
        if (interaction == null) {
            return false;
        }
        ensurePreview();
        if (!preview.isSuccess()) {
            return false;
        }
        var clicked = interaction.getBlockPosition();
        var adjacent = clicked.relative(interaction.getDirection());
        return preview.cells().stream().anyMatch(cell ->
                matches(cell.position(), clicked)
                        || matches(cell.position(), adjacent)
        );
    }

    private double distanceToTree(RoadPoint point) {
        ensurePreview();
        if (!preview.isSuccess()) {
            return Double.POSITIVE_INFINITY;
        }
        double minimum = Double.POSITIVE_INFINITY;
        for (var limb : preview.limbs()) {
            minimum = Math.min(
                    minimum,
                    RoadInteractionMath.segmentDistanceSquared(
                            point, point, limb.start(), limb.end()
                    )
            );
        }
        return Math.sqrt(minimum);
    }

    private RoadPoint playerPosition() {
        var position = entrance.getClient().getPlayer().getPosition();
        return new RoadPoint(position.x(), position.y(), position.z());
    }

    private void clearTree() {
        clearDraftState();
        clearOutlines();
        message("Tree draft cleared; choose a base", ChatFormatting.GRAY);
    }

    private void clearDraftState() {
        cancelMaterialPreview();
        draft = TreeDraft.EMPTY;
        preview = TreeVoxelizer.Result.failure(java.util.List.of("No tree"));
        materialPreview = RoadPatternCompiler.CompilationResult.failure(
                "No tree",
                java.util.List.of()
        );
        materialPreviewPreset = null;
        materialPreviewRecipes = Map.of();
        materialPreviewSafety = null;
        materialPreviewDirty = true;
        moveArmed = false;
        axisMove = AxisMove.NONE;
        hideTooltip();
    }

    private void selectAxis(AxisMove selectedAxis) {
        axisMove = selectedAxis;
        moveArmed = false;
        message(
                "Axis " + selectedAxis.name()
                        + " selected; click the new coordinate",
                axisColor(selectedAxis)
        );
    }

    private ProceduralSafetyConfig safety() {
        return entrance.getConfigStorage().get().proceduralSafetyConfig();
    }

    private static String pointName(int index) {
        return switch (index) {
            case 0 -> "tree base";
            case 1 -> "tree crown";
            default -> "branch " + (index - 1);
        };
    }

    private static RoadPoint gizmoEnd(RoadPoint center, AxisMove axis) {
        return switch (axis) {
            case X -> center.add(GIZMO_LENGTH, 0.0, 0.0);
            case Y -> center.add(0.0, GIZMO_LENGTH, 0.0);
            case Z -> center.add(0.0, 0.0, GIZMO_LENGTH);
            case NONE -> center;
        };
    }

    private static int[] axisRgb(AxisMove axis) {
        return switch (axis) {
            case X -> new int[]{205, 92, 92};
            case Y -> new int[]{92, 184, 112};
            case Z -> new int[]{92, 132, 205};
            case NONE -> new int[]{180, 180, 180};
        };
    }

    private static ChatFormatting axisColor(AxisMove axis) {
        return switch (axis) {
            case X -> ChatFormatting.RED;
            case Y -> ChatFormatting.GREEN;
            case Z -> ChatFormatting.BLUE;
            case NONE -> ChatFormatting.GRAY;
        };
    }

    private static RoadPoint targetPoint(BlockInteraction interaction) {
        BlockPosition position = interaction.getBlockPosition()
                .relative(interaction.getDirection());
        var center = position.getCenter();
        return new RoadPoint(center.x(), center.y(), center.z());
    }

    private static BoundingBox3d pointBox(RoadPoint point) {
        var position = new BlockPosition(
                (int) Math.floor(point.x()),
                (int) Math.floor(point.y()),
                (int) Math.floor(point.z())
        );
        return BoundingBox3d.fromLowerCornersOf(position.toVector3i());
    }

    private static Vector3d vector(RoadPoint point) {
        return new Vector3d(point.x(), point.y(), point.z());
    }

    private static boolean matches(
            dev.huskuraft.effortless.client.pattern.procedural.GridPosition
                    first,
            BlockPosition second
    ) {
        return first.x() == second.x()
                && first.y() == second.y()
                && first.z() == second.z();
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

    private enum AxisMove {
        NONE,
        X,
        Y,
        Z
    }
}
