package dev.huskuraft.effortless.client.pattern.procedural.config;

public record ProceduralBlockEntry(
        String itemId,
        double weight,
        double gradientStart,
        double gradientEnd,
        double noiseMinimum,
        double noiseMaximum,
        double gradientPosition
) {

    public ProceduralBlockEntry(
            String itemId,
            double weight,
            double gradientStart,
            double gradientEnd,
            double noiseMinimum,
            double noiseMaximum
    ) {
        this(
                itemId,
                weight,
                gradientStart,
                gradientEnd,
                noiseMinimum,
                noiseMaximum,
                -1.0
        );
    }

    public ProceduralBlockEntry {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Item id must not be blank");
        }
    }

    public static ProceduralBlockEntry weighted(String itemId, double weight) {
        return new ProceduralBlockEntry(
                itemId,
                weight,
                1.0,
                1.0,
                1.0,
                1.0,
                -1.0
        );
    }
}
