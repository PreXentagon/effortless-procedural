package dev.huskuraft.effortless.client.pattern.procedural.config;

/**
 * Client-side candidate-pool source used while compiling a pattern recipe.
 *
 * <p>The non-custom values are resolved from the current player immediately
 * before procedural generation. The compiled server-bound randomizer always
 * remains a stock {@code CUSTOMIZE} sequence containing explicit choices.</p>
 */
public enum PatternMaterialSource {
    CUSTOM_PALETTE,
    INVENTORY,
    HOTBAR,
    HANDS;

    public String displayName() {
        return switch (this) {
            case CUSTOM_PALETTE -> "Custom palette";
            case INVENTORY -> "Live inventory";
            case HOTBAR -> "Live hotbar";
            case HANDS -> "Main + offhand";
        };
    }
}
