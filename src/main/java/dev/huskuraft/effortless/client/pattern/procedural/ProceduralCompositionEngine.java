package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralCompositionLayer;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.road.RoadPoint;
import dev.huskuraft.effortless.client.road.RoadSpline;
import dev.huskuraft.effortless.client.tree.TreeCell;
import dev.huskuraft.effortless.client.tree.TreeMaterialResolver;
import dev.huskuraft.effortless.client.tree.TreeVoxelizer;

/**
 * Deterministically flattens ordered primitive/tree scene layers. It never
 * creates a network type: callers turn the returned cells into ordinary
 * block or air entries before sending an existing clipboard request.
 */
public final class ProceduralCompositionEngine {

    public static final int MAXIMUM_LAYERS = 256;

    private ProceduralCompositionEngine() {
    }

    public static Result compose(
            Set<GridPosition> basePositions,
            List<RoadSpline.Sample> pathSamples,
            ProceduralPatternLibrary library,
            ProceduralPatternPreset rootPreset,
            int maximumPositions
    ) {
        return compose(
                basePositions, pathSamples, library, rootPreset,
                maximumPositions, () -> false
        );
    }

    public static Result compose(
            Set<GridPosition> basePositions,
            List<RoadSpline.Sample> pathSamples,
            ProceduralPatternLibrary library,
            ProceduralPatternPreset rootPreset,
            int maximumPositions,
            BooleanSupplier cancelled
    ) {
        if (basePositions.isEmpty()) {
            return Result.failure(List.of(
                    "Composition requires non-empty base geometry"
            ));
        }
        if (maximumPositions < 1) {
            return Result.failure(List.of(
                    "Composition position limit must be positive"
            ));
        }
        if (rootPreset.advanced().compositionLayers().size()
                > MAXIMUM_LAYERS) {
            return Result.failure(List.of(
                    "Scene composition contains "
                            + rootPreset.advanced().compositionLayers().size()
                            + " layers, above the limit of "
                            + MAXIMUM_LAYERS
            ));
        }
        var positions = new LinkedHashSet<>(basePositions.stream()
                .sorted(GridPosition.TRAVERSAL_ORDER)
                .toList());
        var additions = new LinkedHashMap<GridPosition, GeneratedCell>();
        var erasers = new LinkedHashSet<GridPosition>();
        var skipped = new LinkedHashSet<GridPosition>();
        var rootPaths = new ArrayList<List<RoadSpline.Sample>>();
        var generatedPaths = new ArrayList<List<RoadSpline.Sample>>();
        if (pathSamples != null && !pathSamples.isEmpty()) {
            rootPaths.add(List.copyOf(pathSamples));
        }
        var errors = new ArrayList<String>();

        for (var layer : rootPreset.advanced().compositionLayers()) {
            if (cancelled.getAsBoolean()) {
                return Result.failure(List.of("Composition was cancelled"));
            }
            if (!layer.enabled()) {
                continue;
            }
            var validation = layer.validate();
            if (!validation.isEmpty()) {
                validation.forEach(error -> errors.add(
                        layer.name() + ": " + error
                ));
                continue;
            }
            GeneratedLayer generated;
            try {
                generated = generate(
                        layer, positions, rootPaths, generatedPaths,
                        library, rootPreset, maximumPositions, cancelled
                );
            } catch (CancellationException exception) {
                return Result.failure(List.of("Composition was cancelled"));
            }
            if (!generated.errors().isEmpty()) {
                generated.errors().forEach(error -> errors.add(
                        layer.name() + ": " + error
                ));
                continue;
            }
            var shape = generated.cells().keySet();
            switch (layer.operation()) {
                case UNION -> {
                    for (var entry : generated.cells().entrySet()) {
                        boolean restored = erasers.contains(entry.getKey())
                                || skipped.contains(entry.getKey());
                        if (positions.add(entry.getKey()) || restored) {
                            additions.put(entry.getKey(), entry.getValue());
                        }
                    }
                    erasers.removeAll(shape);
                    skipped.removeAll(shape);
                    generatedPaths.addAll(generated.paths());
                }
                case REPLACE -> {
                    positions.addAll(shape);
                    additions.putAll(generated.cells());
                    erasers.removeAll(shape);
                    skipped.removeAll(shape);
                    generatedPaths.addAll(generated.paths());
                }
                case SUBTRACT -> {
                    positions.addAll(shape);
                    additions.keySet().removeAll(shape);
                    skipped.removeAll(shape);
                    erasers.addAll(shape);
                }
                case SKIP -> {
                    positions.removeAll(shape);
                    additions.keySet().removeAll(shape);
                    erasers.removeAll(shape);
                    skipped.addAll(shape);
                }
                case INTERSECT -> {
                    positions.retainAll(shape);
                    additions.keySet().retainAll(shape);
                    erasers.retainAll(shape);
                    skipped.removeAll(positions);
                }
            }
            if (positions.size() > maximumPositions) {
                errors.add(layer.name() + ": composition contains "
                        + positions.size() + " positions, above the limit of "
                        + maximumPositions);
                break;
            }
        }
        if (!errors.isEmpty()) {
            return Result.failure(errors);
        }
        var ordered = positions.stream()
                .sorted(GridPosition.TRAVERSAL_ORDER)
                .toList();
        return Result.success(
                ordered,
                additions,
                erasers,
                skipped
        );
    }

    private static GeneratedLayer generate(
            ProceduralCompositionLayer layer,
            Set<GridPosition> current,
            List<List<RoadSpline.Sample>> rootPaths,
            List<List<RoadSpline.Sample>> generatedPaths,
            ProceduralPatternLibrary library,
            ProceduralPatternPreset rootPreset,
            int maximumPositions,
            BooleanSupplier cancelled
    ) {
        var preset = resolvePreset(layer, library, rootPreset);
        if (preset.isEmpty()) {
            return GeneratedLayer.failure(List.of(
                    "Referenced preset '" + layer.presetId()
                            + "' was not found"
            ));
        }
        var anchors = anchors(layer, current, rootPaths, generatedPaths);
        if (anchors.isEmpty()) {
            return GeneratedLayer.failure(List.of(
                    isPathAnchor(layer.anchor())
                            ? "Path anchoring requires spline or tree path samples"
                            : "Could not resolve a composition anchor"
            ));
        }
        if (layer.generator()
                == ProceduralCompositionLayer.Generator.PRIMITIVE) {
            long perInstance = (long) layer.sizeX()
                    * layer.sizeY() * layer.sizeZ();
            long instances = Math.min(
                    anchors.size(), layer.maximumInstances()
            );
            long scanWork = saturatedMultiply(perInstance, instances);
            long scanLimit = saturatedMultiply(maximumPositions, 64L);
            if (scanWork > scanLimit) {
                return GeneratedLayer.failure(List.of(
                        "Primitive scan work exceeds the composition limit ("
                                + scanWork + " candidates > " + scanLimit
                                + "); raise the client position limit or "
                                + "reduce the shape/instance count"
                ));
            }
        }
        var cells = new LinkedHashMap<GridPosition, GeneratedCell>();
        var producedPaths = new ArrayList<List<RoadSpline.Sample>>();
        int instance = 0;
        for (var anchor : anchors) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
            if (instance >= layer.maximumInstances()) {
                break;
            }
            if (layer.generator()
                    == ProceduralCompositionLayer.Generator.TREE) {
                var generatedTree = tree(
                        layer, anchor, preset.get(), library, rootPreset,
                        instance, Math.max(1, maximumPositions - cells.size())
                );
                if (!generatedTree.errors().isEmpty()) {
                    return generatedTree;
                }
                cells.putAll(generatedTree.cells());
                producedPaths.addAll(generatedTree.paths());
            } else {
                cells.putAll(primitive(
                        layer, anchor, preset.get(), maximumPositions,
                        cancelled
                ));
            }
            if (cells.size() > maximumPositions) {
                return GeneratedLayer.failure(List.of(
                        "Layer exceeded the composition position limit"
                ));
            }
            instance++;
        }
        return GeneratedLayer.success(cells, producedPaths);
    }

    private static Optional<ProceduralPatternPreset> resolvePreset(
            ProceduralCompositionLayer layer,
            ProceduralPatternLibrary library,
            ProceduralPatternPreset rootPreset
    ) {
        if (layer.presetId().isBlank()) {
            return Optional.of(withoutComposition(rootPreset));
        }
        try {
            var resolved = library.resolvedPreset(
                    UUID.fromString(layer.presetId())
            );
            return resolved.isSuccess()
                    ? resolved.preset().map(
                            ProceduralCompositionEngine::withoutComposition
                    )
                    : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static ProceduralPatternPreset withoutComposition(
            ProceduralPatternPreset preset
    ) {
        return preset.withAdvanced(
                preset.advanced().withCompositionLayers(List.of())
        );
    }

    private static List<AnchorPoint> anchors(
            ProceduralCompositionLayer layer,
            Set<GridPosition> positions,
            List<List<RoadSpline.Sample>> rootPaths,
            List<List<RoadSpline.Sample>> generatedPaths
    ) {
        var bounds = GenerationBounds.enclosing(positions);
        double centerX = (bounds.minX() + bounds.maxX()) * 0.5
                + layer.offsetX();
        double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5
                + layer.offsetZ();
        if (layer.anchor()
                == ProceduralCompositionLayer.Anchor.BOUNDS_CENTER) {
            return List.of(new AnchorPoint(
                    centerX,
                    bounds.minY() + layer.offsetY(),
                    centerZ,
                    0.0,
                    1.0
            ));
        }
        if (layer.anchor()
                == ProceduralCompositionLayer.Anchor.BOUNDS_TOP) {
            return List.of(new AnchorPoint(
                    centerX,
                    bounds.maxY() + 1.0 + layer.offsetY(),
                    centerZ,
                    0.0,
                    1.0
            ));
        }
        var pathSamples = switch (layer.anchor()) {
            case PATH -> rootPaths;
            case GENERATED_PATH -> generatedPaths;
            case ALL_PATHS -> {
                var combined = new ArrayList<List<RoadSpline.Sample>>(
                        rootPaths.size() + generatedPaths.size()
                );
                combined.addAll(rootPaths);
                combined.addAll(generatedPaths);
                yield combined;
            }
            case BOUNDS_CENTER, BOUNDS_TOP -> List
                    .<List<RoadSpline.Sample>>of();
        };
        if (pathSamples.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<AnchorPoint>();
        for (var path : pathSamples) {
            for (var sample : resamplePath(path, layer.spacing())) {
                result.add(pathAnchor(layer, sample));
                if (result.size() >= layer.maximumInstances()) {
                    return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * Stable arc-length resampling. Generated tree limbs deliberately contain
     * only their endpoints; interpolating here makes spacing mean the same
     * thing for roads, limbs and future path providers.
     */
    static List<RoadSpline.Sample> resamplePath(
            List<RoadSpline.Sample> path,
            double spacing
    ) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<RoadSpline.Sample>();
        result.add(path.getFirst());
        double remaining = spacing;
        for (int index = 1; index < path.size(); index++) {
            var first = path.get(index - 1);
            var second = path.get(index);
            double length = first.point().distance(second.point());
            if (length <= 1.0e-9) {
                continue;
            }
            double along = remaining;
            while (along <= length + 1.0e-9) {
                double amount = Math.min(1.0, along / length);
                var point = first.point().mul(1.0 - amount)
                        .add(second.point().mul(amount));
                var tangent = first.tangent().mul(1.0 - amount)
                        .add(second.tangent().mul(amount)).normalize();
                result.add(new RoadSpline.Sample(
                        point,
                        tangent,
                        first.progress() + (second.progress()
                                - first.progress()) * amount,
                        first.segmentIndex(),
                        first.segmentT() + (second.segmentT()
                                - first.segmentT()) * amount
                ));
                along += spacing;
            }
            remaining = along - length;
            if (remaining <= 1.0e-9) {
                remaining = spacing;
            }
        }
        return List.copyOf(result);
    }

    private static AnchorPoint pathAnchor(
            ProceduralCompositionLayer layer,
            RoadSpline.Sample sample
    ) {
        var tangent = sample.tangent().normalize();
        // PATH offsets use the sampled path's stable local frame: X is
        // lateral, Y is vertical and Z follows the path.
        double lateralX = -tangent.z();
        double lateralZ = tangent.x();
        return new AnchorPoint(
                sample.point().x() + lateralX * layer.offsetX()
                        + tangent.x() * layer.offsetZ(),
                sample.point().y() + layer.offsetY(),
                sample.point().z() + lateralZ * layer.offsetX()
                        + tangent.z() * layer.offsetZ(),
                tangent.x(), tangent.z()
        );
    }

    private static boolean isPathAnchor(
            ProceduralCompositionLayer.Anchor anchor
    ) {
        return anchor == ProceduralCompositionLayer.Anchor.PATH
                || anchor == ProceduralCompositionLayer.Anchor.GENERATED_PATH
                || anchor == ProceduralCompositionLayer.Anchor.ALL_PATHS;
    }

    private static Map<GridPosition, GeneratedCell> primitive(
            ProceduralCompositionLayer layer,
            AnchorPoint anchor,
            ProceduralPatternPreset preset,
            int maximumPositions,
            BooleanSupplier cancelled
    ) {
        var result = new LinkedHashMap<GridPosition, GeneratedCell>();
        for (int y = 0; y < layer.sizeY(); y++) {
            for (int x = 0; x < layer.sizeX(); x++) {
                for (int z = 0; z < layer.sizeZ(); z++) {
                    if ((z & 63) == 0 && cancelled.getAsBoolean()) {
                        throw new CancellationException();
                    }
                    double localX = x + 0.5 - layer.sizeX() * 0.5;
                    double localY = y + 0.5;
                    double localZ = z + 0.5 - layer.sizeZ() * 0.5;
                    if (!inside(layer, localX, localY, localZ, 0)) {
                        continue;
                    }
                    if (layer.hollow()
                            && inside(
                                    layer, localX, localY, localZ,
                                    layer.shellThickness()
                            )) {
                        continue;
                    }
                    var transformed = transform(
                            anchor, localX, localZ,
                            layer.rotationDegrees()
                    );
                    var position = new GridPosition(
                            (int) Math.floor(transformed.x()),
                            (int) Math.floor(anchor.y() + localY),
                            (int) Math.floor(transformed.z())
                    );
                    result.putIfAbsent(
                            position,
                            new GeneratedCell(preset, StructuralGeometry.NONE)
                    );
                    if (result.size() > maximumPositions) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    private static long saturatedMultiply(long first, long second) {
        if (first <= 0L || second <= 0L) {
            return 0L;
        }
        if (first > Long.MAX_VALUE / second) {
            return Long.MAX_VALUE;
        }
        return first * second;
    }

    private static boolean inside(
            ProceduralCompositionLayer layer,
            double x,
            double y,
            double z,
            int inset
    ) {
        return ProceduralPrimitiveEvaluator.contains(
                layer.primitive(), x, y, z,
                layer.sizeX(), layer.sizeY(), layer.sizeZ(), inset
        );
    }

    private static GeneratedLayer tree(
            ProceduralCompositionLayer layer,
            AnchorPoint anchor,
            ProceduralPatternPreset preset,
            ProceduralPatternLibrary library,
            ProceduralPatternPreset rootPreset,
            int instance,
            int maximumCells
    ) {
        long seed = StableRandom.mixSeed(
                rootPreset.seed(), layer.seedSalt()
        );
        int variant = layer.safeVariation() ? instance : 0;
        var tree = TreeVoxelizer.generate(
                new RoadPoint(anchor.x(), anchor.y(), anchor.z()),
                preset.advanced().treeGeneration(),
                seed,
                variant,
                maximumCells
        );
        if (!tree.isSuccess()) {
            return GeneratedLayer.failure(tree.errors());
        }
        var roles = TreeMaterialResolver.resolve(library, preset);
        if (!roles.isSuccess()) {
            return GeneratedLayer.failure(roles.errors());
        }
        var result = new LinkedHashMap<GridPosition, GeneratedCell>();
        for (TreeCell cell : tree.cells()) {
            var recipe = roles.recipes().getOrDefault(cell.role(), preset);
            result.put(
                    cell.position(),
                    new GeneratedCell(withoutComposition(recipe), cell.geometry())
            );
        }
        var paths = tree.limbs().stream().map(limb -> {
            var tangent = limb.end().sub(limb.start()).normalize();
            return List.of(
                    new RoadSpline.Sample(
                            limb.start(), tangent, 0.0, 0, 0.0
                    ),
                    new RoadSpline.Sample(
                            limb.end(), tangent, 1.0, 0, 1.0
                    )
            );
        }).toList();
        return GeneratedLayer.success(result, paths);
    }

    private static Point2 transform(
            AnchorPoint anchor,
            double localX,
            double localZ,
            double extraDegrees
    ) {
        double tangentLength = Math.hypot(anchor.tangentX(), anchor.tangentZ());
        double tangentX = tangentLength < 1.0e-9
                ? 0.0 : anchor.tangentX() / tangentLength;
        double tangentZ = tangentLength < 1.0e-9
                ? 1.0 : anchor.tangentZ() / tangentLength;
        double radians = Math.toRadians(extraDegrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double rotatedX = localX * cosine - localZ * sine;
        double rotatedZ = localX * sine + localZ * cosine;
        double worldX = anchor.x() - rotatedX * tangentZ
                + rotatedZ * tangentX;
        double worldZ = anchor.z() + rotatedX * tangentX
                + rotatedZ * tangentZ;
        return new Point2(worldX, worldZ);
    }

    public record GeneratedCell(
            ProceduralPatternPreset preset,
            StructuralGeometry geometry
    ) {
    }

    public record Result(
            List<GridPosition> positions,
            Map<GridPosition, GeneratedCell> additions,
            Set<GridPosition> erasers,
            Set<GridPosition> skipped,
            List<String> errors
    ) {
        public Result {
            positions = List.copyOf(positions);
            additions = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(additions)
            );
            erasers = java.util.Collections.unmodifiableSet(
                    new LinkedHashSet<>(erasers)
            );
            skipped = java.util.Collections.unmodifiableSet(
                    new LinkedHashSet<>(skipped)
            );
            errors = List.copyOf(errors);
        }

        public static Result success(
                List<GridPosition> positions,
                Map<GridPosition, GeneratedCell> additions,
                Set<GridPosition> erasers,
                Set<GridPosition> skipped
        ) {
            return new Result(
                    positions, additions, erasers, skipped, List.of()
            );
        }

        public static Result failure(List<String> errors) {
            return new Result(
                    List.of(), Map.of(), Set.of(), Set.of(), errors
            );
        }

        public boolean isSuccess() {
            return errors.isEmpty();
        }
    }

    private record GeneratedLayer(
            Map<GridPosition, GeneratedCell> cells,
            List<List<RoadSpline.Sample>> paths,
            List<String> errors
    ) {
        static GeneratedLayer success(
                Map<GridPosition, GeneratedCell> cells,
                List<List<RoadSpline.Sample>> paths
        ) {
            return new GeneratedLayer(
                    java.util.Collections.unmodifiableMap(
                            new LinkedHashMap<>(cells)
                    ),
                    paths.stream().map(List::copyOf).toList(),
                    List.of()
            );
        }

        static GeneratedLayer failure(List<String> errors) {
            return new GeneratedLayer(
                    Map.of(), List.of(), List.copyOf(errors)
            );
        }
    }

    private record AnchorPoint(
            double x,
            double y,
            double z,
            double tangentX,
            double tangentZ
    ) {
    }

    private record Point2(double x, double z) {
    }
}
