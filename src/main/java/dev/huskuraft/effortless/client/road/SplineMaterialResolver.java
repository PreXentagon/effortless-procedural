package dev.huskuraft.effortless.client.road;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;

/** Resolves spline material regions without executing linked generators. */
public final class SplineMaterialResolver {

    private SplineMaterialResolver() {
    }

    public static Result resolve(
            ProceduralPatternLibrary library,
            ProceduralPatternPreset splinePreset
    ) {
        var links = splinePreset.advanced().roadProfile().materialLinks();
        var recipes = new EnumMap<RoadCell.Role, ProceduralPatternPreset>(
                RoadCell.Role.class
        );
        var errors = new ArrayList<String>();
        recipes.put(
                RoadCell.Role.SURFACE,
                resolveRole(
                        library, splinePreset, links.surfaceRecipeId(),
                        "surface", errors
                )
        );
        recipes.put(
                RoadCell.Role.SHOULDER,
                resolveRole(
                        library, splinePreset, links.shoulderRecipeId(),
                        "shoulder", errors
                )
        );
        recipes.put(
                RoadCell.Role.FOUNDATION,
                resolveRole(
                        library, splinePreset, links.foundationRecipeId(),
                        "foundation", errors
                )
        );
        recipes.put(
                RoadCell.Role.CURB,
                resolveRole(
                        library, splinePreset, links.curbRecipeId(),
                        "curb", errors
                )
        );
        recipes.put(
                RoadCell.Role.MARKING,
                resolveRole(
                        library, splinePreset, links.markingRecipeId(),
                        "marking", errors
                )
        );
        var damage = resolveOptionalRole(
                library, links.damageRecipeId(), "damage", errors
        );
        var bands = new ArrayList<ProceduralPatternPreset>();
        var configuredBands = splinePreset.advanced().roadProfile()
                .crossSectionBands();
        for (int index = 0; index < configuredBands.size(); index++) {
            bands.add(resolveRole(
                    library, splinePreset,
                    configuredBands.get(index).recipeId(),
                    "band " + (index + 1), errors
            ));
        }
        var cutout = splinePreset.advanced().roadProfile().cutout();
        Optional<ProceduralPatternPreset> cutoutWalls = Optional.empty();
        Optional<ProceduralPatternPreset> cutoutFloor = Optional.empty();
        if (cutout.enabled() && cutout.lineWalls()) {
            cutoutWalls = Optional.of(resolveRole(
                    library, splinePreset, cutout.wallRecipeId(),
                    "cutout walls", errors
            ));
        }
        if (cutout.enabled() && cutout.lineFloor()) {
            cutoutFloor = Optional.of(resolveRole(
                    library, splinePreset, cutout.floorRecipeId(),
                    "cutout floor", errors
            ));
        }
        return errors.isEmpty()
                ? Result.success(
                        recipes, damage, bands,
                        cutoutWalls, cutoutFloor
                )
                : Result.failure(errors);
    }

    private static Optional<ProceduralPatternPreset> resolveOptionalRole(
            ProceduralPatternLibrary library,
            String reference,
            String role,
            List<String> errors
    ) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        UUID id;
        try {
            id = UUID.fromString(reference);
        } catch (IllegalArgumentException exception) {
            errors.add("Spline " + role + " recipe id is invalid");
            return Optional.empty();
        }
        var resolution = library.resolvedPreset(id);
        if (!resolution.isSuccess()) {
            errors.add("Spline " + role + " recipe: "
                    + String.join("; ", resolution.errors()));
            return Optional.empty();
        }
        return resolution.preset();
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
            errors.add("Spline " + role + " recipe id is invalid");
            return fallback;
        }
        var resolution = library.resolvedPreset(id);
        if (!resolution.isSuccess()) {
            errors.add("Spline " + role + " recipe: "
                    + String.join("; ", resolution.errors()));
            return fallback;
        }
        return resolution.preset().orElseThrow();
    }

    public record Result(
            Map<RoadCell.Role, ProceduralPatternPreset> recipes,
            Optional<ProceduralPatternPreset> damageRecipe,
            List<ProceduralPatternPreset> bandRecipes,
            Optional<ProceduralPatternPreset> cutoutWallRecipe,
            Optional<ProceduralPatternPreset> cutoutFloorRecipe,
            List<String> errors
    ) {
        public Result {
            recipes = Map.copyOf(recipes);
            damageRecipe = damageRecipe == null
                    ? Optional.empty()
                    : damageRecipe;
            bandRecipes = List.copyOf(bandRecipes);
            cutoutWallRecipe = cutoutWallRecipe == null
                    ? Optional.empty() : cutoutWallRecipe;
            cutoutFloorRecipe = cutoutFloorRecipe == null
                    ? Optional.empty() : cutoutFloorRecipe;
            errors = List.copyOf(errors);
        }

        static Result success(
                Map<RoadCell.Role, ProceduralPatternPreset> recipes,
                Optional<ProceduralPatternPreset> damageRecipe,
                List<ProceduralPatternPreset> bandRecipes,
                Optional<ProceduralPatternPreset> cutoutWallRecipe,
                Optional<ProceduralPatternPreset> cutoutFloorRecipe
        ) {
            return new Result(
                    recipes, damageRecipe, bandRecipes,
                    cutoutWallRecipe, cutoutFloorRecipe, List.of()
            );
        }

        static Result failure(List<String> errors) {
            return new Result(
                    Map.of(), Optional.empty(), List.of(),
                    Optional.empty(), Optional.empty(), errors
            );
        }

        public boolean isSuccess() {
            return errors.isEmpty();
        }
    }
}
