package dev.huskuraft.effortless.client.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.RailBlockStateResolver;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralMaterialClassifier;
import dev.huskuraft.universal.api.core.BlockState;

/**
 * Resolves placement-sensitive block states without depending on loader or
 * Minecraft implementation classes. All legal states of the selected block
 * already exist in Universal's stock block-state registry, so the resolver
 * selects the closest variant by stable property names.
 */
public final class TreeBlockStateResolver {

    private static final Map<Object, List<BlockState>> VARIANTS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private TreeBlockStateResolver() {
    }

    public static BlockState resolve(
            BlockState original,
            TreeCell cell,
            Set<GridPosition> occupied
    ) {
        return resolve(
                original, cell, occupied, Map.of(), Map.of()
        );
    }

    public static BlockState resolve(
            BlockState original,
            TreeCell cell,
            Set<GridPosition> occupied,
            Map<GridPosition, TreeCell> cells,
            Map<GridPosition, BlockState> rawStates
    ) {
        var geometry = new HashMap<GridPosition, StructuralGeometry>();
        cells.forEach((position, value) ->
                geometry.put(position, value.geometry())
        );
        return resolveGeometry(
                original, cell.position(), cell.geometry(), occupied,
                geometry, rawStates
        );
    }

    /** Generic entry point reusable by any explicit-cell generator. */
    public static BlockState resolveGeometry(
            BlockState original,
            GridPosition position,
            StructuralGeometry geometry,
            Set<GridPosition> occupied,
            Map<GridPosition, StructuralGeometry> geometries,
            Map<GridPosition, BlockState> rawStates
    ) {
        if (original == null || original.isAir()) {
            return original;
        }
        var variants = variants(original);
        if (variants.size() <= 1) {
            return original;
        }
        return resolveGeometryFromVariants(
                original, position, geometry, occupied,
                geometries, rawStates, variants
        );
    }

    static BlockState resolveFromVariants(
            BlockState original,
            TreeCell cell,
            Set<GridPosition> occupied,
            Map<GridPosition, TreeCell> cells,
            Map<GridPosition, BlockState> rawStates,
            List<BlockState> variants
    ) {
        var geometries = new HashMap<GridPosition, StructuralGeometry>();
        cells.forEach((position, value) ->
                geometries.put(position, value.geometry())
        );
        return resolveGeometryFromVariants(
                original, cell.position(), cell.geometry(), occupied,
                geometries, rawStates, variants
        );
    }

    static BlockState resolveGeometryFromVariants(
            BlockState original,
            GridPosition position,
            StructuralGeometry geometry,
            Set<GridPosition> occupied,
            Map<GridPosition, StructuralGeometry> geometries,
            Map<GridPosition, BlockState> rawStates,
            List<BlockState> variants
    ) {
        var available = availableValues(variants);
        var desired = desiredProperties(
                geometry, position, occupied, rawStates, available
        );
        if (available.containsKey("shape")
                && available.containsKey("half")
                && available.containsKey("facing")) {
            desired = new LinkedHashMap<>(desired);
            desired.put(
                    "shape",
                    stairShape(
                            position, geometry, available,
                            geometries, rawStates
                    )
            );
        }
        return closestVariant(original, desired, variants);
    }

    static Map<String, String> desiredProperties(
            TreeCell cell,
            Set<GridPosition> occupied,
            Map<String, Set<String>> available
    ) {
        return desiredProperties(
                cell.geometry(), cell.position(), occupied,
                Map.of(), available
        );
    }

    static Map<String, String> desiredProperties(
            StructuralGeometry geometry,
            GridPosition position,
            Set<GridPosition> occupied,
            Map<GridPosition, BlockState> rawStates,
            Map<String, Set<String>> available
    ) {
        var desired = new LinkedHashMap<String, String>();

        putIfAvailable(
                desired, available, "axis",
                dominantAxis(geometry)
        );
        putIfAvailable(
                desired, available, "horizontal_axis",
                horizontalAxis(geometry)
        );
        putIfAvailable(
                desired, available, "facing",
                facing(geometry, available.getOrDefault("facing", Set.of()))
        );

        boolean stair = available.containsKey("shape")
                && available.containsKey("half")
                && available.containsKey("facing");
        if (stair) {
            putIfAvailable(desired, available, "shape", "straight");
            putIfAvailable(
                    desired, available, "half",
                    slabHalf(geometry)
            );
        } else if (available.containsKey("type")) {
            // Slabs follow a falling limb on their upper half so descending
            // branch silhouettes remain visually continuous.
            putIfAvailable(
                    desired, available, "type",
                    slabHalf(geometry)
            );
        }

        if (available.containsKey("shape") && !stair) {
            RailBlockStateResolver.shape(
                    geometry, available.get("shape")
            ).ifPresent(shape -> desired.put("shape", shape));
        }

        // Player-built foliage must not decay after the snapshot is applied.
        putIfAvailable(desired, available, "persistent", "true");

        if (!available.containsKey("persistent")) {
            addConnections(
                    desired, available, position, occupied, rawStates
            );
        }

        return Map.copyOf(desired);
    }

    private static String stairShape(
            GridPosition position,
            StructuralGeometry geometry,
            Map<String, Set<String>> available,
            Map<GridPosition, StructuralGeometry> geometries,
            Map<GridPosition, BlockState> rawStates
    ) {
        if (geometries.isEmpty() || rawStates.isEmpty()) {
            return "straight";
        }
        String facing = facing(
                geometry,
                available.getOrDefault("facing", Set.of())
        );
        int[] direction = horizontalDirection(facing);
        if (direction == null) {
            return "straight";
        }
        var front = position.offset(direction[0], 0, direction[1]);
        String turn = stairTurn(
                facing, geometries.get(front), rawStates.get(front)
        );
        if (turn != null) {
            return "outer_" + turn;
        }
        var back = position.offset(-direction[0], 0, -direction[1]);
        turn = stairTurn(
                facing, geometries.get(back), rawStates.get(back)
        );
        return turn == null ? "straight" : "inner_" + turn;
    }

    private static String stairTurn(
            String facing,
            StructuralGeometry neighbor,
            BlockState neighborState
    ) {
        if (neighbor == null || neighborState == null) {
            return null;
        }
        var properties = properties(neighborState);
        if (!properties.containsKey("shape")
                || !properties.containsKey("half")
                || !properties.containsKey("facing")) {
            return null;
        }
        String neighborFacing = facing(
                neighbor,
                Set.of("north", "south", "east", "west")
        );
        int[] current = horizontalDirection(facing);
        int[] next = horizontalDirection(neighborFacing);
        if (current == null || next == null
                || current[0] == next[0] && current[1] == next[1]
                || current[0] == -next[0] && current[1] == -next[1]) {
            return null;
        }
        int cross = current[0] * next[1] - current[1] * next[0];
        return cross < 0 ? "left" : "right";
    }

    private static int[] horizontalDirection(String facing) {
        return switch (facing) {
            case "north" -> new int[]{0, -1};
            case "south" -> new int[]{0, 1};
            case "west" -> new int[]{-1, 0};
            case "east" -> new int[]{1, 0};
            default -> null;
        };
    }

    private static void addConnections(
            Map<String, String> desired,
            Map<String, Set<String>> available,
            GridPosition position,
            Set<GridPosition> occupied,
            Map<GridPosition, BlockState> rawStates
    ) {
        boolean north = connects(
                position.offset(0, 0, -1), occupied, rawStates
        );
        boolean south = connects(
                position.offset(0, 0, 1), occupied, rawStates
        );
        boolean west = connects(
                position.offset(-1, 0, 0), occupied, rawStates
        );
        boolean east = connects(
                position.offset(1, 0, 0), occupied, rawStates
        );
        putConnection(desired, available, "north", north);
        putConnection(desired, available, "south", south);
        putConnection(desired, available, "west", west);
        putConnection(desired, available, "east", east);

        if (available.containsKey("up")) {
            boolean straight = (north && south && !east && !west)
                    || (east && west && !north && !south);
            boolean blockAbove = occupied.contains(position.offset(0, 1, 0));
            putIfAvailable(
                    desired, available, "up",
                    Boolean.toString(!straight || blockAbove)
            );
        }
    }

    private static boolean connects(
            GridPosition neighbor,
            Set<GridPosition> occupied,
            Map<GridPosition, BlockState> rawStates
    ) {
        if (!occupied.contains(neighbor)) {
            return false;
        }
        var state = rawStates.get(neighbor);
        return state == null
                || StructuralMaterialClassifier.canSupportConnector(state);
    }

    private static void putConnection(
            Map<String, String> desired,
            Map<String, Set<String>> available,
            String property,
            boolean connected
    ) {
        var values = available.get(property);
        if (values == null) {
            return;
        }
        if (values.contains("true") && values.contains("false")) {
            desired.put(property, Boolean.toString(connected));
        } else if (values.contains("none") && values.contains("low")) {
            desired.put(property, connected ? "low" : "none");
        }
    }

    private static String dominantAxis(StructuralGeometry geometry) {
        double x = Math.abs(geometry.tangentX());
        double y = Math.abs(geometry.tangentY());
        double z = Math.abs(geometry.tangentZ());
        if (y >= x && y >= z) {
            return "y";
        }
        return x >= z ? "x" : "z";
    }

    private static String horizontalAxis(StructuralGeometry geometry) {
        return Math.abs(geometry.tangentX())
                >= Math.abs(geometry.tangentZ())
                ? "x" : "z";
    }

    private static String facing(
            StructuralGeometry geometry,
            Set<String> allowed
    ) {
        boolean useNormal = geometry.depth() >= 0.55
                && Math.hypot(geometry.normalX(), geometry.normalZ()) > 0.15;
        double x = useNormal ? geometry.normalX() : geometry.tangentX();
        double y = useNormal ? geometry.normalY() : geometry.tangentY();
        double z = useNormal ? geometry.normalZ() : geometry.tangentZ();
        if (allowed.contains("up") && allowed.contains("down")
                && Math.abs(y) >= Math.abs(x)
                && Math.abs(y) >= Math.abs(z)) {
            return y < 0.0 ? "down" : "up";
        }
        if (Math.abs(x) >= Math.abs(z)) {
            return x < 0.0 ? "west" : "east";
        }
        return z < 0.0 ? "north" : "south";
    }

    private static String slabHalf(StructuralGeometry geometry) {
        if (geometry.depth() >= 0.55
                && Math.abs(geometry.normalY()) > 0.15) {
            return geometry.normalY() < 0.0 ? "top" : "bottom";
        }
        return geometry.tangentY() < -0.18 ? "top" : "bottom";
    }

    private static void putIfAvailable(
            Map<String, String> desired,
            Map<String, Set<String>> available,
            String property,
            String value
    ) {
        var values = available.get(property);
        if (value != null && values != null && values.contains(value)) {
            desired.put(property, value);
        }
    }

    private static BlockState closestVariant(
            BlockState original,
            Map<String, String> desired,
            List<BlockState> variants
    ) {
        var originalProperties = properties(original);
        BlockState best = original;
        int bestScore = Integer.MIN_VALUE;
        for (var candidate : variants) {
            var values = properties(candidate);
            boolean matches = desired.entrySet().stream().allMatch(entry ->
                    entry.getValue().equals(values.get(entry.getKey()))
            );
            if (!matches) {
                continue;
            }
            int score = 0;
            for (var entry : originalProperties.entrySet()) {
                if (!desired.containsKey(entry.getKey())
                        && entry.getValue().equals(
                                values.get(entry.getKey())
                        )) {
                    score++;
                }
            }
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static Map<String, Set<String>> availableValues(
            List<BlockState> variants
    ) {
        var values = new LinkedHashMap<String, Set<String>>();
        for (var state : variants) {
            properties(state).forEach((name, value) ->
                    values.computeIfAbsent(
                            name, ignored -> new LinkedHashSet<>()
                    ).add(value)
            );
        }
        return values;
    }

    private static Map<String, String> properties(BlockState state) {
        var values = new HashMap<String, String>();
        state.getPropertiesMap().forEach((property, value) ->
                values.put(property.getName(), property.getName(value))
        );
        return values;
    }

    private static List<BlockState> variants(BlockState original) {
        Object block = original.getBlock().refs();
        var cached = VARIANTS.get(block);
        if (cached != null) {
            return cached;
        }
        var found = new ArrayList<BlockState>();
        for (var state : BlockState.REGISTRY) {
            if (state != null && state.getBlock().refs() == block) {
                found.add(state);
            }
        }
        var result = found.isEmpty()
                ? List.of(original)
                : List.copyOf(found);
        VARIANTS.put(block, result);
        return result;
    }
}
