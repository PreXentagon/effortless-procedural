package dev.huskuraft.effortless.client.road;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import dev.huskuraft.effortless.client.pattern.procedural.GridPosition;
import dev.huskuraft.effortless.client.pattern.procedural.StructuralGeometry;

/**
 * Sweeps an upright rectangular road profile along a sampled spline.
 *
 * <p>The MVP deliberately uses a local, deterministic union of sampled
 * cross-sections. It avoids recursive backtracking and global mesh solving.
 * Tight self-intersections are merged into one voxel using a stable priority:
 * surface before foundation, then center-most, then earliest along the path.</p>
 */
public final class RoadVoxelizer {

    public static final int MAX_CELLS = 250_000;

    private RoadVoxelizer() {
    }

    public static Result voxelize(
            List<RoadPoint> controlPoints,
            RoadProfile profile
    ) {
        return voxelize(controlPoints, profile, MAX_CELLS);
    }

    public static Result voxelize(
            List<RoadPoint> controlPoints,
            RoadProfile profile,
            int maximumCells
    ) {
        var errors = new ArrayList<>(profile.validate());
        if (controlPoints.size() < 2) {
            errors.add("A spline requires at least two control points");
        }
        if (maximumCells < 1) {
            errors.add("Spline cell limit must be positive");
        }
        if (!errors.isEmpty()) {
            return Result.failure(errors);
        }

        var spline = RoadSpline.sample(
                controlPoints,
                profile.tension(),
                profile.sampleSpacing()
        );
        if (!spline.isSuccess()) {
            return Result.failure(List.of(spline.error()));
        }
        long upperBound = (long) spline.samples().size()
                * profile.totalWidth() * profile.thickness();
        for (var band : profile.crossSectionBands()) {
            int sides = band.side() == SplineCrossSectionBand.Side.BOTH
                    ? 2 : 1;
            upperBound += (long) spline.samples().size()
                    * band.width() * band.depth() * sides;
        }
        if (upperBound > (long) maximumCells * 8L) {
            return Result.failure(List.of(
                    "Spline sampling would examine too many cells ("
                            + upperBound + " candidates)"
            ));
        }

        var cells = new LinkedHashMap<GridPosition, CellCandidate>();
        RoadPoint previousLateral = new RoadPoint(1.0, 0.0, 0.0);
        int totalWidth = profile.totalWidth();
        for (var sample : spline.samples()) {
            var tangent = sample.tangent();
            var lateral = new RoadPoint(-tangent.z(), 0.0, tangent.x())
                    .normalize();
            if (lateral.lengthSquared() <= 1.0e-12) {
                lateral = previousLateral;
            } else if (lateral.dot(previousLateral) < 0.0) {
                lateral = lateral.mul(-1.0);
            }
            previousLateral = lateral;

            for (int depth = 0; depth < profile.thickness(); depth++) {
                int spread = profile.subtype() == SplineSubtype.EMBANKMENT
                        ? depth
                        : 0;
                for (int column = -spread;
                        column < totalWidth + spread;
                        column++) {
                    double clampedColumn = Math.max(
                            0,
                            Math.min(totalWidth - 1, column)
                    );
                    double normalizedLateral = totalWidth <= 1
                            ? 0.5
                            : clampedColumn / (double) (totalWidth - 1);
                    double offset = column - (totalWidth - 1) / 2.0;
                    boolean outsideProfile = column < 0
                            || column >= totalWidth;
                    boolean shoulder = outsideProfile
                            || column < profile.shoulderWidth()
                            || column >= profile.shoulderWidth()
                            + profile.surfaceWidth();
                    double crossHeight = crossSectionHeight(
                            profile.subtype(), normalizedLateral, shoulder
                    );
                    double crossSlope = crossSectionSlope(
                            profile.subtype(), normalizedLateral, shoulder
                    );
                    var top = sample.point()
                            .add(lateral.mul(offset))
                            .add(0.0, crossHeight, 0.0);
                    var position = new GridPosition(
                            blockCoordinate(top.x()),
                            blockCoordinate(top.y() - depth),
                            blockCoordinate(top.z())
                    );
                    double normalizedDepth = profile.thickness() <= 1
                            ? 0.0
                            : (double) depth
                            / (double) (profile.thickness() - 1);
                    var role = role(
                            profile, column, totalWidth, depth, shoulder
                    );
                    var normal = roadNormal(tangent, lateral, crossSlope);
                    if (depth > 0) {
                        normal = normal.mul(-1.0);
                    }
                    double endpoint = Math.max(
                            StructuralGeometry.tipFromPath(sample.progress()),
                            StructuralGeometry.tipFromPath(
                                    1.0 - sample.progress()
                            )
                    );
                    var geometry = new StructuralGeometry(
                            sample.progress(),
                            normalizedLateral,
                            normalizedDepth,
                            normalizedThickness(profile.thickness()),
                            Math.min(
                                    1.0,
                                    Math.max(
                                            Math.abs(tangent.y()),
                                            Math.abs(crossSlope)
                                    )
                            ),
                            endpoint,
                            0.0,
                            tangent.x(), tangent.y(), tangent.z(),
                            normal.x(), normal.y(), normal.z()
                    );
                    var candidate = new RoadCell(position, role, geometry);
                    var voxelCenter = new RoadPoint(
                            position.x() + 0.5,
                            position.y() + 0.5,
                            position.z() + 0.5
                    );
                    var sweptCenter = top.add(0.0, -depth, 0.0);
                    cells.merge(
                            position,
                            new CellCandidate(
                                    candidate,
                                    sweptCenter.distanceSquared(voxelCenter)
                            ),
                            RoadVoxelizer::preferred
                    );
                    if (cells.size() > maximumCells) {
                        return Result.failure(List.of(
                                "Spline contains more than " + maximumCells
                                        + " unique cells"
                        ));
                    }
                }
            }

            int leftWidth = 0;
            int rightWidth = 0;
            int totalBandWidth = profile.crossSectionBands().stream()
                    .mapToInt(SplineCrossSectionBand::width)
                    .sum();
            double lateralDenominator = Math.max(
                    1.0, totalWidth - 1.0 + totalBandWidth * 2.0
            );
            for (int bandIndex = 0;
                    bandIndex < profile.crossSectionBands().size();
                    bandIndex++) {
                var band = profile.crossSectionBands().get(bandIndex);
                for (var side : bandSides(band.side())) {
                    int accumulated = side < 0 ? leftWidth : rightWidth;
                    for (int column = 0; column < band.width(); column++) {
                        double edge = (totalWidth - 1) / 2.0;
                        double offset = side * (
                                edge + 1.0 + accumulated + column
                        );
                        for (int depth = 0; depth < band.depth(); depth++) {
                            var top = sample.point()
                                    .add(lateral.mul(offset))
                                    .add(0.0, band.heightOffset(), 0.0);
                            var position = new GridPosition(
                                    blockCoordinate(top.x()),
                                    blockCoordinate(top.y() - depth),
                                    blockCoordinate(top.z())
                            );
                            double normalizedLateral = Math.max(
                                    0.0,
                                    Math.min(
                                            1.0,
                                            0.5 + offset
                                                    / lateralDenominator
                                    )
                            );
                            var normal = bandNormal(
                                    band.placement(), lateral, side,
                                    tangent, depth
                            );
                            double normalizedDepth = band.depth() <= 1
                                    ? 0.0
                                    : (double) depth
                                            / (band.depth() - 1.0);
                            if (band.placement()
                                    != SplineCrossSectionBand.Placement.AUTO
                                    && band.placement()
                                    != SplineCrossSectionBand.Placement
                                            .FULL_BLOCK) {
                                normalizedDepth = 1.0;
                            }
                            double endpoint = Math.max(
                                    StructuralGeometry.tipFromPath(
                                            sample.progress()
                                    ),
                                    StructuralGeometry.tipFromPath(
                                            1.0 - sample.progress()
                                    )
                            );
                            var geometry = new StructuralGeometry(
                                    sample.progress(), normalizedLateral,
                                    normalizedDepth,
                                    normalizedThickness(band.depth()),
                                    Math.min(1.0, Math.abs(tangent.y())),
                                    endpoint, 0.0,
                                    tangent.x(), tangent.y(), tangent.z(),
                                    normal.x(), normal.y(), normal.z()
                            );
                            var candidate = new RoadCell(
                                    position, RoadCell.Role.BAND,
                                    geometry, bandIndex
                            );
                            var voxelCenter = new RoadPoint(
                                    position.x() + 0.5,
                                    position.y() + 0.5,
                                    position.z() + 0.5
                            );
                            var sweptCenter = top.add(0.0, -depth, 0.0);
                            cells.merge(
                                    position,
                                    new CellCandidate(
                                            candidate,
                                            sweptCenter.distanceSquared(
                                                    voxelCenter
                                            )
                                    ),
                                    RoadVoxelizer::preferred
                            );
                            if (cells.size() > maximumCells) {
                                return Result.failure(List.of(
                                        "Spline contains more than "
                                                + maximumCells
                                                + " unique cells"
                                ));
                            }
                        }
                    }
                }
                if (band.side() != SplineCrossSectionBand.Side.RIGHT) {
                    leftWidth += band.width();
                }
                if (band.side() != SplineCrossSectionBand.Side.LEFT) {
                    rightWidth += band.width();
                }
            }
        }

        var ordered = cells.values().stream()
                .map(CellCandidate::cell)
                .sorted(Comparator.comparing(RoadCell::position))
                .toList();
        return Result.success(ordered, spline.samples(), spline.length());
    }

    private static CellCandidate preferred(
            CellCandidate first,
            CellCandidate second
    ) {
        int role = Integer.compare(
                rolePriority(first.cell().role()),
                rolePriority(second.cell().role())
        );
        if (role != 0) {
            return role < 0 ? first : second;
        }
        int proximity = Double.compare(
                first.voxelCenterDistanceSquared(),
                second.voxelCenterDistanceSquared()
        );
        if (proximity != 0) {
            return proximity < 0 ? first : second;
        }
        double firstCenter = Math.abs(first.cell().lateral() - 0.5);
        double secondCenter = Math.abs(second.cell().lateral() - 0.5);
        int center = Double.compare(firstCenter, secondCenter);
        if (center != 0) {
            return center < 0 ? first : second;
        }
        return first.cell().path() <= second.cell().path() ? first : second;
    }

    private static RoadCell.Role role(
            RoadProfile profile,
            int column,
            int totalWidth,
            int depth,
            boolean shoulder
    ) {
        if (depth > 0) {
            return RoadCell.Role.FOUNDATION;
        }
        var links = profile.materialLinks();
        boolean outerEdge = column == 0 || column == totalWidth - 1;
        if (shoulder) {
            return outerEdge && !links.curbRecipeId().isBlank()
                    ? RoadCell.Role.CURB
                    : RoadCell.Role.SHOULDER;
        }
        if (!links.markingRecipeId().isBlank()) {
            int surfaceColumn = column - profile.shoulderWidth();
            boolean marking = surfaceColumn == profile.surfaceWidth() / 2;
            if (marking) {
                return RoadCell.Role.MARKING;
            }
        }
        return RoadCell.Role.SURFACE;
    }

    private static int rolePriority(RoadCell.Role role) {
        return switch (role) {
            case MARKING, CURB, BAND -> 0;
            case SURFACE -> 1;
            case SHOULDER -> 2;
            case FOUNDATION, CUTOUT_WALL, CUTOUT_FLOOR -> 3;
        };
    }

    private static int[] bandSides(SplineCrossSectionBand.Side side) {
        return switch (side) {
            case LEFT -> new int[]{-1};
            case RIGHT -> new int[]{1};
            case BOTH -> new int[]{-1, 1};
        };
    }

    private static RoadPoint bandNormal(
            SplineCrossSectionBand.Placement placement,
            RoadPoint lateral,
            int side,
            RoadPoint tangent,
            int depth
    ) {
        var outward = lateral.mul(side);
        return switch (placement) {
            case STAIR_OUTWARD -> outward;
            case STAIR_INWARD -> outward.mul(-1.0);
            case SLAB_TOP -> new RoadPoint(0.0, -1.0, 0.0);
            case SLAB_BOTTOM -> new RoadPoint(0.0, 1.0, 0.0);
            case AUTO, FULL_BLOCK -> {
                var normal = roadNormal(tangent, lateral, 0.0);
                yield depth > 0 ? normal.mul(-1.0) : normal;
            }
        };
    }

    private static int blockCoordinate(double centerCoordinate) {
        return (int) Math.floor(centerCoordinate);
    }

    private static RoadPoint roadNormal(
            RoadPoint tangent,
            RoadPoint lateral,
            double crossSlope
    ) {
        var crossTangent = new RoadPoint(
                lateral.x(), crossSlope, lateral.z()
        ).normalize();
        var normal = new RoadPoint(
                crossTangent.y() * tangent.z()
                        - crossTangent.z() * tangent.y(),
                crossTangent.z() * tangent.x()
                        - crossTangent.x() * tangent.z(),
                crossTangent.x() * tangent.y()
                        - crossTangent.y() * tangent.x()
        ).normalize();
        return normal.y() < 0.0 ? normal.mul(-1.0) : normal;
    }

    private static double crossSectionHeight(
            SplineSubtype subtype,
            double lateral,
            boolean shoulder
    ) {
        double signed = lateral * 2.0 - 1.0;
        double edge = Math.abs(signed);
        return switch (subtype) {
            case CROWNED_ROAD -> 0.9 * (1.0 - edge);
            case BANKED_ROAD -> signed * 0.75;
            case TRENCH -> shoulder ? 0.0 : -1.0;
            case RAIL_BED -> shoulder ? 0.0 : 0.55;
            case PATH, FLAT_ROAD, EMBANKMENT, BRIDGE_DECK, CUSTOM -> 0.0;
        };
    }

    private static double crossSectionSlope(
            SplineSubtype subtype,
            double lateral,
            boolean shoulder
    ) {
        if (shoulder && subtype != SplineSubtype.BANKED_ROAD) {
            return 0.0;
        }
        return switch (subtype) {
            case CROWNED_ROAD -> lateral < 0.5 ? 0.9 : -0.9;
            case BANKED_ROAD -> 0.75;
            case TRENCH -> lateral < 0.5 ? -1.0 : 1.0;
            case RAIL_BED -> lateral < 0.5 ? 0.55 : -0.55;
            case PATH, FLAT_ROAD, EMBANKMENT, BRIDGE_DECK, CUSTOM -> 0.0;
        };
    }

    private static double normalizedThickness(int thickness) {
        return Math.max(0.0, Math.min(1.0, (thickness - 1.0) / 7.0));
    }

    private record CellCandidate(
            RoadCell cell,
            double voxelCenterDistanceSquared
    ) {
    }

    public record Result(
            List<RoadCell> cells,
            List<RoadSpline.Sample> samples,
            double length,
            List<String> errors
    ) {

        public Result {
            cells = List.copyOf(cells);
            samples = List.copyOf(samples);
            errors = List.copyOf(errors);
        }

        public static Result success(
                List<RoadCell> cells,
                List<RoadSpline.Sample> samples,
                double length
        ) {
            return new Result(cells, samples, length, List.of());
        }

        public static Result failure(List<String> errors) {
            return new Result(List.of(), List.of(), 0.0, errors);
        }

        public boolean isSuccess() {
            return errors.isEmpty();
        }
    }
}
