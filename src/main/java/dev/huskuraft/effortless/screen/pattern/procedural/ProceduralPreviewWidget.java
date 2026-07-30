package dev.huskuraft.effortless.screen.pattern.procedural;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import dev.huskuraft.effortless.building.Context;
import dev.huskuraft.effortless.building.structure.BuildMode;
import dev.huskuraft.effortless.building.structure.builder.Structure;
import dev.huskuraft.effortless.client.pattern.procedural.ExistingNeighborLookup;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationRequest;
import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralContextCompiler;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralGenerator;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralPresetAdapter;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.renderer.opertaion.BlockRenderLayers;
import dev.huskuraft.universal.api.core.BlockInteraction;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.Direction;
import dev.huskuraft.universal.api.core.Item;
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
    private final Supplier<BuildMode> mode;
    private final Supplier<PreviewOrientation> orientation;
    private final Supplier<Structure> structure;
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
            Supplier<BuildMode> mode,
            Supplier<PreviewOrientation> orientation,
            Supplier<Structure> structure
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
        this.mode = mode;
        this.orientation = orientation;
        this.structure = structure;
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
        return mode.get().getDisplayName().getString()
                + " \u00b7 "
                + PreviewOrientation.kind(mode.get())
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
        var resolved = ProceduralContextCompiler.resolvePreviewMaterials(
                getEntrance().getClient().getPlayer(),
                preset.get()
        );
        var key = new PreviewKey(
                resolved,
                mode.get(),
                orientation.get(),
                structure.get()
        );
        if (key.equals(currentKey)) {
            return;
        }
        currentKey = key;
        currentJob = request(key);
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
            var adaptation = ProceduralPresetAdapter.adapt(key.preset());
            if (!adaptation.isSuccess()) {
                job.model = PreviewModel.failure(
                        String.join("; ", adaptation.errors())
                );
                return;
            }
            var positions = positions(
                    key.mode(),
                    key.orientation(),
                    key.structure()
            );
            job.total.set(positions.size());
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
                    }
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
                    ""
            );
        } catch (RuntimeException exception) {
            job.model = PreviewModel.failure(
                    exception.getClass().getSimpleName() + ": "
                            + exception.getMessage()
            );
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
        var playerPosition = getEntrance().getClient().getPlayer()
                .getPosition()
                .toVector3i();
        for (var position : model.positions()) {
            var item = model.placements().get(position);
            if (!(item instanceof BlockItem blockItem)) {
                continue;
            }
            var blockPosition = new BlockPosition(
                    position.x(),
                    position.y(),
                    position.z()
            );
            renderer.pushPose();
            renderer.translate(position.x(), position.y(), position.z());
            renderer.renderBlockState(
                    BlockRenderLayers.previewBlock(Color.WHITE.getRGB()),
                    world,
                    blockPosition.add(playerPosition),
                    blockItem.getBlock().getDefaultBlockState()
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
            BuildMode mode,
            PreviewOrientation orientation,
            Structure structure
    ) {
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
            BuildMode mode,
            PreviewOrientation orientation,
            Structure structure
    ) {
    }

    private static final class PreviewJob {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicInteger completed = new AtomicInteger();
        private final AtomicInteger total = new AtomicInteger();
        private volatile PreviewModel model;
        private CompletableFuture<Void> future;
    }

    private record PreviewModel(
            Map<GridPosition, Item> placements,
            List<GridPosition> positions,
            String error
    ) {
        private PreviewModel {
            placements = Map.copyOf(placements);
            positions = List.copyOf(positions);
        }

        static PreviewModel failure(String error) {
            return new PreviewModel(Map.of(), List.of(), error);
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
