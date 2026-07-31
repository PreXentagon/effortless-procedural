package dev.huskuraft.effortless.client.pattern.procedural;

public enum Coordinate {
    X,
    Y,
    Z,
    DISTANCE,
    TRAVERSAL,
    PATH,
    LATERAL,
    DEPTH,
    THICKNESS,
    SLOPE,
    TIP,
    JUNCTION;

    public double sample(GenerationContext<?> context) {
        var custom = context.coordinateLookup().sample(
                this,
                context.position()
        );
        if (custom.isPresent()) {
            return clamp01(custom.getAsDouble());
        }
        return switch (this) {
            case X -> context.bounds().normalizedX(context.position());
            case Y -> context.bounds().normalizedY(context.position());
            case Z -> context.bounds().normalizedZ(context.position());
            case DISTANCE -> context.bounds().normalizedDistance(context.position());
            case TRAVERSAL, PATH -> context.positionCount() <= 1
                    ? 0.0
                    : (double) context.ordinal() / (double) (context.positionCount() - 1);
            case LATERAL -> context.bounds().normalizedX(context.position());
            case DEPTH -> context.bounds().normalizedY(context.position());
            // Structural channels deliberately have neutral fallbacks. They
            // become meaningful only when a generator supplies metadata.
            case THICKNESS, SLOPE, TIP, JUNCTION -> 0.0;
        };
    }

    public boolean isScalar() {
        return switch (this) {
            case DISTANCE, TRAVERSAL, PATH, LATERAL, DEPTH,
                    THICKNESS, SLOPE, TIP, JUNCTION -> true;
            case X, Y, Z -> false;
        };
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
