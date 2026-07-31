package dev.huskuraft.effortless.client.road;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-local road cross-section and spline sampling settings.
 */
public record RoadProfile(
        int surfaceWidth,
        int thickness,
        int shoulderWidth,
        double tension,
        double sampleSpacing,
        SplineSubtype subtype,
        SplineMaterialLinks materialLinks,
        List<SplineCrossSectionBand> crossSectionBands,
        SplineCutoutConfig cutout
) {

    public static final int MAX_SURFACE_WIDTH = 64;
    public static final int MAX_THICKNESS = 16;
    public static final int MAX_SHOULDER_WIDTH = 16;
    public static final int MAX_CROSS_SECTION_BANDS = 16;
    public static final double MIN_SAMPLE_SPACING = 0.1;
    public static final double MAX_SAMPLE_SPACING = 1.0;

    public static final RoadProfile DEFAULT =
            forSubtype(SplineSubtype.FLAT_ROAD);

    /** Source-compatible constructor used by legacy configs and tests. */
    public RoadProfile(
            int surfaceWidth,
            int thickness,
            int shoulderWidth,
            double tension,
            double sampleSpacing
    ) {
        this(
                surfaceWidth, thickness, shoulderWidth, tension,
                sampleSpacing, SplineSubtype.CUSTOM,
                SplineMaterialLinks.DEFAULT, List.of(),
                SplineCutoutConfig.DEFAULT
        );
    }

    public RoadProfile(
            int surfaceWidth,
            int thickness,
            int shoulderWidth,
            double tension,
            double sampleSpacing,
            SplineSubtype subtype
    ) {
        this(
                surfaceWidth, thickness, shoulderWidth, tension,
                sampleSpacing, subtype, SplineMaterialLinks.DEFAULT,
                List.of(), SplineCutoutConfig.DEFAULT
        );
    }

    /** Source-compatible constructor used before cross-section bands. */
    public RoadProfile(
            int surfaceWidth,
            int thickness,
            int shoulderWidth,
            double tension,
            double sampleSpacing,
            SplineSubtype subtype,
            SplineMaterialLinks materialLinks
    ) {
        this(
                surfaceWidth, thickness, shoulderWidth, tension,
                sampleSpacing, subtype, materialLinks, List.of(),
                SplineCutoutConfig.DEFAULT
        );
    }

    public RoadProfile {
        subtype = subtype == null ? SplineSubtype.CUSTOM : subtype;
        materialLinks = materialLinks == null
                ? SplineMaterialLinks.DEFAULT
                : materialLinks;
        crossSectionBands = crossSectionBands == null
                ? List.of()
                : List.copyOf(crossSectionBands);
        cutout = cutout == null ? SplineCutoutConfig.DEFAULT : cutout;
    }

    public static RoadProfile forSubtype(SplineSubtype subtype) {
        return switch (subtype) {
            case PATH -> new RoadProfile(3, 1, 0, 0.18, 0.25, subtype);
            case FLAT_ROAD -> new RoadProfile(5, 2, 1, 0.18, 0.25, subtype);
            case CROWNED_ROAD -> new RoadProfile(7, 2, 1, 0.22, 0.25, subtype);
            case BANKED_ROAD -> new RoadProfile(7, 2, 1, 0.30, 0.25, subtype);
            case EMBANKMENT -> new RoadProfile(7, 4, 1, 0.20, 0.25, subtype);
            case TRENCH -> new RoadProfile(5, 3, 1, 0.18, 0.25, subtype);
            case BRIDGE_DECK -> new RoadProfile(7, 2, 1, 0.25, 0.25, subtype);
            case RAIL_BED -> new RoadProfile(3, 2, 2, 0.24, 0.20, subtype);
            case CUSTOM -> new RoadProfile(5, 1, 0, 0.0, 0.25, subtype);
        };
    }

    public int totalWidth() {
        return surfaceWidth + shoulderWidth * 2;
    }

    public List<String> validate() {
        var errors = new ArrayList<String>();
        if (surfaceWidth < 1 || surfaceWidth > MAX_SURFACE_WIDTH) {
            errors.add("Spline width must be between 1 and "
                    + MAX_SURFACE_WIDTH);
        }
        if (thickness < 1 || thickness > MAX_THICKNESS) {
            errors.add("Spline thickness must be between 1 and "
                    + MAX_THICKNESS);
        }
        if (shoulderWidth < 0
                || shoulderWidth > MAX_SHOULDER_WIDTH) {
            errors.add("Spline shoulder width must be between 0 and "
                    + MAX_SHOULDER_WIDTH);
        }
        if (!Double.isFinite(tension) || tension < 0.0 || tension > 1.0) {
            errors.add("Spline curve tension must be between 0 and 1");
        }
        if (!Double.isFinite(sampleSpacing)
                || sampleSpacing < MIN_SAMPLE_SPACING
                || sampleSpacing > MAX_SAMPLE_SPACING) {
            errors.add("Spline sample spacing must be between "
                    + MIN_SAMPLE_SPACING + " and " + MAX_SAMPLE_SPACING);
        }
        if (crossSectionBands.size() > MAX_CROSS_SECTION_BANDS) {
            errors.add("A spline may contain at most "
                    + MAX_CROSS_SECTION_BANDS + " cross-section bands");
        }
        for (int index = 0; index < crossSectionBands.size(); index++) {
            for (var error : crossSectionBands.get(index).validate()) {
                errors.add("Band " + (index + 1) + ": " + error);
            }
        }
        errors.addAll(cutout.validate());
        return List.copyOf(errors);
    }

    public RoadProfile withSurfaceWidth(int value) {
        return new RoadProfile(
                value, thickness, shoulderWidth, tension, sampleSpacing,
                SplineSubtype.CUSTOM, materialLinks, crossSectionBands, cutout
        );
    }

    public RoadProfile withThickness(int value) {
        return new RoadProfile(
                surfaceWidth, value, shoulderWidth, tension, sampleSpacing,
                SplineSubtype.CUSTOM, materialLinks, crossSectionBands, cutout
        );
    }

    public RoadProfile withShoulderWidth(int value) {
        return new RoadProfile(
                surfaceWidth, thickness, value, tension, sampleSpacing,
                SplineSubtype.CUSTOM, materialLinks, crossSectionBands, cutout
        );
    }

    public RoadProfile withTension(double value) {
        return new RoadProfile(
                surfaceWidth, thickness, shoulderWidth, value, sampleSpacing,
                SplineSubtype.CUSTOM, materialLinks, crossSectionBands, cutout
        );
    }

    public RoadProfile withSampleSpacing(double value) {
        return new RoadProfile(
                surfaceWidth, thickness, shoulderWidth, tension, value,
                SplineSubtype.CUSTOM, materialLinks, crossSectionBands, cutout
        );
    }

    public RoadProfile withSubtype(SplineSubtype value) {
        if (value == null || value == SplineSubtype.CUSTOM) {
            return new RoadProfile(
                    surfaceWidth, thickness, shoulderWidth, tension,
                    sampleSpacing, SplineSubtype.CUSTOM, materialLinks,
                    crossSectionBands, cutout
            );
        }
        var preset = forSubtype(value);
        return new RoadProfile(
                preset.surfaceWidth(), preset.thickness(),
                preset.shoulderWidth(), preset.tension(),
                preset.sampleSpacing(), preset.subtype(), materialLinks,
                crossSectionBands, cutout
        );
    }

    /**
     * Changes only the cross-section style discriminator. Unlike
     * {@link #withSubtype(SplineSubtype)}, this deliberately retains tuned
     * dimensions and linked geometry. The workbench preview uses it so its
     * preview-only subtype toolbar cannot reset the recipe being inspected.
     */
    public RoadProfile withSubtypePreservingGeometry(SplineSubtype value) {
        return new RoadProfile(
                surfaceWidth, thickness, shoulderWidth, tension,
                sampleSpacing,
                value == null ? SplineSubtype.CUSTOM : value,
                materialLinks, crossSectionBands, cutout
        );
    }

    public RoadProfile withMaterialLinks(SplineMaterialLinks value) {
        return new RoadProfile(
                surfaceWidth, thickness, shoulderWidth, tension,
                sampleSpacing, subtype, value, crossSectionBands, cutout
        );
    }

    public RoadProfile withCrossSectionBands(
            List<SplineCrossSectionBand> value
    ) {
        return new RoadProfile(
                surfaceWidth, thickness, shoulderWidth, tension,
                sampleSpacing, subtype, materialLinks, value, cutout
        );
    }

    public RoadProfile withCutout(SplineCutoutConfig value) {
        return new RoadProfile(
                surfaceWidth, thickness, shoulderWidth, tension,
                sampleSpacing, subtype, materialLinks,
                crossSectionBands, value
        );
    }
}
