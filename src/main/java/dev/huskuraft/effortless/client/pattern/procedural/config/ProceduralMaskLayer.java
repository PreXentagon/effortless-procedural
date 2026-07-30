package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.List;
import java.util.UUID;

import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;
import dev.huskuraft.effortless.client.pattern.procedural.MaskedWeightSource;
import dev.huskuraft.effortless.client.pattern.procedural.SpatialField;

public record ProceduralMaskLayer(
        UUID id,
        String name,
        boolean enabled,
        MaskedWeightSource.Shape shape,
        Coordinate coordinate,
        double minimum,
        double maximum,
        boolean inverted,
        List<String> itemIds,
        MaskedWeightSource.Mode mode,
        double multiplier,
        int period,
        int thickness,
        SpatialField spatialField
) {

    public ProceduralMaskLayer(
            UUID id,
            String name,
            boolean enabled,
            MaskedWeightSource.Shape shape,
            Coordinate coordinate,
            double minimum,
            double maximum,
            boolean inverted,
            List<String> itemIds,
            MaskedWeightSource.Mode mode,
            double multiplier,
            int period,
            int thickness
    ) {
        this(
                id, name, enabled, shape, coordinate, minimum, maximum,
                inverted, itemIds, mode, multiplier, period, thickness,
                SpatialField.linear(coordinate)
        );
    }

    public ProceduralMaskLayer {
        itemIds = List.copyOf(itemIds);
        spatialField = spatialField == null
                ? SpatialField.linear(coordinate)
                : spatialField;
    }

    public ProceduralMaskLayer withSpatialField(SpatialField value) {
        return new ProceduralMaskLayer(
                id, name, enabled, shape, coordinate, minimum, maximum,
                inverted, itemIds, mode, multiplier, period, thickness, value
        );
    }

    public static ProceduralMaskLayer defaultLayer() {
        return new ProceduralMaskLayer(
                UUID.randomUUID(),
                "Layer",
                true,
                MaskedWeightSource.Shape.RANGE,
                Coordinate.Y,
                0.0,
                0.5,
                false,
                List.of("minecraft:stone"),
                MaskedWeightSource.Mode.MULTIPLY,
                2.0,
                4,
                1,
                SpatialField.linear(Coordinate.Y)
        );
    }
}
