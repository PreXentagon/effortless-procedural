package dev.huskuraft.effortless.client.tree;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.StableRandom;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;
import dev.huskuraft.effortless.client.pattern.procedural.VoxelPath;
import dev.huskuraft.effortless.client.road.RoadCell;
import dev.huskuraft.effortless.client.road.RoadPoint;
import dev.huskuraft.effortless.client.road.RoadSpline;
import dev.huskuraft.effortless.client.road.RoadVoxelizer;

/**
 * Deterministically sweeps round trunk/branch profiles and optional spherical
 * foliage around a custom tree skeleton.
 */
public final class TreeVoxelizer {

    private TreeVoxelizer() {
    }

    /**
     * Builds a complete deterministic tree from one placement anchor. The
     * generated skeleton is curved and tapered before foliage density is
     * evaluated, so preview and placement consume the same explicit cells.
     */
    public static Result generate(
            RoadPoint anchor,
            TreeGenerationConfig config,
            long presetSeed,
            int visibleVariant,
            int maximumCells
    ) {
        var errors = new ArrayList<>(config.validate());
        if (maximumCells < 1) {
            errors.add("Tree cell limit must be positive");
        }
        if (!errors.isEmpty()) {
            return Result.failure(errors);
        }
        var variant = TreeVariant.resolve(
                config,
                presetSeed,
                anchor,
                visibleVariant
        );
        return generateGeometry(
                anchor,
                trunkPath(anchor, variant),
                List.of(),
                config,
                variant,
                maximumCells
        );
    }

    /**
     * Generates an archetype tree whose trunk and selected primary branches
     * are steered by in-world guide points. The species algorithms still own
     * taper, remaining branch distribution, forks, roots, and foliage.
     */
    public static Result generateGuided(
            List<RoadPoint> points,
            TreeGenerationConfig config,
            long presetSeed,
            int visibleVariant,
            int maximumCells
    ) {
        var errors = new ArrayList<>(config.validate());
        if (points.size() < 2) {
            errors.add("A guided tree requires a base and crown point");
        }
        if (maximumCells < 1) {
            errors.add("Tree cell limit must be positive");
        }
        if (!errors.isEmpty()) {
            return Result.failure(errors);
        }
        var anchor = points.get(0);
        var crown = points.get(1);
        if (anchor.distance(crown) < 0.5) {
            return Result.failure(List.of(
                    "Tree base and crown must be at least half a block apart"
            ));
        }
        var variant = TreeVariant.resolve(
                config,
                presetSeed,
                anchor,
                visibleVariant
        );
        return generateGeometry(
                anchor,
                guidedTrunkPath(anchor, crown, variant),
                points.size() <= 2
                        ? List.of()
                        : List.copyOf(points.subList(2, points.size())),
                config,
                variant,
                maximumCells
        );
    }

    private static Result generateGeometry(
            RoadPoint anchor,
            List<RoadPoint> trunk,
            List<RoadPoint> branchGuides,
            TreeGenerationConfig config,
            TreeVariant variant,
            int maximumCells
    ) {
        var cells = new LinkedHashMap<GridPosition, Candidate>();
        var limbs = new ArrayList<Limb>();
        for (int index = 1; index < trunk.size(); index++) {
            double startProgress = (double) (index - 1)
                    / (trunk.size() - 1);
            double endProgress = (double) index / (trunk.size() - 1);
            double startRadius = trunkRadius(variant, startProgress);
            double endRadius = trunkRadius(variant, endProgress);
            if (!sweepTapered(
                    cells,
                    trunk.get(index - 1),
                    trunk.get(index),
                    startRadius,
                    endRadius,
                    TreeCell.Role.TRUNK,
                    startProgress,
                    endProgress,
                    maximumCells
            )) {
                return tooLarge(maximumCells);
            }
            limbs.add(new Limb(
                    trunk.get(index - 1),
                    trunk.get(index),
                    TreeCell.Role.TRUNK
            ));
        }

        var terminals = new ArrayList<Terminal>();
        if (!branches(
                cells,
                limbs,
                terminals,
                trunk,
                branchGuides,
                config,
                variant,
                maximumCells
        )) {
            return tooLarge(maximumCells);
        }
        if (!roots(
                cells,
                limbs,
                anchor,
                trunk,
                config,
                variant,
                maximumCells
        )) {
            return tooLarge(maximumCells);
        }
        if (!foliage(
                cells,
                terminals,
                trunk,
                config,
                variant,
                maximumCells
        )) {
            return tooLarge(maximumCells);
        }
        pruneUnsupportedCanopy(cells);
        markJunctions(cells);
        var ordered = cells.values().stream()
                .map(Candidate::cell)
                .sorted(Comparator.comparing(TreeCell::position))
                .toList();
        return Result.success(ordered, limbs);
    }

    private static List<RoadPoint> trunkPath(
            RoadPoint anchor,
            TreeVariant variant
    ) {
        int segments = Math.max(6, variant.height() * 2);
        var points = new ArrayList<RoadPoint>(segments + 1);
        double phase = variant.rotation();
        for (int index = 0; index <= segments; index++) {
            double progress = (double) index / segments;
            double envelope = progress * progress * (3.0 - 2.0 * progress);
            double bend = variant.trunkBend() * variant.height() * 0.18;
            double x = Math.sin(phase + progress * 2.4)
                    * bend * envelope;
            double z = Math.cos(phase * 0.73 + progress * 2.0)
                    * bend * envelope;
            x += (variant.unit(200 + index) - 0.5)
                    * variant.trunkBend() * 0.35 * progress;
            z += (variant.unit(400 + index) - 0.5)
                    * variant.trunkBend() * 0.35 * progress;
            points.add(anchor.add(x, progress * variant.height(), z));
        }
        return List.copyOf(points);
    }

    private static List<RoadPoint> guidedTrunkPath(
            RoadPoint base,
            RoadPoint crown,
            TreeVariant variant
    ) {
        double length = base.distance(crown);
        int segments = Math.max(6, (int) Math.ceil(length * 2.0));
        var direction = crown.sub(base).normalize();
        var reference = Math.abs(direction.y()) < 0.92
                ? new RoadPoint(0.0, 1.0, 0.0)
                : new RoadPoint(1.0, 0.0, 0.0);
        var side = cross(direction, reference).normalize();
        var forward = cross(side, direction).normalize();
        var control = RoadPoint.lerp(base, crown, 0.5)
                .add(side.mul(
                        Math.sin(variant.rotation())
                                * variant.trunkBend() * length * 0.12
                ))
                .add(forward.mul(
                        Math.cos(variant.rotation())
                                * variant.trunkBend() * length * 0.12
                ));
        var points = new ArrayList<RoadPoint>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            double progress = (double) index / segments;
            points.add(quadratic(base, control, crown, progress));
        }
        return List.copyOf(points);
    }

    private static double trunkRadius(TreeVariant variant, double progress) {
        double base = voxelRadius(variant.baseRadius());
        double tip = voxelRadius(variant.tipRadius());
        double taper = Math.pow(progress, 0.78);
        double radius = base + (tip - base) * taper;
        double flare = progress < 0.16
                ? (1.0 - progress / 0.16) * base * 0.55
                : 0.0;
        return Math.max(0.42, radius + flare);
    }

    private static boolean branches(
            LinkedHashMap<GridPosition, Candidate> cells,
            List<Limb> limbs,
            List<Terminal> terminals,
            List<RoadPoint> trunk,
            List<RoadPoint> branchGuides,
            TreeGenerationConfig config,
            TreeVariant variant,
            int maximumCells
    ) {
        int branchCount = Math.max(
                variant.branchCount(),
                branchGuides.size()
        );
        if (branchCount <= 0) {
            terminals.add(new Terminal(
                    trunk.get(trunk.size() - 1), 1.0, 0
            ));
            return true;
        }
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        var branchRole = config.archetype().isPalm()
                ? TreeCell.Role.CANOPY
                : TreeCell.Role.BRANCH;
        for (int branch = 0; branch < branchCount; branch++) {
            double unit = (branch + 0.5) / branchCount;
            double progress = branchProgress(config.archetype(), unit);
            if (!config.styleLock()) {
                progress = clamp01(
                        progress
                                + (variant.unit(500 + branch) - 0.5) * 0.18
                );
            }
            int trunkIndex = Math.max(
                    1,
                    Math.min(
                            trunk.size() - 2,
                            (int) Math.round(progress * (trunk.size() - 1))
                    )
            );
            var root = trunk.get(trunkIndex);
            double angle = variant.rotation() + branch * goldenAngle
                    + (variant.unit(600 + branch) - 0.5) * 0.7;
            double crownProfile = crownProfile(
                    config.archetype(), progress
            );
            if (!config.styleLock()) {
                crownProfile *= 0.72
                        + variant.unit(700 + branch) * 0.56;
            }
            double length = Math.max(
                    1.5,
                    variant.crownRadius() * variant.branchLengthScale()
                            * crownProfile
                            * (0.78 + variant.unit(800 + branch) * 0.44)
            );
            double lift = branchLift(
                    config.archetype(),
                    length,
                    progress,
                    variant.branchDroop()
            );
            if (!config.styleLock()) {
                lift += (variant.unit(900 + branch) - 0.5)
                        * length * 0.34;
            }
            var radial = new RoadPoint(
                    Math.cos(angle), 0.0, Math.sin(angle)
            );
            boolean guided = branch < branchGuides.size();
            var end = guided
                    ? branchGuides.get(branch)
                    : root.add(
                            radial.x() * length,
                            lift,
                            radial.z() * length
                    );
            if (guided) {
                length = Math.max(1.0, root.distance(end));
                var horizontal = new RoadPoint(
                        end.x() - root.x(), 0.0, end.z() - root.z()
                );
                if (horizontal.length() > 0.001) {
                    radial = horizontal.normalize();
                }
            }
            double side = (variant.unit(1000 + branch) - 0.5)
                    * length * 0.22;
            var normal = new RoadPoint(-radial.z(), 0.0, radial.x());
            var control = RoadPoint.lerp(root, end, 0.48).add(
                    normal.x() * side,
                    length * (0.18 - variant.branchDroop() * 0.08),
                    normal.z() * side
            );
            int curveSegments = Math.max(3, (int) Math.ceil(length * 0.7));
            var previous = root;
            double rootRadius = Math.max(
                    0.48,
                    trunkRadius(variant, progress) * 0.58
            );
            for (int segment = 1; segment <= curveSegments; segment++) {
                double t = (double) segment / curveSegments;
                var point = quadratic(root, control, end, t);
                double startRadius = Math.max(
                        0.40,
                        rootRadius * Math.pow(1.0 - (t - 1.0 / curveSegments), 0.9)
                );
                double endRadius = Math.max(
                        0.38,
                        rootRadius * Math.pow(1.0 - t, 0.9)
                );
                if (!sweepTapered(
                        cells, previous, point, startRadius, endRadius,
                        branchRole,
                        (double) (segment - 1) / curveSegments,
                        t,
                        maximumCells
                )) {
                    return false;
                }
                limbs.add(new Limb(previous, point, branchRole));
                previous = point;
            }
            terminals.add(new Terminal(end, progress, branch));

            if (shouldFork(config.archetype(), branch, branchCount)
                    || (!config.styleLock() && branch % 5 == 0)) {
                for (int fork = 0; fork < 2; fork++) {
                    double forkAngle = angle + (fork == 0 ? -0.55 : 0.55);
                    double forkLength = length * (0.30
                            + variant.unit(1200 + branch * 2 + fork) * 0.18);
                    var forkEnd = end.add(
                            Math.cos(forkAngle) * forkLength,
                            forkLength * (0.20 - variant.branchDroop() * 0.20),
                            Math.sin(forkAngle) * forkLength
                    );
                    if (!sweepTapered(
                            cells, end, forkEnd,
                            Math.max(0.4, rootRadius * 0.42), 0.38,
                            TreeCell.Role.BRANCH, 0.0, 1.0,
                            maximumCells
                    )) {
                        return false;
                    }
                    limbs.add(new Limb(end, forkEnd, TreeCell.Role.BRANCH));
                    terminals.add(new Terminal(
                            forkEnd,
                            progress,
                            branch * 2 + fork + branchCount
                    ));
                }
            }
            if (shouldRiseAsLeader(
                    config.archetype(), branch, branchCount
            )) {
                double leaderLength = length * (0.48
                        + variant.unit(1350 + branch) * 0.25);
                var leaderEnd = end.add(
                        radial.x() * leaderLength * 0.20
                                + normal.x() * leaderLength * 0.14,
                        leaderLength * (0.72
                                + variant.unit(1380 + branch) * 0.24),
                        radial.z() * leaderLength * 0.20
                                + normal.z() * leaderLength * 0.14
                );
                if (!sweepTapered(
                        cells, end, leaderEnd,
                        Math.max(0.42, rootRadius * 0.50), 0.38,
                        TreeCell.Role.BRANCH, 0.0, 1.0, maximumCells
                )) {
                    return false;
                }
                limbs.add(new Limb(
                        end, leaderEnd, TreeCell.Role.BRANCH
                ));
                terminals.add(new Terminal(
                        leaderEnd,
                        Math.min(1.0, progress + 0.18),
                        branchCount * 5 + branch
                ));
            }
        }
        terminals.add(new Terminal(
                trunk.get(trunk.size() - 1), 1.0, branchCount * 4 + 1
        ));
        return true;
    }

    private static boolean roots(
            LinkedHashMap<GridPosition, Candidate> cells,
            List<Limb> limbs,
            RoadPoint anchor,
            List<RoadPoint> trunk,
            TreeGenerationConfig config,
            TreeVariant variant,
            int maximumCells
    ) {
        for (int root = 0; root < variant.rootCount(); root++) {
            double angle = variant.rotation()
                    + root * Math.PI * 2.0 / Math.max(1, variant.rootCount())
                    + (variant.unit(1500 + root) - 0.5) * 0.5;
            double length = Math.max(
                    1.5,
                    variant.baseRadius() * variant.rootLengthScale()
                            * (1.4 + variant.unit(1600 + root))
            );
            boolean stilted = config.archetype() == TreeArchetype.MANGROVE;
            var start = stilted
                    ? trunk.get(Math.min(
                            trunk.size() - 1,
                            Math.max(1, (int) Math.round(
                                    trunk.size() * (0.16
                                            + (root % 4) * 0.055)
                            ))
                    ))
                    : anchor;
            var middle = RoadPoint.lerp(start, anchor, stilted ? 0.55 : 0.0)
                    .add(
                    Math.cos(angle) * length * 0.48,
                    stilted ? -0.08 : -0.12,
                    Math.sin(angle) * length * 0.48
                    );
            var end = anchor.add(
                    Math.cos(angle) * length,
                    -0.75 - variant.unit(1700 + root) * 0.8,
                    Math.sin(angle) * length
            );
            var previous = start;
            for (int segment = 1; segment <= 3; segment++) {
                double t = segment / 3.0;
                var point = quadratic(start, middle, end, t);
                double startRadius = Math.max(
                        0.42,
                        trunkRadius(variant, 0.0) * (0.58 - t * 0.12)
                );
                double endRadius = Math.max(0.38, startRadius * 0.55);
                if (!sweepTapered(
                        cells, previous, point, startRadius, endRadius,
                        TreeCell.Role.ROOT,
                        (double) (segment - 1) / 3.0,
                        t,
                        maximumCells
                )) {
                    return false;
                }
                limbs.add(new Limb(previous, point, TreeCell.Role.ROOT));
                previous = point;
            }
        }
        return true;
    }

    private static boolean foliage(
            LinkedHashMap<GridPosition, Candidate> cells,
            List<Terminal> terminals,
            List<RoadPoint> trunk,
            TreeGenerationConfig config,
            TreeVariant variant,
            int maximumCells
    ) {
        if (config.archetype().isPalm()) {
            for (var terminal : terminals) {
                if (!foliageEllipsoid(
                        cells, terminal.point(), 1.45, 0.75, 1.45,
                        terminal.progress(), variant, maximumCells
                )) {
                    return false;
                }
            }
            return foliageEllipsoid(
                    cells, trunk.get(trunk.size() - 1),
                    2.0, 1.35, 2.0, 1.0, variant, maximumCells
            );
        }
        if (config.archetype().isConifer()) {
            boolean pine = config.archetype() == TreeArchetype.PINE;
            int layers = Math.max(
                    4,
                    variant.height() / (pine ? 4 : 3)
            );
            for (int layer = 0; layer < layers; layer++) {
                double baseProgress = pine ? 0.48 : 0.20;
                double progress = baseProgress
                        + (0.96 - baseProgress) * layer
                        / Math.max(1, layers - 1);
                int index = Math.min(
                        trunk.size() - 1,
                        (int) Math.round(progress * (trunk.size() - 1))
                );
                double normalized = (progress - baseProgress)
                        / Math.max(0.01, 1.0 - baseProgress);
                double radius = variant.crownRadius()
                        * Math.pow(1.0 - normalized, pine ? 0.58 : 0.78)
                        + 0.9;
                if (pine) {
                    radius *= 0.82 + 0.18 * Math.sin(layer * 2.7);
                }
                if (!config.styleLock()) {
                    radius *= 0.72 + variant.unit(2500 + layer) * 0.56;
                }
                if (!foliageEllipsoid(
                        cells, trunk.get(index), radius, 1.2, radius,
                        progress, variant, maximumCells
                )) {
                    return false;
                }
            }
            return foliageEllipsoid(
                    cells,
                    trunk.get(trunk.size() - 1),
                    1.6,
                    2.5,
                    1.6,
                    1.0,
                    variant,
                    maximumCells
            );
        }
        for (var terminal : terminals) {
            double radial = terminal.progress() < 0.55 ? 0.72 : 1.0;
            double rx = Math.max(
                    1.2,
                    variant.crownRadius() * 0.42 * radial
            );
            double ry = Math.max(1.2, variant.crownHeight() * 0.28);
            double rz = rx;
            if (!config.styleLock()) {
                rx *= 0.72 + variant.unit(2700 + terminal.index()) * 0.56;
                ry *= 0.72 + variant.unit(2900 + terminal.index()) * 0.56;
                rz *= 0.72 + variant.unit(3100 + terminal.index()) * 0.56;
            }
            if (config.archetype().isUmbrella()) {
                rx *= 1.25;
                rz *= 1.25;
                ry *= 0.48;
            } else if (config.archetype().isPalm()) {
                rx *= 0.72;
                rz *= 0.72;
                ry *= 0.55;
            } else if (config.archetype().isWeeping()) {
                ry *= 0.75;
            }
            if (!foliageEllipsoid(
                    cells, terminal.point(), rx, ry, rz,
                    terminal.progress(), variant, maximumCells
            )) {
                return false;
            }
            if (config.archetype().isWeeping()) {
                int strands = 2 + Math.floorMod(terminal.index(), 2);
                for (int strand = 0; strand < strands; strand++) {
                    int hanging = Math.max(
                            2,
                            (int) Math.round(
                                    variant.crownHeight()
                                            * (0.40
                                            + Math.floorMod(
                                                    terminal.index() + strand,
                                                    4
                                            ) * 0.10)
                            )
                    );
                    double angle = (terminal.index() * 2.399
                            + strand * Math.PI * 2.0 / strands);
                    var strandStart = terminal.point().add(
                            Math.cos(angle) * Math.max(0.8, rx * 0.55),
                            -Math.max(0.0, ry * 0.12),
                            Math.sin(angle) * Math.max(0.8, rz * 0.55)
                    );
                    if (!connectSpine(
                            cells, terminal.point(), strandStart,
                            TreeCell.Role.CANOPY, terminal.progress(),
                            terminal.progress(), maximumCells
                    ) || !hangingFoliage(
                            cells, strandStart, hanging,
                            terminal.progress(), variant,
                            terminal.index() * 4 + strand, maximumCells
                    )) {
                        return false;
                    }
                }
            }
        }
        var crown = trunk.get(trunk.size() - 1).add(
                0.0,
                -variant.crownHeight() * 0.18,
                0.0
        );
        double crownY = config.archetype().isUmbrella()
                ? Math.max(1.2, variant.crownHeight() * 0.24)
                : Math.max(1.5, variant.crownHeight() * 0.45);
        return foliageEllipsoid(
                cells, crown, variant.crownRadius() * 0.75, crownY,
                variant.crownRadius() * 0.75, 1.0, variant, maximumCells
        );
    }

    private static boolean hangingFoliage(
            LinkedHashMap<GridPosition, Candidate> cells,
            RoadPoint start,
            int length,
            double path,
            TreeVariant variant,
            int ordinal,
            int maximumCells
    ) {
        var previous = start;
        double phase = variant.unit(1800 + ordinal) * Math.PI * 2.0;
        for (int offset = 1; offset <= length; offset++) {
            double sway = Math.min(1.35, offset * 0.11);
            var point = start.add(
                    Math.sin(phase + offset * 0.42) * sway,
                    -offset,
                    Math.cos(phase * 0.73 + offset * 0.37) * sway
            );
            if (!connectSpine(
                    cells, previous, point,
                    TreeCell.Role.CANOPY, path, path, maximumCells
            )) {
                return false;
            }
            previous = point;

            // Sparse side leaves add the irregular silhouette while the
            // strand itself remains continuous and attached.
            if (variant.unit(2300 + ordinal * 31 + offset)
                    <= variant.foliageDensity() * 0.55) {
                int sideX = Math.abs(Math.sin(phase + offset))
                        >= Math.abs(Math.cos(phase + offset)) ? 1 : 0;
                int sideZ = sideX == 0 ? 1 : 0;
                if (Math.sin(phase + offset) < 0.0) {
                    sideX = -sideX;
                }
                if (Math.cos(phase + offset) < 0.0) {
                    sideZ = -sideZ;
                }
                var position = grid(point).offset(sideX, 0, sideZ);
                merge(cells, new TreeCell(
                        position, path, 1.0,
                        (double) offset / Math.max(1, length),
                        TreeCell.Role.CANOPY,
                        0.0, -1.0, 0.0
                ), offset + 0.25);
            }
            if (cells.size() > maximumCells) {
                return false;
            }
        }
        return true;
    }

    private static boolean foliageEllipsoid(
            LinkedHashMap<GridPosition, Candidate> cells,
            RoadPoint center,
            double radiusX,
            double radiusY,
            double radiusZ,
            double path,
            TreeVariant variant,
            int maximumCells
    ) {
        int minX = (int) Math.floor(center.x() - radiusX);
        int maxX = (int) Math.floor(center.x() + radiusX);
        int minY = (int) Math.floor(center.y() - radiusY);
        int maxY = (int) Math.floor(center.y() + radiusY);
        int minZ = (int) Math.floor(center.z() - radiusZ);
        int maxZ = (int) Math.floor(center.z() + radiusZ);
        long estimate = (long) (maxX - minX + 1)
                * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (estimate > Math.max(1L, maximumCells) * 48L) {
            return false;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = (x + 0.5 - center.x()) / radiusX;
                    double dy = (y + 0.5 - center.y()) / radiusY;
                    double dz = (z + 0.5 - center.z()) / radiusZ;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > 1.0) {
                        continue;
                    }
                    var position = new GridPosition(x, y, z);
                    double noise = StableRandom.valueNoise(
                            variant.seed(),
                            x * variant.foliageNoiseFrequency(),
                            y * variant.foliageNoiseFrequency(),
                            z * variant.foliageNoiseFrequency(),
                            0x464F4C494147454CL
                    );
                    double edge = clamp01((1.0 - distance) * 1.45 + 0.18);
                    double density = clamp01(
                            variant.foliageDensity()
                                    * (0.62 + edge * 0.55)
                                    + (noise - 0.5) * 0.34
                    );
                    double selection = StableRandom.positionUnit(
                            variant.seed(), position, 0, 0,
                            0x4C45414644454E53L
                    );
                    if (selection > density) {
                        continue;
                    }
                    merge(cells, new TreeCell(
                            position, clamp01(path), 1.0, distance,
                            TreeCell.Role.CANOPY
                    ), distance * distance);
                    if (cells.size() > maximumCells) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean sweepTapered(
            LinkedHashMap<GridPosition, Candidate> cells,
            RoadPoint start,
            RoadPoint end,
            double startRadius,
            double endRadius,
            TreeCell.Role role,
            double pathStart,
            double pathEnd,
            int maximumCells
    ) {
        double length = start.distance(end);
        int samples = Math.max(1, (int) Math.ceil(length / 0.32));
        double maximumRadius = Math.max(startRadius, endRadius);
        long diameter = (long) Math.ceil(maximumRadius * 2.0) + 2L;
        if ((samples + 1L) * diameter * diameter * diameter
                > Math.max(1L, maximumCells) * 96L) {
            return false;
        }
        if (!connectSpine(
                cells, start, end, role, pathStart, pathEnd, maximumCells
        )) {
            return false;
        }
        var tangent = end.sub(start).normalize();
        double slope = clamp01(
                Math.abs(endRadius - startRadius)
                        / Math.max(0.001, length) * 2.0
        );
        double taper = (startRadius - endRadius)
                / Math.max(0.001, length);
        for (int sample = 0; sample <= samples; sample++) {
            double progress = (double) sample / samples;
            var center = RoadPoint.lerp(start, end, progress);
            double radius = startRadius
                    + (endRadius - startRadius) * progress;
            double path = pathStart + (pathEnd - pathStart) * progress;
            int minX = (int) Math.floor(center.x() - radius);
            int maxX = (int) Math.floor(center.x() + radius);
            int minY = (int) Math.floor(center.y() - radius);
            int maxY = (int) Math.floor(center.y() + radius);
            int minZ = (int) Math.floor(center.z() - radius);
            int maxZ = (int) Math.floor(center.z() + radius);
            double radiusSquared = radius * radius + 0.08;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        var voxelCenter = new RoadPoint(
                                x + 0.5, y + 0.5, z + 0.5
                        );
                        double distanceSquared =
                                voxelCenter.distanceSquared(center);
                        if (distanceSquared > radiusSquared) {
                            continue;
                        }
                        double distance = Math.sqrt(distanceSquared);
                        var radial = distance <= 1.0e-9
                                ? new RoadPoint(0.0, 0.0, 0.0)
                                : voxelCenter.sub(center).mul(1.0 / distance);
                        var surfaceNormal = radial.add(tangent.mul(taper));
                        var geometry = new StructuralGeometry(
                                clamp01(path), lateral(role),
                                clamp01(distance / Math.max(0.4, radius)),
                                normalizedThickness(radius),
                                slope,
                                StructuralGeometry.tipFromPath(path),
                                0.0,
                                tangent.x(), tangent.y(), tangent.z(),
                                surfaceNormal.x(), surfaceNormal.y(),
                                surfaceNormal.z()
                        );
                        merge(cells, new TreeCell(
                                new GridPosition(x, y, z), role, geometry
                        ), distanceSquared);
                        if (cells.size() > maximumCells) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Inserts a deterministic six-connected centerline. The volume sweep
     * supplies thickness, while this spine guarantees that diagonal or thin
     * limbs never contain invisible gaps.
     */
    private static boolean connectSpine(
            LinkedHashMap<GridPosition, Candidate> cells,
            RoadPoint start,
            RoadPoint end,
            TreeCell.Role role,
            double pathStart,
            double pathEnd,
            int maximumCells
    ) {
        var spine = VoxelPath.faceConnectedLine(
                grid(start), grid(end), VoxelPath.TieOrder.Y_X_Z
        );
        var tangent = end.sub(start).normalize();
        int last = Math.max(1, spine.size() - 1);
        for (int step = 0; step < spine.size(); step++) {
            var current = spine.get(step);
            double progress = (double) step / last;
            double path = pathStart + (pathEnd - pathStart) * progress;
            double lateral = role == TreeCell.Role.TRUNK ? 0.0
                    : role == TreeCell.Role.BRANCH ? 0.35
                    : role == TreeCell.Role.ROOT ? 0.68 : 1.0;
            var geometry = new StructuralGeometry(
                    clamp01(path), lateral, 0.0,
                    0.0, 0.0, StructuralGeometry.tipFromPath(path), 0.0,
                    tangent.x(), tangent.y(), tangent.z(),
                    0.0, 0.0, 0.0
            );
            merge(cells, new TreeCell(current, role, geometry), -0.25);
            if (cells.size() > maximumCells) {
                return false;
            }
        }
        return true;
    }

    /**
     * Marks branch/root/trunk convergence independently of any one generator
     * type. The value is propagated one block from a centerline junction so
     * structural rules can protect both the core and its visible shell.
     */
    private static void markJunctions(
            LinkedHashMap<GridPosition, Candidate> cells
    ) {
        var spine = new HashSet<GridPosition>();
        for (var candidate : cells.values()) {
            var cell = candidate.cell();
            if (cell.role() != TreeCell.Role.CANOPY && cell.depth() <= 0.05) {
                spine.add(cell.position());
            }
        }
        var junctions = new ArrayList<GridPosition>();
        for (var position : spine) {
            int neighbors = 0;
            for (var direction : GridPosition.Direction.values()) {
                if (spine.contains(position.offset(
                        direction.dx(), direction.dy(), direction.dz()
                ))) {
                    neighbors++;
                }
            }
            if (neighbors >= 3) {
                junctions.add(position);
            }
        }
        for (var junction : junctions) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int distance = Math.abs(dx) + Math.abs(dy)
                                + Math.abs(dz);
                        if (distance > 1) {
                            continue;
                        }
                        var position = junction.offset(dx, dy, dz);
                        var candidate = cells.get(position);
                        if (candidate == null
                                || candidate.cell().role()
                                == TreeCell.Role.CANOPY) {
                            continue;
                        }
                        double value = distance == 0 ? 1.0 : 0.55;
                        var updated = candidate.cell().withJunction(
                                Math.max(candidate.cell().junction(), value)
                        );
                        cells.put(position, new Candidate(
                                updated, candidate.distanceSquared()
                        ));
                    }
                }
            }
        }
    }

    /** Removes random leaf islands while retaining diagonal leaf contact. */
    private static void pruneUnsupportedCanopy(
            LinkedHashMap<GridPosition, Candidate> cells
    ) {
        var structural = new HashSet<GridPosition>();
        var canopy = new HashSet<GridPosition>();
        for (var entry : cells.entrySet()) {
            if (entry.getValue().cell().role() == TreeCell.Role.CANOPY) {
                canopy.add(entry.getKey());
            } else {
                structural.add(entry.getKey());
            }
        }
        if (canopy.isEmpty() || structural.isEmpty()) {
            return;
        }
        var supported = new HashSet<GridPosition>();
        var queue = new ArrayDeque<GridPosition>();
        for (var position : canopy) {
            if (hasNeighbor(position, structural)) {
                supported.add(position);
                queue.add(position);
            }
        }
        while (!queue.isEmpty()) {
            var position = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        var neighbor = position.offset(dx, dy, dz);
                        if (canopy.contains(neighbor)
                                && supported.add(neighbor)) {
                            queue.addLast(neighbor);
                        }
                    }
                }
            }
        }
        cells.entrySet().removeIf(entry ->
                entry.getValue().cell().role() == TreeCell.Role.CANOPY
                        && !supported.contains(entry.getKey())
        );
    }

    private static boolean hasNeighbor(
            GridPosition position,
            Set<GridPosition> candidates
    ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dy != 0 || dz != 0)
                            && candidates.contains(
                                    position.offset(dx, dy, dz)
                            )) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static RoadPoint quadratic(
            RoadPoint start,
            RoadPoint control,
            RoadPoint end,
            double progress
    ) {
        double inverse = 1.0 - progress;
        return start.mul(inverse * inverse)
                .add(control.mul(2.0 * inverse * progress))
                .add(end.mul(progress * progress));
    }

    private static RoadPoint cross(RoadPoint first, RoadPoint second) {
        return new RoadPoint(
                first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x()
        );
    }

    private static double branchProgress(
            TreeArchetype archetype,
            double unit
    ) {
        return switch (archetype) {
            case PALM -> 0.86 + unit * 0.10;
            case SPRUCE -> 0.20 + unit * 0.74;
            case PINE -> 0.48 + unit * 0.46;
            case ACACIA, CHERRY -> 0.60 + unit * 0.30;
            case BIRCH -> 0.42 + unit * 0.50;
            case JUNGLE -> 0.40 + unit * 0.54;
            case DARK_OAK -> 0.27 + unit * 0.60;
            case MANGROVE -> 0.34 + unit * 0.58;
            case WILLOW -> 0.38 + unit * 0.55;
            case GIANT_FANTASY -> 0.22 + unit * 0.72;
            case OAK, CUSTOM -> 0.32 + unit * 0.60;
        };
    }

    private static double crownProfile(
            TreeArchetype archetype,
            double progress
    ) {
        return switch (archetype) {
            case PALM -> 1.0;
            case SPRUCE -> Math.max(0.24, 1.28 - progress);
            case PINE -> Math.max(0.38, 1.18 - progress * 0.62);
            case ACACIA, CHERRY -> 0.92 + progress * 0.22;
            case BIRCH -> Math.max(
                    0.42, 0.88 - Math.abs(progress - 0.72) * 0.72
            );
            case JUNGLE, DARK_OAK, GIANT_FANTASY -> Math.max(
                    0.58, 1.12 - Math.abs(progress - 0.62) * 0.82
            );
            case WILLOW -> Math.max(
                    0.65, 1.10 - Math.abs(progress - 0.62) * 0.70
            );
            case MANGROVE -> Math.max(
                    0.60, 1.08 - Math.abs(progress - 0.67) * 0.82
            );
            case OAK, CUSTOM -> Math.max(
                    0.48, 1.04 - Math.abs(progress - 0.68) * 1.0
            );
        };
    }

    private static double branchLift(
            TreeArchetype archetype,
            double length,
            double progress,
            double droop
    ) {
        return switch (archetype) {
            case PALM -> -length * (0.10 + droop * 0.20);
            case SPRUCE -> length * (
                    progress > 0.78 ? 0.16 : -0.07 - droop * 0.10
            );
            case PINE -> length * (
                    progress > 0.82 ? 0.18 : -0.03 - droop * 0.08
            );
            case ACACIA, CHERRY -> length * (0.06 - droop * 0.10);
            case WILLOW -> length * (0.12 - droop * 0.34);
            case MANGROVE -> length * (0.16 - droop * 0.22);
            case BIRCH -> length * (0.24 - droop * 0.18);
            case JUNGLE, DARK_OAK, GIANT_FANTASY ->
                    length * (0.20 - droop * 0.23);
            case OAK, CUSTOM -> length * (0.24 - droop * 0.22);
        };
    }

    private static boolean shouldFork(
            TreeArchetype archetype,
            int branch,
            int branchCount
    ) {
        return !archetype.isConifer()
                && !archetype.isPalm()
                && branchCount <= 80
                && branch % (archetype == TreeArchetype.GIANT_FANTASY ? 2 : 3)
                        == 0;
    }

    private static boolean shouldRiseAsLeader(
            TreeArchetype archetype,
            int branch,
            int branchCount
    ) {
        if (branchCount > 96) {
            return false;
        }
        int interval = switch (archetype) {
            case GIANT_FANTASY -> 3;
            case JUNGLE, DARK_OAK -> 4;
            case OAK, MANGROVE, WILLOW -> 5;
            default -> 0;
        };
        return interval > 0 && branch % interval == 1;
    }

    private static double voxelRadius(int configuredRadius) {
        return Math.max(0.45, configuredRadius - 0.45);
    }

    private static GridPosition grid(RoadPoint point) {
        return new GridPosition(
                (int) Math.floor(point.x()),
                (int) Math.floor(point.y()),
                (int) Math.floor(point.z())
        );
    }

    private record Terminal(RoadPoint point, double progress, int index) {
    }

    public static Result voxelize(
            List<RoadPoint> points,
            TreeProfile profile,
            int maximumCells
    ) {
        var errors = new ArrayList<>(profile.validate());
        if (points.size() < 2) {
            errors.add("A tree requires a base and crown point");
        }
        if (maximumCells < 1) {
            errors.add("Tree cell limit must be positive");
        }
        if (!errors.isEmpty()) {
            return Result.failure(errors);
        }

        var base = points.get(0);
        var crown = points.get(1);
        double trunkLength = base.distance(crown);
        if (trunkLength < 0.5) {
            return Result.failure(List.of(
                    "Tree base and crown must be at least half a block apart"
            ));
        }

        var cells = new LinkedHashMap<GridPosition, Candidate>();
        if (!sweep(
                cells,
                base,
                crown,
                profile.trunkRadius(),
                profile.sampleSpacing(),
                TreeCell.Role.TRUNK,
                0.0,
                trunkLength,
                maximumCells
        )) {
            return tooLarge(maximumCells);
        }

        var limbs = new ArrayList<Limb>();
        limbs.add(new Limb(base, crown, TreeCell.Role.TRUNK));
        for (int index = 2; index < points.size(); index++) {
            var endpoint = points.get(index);
            var root = projectOntoSegment(endpoint, base, crown);
            if (root.distance(endpoint) < 0.5) {
                continue;
            }
            limbs.add(new Limb(root, endpoint, TreeCell.Role.BRANCH));
            double rootProgress = clampedProgress(root, base, crown);
            if (!sweep(
                    cells,
                    root,
                    endpoint,
                    profile.branchRadius(),
                    profile.sampleSpacing(),
                    TreeCell.Role.BRANCH,
                    0.0,
                    1.0,
                    maximumCells
            )) {
                return tooLarge(maximumCells);
            }
        }

        if (profile.canopyRadius() > 0) {
            if (!sphere(
                    cells,
                    crown,
                    profile.canopyRadius(),
                    1.0,
                    maximumCells
            )) {
                return tooLarge(maximumCells);
            }
            for (int index = 2; index < points.size(); index++) {
                if (!sphere(
                        cells,
                        points.get(index),
                        profile.canopyRadius(),
                        clampedProgress(
                                projectOntoSegment(
                                        points.get(index),
                                        base,
                                        crown
                                ),
                                base,
                                crown
                        ),
                        maximumCells
                )) {
                    return tooLarge(maximumCells);
                }
            }
        }

        var ordered = cells.values().stream()
                .map(Candidate::cell)
                .sorted(Comparator.comparing(TreeCell::position))
                .toList();
        return Result.success(ordered, limbs);
    }

    private static boolean sweep(
            LinkedHashMap<GridPosition, Candidate> cells,
            RoadPoint start,
            RoadPoint end,
            int radius,
            double spacing,
            TreeCell.Role role,
            double pathStart,
            double pathEnd,
            int maximumCells
    ) {
        double length = start.distance(end);
        int samples = Math.max(1, (int) Math.ceil(length / spacing));
        long candidateEstimate = (long) (samples + 1)
                * (radius * 2L + 1L)
                * (radius * 2L + 1L)
                * (radius * 2L + 1L);
        if (candidateEstimate > Math.max(1L, maximumCells) * 64L) {
            return false;
        }
        for (int sample = 0; sample <= samples; sample++) {
            double progress = (double) sample / samples;
            var center = start.add(end.sub(start).mul(progress));
            double path = pathStart + (pathEnd - pathStart) * progress;
            int minX = (int) Math.floor(center.x() - radius);
            int maxX = (int) Math.floor(center.x() + radius);
            int minY = (int) Math.floor(center.y() - radius);
            int maxY = (int) Math.floor(center.y() + radius);
            int minZ = (int) Math.floor(center.z() - radius);
            int maxZ = (int) Math.floor(center.z() + radius);
            double radiusSquared = radius * radius + 0.35;
            var tangent = end.sub(start).normalize();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        var voxelCenter = new RoadPoint(
                                x + 0.5, y + 0.5, z + 0.5
                        );
                        double distanceSquared =
                                voxelCenter.distanceSquared(center);
                        if (distanceSquared > radiusSquared) {
                            continue;
                        }
                        double distance = Math.sqrt(distanceSquared);
                        var radial = distance <= 1.0e-9
                                ? new RoadPoint(0.0, 0.0, 0.0)
                                : voxelCenter.sub(center).mul(1.0 / distance);
                        var geometry = new StructuralGeometry(
                                clamp01(path), lateral(role),
                                clamp01(distance / Math.max(1, radius)),
                                normalizedThickness(radius), 0.0,
                                StructuralGeometry.tipFromPath(path), 0.0,
                                tangent.x(), tangent.y(), tangent.z(),
                                radial.x(), radial.y(), radial.z()
                        );
                        var cell = new TreeCell(
                                new GridPosition(x, y, z), role, geometry
                        );
                        merge(cells, cell, distanceSquared);
                        if (cells.size() > maximumCells) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean sphere(
            LinkedHashMap<GridPosition, Candidate> cells,
            RoadPoint center,
            int radius,
            double path,
            int maximumCells
    ) {
        long diameter = radius * 2L + 1L;
        if (diameter * diameter * diameter
                > Math.max(1L, maximumCells) * 32L) {
            return false;
        }
        int minX = (int) Math.floor(center.x() - radius);
        int maxX = (int) Math.floor(center.x() + radius);
        int minY = (int) Math.floor(center.y() - radius);
        int maxY = (int) Math.floor(center.y() + radius);
        int minZ = (int) Math.floor(center.z() - radius);
        int maxZ = (int) Math.floor(center.z() + radius);
        double radiusSquared = radius * radius + 0.35;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    var voxelCenter = new RoadPoint(
                            x + 0.5, y + 0.5, z + 0.5
                    );
                    double distanceSquared =
                            voxelCenter.distanceSquared(center);
                    if (distanceSquared > radiusSquared) {
                        continue;
                    }
                    var cell = new TreeCell(
                            new GridPosition(x, y, z),
                            clamp01(path),
                            1.0,
                            clamp01(Math.sqrt(distanceSquared)
                                    / Math.max(1, radius)),
                            TreeCell.Role.CANOPY
                    );
                    merge(cells, cell, distanceSquared);
                    if (cells.size() > maximumCells) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void merge(
            LinkedHashMap<GridPosition, Candidate> cells,
            TreeCell cell,
            double distanceSquared
    ) {
        cells.merge(
                cell.position(),
                new Candidate(cell, distanceSquared),
                (first, second) -> {
                    int role = Integer.compare(
                            first.cell().role().ordinal(),
                            second.cell().role().ordinal()
                    );
                    if (role != 0) {
                        return role < 0 ? first : second;
                    }
                    return first.distanceSquared()
                            <= second.distanceSquared() ? first : second;
                }
        );
    }

    private static Result tooLarge(int maximumCells) {
        return Result.failure(List.of(
                "Tree contains more than the configured "
                        + maximumCells + " cells"
        ));
    }

    private static RoadPoint projectOntoSegment(
            RoadPoint point,
            RoadPoint start,
            RoadPoint end
    ) {
        var delta = end.sub(start);
        double lengthSquared = delta.lengthSquared();
        if (lengthSquared <= 1.0e-12) {
            return start;
        }
        double progress = clamp01(point.sub(start).dot(delta) / lengthSquared);
        return start.add(delta.mul(progress));
    }

    private static double clampedProgress(
            RoadPoint point,
            RoadPoint start,
            RoadPoint end
    ) {
        var delta = end.sub(start);
        double lengthSquared = delta.lengthSquared();
        return lengthSquared <= 1.0e-12
                ? 0.0
                : clamp01(point.sub(start).dot(delta) / lengthSquared);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double normalizedThickness(double radius) {
        return clamp01((radius - 0.38) / 3.0);
    }

    private static double lateral(TreeCell.Role role) {
        return switch (role) {
            case TRUNK -> 0.0;
            case BRANCH -> 0.35;
            case ROOT -> 0.68;
            case CANOPY -> 1.0;
        };
    }

    private record Candidate(TreeCell cell, double distanceSquared) {
    }

    public record Limb(
            RoadPoint start,
            RoadPoint end,
            TreeCell.Role role
    ) {
    }

    public record Result(
            List<TreeCell> cells,
            List<Limb> limbs,
            List<String> errors
    ) {

        public Result {
            cells = List.copyOf(cells);
            limbs = List.copyOf(limbs);
            errors = List.copyOf(errors);
        }

        public static Result success(
                List<TreeCell> cells,
                List<Limb> limbs
        ) {
            return new Result(cells, limbs, List.of());
        }

        public static Result failure(List<String> errors) {
            return new Result(List.of(), List.of(), errors);
        }

        public boolean isSuccess() {
            return errors.isEmpty();
        }

        /**
         * Reuses the road compatibility compiler with generic spatial
         * coordinates. No road or tree type is serialized.
         */
        public RoadVoxelizer.Result asRoadResult() {
            if (!isSuccess()) {
                return RoadVoxelizer.Result.failure(errors);
            }
            var roadCells = cells.stream().map(cell -> new RoadCell(
                    cell.position(),
                    switch (cell.role()) {
                        case TRUNK -> RoadCell.Role.SURFACE;
                        case BRANCH -> RoadCell.Role.SHOULDER;
                        case ROOT -> RoadCell.Role.SURFACE;
                        case CANOPY -> RoadCell.Role.FOUNDATION;
                    },
                    cell.geometry()
            )).toList();
            var samples = limbs.stream()
                    .flatMap(limb -> java.util.stream.Stream.of(
                            new RoadSpline.Sample(
                                    limb.start(),
                                    limb.end().sub(limb.start()).normalize(),
                                    0.0,
                                    0,
                                    0.0
                            ),
                            new RoadSpline.Sample(
                                    limb.end(),
                                    limb.end().sub(limb.start()).normalize(),
                                    1.0,
                                    0,
                                    1.0
                            )
                    ))
                    .toList();
            return RoadVoxelizer.Result.success(roadCells, samples, 0.0);
        }
    }
}
