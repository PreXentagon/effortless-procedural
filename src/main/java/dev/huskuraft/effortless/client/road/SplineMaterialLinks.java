package dev.huskuraft.effortless.client.road;

/** Client-local linked pattern recipes for spline material regions. */
public record SplineMaterialLinks(
        String surfaceRecipeId,
        String shoulderRecipeId,
        String foundationRecipeId,
        String curbRecipeId,
        String markingRecipeId,
        String damageRecipeId
) {

    public static final SplineMaterialLinks DEFAULT = new SplineMaterialLinks(
            "", "", "", "", "", ""
    );

    public SplineMaterialLinks {
        surfaceRecipeId = normalize(surfaceRecipeId);
        shoulderRecipeId = normalize(shoulderRecipeId);
        foundationRecipeId = normalize(foundationRecipeId);
        curbRecipeId = normalize(curbRecipeId);
        markingRecipeId = normalize(markingRecipeId);
        damageRecipeId = normalize(damageRecipeId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean hasLinks() {
        return !surfaceRecipeId.isBlank()
                || !shoulderRecipeId.isBlank()
                || !foundationRecipeId.isBlank()
                || !curbRecipeId.isBlank()
                || !markingRecipeId.isBlank()
                || !damageRecipeId.isBlank();
    }
}
