package dev.huskuraft.effortless.client.tree;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;

/** Resolves role recipes without ever following another tree generator. */
public final class TreeMaterialResolver {

    private TreeMaterialResolver() {
    }

    public static Result resolve(
            ProceduralPatternLibrary library,
            ProceduralPatternPreset treePreset
    ) {
        var config = treePreset.advanced().treeGeneration();
        var recipes = new EnumMap<TreeCell.Role, ProceduralPatternPreset>(
                TreeCell.Role.class
        );
        var errors = new ArrayList<String>();
        recipes.put(
                TreeCell.Role.TRUNK,
                resolveRole(
                        library, treePreset, config.trunkRecipeId(),
                        "trunk", errors
                )
        );
        recipes.put(
                TreeCell.Role.BRANCH,
                resolveRole(
                        library, treePreset, config.branchRecipeId(),
                        "branch", errors
                )
        );
        recipes.put(
                TreeCell.Role.CANOPY,
                resolveRole(
                        library, treePreset, config.foliageRecipeId(),
                        "foliage", errors
                )
        );
        recipes.put(
                TreeCell.Role.ROOT,
                resolveRole(
                        library, treePreset, config.rootRecipeId(),
                        "root", errors
                )
        );
        return errors.isEmpty()
                ? Result.success(recipes)
                : Result.failure(errors);
    }

    private static ProceduralPatternPreset resolveRole(
            ProceduralPatternLibrary library,
            ProceduralPatternPreset fallback,
            String reference,
            String role,
            List<String> errors
    ) {
        if (reference == null || reference.isBlank()) {
            return fallback;
        }
        UUID id;
        try {
            id = UUID.fromString(reference);
        } catch (IllegalArgumentException exception) {
            errors.add("Tree " + role + " recipe id is invalid");
            return fallback;
        }
        var resolution = library.resolvedPreset(id);
        if (!resolution.isSuccess()) {
            errors.add("Tree " + role + " recipe: "
                    + String.join("; ", resolution.errors()));
            return fallback;
        }
        // Only the referenced preset's material/rule configuration is used.
        // Its TreeGenerationConfig is deliberately ignored, preventing nested
        // tree execution and component cycles.
        return resolution.preset().orElseThrow();
    }

    public record Result(
            Map<TreeCell.Role, ProceduralPatternPreset> recipes,
            List<String> errors
    ) {

        public Result {
            recipes = Map.copyOf(recipes);
            errors = List.copyOf(errors);
        }

        public static Result success(
                Map<TreeCell.Role, ProceduralPatternPreset> recipes
        ) {
            return new Result(recipes, List.of());
        }

        public static Result failure(List<String> errors) {
            return new Result(Map.of(), errors);
        }

        public boolean isSuccess() {
            return errors.isEmpty();
        }
    }
}
