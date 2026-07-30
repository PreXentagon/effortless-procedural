package dev.huskuraft.effortless.client.pattern.procedural.config;

import java.util.UUID;

import dev.huskuraft.effortless.client.pattern.procedural.SpatialField;

/**
 * Reusable client-local spatial/noise asset. Presets reference its id; library
 * resolution copies the current asset values into the runtime rule set.
 */
public record ProceduralFieldAsset(
        UUID id,
        String name,
        SpatialField gradientField,
        ProceduralNoiseConfig noiseConfig
) {

    public ProceduralFieldAsset {
        if (id == null) {
            throw new IllegalArgumentException("Field asset id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Field asset name must not be blank"
            );
        }
        gradientField = gradientField == null
                ? SpatialField.DEFAULT
                : gradientField;
        noiseConfig = noiseConfig == null
                ? ProceduralNoiseConfig.DEFAULT
                : noiseConfig;
    }

    public ProceduralFieldAsset withGradientField(SpatialField value) {
        return new ProceduralFieldAsset(id, name, value, noiseConfig);
    }

    public ProceduralFieldAsset withNoiseConfig(ProceduralNoiseConfig value) {
        return new ProceduralFieldAsset(id, name, gradientField, value);
    }
}
