package dev.huskuraft.effortless.screen.pattern.procedural;

import java.awt.Color;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.structure.BuildMode;
import dev.huskuraft.effortless.building.structure.builder.Structure;
import dev.huskuraft.effortless.client.pattern.procedural.CoordinateLookup;
import dev.huskuraft.effortless.client.pattern.procedural.ExistingNeighborLookup;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationProgress;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationRequest;
import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralContextCompiler;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralGenerator;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralMaterial;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralPresetAdapter;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralBlockStateResolver;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;
import dev.huskuraft.effortless.client.pattern.procedural.StableRandom;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.generator.ClientToolSubtype;
import dev.huskuraft.effortless.client.road.RoadCell;
import dev.huskuraft.effortless.client.road.RoadPoint;
import dev.huskuraft.effortless.client.road.RoadProfile;
import dev.huskuraft.effortless.client.road.SplineMaterialResolver;
import dev.huskuraft.effortless.client.road.SplineCutoutGeometry;
import dev.huskuraft.effortless.client.road.RoadVoxelizer;
import dev.huskuraft.effortless.client.road.SplineSubtype;
import dev.huskuraft.effortless.client.tree.TreeArchetype;
import dev.huskuraft.effortless.client.tree.TreeCell;
import dev.huskuraft.effortless.client.tree.TreeBlockStateResolver;
import dev.huskuraft.effortless.client.tree.TreeGenerationConfig;
import dev.huskuraft.effortless.client.tree.TreeMaterialResolver;
import dev.huskuraft.effortless.client.tree.TreeProfile;
import dev.huskuraft.effortless.client.tree.TreeVoxelizer;
import dev.huskuraft.effortless.renderer.opertaion.BlockRenderLayers;
import dev.huskuraft.universal.api.core.BlockInteraction;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.BlockState;
import dev.huskuraft.universal.api.core.Direction;
import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.math.Vector3f;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/**
 * Asynchronous, deterministic workbench preview. It uses the same adapter and
 * generator as placement, but targets a small synthetic instance of any stock
 * build shape. Shape and orientation are preview-local and never change the
 * player's active build mode.
 */
final class ProceduralPreviewWidget extends AbstractWidget {

    private static final int CACHE_LIMIT = 16;
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                var thread = new Thread(
                        runnable,
                        "Effortless procedural workbench preview"
                );
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            });
    private static final Map<PreviewKey, PreviewJob> CACHE =
            new LinkedHashMap<>(16, 0.75f, true);

    private final Supplier<ProceduralPatternPreset> preset;
    private final Supplier<ProceduralPatternLibrary> library;
    private final Supplier<ProceduralPreviewType> type;
    private final Supplier<PreviewOrientation> orientation;
    private final Supplier<Structure> structure;
    private final Supplier<ClientToolSubtype> clientSubtype;
    private PreviewKey currentKey;
    private PreviewJob currentJob;
    private float panX;
    private float panY;
    private float yawDegrees = 42f;
    private float pitchDegrees = -28f;
    private float zoom = 1f;
    private int dragButton = -1;

    ProceduralPreviewWidget(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            Supplier<ProceduralPatternPreset> preset,
            Supplier<ProceduralPatternLibrary> library,
            Supplier<ProceduralPreviewType> type,
            Supplier<PreviewOrientation> orientation,
            Supplier<Structure> structure,
            Supplier<ClientToolSubtype> clientSubtype
    ) {
        super(
                entrance,
                x,
                y,
                width,
                height,
                Text.translate("effortless.procedural.preview.title")
        );
        this.preset = preset;
        this.library = library;
        this.type = type;
        this.orientation = orientation;
        this.structure = structure;
        this.clientSubtype = clientSubtype;
        this.focusable = true;
    }

    @Override
    public void renderWidget(
            Renderer renderer,
            int mouseX,
            int mouseY,
            float deltaTick
    ) {
        ensureJob();
        renderer.renderRect(getX(), getY(), getRight(), getBottom(), 0xE0080A0D);
        renderer.renderRect(getX(), getY(), getRight(), getY() + 1, 0xFF575C63);
        renderer.renderTextFromStart(
                getTypeface(),
                Text.translate("effortless.procedural.preview.title"),
                getX() + 6,
                getY() + 5,
                0xFFE8E8E8,
                true
        );
        String previewLabel = previewLabel();
        boolean resetHovered = containsResetLabel(mouseX, mouseY);
        renderer.renderTextFromEnd(
                getTypeface(),
                Text.text(previewLabel).withStyle(ChatFormatting.GOLD),
                getRight() - 6,
                getY() + 5,
                resetHovered ? 0xFFFFD178 : 0xFFC4A66B,
                true
        );
        if (resetHovered) {
            int labelWidth = getTypeface().measureWidth(previewLabel);
            renderer.renderRect(
                    getRight() - 6 - labelWidth,
                    getY() + 16,
                    getRight() - 6,
                    getY() + 17,
                    0xFFFFD178
            );
        }

        if (currentJob == null) {
            return;
        }
        var model = currentJob.model;
        if (model == null) {
            int total = currentJob.total.get();
            int complete = currentJob.completed.get();
            String progress = total <= 0
                    ? "Preparing..."
                    : "Generating " + complete + " / " + total;
            renderer.renderTextFromCenter(
                    getTypeface(),
                    Text.text(progress).withStyle(ChatFormatting.GRAY),
                    getCenterX(),
                    getCenterY(),
                    0xFFB8B8B8,
                    true
            );
            renderProgress(renderer, complete, total);
            return;
        }
        if (!model.error().isEmpty()) {
            renderer.renderScrollingText(
                    getTypeface(),
                    Text.text(model.error()).withStyle(ChatFormatting.RED),
                    getX() + 8,
                    getCenterY() - 6,
                    getRight() - 8,
                    getCenterY() + 8,
                    0xFFFF7777
            );
            return;
        }
        renderModel(renderer, model);
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && containsResetLabel(mouseX, mouseY)) {
            resetCamera();
            return true;
        }
        if ((button != 0 && button != 1 && button != 2)
                || !containsViewport(mouseX, mouseY)) {
            return false;
        }
        if (button == 2) {
            resetCamera();
            return true;
        }
        dragButton = button;
        return true;
    }

    @Override
    public boolean onMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY
    ) {
        if (dragButton != button) {
            return false;
        }
        if (button == 0) {
            panX += (float) deltaX;
            panY += (float) deltaY;
        } else {
            yawDegrees += (float) deltaX * 0.65f;
            pitchDegrees = clamp(
                    pitchDegrees + (float) deltaY * 0.65f,
                    -85f,
                    85f
            );
        }
        return true;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (dragButton != button) {
            return false;
        }
        dragButton = -1;
        return true;
    }

    @Override
    public boolean onMouseScrolled(
            double mouseX,
            double mouseY,
            double amountX,
            double amountY
    ) {
        if (!containsViewport(mouseX, mouseY)) {
            return false;
        }
        zoom = clamp(zoom * (float) Math.pow(1.12, amountY), 0.35f, 3.5f);
        return true;
    }

    boolean containsViewport(double mouseX, double mouseY) {
        return isVisible()
                && mouseX >= getX()
                && mouseX < getRight()
                && mouseY >= getY() + 18
                && mouseY < getBottom();
    }

    boolean containsInteractionPoint(double mouseX, double mouseY) {
        return containsViewport(mouseX, mouseY)
                || containsResetLabel(mouseX, mouseY);
    }

    boolean isCameraDragging() {
        return dragButton >= 0;
    }

    private boolean containsResetLabel(double mouseX, double mouseY) {
        if (!isVisible() || mouseY < getY() + 2 || mouseY >= getY() + 18) {
            return false;
        }
        int labelWidth = getTypeface().measureWidth(previewLabel());
        return mouseX >= getRight() - 6 - labelWidth
                && mouseX < getRight() - 4;
    }

    private String previewLabel() {
        String kind = PreviewOrientation.kind(type.get());
        if (type.get().isClientOnly()) {
            kind = clientSubtype.get().getNameText().getString();
        }
        return type.get().displayName().getString()
                + " \u00b7 "
                + kind
                + " " + orientation.get().label();
    }

    private void resetCamera() {
        panX = 0f;
        panY = 0f;
        yawDegrees = 42f;
        pitchDegrees = -28f;
        zoom = 1f;
    }

    @Override
    public List<Text> getTooltip() {
        return List.of();
    }

    private void ensureJob() {
        var player = getEntrance().getClient().getPlayer();
        var sourceLibrary = library.get();
        var resolvedPresets = sourceLibrary.presets().stream()
                .map(value -> ProceduralContextCompiler
                        .resolvePreviewMaterials(player, value))
                .toList();
        var previewLibrary = new ProceduralPatternLibrary(
                sourceLibrary.enabled(),
                sourceLibrary.activePresetId(),
                resolvedPresets,
                sourceLibrary.fieldAssets()
        );
        var resolved = resolvedPresets.stream()
                .filter(value -> value.id().equals(preset.get().id()))
                .findFirst()
                .orElseGet(() -> ProceduralContextCompiler
                        .resolvePreviewMaterials(player, preset.get()));
        resolved = applyPreviewSubtype(
                resolved,
                type.get(),
                clientSubtype.get()
        );
        var key = new PreviewKey(
                resolved,
                previewLibrary,
                type.get(),
                orientation.get(),
                structure.get()
        );
        if (key.equals(currentKey)) {
            return;
        }
        currentKey = key;
        currentJob = request(key);
    }

    static ProceduralPatternPreset applyPreviewSubtype(
            ProceduralPatternPreset value,
            ProceduralPreviewType previewType,
            ClientToolSubtype selected
    ) {
        if (previewType.isRoad()
                && selected instanceof SplineSubtype subtype) {
            return value.withAdvanced(value.advanced().withRoadProfile(
                    value.advanced().roadProfile()
                            .withSubtypePreservingGeometry(subtype)
            ));
        }
        if (previewType.isTree()
                && selected instanceof TreeArchetype archetype) {
            return value.withAdvanced(value.advanced().withTreeGeneration(
                    value.advanced().treeGeneration()
                            .withArchetypePreservingGeometry(archetype)
            ));
        }
        return value;
    }

    private static PreviewJob request(PreviewKey key) {
        synchronized (CACHE) {
            var existing = CACHE.get(key);
            if (existing != null) {
                return existing;
            }
            var job = new PreviewJob();
            CACHE.put(key, job);
            while (CACHE.size() > CACHE_LIMIT) {
                var iterator = CACHE.entrySet().iterator();
                var oldest = iterator.next();
                oldest.getValue().cancelled.set(true);
                iterator.remove();
            }
            job.future = CompletableFuture.runAsync(
                    () -> generate(key, job),
                    EXECUTOR
            );
            return job;
        }
    }

    private static void generate(PreviewKey key, PreviewJob job) {
        try {
            var geometry = key.type().isRoad()
                    ? roadGeometry(key.preset().advanced().roadProfile())
                    : key.type().isTree()
                            ? treeGeometry(
                                    key.preset().advanced().treeGeneration(),
                                    key.preset().seed()
                            )
                            : new PreviewGeometry(
                            positions(
                                    key.type(),
                                    key.orientation(),
                                    key.structure()
                            ),
                            CoordinateLookup.NONE,
                            Map.of(),
                            Map.of(),
                            Map.of(),
                            Map.of()
                    );
            var positions = geometry.positions();
            job.total.set(positions.size());
            if (key.type().isTree()) {
                generateTreeMaterials(key, job, geometry);
                return;
            }
            if (key.type().isRoad()) {
                generateSplineMaterials(key, job, geometry);
                return;
            }
            var adaptation = ProceduralPresetAdapter.adapt(key.preset());
            if (!adaptation.isSuccess()) {
                job.model = PreviewModel.failure(
                        String.join("; ", adaptation.errors())
                );
                return;
            }
            var request = new GenerationRequest<>(
                    key.preset().seed(),
                    positions,
                    adaptation.ruleSet().orElseThrow(),
                    ExistingNeighborLookup.NONE,
                    positions.size(),
                    job.cancelled::get,
                    (stage, completed, total) -> {
                        job.completed.set(completed);
                        job.total.set(total);
                    },
                    geometry.coordinates()
            );
            var generated = ProceduralGenerator.generate(request);
            if (!generated.isSuccess()) {
                job.model = PreviewModel.failure(
                        generated.failure().orElseThrow().message()
                );
                return;
            }
            job.model = new PreviewModel(
                    generated.placements(),
                    positions,
                    "",
                    geometry.treeCells(),
                    geometry.roadCells()
            );
        } catch (RuntimeException exception) {
            job.model = PreviewModel.failure(
                    exception.getClass().getSimpleName() + ": "
                            + exception.getMessage()
            );
        }
    }

    private static void generateTreeMaterials(
            PreviewKey key,
            PreviewJob job,
            PreviewGeometry geometry
    ) {
        var roleResolution = TreeMaterialResolver.resolve(
                key.library(),
                key.preset()
        );
        if (!roleResolution.isSuccess()) {
            job.model = PreviewModel.failure(
                    String.join("; ", roleResolution.errors())
            );
            return;
        }
        var placements = new LinkedHashMap<GridPosition, ProceduralMaterial>();
        int completedRoles = 0;
        for (var role : TreeCell.Role.values()) {
            var rolePositions = geometry.positions().stream()
                    .filter(position -> geometry.treeRoles().get(position) == role)
                    .toList();
            if (rolePositions.isEmpty()) {
                continue;
            }
            var rolePreset = roleResolution.recipes().getOrDefault(
                    role,
                    key.preset()
            );
            var adaptation = ProceduralPresetAdapter.adapt(rolePreset);
            if (!adaptation.isSuccess()) {
                job.model = PreviewModel.failure(
                        role.name() + ": "
                                + String.join("; ", adaptation.errors())
                );
                return;
            }
            int roleOffset = completedRoles;
            var request = new GenerationRequest<>(
                    rolePreset.seed(),
                    rolePositions,
                    adaptation.ruleSet().orElseThrow(),
                    ExistingNeighborLookup.NONE,
                    rolePositions.size(),
                    job.cancelled::get,
                    (stage, completed, total) -> job.completed.set(
                            Math.min(
                                    geometry.positions().size(),
                                    roleOffset + completed
                            )
                    ),
                    geometry.coordinates()
            );
            var generated = ProceduralGenerator.generate(request);
            if (!generated.isSuccess()) {
                job.model = PreviewModel.failure(
                        role.name() + ": "
                                + generated.failure().orElseThrow().message()
                );
                return;
            }
            placements.putAll(generated.placements());
            completedRoles += rolePositions.size();
            job.completed.set(completedRoles);
        }
        job.model = new PreviewModel(
                placements,
                geometry.positions(),
                "",
                geometry.treeCells(),
                geometry.roadCells()
        );
    }

    private static void generateSplineMaterials(
            PreviewKey key,
            PreviewJob job,
            PreviewGeometry geometry
    ) {
        var roleResolution = SplineMaterialResolver.resolve(
                key.library(), key.preset()
        );
        if (!roleResolution.isSuccess()) {
            job.model = PreviewModel.failure(
                    String.join("; ", roleResolution.errors())
            );
            return;
        }
        var placements = new LinkedHashMap<GridPosition, ProceduralMaterial>();
        var cells = new LinkedHashMap<>(geometry.roadCells());
        for (var role : List.of(
                RoadCell.Role.SURFACE,
                RoadCell.Role.SHOULDER,
                RoadCell.Role.CURB,
                RoadCell.Role.MARKING,
                RoadCell.Role.FOUNDATION
        )) {
            var rolePositions = cells.entrySet().stream()
                    .filter(entry -> entry.getValue().role() == role)
                    .map(Map.Entry::getKey)
                    .toList();
            var generated = generateSplineRecipe(
                    roleResolution.recipes().getOrDefault(
                            role, key.preset()
                    ),
                    role.name(), role.name(), rolePositions,
                    cells, job
            );
            if (!generated.isSuccess()) {
                job.model = PreviewModel.failure(generated.error());
                return;
            }
            placements.putAll(generated.placements());
        }
        var bands = key.preset().advanced().roadProfile()
                .crossSectionBands();
        for (int index = 0; index < bands.size(); index++) {
            int bandIndex = index;
            var positions = cells.entrySet().stream()
                    .filter(entry -> entry.getValue().role()
                            == RoadCell.Role.BAND)
                    .filter(entry -> entry.getValue().bandIndex()
                            == bandIndex)
                    .map(Map.Entry::getKey)
                    .toList();
            var bandPreset = index < roleResolution.bandRecipes().size()
                    ? roleResolution.bandRecipes().get(index)
                    : key.preset();
            var generated = generateSplineRecipe(
                    bandPreset,
                    "BAND " + (index + 1),
                    "SPLINE_BAND_" + index,
                    positions, cells, job
            );
            if (!generated.isSuccess()) {
                job.model = PreviewModel.failure(generated.error());
                return;
            }
            placements.putAll(generated.placements());
        }
        var selectedCutoutCells = new HashSet<GridPosition>();
        if (roleResolution.damageRecipe().isPresent()) {
            var overlayPositions = cells.keySet().stream()
                    .filter(position -> {
                        var role = cells.get(position).role();
                        return role == RoadCell.Role.SURFACE
                                || role == RoadCell.Role.MARKING;
                    })
                    .toList();
            var damagePreset = roleResolution.damageRecipe().orElseThrow();
            var generated = generateSplineRecipe(
                    damagePreset, "DAMAGE", "SPLINE_DAMAGE",
                    overlayPositions, cells, job
            );
            if (!generated.isSuccess()) {
                job.model = PreviewModel.failure(generated.error());
                return;
            }
            generated.placements().forEach((position, material) -> {
                if (material.kind() != ProceduralMaterial.Kind.SKIP) {
                    placements.put(position, material);
                    if (material.kind()
                            == ProceduralMaterial.Kind.ERASER) {
                        selectedCutoutCells.add(position);
                    }
                }
            });
        }

        var cutout = SplineCutoutGeometry.expand(
                selectedCutoutCells, cells,
                key.preset().advanced().roadProfile().cutout()
        );
        if (!cutout.isSuccess()) {
            job.model = PreviewModel.failure(
                    String.join("; ", cutout.errors())
            );
            return;
        }
        for (var cell : cutout.airCells()) {
            cells.put(cell.position(), cell);
            placements.put(cell.position(), ProceduralMaterial.eraser());
        }
        var cutoutRecipes = List.of(
                new PreviewCutoutRecipe(
                        cutout.wallCells(),
                        roleResolution.cutoutWallRecipe()
                                .orElse(key.preset()),
                        "CUTOUT WALLS", "SPLINE_CUTOUT_WALL"
                ),
                new PreviewCutoutRecipe(
                        cutout.floorCells(),
                        roleResolution.cutoutFloorRecipe()
                                .orElse(key.preset()),
                        "CUTOUT FLOOR", "SPLINE_CUTOUT_FLOOR"
                )
        );
        for (var recipe : cutoutRecipes) {
            for (var cell : recipe.cells()) {
                cells.put(cell.position(), cell);
            }
            var positions = recipe.cells().stream()
                    .map(RoadCell::position)
                    .toList();
            var generated = generateSplineRecipe(
                    recipe.preset(), recipe.label(), recipe.seedSalt(),
                    positions, cells, job
            );
            if (!generated.isSuccess()) {
                job.model = PreviewModel.failure(generated.error());
                return;
            }
            placements.putAll(generated.placements());
        }
        var positions = cells.keySet().stream()
                .sorted(GridPosition.TRAVERSAL_ORDER)
                .toList();
        job.model = new PreviewModel(
                placements,
                positions,
                "",
                geometry.treeCells(),
                cells
        );
    }

    private static PreviewRecipeResult generateSplineRecipe(
            ProceduralPatternPreset preset,
            String label,
            String seedSalt,
            List<GridPosition> positions,
            Map<GridPosition, RoadCell> cells,
            PreviewJob job
    ) {
        if (positions.isEmpty()) {
            return PreviewRecipeResult.success(Map.of());
        }
        var adaptation = ProceduralPresetAdapter.adapt(preset);
        if (!adaptation.isSuccess()) {
            return PreviewRecipeResult.failure(
                    label + ": " + String.join("; ", adaptation.errors())
            );
        }
        long seed = StableRandom.mixSeed(
                preset.seed(), StableRandom.stableStringHash(seedSalt)
        );
        CoordinateLookup coordinates = (coordinate, position) -> {
            var cell = cells.get(position);
            return cell == null
                    ? OptionalDouble.empty()
                    : cell.geometry().sample(coordinate);
        };
        var request = new GenerationRequest<>(
                seed, positions, adaptation.ruleSet().orElseThrow(),
                ExistingNeighborLookup.NONE, positions.size(),
                job.cancelled::get, GenerationProgress.NONE, coordinates
        );
        var generated = ProceduralGenerator.generate(request);
        if (!generated.isSuccess()) {
            return PreviewRecipeResult.failure(
                    label + ": "
                            + generated.failure().orElseThrow().message()
            );
        }
        job.completed.addAndGet(positions.size());
        return PreviewRecipeResult.success(generated.placements());
    }

    private record PreviewCutoutRecipe(
            List<RoadCell> cells,
            ProceduralPatternPreset preset,
            String label,
            String seedSalt
    ) {
    }

    private record PreviewRecipeResult(
            Map<GridPosition, ProceduralMaterial> placements,
            String error
    ) {
        static PreviewRecipeResult success(
                Map<GridPosition, ProceduralMaterial> placements
        ) {
            return new PreviewRecipeResult(Map.copyOf(placements), "");
        }

        static PreviewRecipeResult failure(String error) {
            return new PreviewRecipeResult(Map.of(), error);
        }

        boolean isSuccess() {
            return error.isEmpty();
        }
    }

    private void renderModel(Renderer renderer, PreviewModel model) {
        if (model.placements().isEmpty()) {
            return;
        }
        var bounds = Bounds.of(model.positions());
        int viewportTop = getY() + 18;
        int viewportHeight = Math.max(1, getHeight() - 24);
        renderer.pushScissor(
                getX() + 2,
                viewportTop,
                getWidth() - 4,
                viewportHeight
        );
        renderer.pushPose();
        renderer.translate(
                getCenterX() + panX,
                viewportTop + viewportHeight / 2f + 3 + panY,
                1000
        );

        // Project all eight corners for the current orbit. This uses the same
        // X-then-Y transform as the renderer and fills the viewport without
        // relying on an angle-specific wall/floor heuristic.
        var projected = projectedSize(
                bounds,
                pitchDegrees,
                yawDegrees
        );
        double scale = Math.min(
                (getWidth() - 24.0) / projected.width(),
                (viewportHeight - 18.0) / projected.height()
        ) * zoom;
        renderer.scale(-Math.max(0.5, scale));
        renderer.rotate(Vector3f.XP.rotationDegrees(pitchDegrees));
        renderer.rotate(Vector3f.YP.rotationDegrees(yawDegrees));
        renderer.translate(
                -bounds.centerX(),
                -bounds.centerY(),
                -bounds.centerZ()
        );
        var world = getEntrance().getClient().getWorld();
        // Model tessellation samples ambient occlusion from the supplied
        // world position before our fixed-light shader sees the vertices.
        // Sampling beside the player therefore made the GUI preview inherit
        // cave walls, ceilings, and other nearby occluders. Keep biome tinting
        // from the real client world, but tessellate in guaranteed empty space
        // above its build ceiling so the workbench has stable studio lighting.
        int studioY = world.getMaxBuildHeight() + 64;
        var occupied = new HashSet<GridPosition>();
        var rawStates = new LinkedHashMap<GridPosition, BlockState>();
        var roadGeometries = new LinkedHashMap<
                GridPosition, StructuralGeometry>();
        model.roadCells().forEach((position, cell) ->
                roadGeometries.put(position, cell.geometry()));
        model.placements().forEach((position, material) -> {
            if (material.kind() == ProceduralMaterial.Kind.BLOCK) {
                occupied.add(position);
                rawStates.put(
                        position,
                        material.placeableBlock().orElseThrow()
                                .getBlock().getDefaultBlockState()
                );
            }
        });
        for (var position : model.positions()) {
            var item = model.placements().get(position);
            if (item == null
                    || item.kind() != ProceduralMaterial.Kind.BLOCK) {
                continue;
            }
            var blockItem = item.placeableBlock().orElseThrow();
            var blockPosition = new BlockPosition(
                    position.x(),
                    position.y(),
                    position.z()
            );
            var state = rawStates.get(position);
            var treeCell = model.treeCells().get(position);
            if (treeCell != null) {
                state = TreeBlockStateResolver.resolve(
                        state, treeCell, occupied,
                        model.treeCells(), rawStates
                );
            } else if (model.roadCells().containsKey(position)) {
                state = StructuralBlockStateResolver.resolve(
                        state,
                        position,
                        model.roadCells().get(position).geometry(),
                        occupied,
                        roadGeometries,
                        rawStates
                );
            }
            renderer.pushPose();
            renderer.translate(position.x(), position.y(), position.z());
            renderer.renderBlockState(
                    BlockRenderLayers.previewBlock(Color.WHITE.getRGB()),
                    world,
                    new BlockPosition(
                            blockPosition.x(),
                            studioY + blockPosition.y(),
                            blockPosition.z()
                    ),
                    state
            );
            renderer.popPose();
        }
        // Block models are buffered. Flush while the viewport scissor and the
        // preview depth state are still active, or rotated/zoomed geometry can
        // be submitted after the clip has been removed.
        renderer.flush();
        renderer.popPose();
        renderer.popScissor();
    }

    private void renderProgress(Renderer renderer, int complete, int total) {
        int left = getX() + 12;
        int right = getRight() - 12;
        int y = getCenterY() + 12;
        renderer.renderRect(left, y, right, y + 3, 0xFF24272C);
        if (total > 0) {
            int filled = left + (int) Math.round(
                    (right - left) * Math.min(1.0, (double) complete / total)
            );
            renderer.renderRect(
                    left,
                    y,
                    filled,
                    y + 3,
                    ProceduralTheme.GOLD
            );
        }
    }

    private static List<GridPosition> positions(
            ProceduralPreviewType type,
            PreviewOrientation orientation,
            Structure structure
    ) {
        var mode = type.stockMode();
        var anchors = anchors(mode, orientation);
        var context = Context.defaultSet().withStructure(structure);
        for (var anchor : anchors) {
            context = context.withNextInteraction(interaction(anchor));
        }
        var absolute = structure.collect(context).toList();
        if (absolute.isEmpty()) {
            return List.of(new GridPosition(0, 0, 0));
        }
        int minX = absolute.stream().mapToInt(BlockPosition::x).min()
                .orElse(0);
        int minY = absolute.stream().mapToInt(BlockPosition::y).min()
                .orElse(0);
        int minZ = absolute.stream().mapToInt(BlockPosition::z).min()
                .orElse(0);
        return absolute.stream()
                .map(position -> new GridPosition(
                        position.x() - minX,
                        position.y() - minY,
                        position.z() - minZ
                ))
                .distinct()
                .toList();
    }

    private static List<BlockPosition> anchors(
            BuildMode mode,
            PreviewOrientation orientation
    ) {
        var origin = new BlockPosition(0, 0, 0);
        return switch (mode) {
            case SINGLE -> List.of(origin);
            case LINE -> List.of(origin, switch (orientation) {
                case Y -> new BlockPosition(0, 11, 0);
                case Z -> new BlockPosition(0, 0, 15);
                default -> new BlockPosition(15, 0, 0);
            });
            case WALL -> List.of(
                    origin,
                    orientation == PreviewOrientation.XY
                            ? new BlockPosition(18, 11, 0)
                            : new BlockPosition(0, 11, 18)
            );
            case FLOOR -> List.of(origin, new BlockPosition(15, 0, 15));
            case CUBOID -> List.of(
                    origin,
                    new BlockPosition(8, 8, 0),
                    new BlockPosition(8, 8, 8)
            );
            case DIAGONAL_LINE -> List.of(origin, switch (orientation) {
                case XZ -> new BlockPosition(12, 0, 12);
                case YZ -> new BlockPosition(0, 8, 12);
                case XYZ -> new BlockPosition(10, 7, 10);
                default -> new BlockPosition(12, 8, 0);
            });
            case DIAGONAL_WALL -> List.of(
                    origin,
                    new BlockPosition(13, 0, 10),
                    new BlockPosition(13, 8, 10)
            );
            case SLOPE_FLOOR -> List.of(
                    origin,
                    new BlockPosition(12, 0, 8),
                    new BlockPosition(12, 7, 8)
            );
            case CIRCLE -> List.of(origin, switch (orientation) {
                case XY -> new BlockPosition(13, 9, 0);
                case XZ -> new BlockPosition(13, 0, 13);
                default -> new BlockPosition(0, 9, 13);
            });
            case CYLINDER -> switch (orientation) {
                case X -> List.of(
                        origin,
                        new BlockPosition(0, 8, 8),
                        new BlockPosition(9, 8, 8)
                );
                case Z -> List.of(
                        origin,
                        new BlockPosition(8, 8, 0),
                        new BlockPosition(8, 8, 9)
                );
                default -> List.of(
                        origin,
                        new BlockPosition(8, 0, 8),
                        new BlockPosition(8, 9, 8)
                );
            };
            case SPHERE -> List.of(
                    origin,
                    new BlockPosition(8, 0, 8),
                    new BlockPosition(8, 8, 8)
            );
            case PYRAMID, CONE -> List.of(
                    origin,
                    new BlockPosition(10, 0, 10),
                    new BlockPosition(10, 9, 10)
            );
            case DISABLED -> List.of(origin);
        };
    }

    static PreviewGeometry roadGeometry(RoadProfile profile) {
        var road = RoadVoxelizer.voxelize(
                List.of(
                        new RoadPoint(0.5, 2.5, 0.5),
                        new RoadPoint(7.5, 2.5, 0.5),
                        new RoadPoint(13.5, 2.5, 8.5),
                        new RoadPoint(20.5, 2.5, 8.5)
                ),
                profile
        );
        if (!road.isSuccess()) {
            throw new IllegalArgumentException(
                    String.join("; ", road.errors())
            );
        }
        int minX = road.cells().stream()
                .mapToInt(cell -> cell.position().x()).min().orElse(0);
        int minY = road.cells().stream()
                .mapToInt(cell -> cell.position().y()).min().orElse(0);
        int minZ = road.cells().stream()
                .mapToInt(cell -> cell.position().z()).min().orElse(0);
        var normalizedCells = new LinkedHashMap<GridPosition, RoadCell>();
        for (var cell : road.cells()) {
            var source = cell.position();
            var normalized = new GridPosition(
                    source.x() - minX,
                    source.y() - minY,
                    source.z() - minZ
            );
            normalizedCells.put(normalized, new RoadCell(
                    normalized, cell.role(), cell.geometry(),
                    cell.bandIndex()
            ));
        }
        CoordinateLookup coordinates = (coordinate, position) -> {
            var cell = normalizedCells.get(position);
            if (cell == null) {
                return OptionalDouble.empty();
            }
            return cell.geometry().sample(coordinate);
        };
        var roles = new LinkedHashMap<GridPosition, RoadCell.Role>();
        normalizedCells.forEach((position, cell) ->
                roles.put(position, cell.role()));
        return new PreviewGeometry(
                List.copyOf(normalizedCells.keySet()),
                coordinates,
                Map.of(),
                roles,
                normalizedCells,
                Map.of()
        );
    }

    static PreviewGeometry treeGeometry(TreeProfile profile) {
        return treeGeometry(TreeGenerationConfig.legacySkeleton(profile), 0L);
    }

    static PreviewGeometry treeGeometry(
            TreeGenerationConfig config,
            long seed
    ) {
        // Show the archetype's natural silhouette. Optional branch guides are
        // deliberately absent here: displaying four synthetic guides made
        // every species look like the same squat, four-lobed tree and did not
        // match a normal base/crown placement in the world.
        double expectedHeight = (
                config.minimumHeight() + config.maximumHeight()
        ) * 0.5;
        var guides = List.of(
                new RoadPoint(0.5, 0.5, 0.5),
                new RoadPoint(0.5, expectedHeight + 0.5, 0.5)
        );
        var tree = TreeVoxelizer.generateGuided(
                guides,
                config,
                seed,
                config.variant(),
                100_000
        );
        if (!tree.isSuccess()) {
            throw new IllegalArgumentException(
                    String.join("; ", tree.errors())
            );
        }
        int minX = tree.cells().stream()
                .mapToInt(cell -> cell.position().x()).min().orElse(0);
        int minY = tree.cells().stream()
                .mapToInt(cell -> cell.position().y()).min().orElse(0);
        int minZ = tree.cells().stream()
                .mapToInt(cell -> cell.position().z()).min().orElse(0);
        var normalizedCells = new LinkedHashMap<GridPosition, TreeCell>();
        var roles = new LinkedHashMap<GridPosition, TreeCell.Role>();
        for (var cell : tree.cells()) {
            var source = cell.position();
            var normalized = new GridPosition(
                    source.x() - minX,
                    source.y() - minY,
                    source.z() - minZ
            );
            normalizedCells.put(normalized, new TreeCell(
                    normalized, cell.role(), cell.geometry()
            ));
            roles.put(normalized, cell.role());
        }
        CoordinateLookup coordinates = (coordinate, position) -> {
            var cell = normalizedCells.get(position);
            if (cell == null) {
                return OptionalDouble.empty();
            }
            return cell.geometry().sample(coordinate);
        };
        return new PreviewGeometry(
                List.copyOf(normalizedCells.keySet()),
                coordinates,
                roles,
                Map.of(),
                Map.of(),
                normalizedCells
        );
    }

    private static BlockInteraction interaction(BlockPosition position) {
        return new BlockInteraction(
                position.getCenter(),
                Direction.UP,
                position,
                true
        );
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static ProjectedSize projectedSize(
            Bounds bounds,
            float pitchDegrees,
            float yawDegrees
    ) {
        double halfX = bounds.sizeX() / 2.0;
        double halfY = bounds.sizeY() / 2.0;
        double halfZ = bounds.sizeZ() / 2.0;
        double pitch = Math.toRadians(pitchDegrees);
        double yaw = Math.toRadians(yawDegrees);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (int xSign : new int[]{-1, 1}) {
            for (int ySign : new int[]{-1, 1}) {
                for (int zSign : new int[]{-1, 1}) {
                    double x = xSign * halfX;
                    double y = ySign * halfY;
                    double z = zSign * halfZ;
                    // MatrixStack post-multiplies rotations, so the Y orbit is
                    // applied to a point before the X pitch.
                    double rotatedX = x * cosYaw + z * sinYaw;
                    double rotatedZ = -x * sinYaw + z * cosYaw;
                    double rotatedY = y * cosPitch
                            - rotatedZ * sinPitch;
                    minimumX = Math.min(minimumX, rotatedX);
                    maximumX = Math.max(maximumX, rotatedX);
                    minimumY = Math.min(minimumY, rotatedY);
                    maximumY = Math.max(maximumY, rotatedY);
                }
            }
        }
        return new ProjectedSize(
                Math.max(1.0, maximumX - minimumX),
                Math.max(1.0, maximumY - minimumY)
        );
    }

    private record PreviewKey(
            ProceduralPatternPreset preset,
            ProceduralPatternLibrary library,
            ProceduralPreviewType type,
            PreviewOrientation orientation,
            Structure structure
    ) {
    }

    record PreviewGeometry(
            List<GridPosition> positions,
            CoordinateLookup coordinates,
            Map<GridPosition, TreeCell.Role> treeRoles,
            Map<GridPosition, RoadCell.Role> roadRoles,
            Map<GridPosition, RoadCell> roadCells,
            Map<GridPosition, TreeCell> treeCells
    ) {
        PreviewGeometry {
            positions = List.copyOf(positions);
            treeRoles = Map.copyOf(treeRoles);
            roadRoles = Map.copyOf(roadRoles);
            roadCells = Map.copyOf(roadCells);
            treeCells = Map.copyOf(treeCells);
        }
    }

    private static final class PreviewJob {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicInteger completed = new AtomicInteger();
        private final AtomicInteger total = new AtomicInteger();
        private volatile PreviewModel model;
        private CompletableFuture<Void> future;
    }

    private record PreviewModel(
            Map<GridPosition, ProceduralMaterial> placements,
            List<GridPosition> positions,
            String error,
            Map<GridPosition, TreeCell> treeCells,
            Map<GridPosition, RoadCell> roadCells
    ) {
        private PreviewModel {
            placements = Map.copyOf(placements);
            positions = List.copyOf(positions);
            treeCells = Map.copyOf(treeCells);
            roadCells = Map.copyOf(roadCells);
        }

        static PreviewModel failure(String error) {
            return new PreviewModel(
                    Map.of(), List.of(), error, Map.of(), Map.of()
            );
        }
    }

    private record ProjectedSize(double width, double height) {
    }

    private record Bounds(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        static Bounds of(List<GridPosition> positions) {
            int minX = positions.stream().mapToInt(GridPosition::x).min()
                    .orElse(0);
            int minY = positions.stream().mapToInt(GridPosition::y).min()
                    .orElse(0);
            int minZ = positions.stream().mapToInt(GridPosition::z).min()
                    .orElse(0);
            int maxX = positions.stream().mapToInt(GridPosition::x).max()
                    .orElse(0);
            int maxY = positions.stream().mapToInt(GridPosition::y).max()
                    .orElse(0);
            int maxZ = positions.stream().mapToInt(GridPosition::z).max()
                    .orElse(0);
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        int sizeX() {
            return maxX - minX + 1;
        }

        int sizeY() {
            return maxY - minY + 1;
        }

        int sizeZ() {
            return maxZ - minZ + 1;
        }

        double centerX() {
            return (minX + maxX + 1) / 2.0;
        }

        double centerY() {
            return (minY + maxY + 1) / 2.0;
        }

        double centerZ() {
            return (minZ + maxZ + 1) / 2.0;
        }
    }
}
