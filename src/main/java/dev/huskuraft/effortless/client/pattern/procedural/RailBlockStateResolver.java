package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.Optional;
import java.util.Set;

/**
 * Selects an existing rail shape from structural tangent metadata.
 *
 * <p>This is registry- and loader-neutral. Vanilla rails use descriptive
 * values such as {@code east_west}; Create 6.x tracks expose compact values
 * such as {@code xo}, {@code zo}, {@code pd}, and {@code nd}. Add-ons that
 * reuse either state vocabulary work without a compile-time dependency.</p>
 */
public final class RailBlockStateResolver {

    private RailBlockStateResolver() {
    }

    public static Optional<String> shape(
            StructuralGeometry geometry,
            Set<String> available
    ) {
        if (available.contains("xo") && available.contains("zo")) {
            return Optional.of(createShape(geometry, available));
        }
        if (available.contains("east_west")
                && available.contains("north_south")) {
            return Optional.of(vanillaShape(geometry, available));
        }
        return Optional.empty();
    }

    private static String createShape(
            StructuralGeometry geometry,
            Set<String> available
    ) {
        double x = geometry.tangentX();
        double y = geometry.tangentY();
        double z = geometry.tangentZ();
        if (Math.abs(y) > 0.28) {
            boolean forwardRises = y > 0.0;
            double riseX = forwardRises ? x : -x;
            double riseZ = forwardRises ? z : -z;
            String ascending = Math.abs(riseX) >= Math.abs(riseZ)
                    ? riseX >= 0.0 ? "ae" : "aw"
                    : riseZ >= 0.0 ? "as" : "an";
            if (available.contains(ascending)) {
                return ascending;
            }
        }
        double diagonalRatio = Math.min(Math.abs(x), Math.abs(z))
                / Math.max(0.000001, Math.max(Math.abs(x), Math.abs(z)));
        if (diagonalRatio >= 0.45) {
            String diagonal = x * z >= 0.0 ? "pd" : "nd";
            if (available.contains(diagonal)) {
                return diagonal;
            }
        }
        return Math.abs(x) >= Math.abs(z) ? "xo" : "zo";
    }

    private static String vanillaShape(
            StructuralGeometry geometry,
            Set<String> available
    ) {
        double x = geometry.tangentX();
        double y = geometry.tangentY();
        double z = geometry.tangentZ();
        if (Math.abs(y) > 0.28) {
            boolean forwardRises = y > 0.0;
            double riseX = forwardRises ? x : -x;
            double riseZ = forwardRises ? z : -z;
            String ascending = Math.abs(riseX) >= Math.abs(riseZ)
                    ? riseX >= 0.0 ? "ascending_east" : "ascending_west"
                    : riseZ >= 0.0 ? "ascending_south" : "ascending_north";
            if (available.contains(ascending)) {
                return ascending;
            }
        }
        return Math.abs(x) >= Math.abs(z)
                ? "east_west"
                : "north_south";
    }
}
