package dev.huskuraft.effortless.screen.pattern.procedural;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import dev.huskuraft.effortless.client.pattern.procedural.ExistingNeighborLookup;
import dev.huskuraft.effortless.client.pattern.procedural.GenerationRequest;
import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralContextCompiler;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralGenerator;
import dev.huskuraft.effortless.client.pattern.procedural.ProceduralPresetAdapter;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.renderer.opertaion.BlockRenderLayers;
import dev.huskuraft.universal.api.core.BlockItem;
import dev.huskuraft.universal.api.core.BlockPosition;
import dev.huskuraft.universal.api.core.Item;
import dev.huskuraft.universal.api.gui.AbstractWidget;
import dev.huskuraft.universal.api.math.Vector3f;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.renderer.Renderer;
import dev.huskuraft.universal.api.text.ChatFormatting;
import dev.huskuraft.universal.api.text.Text;

/**
 * Asynchronous, deterministic workbench preview. It uses the same adapter and
 * generator as placement, but targets a small synthetic wall, floor, or cube.
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
    private final Supplier<View> view;
    private PreviewKey currentKey;
    private PreviewJob currentJob;

    ProceduralPreviewWidget(
            Entrance entrance,
            int x,
            int y,
            int width,
            int height,
            Supplier<ProceduralPatternPreset> preset,
            Supplier<View> view
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
        this.view = view;
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
        renderer.renderTextFromEnd(
                getTypeface(),
                Text.translate(
                        "effortless.procedural.preview.view."
                                + view.get().name().toLowerCase()
                ).withStyle(ChatFormatting.GOLD),
                getRight() - 6,
                getY() + 5,
                0xFFC4A66B,
                true
        );

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
    public List<Text> getTooltip() {
        return List.of();
    }

    private void ensureJob() {
        var resolved = ProceduralContextCompiler.resolvePreviewMaterials(
                getEntrance().getClient().getPlayer(),
                preset.get()
        );
        var key = new PreviewKey(resolved, view.get());
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
            var positions = positions(key.view());
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
                getCenterX(),
                viewportTop + viewportHeight / 2f + 3,
                100
        );

        double projectedWidth = Math.max(
                1.0,
                bounds.sizeX() + bounds.sizeZ() * 0.72
        );
        double projectedHeight = Math.max(
                1.0,
                bounds.sizeY() + (bounds.sizeX() + bounds.sizeZ()) * 0.36
        );
        double scale = Math.min(
                (getWidth() - 18.0) / projectedWidth,
                (viewportHeight - 10.0) / projectedHeight
        );
        renderer.scale(-Math.max(0.5, scale));
        if (view.get() == View.WALL) {
            renderer.rotate(Vector3f.XP.rotationDegrees(-8));
            renderer.rotate(Vector3f.YP.rotationDegrees(8));
        } else {
            renderer.rotate(Vector3f.XP.rotationDegrees(-30));
            renderer.rotate(Vector3f.YP.rotationDegrees(45));
        }
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
                    BlockRenderLayers.block(Color.WHITE.getRGB()),
                    world,
                    blockPosition.add(playerPosition),
                    blockItem.getBlock().getDefaultBlockState()
            );
            renderer.popPose();
        }
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

    private static List<GridPosition> positions(View view) {
        var result = new ArrayList<GridPosition>();
        switch (view) {
            case WALL -> {
                for (int y = 0; y < 12; y++) {
                    for (int x = 0; x < 20; x++) {
                        result.add(new GridPosition(x, y, 0));
                    }
                }
            }
            case FLOOR -> {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        result.add(new GridPosition(x, 0, z));
                    }
                }
            }
            case CUBE -> {
                int size = 9;
                for (int y = 0; y < size; y++) {
                    for (int z = 0; z < size; z++) {
                        for (int x = 0; x < size; x++) {
                            if (x == 0 || x == size - 1
                                    || y == 0 || y == size - 1
                                    || z == 0 || z == size - 1) {
                                result.add(new GridPosition(x, y, z));
                            }
                        }
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    enum View {
        WALL,
        FLOOR,
        CUBE
    }

    private record PreviewKey(
            ProceduralPatternPreset preset,
            View view
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
