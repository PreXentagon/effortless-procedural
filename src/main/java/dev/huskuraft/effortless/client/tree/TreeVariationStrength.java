package dev.huskuraft.effortless.client.tree;

/** Amount of style-preserving parameter mutation applied per variant. */
public enum TreeVariationStrength {
    OFF(0.0),
    SUBTLE(0.45),
    NATURAL(1.0),
    EXPRESSIVE(1.55),
    CUSTOM(1.0);

    private final double scale;

    TreeVariationStrength(double scale) {
        this.scale = scale;
    }

    public double scale() {
        return scale;
    }
}
