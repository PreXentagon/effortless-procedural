package dev.huskuraft.effortless.screen.pattern.procedural;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.building.structure.builder.Structure;
import dev.huskuraft.effortless.client.pattern.procedural.CoordinateLookup;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationBounds;
import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralContextCompiler;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralMaterial;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralPreviewMarkers;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralBlockStateResolver;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.generator.ClientToolSubtype;
import dev.huskuraft.effortless.client.road.RoadCell;
import dev.huskuraft.effortless.client.road.RoadSpline;
import dev.huskuraft.effortless.client.tree.TreeCell;
import dev.huskuraft.effortless.client.tree.TreeBlockStateResolver;
import dev.huskuraft.effortless.renderer.opertaion.BlockRenderLayers;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.BlockState;
import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.math.Vector3f;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/**
 * Asynchronous, deterministic workbench preview. It uses the same adapter and
 * generator as placement, but targets a small synthetic instance of the tool
 * selected by the authoritative workbench rail. Synthetic orientation remains
 * a view aid; the tool and its feature/subtype selections are real.
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
    private PreviewModel sampledModel;
    private List<GridPosition> sampledPositions = List.of();
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
        boolean disabled = !type.get().isClientOnly()
                && type.get().stockMode().isDisabled();
        if (!disabled) {
            ensureJob();
        }
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

        if (disabled) {
            renderer.renderTextFromCenter(
                    getTypeface(),
                    Text.text("Vanilla placement · no generated geometry")
                            .withStyle(ChatFormatting.GRAY),
                    getCenterX(), getCenterY(), 0xFFB8B8B8, true
            );
            return;
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
            renderError(renderer, model.error());
            return;
        }
        renderModel(renderer, model);
    }

    private void renderError(Renderer renderer, String message) {
        var lines = wrapError(message, Math.max(40, getWidth() - 32), 5);
        int lineHeight = 11;
        int firstY = getCenterY() - (lines.size() - 1) * lineHeight / 2;
        for (int index = 0; index < lines.size(); index++) {
            renderer.renderTextFromCenter(
                    getTypeface(),
                    Text.text(lines.get(index)).withStyle(ChatFormatting.RED),
                    getCenterX(),
                    firstY + index * lineHeight,
                    0xFFFF7777,
                    true
            );
        }
    }

    private List<String> wrapError(
            String message,
            int maximumWidth,
            int maximumLines
    ) {
        var lines = new ArrayList<String>();
        var current = new StringBuilder();
        for (var word : message.strip().split("\\s+")) {
            String candidate = current.isEmpty()
                    ? word
                    : current + " " + word;
            if (!current.isEmpty()
                    && getTypeface().measureWidth(candidate) > maximumWidth) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        if (lines.size() <= maximumLines) {
            return lines;
        }
        var visible = new ArrayList<>(lines.subList(0, maximumLines));
        String last = visible.getLast();
        while (!last.isEmpty()
                && getTypeface().measureWidth(last + "...") > maximumWidth) {
            last = last.substring(0, last.length() - 1);
        }
        visible.set(maximumLines - 1, last.stripTrailing() + "...");
        return visible;
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

    String statusText() {
        var job = currentJob;
        if (job == null) {
            return "Preview idle";
        }
        var model = job.model;
        if (model == null) {
            int total = Math.max(1, job.total.get());
            int percent = Math.min(
                    99,
                    (int) Math.round(job.completed.get() * 100.0 / total)
            );
            return "Preview " + percent + "%";
        }
        if (!model.error().isEmpty()) {
            return "Preview error";
        }
        int rendered = currentKey == null
                ? model.positions().size()
                : Math.min(
                        model.positions().size(),
                        currentKey.maximumRenderedPositions()
                );
        return "Preview " + model.positions().size() + " cells"
                + (rendered < model.positions().size()
                        ? " (" + rendered + " shown)" : "");
    }

    String compactStatusText() {
        var job = currentJob;
        if (job == null) {
            return "idle";
        }
        var model = job.model;
        if (model == null) {
            int total = Math.max(1, job.total.get());
            int percent = Math.min(
                    99,
                    (int) Math.round(job.completed.get() * 100.0 / total)
            );
            return percent + "%";
        }
        if (!model.error().isEmpty()) {
            return "preview error";
        }
        int total = model.positions().size();
        int rendered = currentKey == null
                ? total
                : Math.min(total, currentKey.maximumRenderedPositions());
        return rendered < total
                ? total + "/" + rendered + " shown"
                : total + " cells";
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
        var sourcePreset = preset.get();
        var selectedPreviewPreset = ProceduralContextCompiler
                .resolvePreviewMaterials(player, sourcePreset);
        var resolvedPresets = sourceLibrary.presets().stream()
                .map(value -> value.id().equals(sourcePreset.id())
                        ? selectedPreviewPreset
                        : ProceduralContextCompiler.resolvePreviewMaterials(
                                player, value
                        ))
                .toList();
        var previewLibrary = new ProceduralPatternLibrary(
                sourceLibrary.enabled(),
                sourceLibrary.activePresetId(),
                resolvedPresets,
                sourceLibrary.fieldAssets()
        );
        var resolved = selectedPreviewPreset;
        resolved = applyPreviewSubtype(
                resolved,
                type.get(),
                clientSubtype.get()
        );
        var clientConfig = ((EffortlessClient) getEntrance())
                .getConfigStorage().get();
        int maximumPreviewPositions = Math.max(
                1,
                clientConfig.proceduralSafetyConfig().maxCompiledPositions()
        );
        var key = new PreviewKey(
                resolved,
                previewLibrary,
                type.get(),
                orientation.get(),
                structure.get(),
                maximumPreviewPositions,
                Math.max(0, clientConfig.renderConfig().maxRenderVolume())
        );
        if (key.equals(currentKey)) {
            return;
        }
        if (currentJob != null && currentJob.model == null) {
            currentJob.cancelled.set(true);
            synchronized (CACHE) {
                CACHE.remove(currentKey, currentJob);
            }
        }
        currentKey = key;
        currentJob = request(key);
    }

    static ProceduralPatternPreset applyPreviewSubtype(
            ProceduralPatternPreset value,
            ProceduralPreviewType previewType,
            ClientToolSubtype selected
    ) {
        return ProceduralPreviewCompiler.applySubtype(
                value, previewType, selected
        );
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
                    () -> job.model = ProceduralPreviewCompiler.generate(
                            key, job
                    ),
                    EXECUTOR
            );
            return job;
        }
    }

    private void renderModel(Renderer renderer, PreviewModel model) {
        if (model.placements().isEmpty()) {
            return;
        }
        var bounds = GenerationBounds.enclosing(model.positions());
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
        for (var position : renderPositions(model)) {
            var item = model.placements().get(position);
            if (item == null
                    || item.kind() == ProceduralMaterial.Kind.SKIP) {
                continue;
            }
            var blockPosition = new BlockPosition(
                    position.x(),
                    position.y(),
                    position.z()
            );
            var markerConfig = ((EffortlessClient) getEntrance())
                    .getConfigStorage().get()
                    .renderConfig();
            boolean splineCutout = item.kind()
                    == ProceduralMaterial.Kind.ERASER
                    && model.roadCells().containsKey(position);
            var state = item.kind() == ProceduralMaterial.Kind.ERASER
                    ? splineCutout
                            ? ProceduralPreviewMarkers.cutout(markerConfig)
                            : ProceduralPreviewMarkers.eraser(markerConfig)
                    : rawStates.get(position);
            if (item.kind() == ProceduralMaterial.Kind.BLOCK) {
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

    private List<GridPosition> renderPositions(PreviewModel model) {
        if (sampledModel != model) {
            sampledModel = model;
            sampledPositions = PreviewSampling.evenlySpaced(
                    model.positions(),
                    currentKey == null
                            ? model.positions().size()
                            : currentKey.maximumRenderedPositions()
            );
        }
        return sampledPositions;
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

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static ProjectedSize projectedSize(
            GenerationBounds bounds,
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

    record PreviewKey(
            ProceduralPatternPreset preset,
            ProceduralPatternLibrary library,
            ProceduralPreviewType type,
            PreviewOrientation orientation,
            Structure structure,
            int maximumPositions,
            int maximumRenderedPositions
    ) {
    }

    record PreviewGeometry(
            List<GridPosition> positions,
            CoordinateLookup coordinates,
            Map<GridPosition, TreeCell.Role> treeRoles,
            Map<GridPosition, RoadCell.Role> roadRoles,
            Map<GridPosition, RoadCell> roadCells,
            Map<GridPosition, TreeCell> treeCells,
            List<RoadSpline.Sample> pathSamples
    ) {
        PreviewGeometry {
            positions = List.copyOf(positions);
            treeRoles = Map.copyOf(treeRoles);
            roadRoles = Map.copyOf(roadRoles);
            roadCells = Map.copyOf(roadCells);
            treeCells = Map.copyOf(treeCells);
            pathSamples = List.copyOf(pathSamples);
        }
    }

    static final class PreviewJob
            implements ProceduralPreviewCompiler.Monitor {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger total = new AtomicInteger();
        volatile PreviewModel model;
        CompletableFuture<Void> future;

        @Override
        public boolean cancelled() {
            return cancelled.get();
        }

        @Override
        public void setCompleted(int value) {
            completed.set(value);
        }

        @Override
        public void addCompleted(int value) {
            completed.addAndGet(value);
        }

        @Override
        public void setTotal(int value) {
            total.set(value);
        }
    }

    record PreviewModel(
            Map<GridPosition, ProceduralMaterial> placements,
            List<GridPosition> positions,
            String error,
            Map<GridPosition, TreeCell> treeCells,
            Map<GridPosition, RoadCell> roadCells
    ) {
        PreviewModel {
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

}
