package dev.huskuraft.effortless.client.pattern.procedural;

public enum Coordinate {
    X,
    Y,
    Z,
    DISTANCE,
    TRAVERSAL;

    public double sample(GenerationContext<?> context) {
        return switch (this) {
            case X -> context.bounds().normalizedX(context.position());
            case Y -> context.bounds().normalizedY(context.position());
            case Z -> context.bounds().normalizedZ(context.position());
            case DISTANCE -> context.bounds().normalizedDistance(context.position());
            case TRAVERSAL -> context.positionCount() <= 1
                    ? 0.0
                    : (double) context.ordinal() / (double) (context.positionCount() - 1);
        };
    }
}
