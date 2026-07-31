package dev.huskuraft.effortless.client.road;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;

/** Expands selected damage cells into deterministic vertical cutout volumes. */
public final class SplineCutoutGeometry {

    private SplineCutoutGeometry() {
    }

    public static Result expand(
            Set<GridPosition> selectedSurfaceCells,
            Map<GridPosition, RoadCell> roadCells,
            SplineCutoutConfig config
    ) {
        if (!config.enabled() || selectedSurfaceCells.isEmpty()) {
            return Result.EMPTY;
        }
        var errors = config.validate();
        if (!errors.isEmpty()) {
            return new Result(List.of(), List.of(), List.of(), errors);
        }

        var footprint = new LinkedHashMap<Horizontal, RoadCell>();
        selectedSurfaceCells.stream()
                .sorted(GridPosition.TRAVERSAL_ORDER)
                .forEach(position -> {
                    var source = roadCells.get(position);
                    if (source == null) {
                        return;
                    }
                    var key = new Horizontal(position.x(), position.z());
                    footprint.merge(key, source, (first, second) ->
                            first.position().y() >= second.position().y()
                                    ? first : second
                    );
                });
        if (footprint.isEmpty()) {
            return Result.EMPTY;
        }

        double centerX = footprint.keySet().stream()
                .mapToInt(Horizontal::x).average().orElse(0.0);
        double centerZ = footprint.keySet().stream()
                .mapToInt(Horizontal::z).average().orElse(0.0);
        var air = new LinkedHashMap<GridPosition, RoadCell>();
        var walls = new LinkedHashMap<GridPosition, RoadCell>();
        var floor = new LinkedHashMap<GridPosition, RoadCell>();

        for (int level = 1; level <= config.depth(); level++) {
            int erosion = (int) Math.floor(
                    (level - 1) * config.taperPerLayer()
            );
            var layer = erode(footprint, erosion);
            if (layer.isEmpty()) {
                break;
            }
            Set<Horizontal> wallKeys = config.lineWalls()
                    && config.wallThickness() > 0
                    && !(config.lineFloor() && level == config.depth())
                    ? boundary(layer.keySet(), config.wallThickness())
                    : Set.of();
            for (var entry : layer.entrySet()) {
                var source = entry.getValue();
                var position = new GridPosition(
                        entry.getKey().x(),
                        source.position().y() - level,
                        entry.getKey().z()
                );
                if (config.lineFloor() && level == config.depth()) {
                    floor.put(
                            position,
                            cutoutCell(
                                    source, position,
                                    RoadCell.Role.CUTOUT_FLOOR,
                                    0.0, 1.0, 0.0
                            )
                    );
                } else if (wallKeys.contains(entry.getKey())) {
                    walls.put(
                            position,
                            cutoutCell(
                                    source, position,
                                    RoadCell.Role.CUTOUT_WALL,
                                    entry.getKey().x() - centerX,
                                    0.0,
                                    entry.getKey().z() - centerZ
                            )
                    );
                } else {
                    air.put(
                            position,
                            cutoutCell(
                                    source, position,
                                    RoadCell.Role.CUTOUT_WALL,
                                    0.0, -1.0, 0.0
                            )
                    );
                }
            }
        }
        Comparator<RoadCell> order = Comparator.comparing(RoadCell::position);
        return new Result(
                air.values().stream().sorted(order).toList(),
                walls.values().stream().sorted(order).toList(),
                floor.values().stream().sorted(order).toList(),
                List.of()
        );
    }

    private static RoadCell cutoutCell(
            RoadCell source,
            GridPosition position,
            RoadCell.Role role,
            double normalX,
            double normalY,
            double normalZ
    ) {
        var geometry = source.geometry();
        return new RoadCell(
                position,
                role,
                new StructuralGeometry(
                        geometry.path(), geometry.lateral(), 1.0,
                        1.0, geometry.slope(), geometry.tip(),
                        geometry.junction(),
                        geometry.tangentX(), geometry.tangentY(),
                        geometry.tangentZ(),
                        normalX, normalY, normalZ
                )
        );
    }

    private static Map<Horizontal, RoadCell> erode(
            Map<Horizontal, RoadCell> source,
            int iterations
    ) {
        var current = new LinkedHashMap<>(source);
        for (int iteration = 0;
                iteration < iterations && !current.isEmpty();
                iteration++) {
            var keys = Set.copyOf(current.keySet());
            current.entrySet().removeIf(entry ->
                    isBoundary(entry.getKey(), keys)
            );
        }
        return current;
    }

    private static Set<Horizontal> boundary(
            Set<Horizontal> footprint,
            int thickness
    ) {
        var remaining = new HashSet<>(footprint);
        var result = new HashSet<Horizontal>();
        for (int layer = 0;
                layer < thickness && !remaining.isEmpty();
                layer++) {
            var ring = new HashSet<Horizontal>();
            for (var position : remaining) {
                if (isBoundary(position, remaining)) {
                    ring.add(position);
                }
            }
            result.addAll(ring);
            remaining.removeAll(ring);
        }
        return Set.copyOf(result);
    }

    private static boolean isBoundary(
            Horizontal position,
            Set<Horizontal> footprint
    ) {
        return !footprint.contains(position.offset(1, 0))
                || !footprint.contains(position.offset(-1, 0))
                || !footprint.contains(position.offset(0, 1))
                || !footprint.contains(position.offset(0, -1));
    }

    private record Horizontal(int x, int z) {
        Horizontal offset(int offsetX, int offsetZ) {
            return new Horizontal(x + offsetX, z + offsetZ);
        }
    }

    public record Result(
            List<RoadCell> airCells,
            List<RoadCell> wallCells,
            List<RoadCell> floorCells,
            List<String> errors
    ) {
        public static final Result EMPTY = new Result(
                List.of(), List.of(), List.of(), List.of()
        );

        public Result {
            airCells = List.copyOf(airCells);
            wallCells = List.copyOf(wallCells);
            floorCells = List.copyOf(floorCells);
            errors = List.copyOf(errors);
        }

        public boolean isSuccess() {
            return errors.isEmpty();
        }

        public int size() {
            return airCells.size() + wallCells.size() + floorCells.size();
        }
    }
}
