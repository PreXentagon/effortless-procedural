package dev.huskuraft.effortless.client.pattern.procedural.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;

class ProceduralPatternLibraryTest {

    private static final UUID PARENT =
            UUID.fromString("7d99d34c-ec18-44f3-928e-00e4d79695da");
    private static final UUID CHILD =
            UUID.fromString("40f7555b-abd2-4db4-b9dc-b12c80bb765f");

    @Test
    void compositionMergesInStableOrderAndChildOverridesBlocks() {
        var parent = preset(
                PARENT,
                "Parent",
                List.of(
                        ProceduralBlockEntry.weighted("minecraft:stone", 1.0),
                        ProceduralBlockEntry.weighted("minecraft:dirt", 2.0)
                ),
                ProceduralAdvancedConfig.DEFAULT
        ).withForbiddenAdjacency(List.of(
                new ProceduralForbiddenPair(
                        "minecraft:stone",
                        "minecraft:dirt"
                )
        ));
        var childAdvanced = ProceduralAdvancedConfig.DEFAULT.withParent(
                PARENT.toString(),
                true,
                true
        );
        var child = preset(
                CHILD,
                "Child",
                List.of(
                        ProceduralBlockEntry.weighted("minecraft:stone", 9.0),
                        ProceduralBlockEntry.weighted("minecraft:gravel", 3.0)
                ),
                childAdvanced
        );
        var library = new ProceduralPatternLibrary(
                true,
                CHILD,
                List.of(parent, child)
        );

        var resolution = library.resolvedActivePreset();
        var resolved = resolution.preset().orElseThrow();

        assertTrue(resolution.isSuccess());
        assertEquals(
                List.of(
                        "minecraft:stone",
                        "minecraft:dirt",
                        "minecraft:gravel"
                ),
                resolved.blocks().stream()
                        .map(ProceduralBlockEntry::itemId)
                        .toList()
        );
        assertEquals(9.0, resolved.blocks().get(0).weight());
        assertEquals(parent.forbiddenAdjacency(), resolved.forbiddenAdjacency());
        assertTrue(resolved.advanced().parentPresetId().isBlank());
    }

    @Test
    void compositionCycleReturnsUsefulFailure() {
        var first = preset(
                PARENT,
                "First",
                List.of(ProceduralBlockEntry.weighted("minecraft:stone", 1.0)),
                ProceduralAdvancedConfig.DEFAULT.withParent(
                        CHILD.toString(), true, true
                )
        );
        var second = preset(
                CHILD,
                "Second",
                List.of(ProceduralBlockEntry.weighted("minecraft:dirt", 1.0)),
                ProceduralAdvancedConfig.DEFAULT.withParent(
                        PARENT.toString(), true, true
                )
        );

        var result = new ProceduralPatternLibrary(
                true, PARENT, List.of(first, second)
        ).resolvedActivePreset();

        assertFalse(result.isSuccess());
        assertTrue(result.errors().get(0).contains("cycle"));
    }

    @Test
    void activePresetCyclingWrapsBothDirections() {
        var first = preset(
                PARENT,
                "First",
                List.of(ProceduralBlockEntry.weighted("minecraft:stone", 1.0)),
                ProceduralAdvancedConfig.DEFAULT
        );
        var second = preset(
                CHILD,
                "Second",
                List.of(ProceduralBlockEntry.weighted("minecraft:dirt", 1.0)),
                ProceduralAdvancedConfig.DEFAULT
        );
        var library = new ProceduralPatternLibrary(
                true, PARENT, List.of(first, second)
        );

        assertEquals(
                CHILD,
                library.cycleActivePreset(-1).activePresetId()
        );
        assertEquals(
                CHILD,
                library.cycleActivePreset(1).activePresetId()
        );
    }

    @Test
    void editingOrAddingPresetDoesNotSilentlyChangeActivePreset() {
        var first = preset(
                PARENT,
                "First",
                List.of(ProceduralBlockEntry.weighted("minecraft:stone", 1.0)),
                ProceduralAdvancedConfig.DEFAULT
        );
        var second = preset(
                CHILD,
                "Second",
                List.of(ProceduralBlockEntry.weighted("minecraft:dirt", 1.0)),
                ProceduralAdvancedConfig.DEFAULT
        );
        var library = new ProceduralPatternLibrary(
                true, PARENT, List.of(first, second)
        );

        var edited = library.put(second.withName("Edited second"));
        var added = edited.put(second.duplicate());

        assertEquals(PARENT, edited.activePresetId());
        assertEquals(PARENT, added.activePresetId());
    }

    @Test
    void removingFallbackBlockSelectsFirstRemainingCandidate() {
        var original = preset(
                PARENT,
                "Fallback",
                List.of(
                        ProceduralBlockEntry.weighted("minecraft:stone", 1.0),
                        ProceduralBlockEntry.weighted("minecraft:dirt", 1.0)
                ),
                ProceduralAdvancedConfig.DEFAULT
        );

        var updated = original.withBlocks(List.of(
                ProceduralBlockEntry.weighted("minecraft:dirt", 1.0)
        ));

        assertEquals("minecraft:dirt", updated.fallbackItemId());
    }

    @Test
    void compositionDepthIsBounded() {
        var ids = new ArrayList<UUID>();
        for (int index = 0;
             index <= ProceduralPatternLibrary.MAX_COMPOSITION_DEPTH;
             index++) {
            ids.add(new UUID(0L, index + 1L));
        }
        var presets = new ArrayList<ProceduralPatternPreset>();
        for (int index = 0; index < ids.size(); index++) {
            var advanced = index + 1 < ids.size()
                    ? ProceduralAdvancedConfig.DEFAULT.withParent(
                            ids.get(index + 1).toString(),
                            true,
                            true
                    )
                    : ProceduralAdvancedConfig.DEFAULT;
            presets.add(preset(
                    ids.get(index),
                    "Layer " + index,
                    List.of(ProceduralBlockEntry.weighted(
                            "minecraft:stone",
                            1.0
                    )),
                    advanced
            ));
        }

        var resolution = new ProceduralPatternLibrary(
                true,
                ids.get(0),
                presets
        ).resolvedActivePreset();

        assertFalse(resolution.isSuccess());
        assertTrue(resolution.errors().get(0).contains("exceeds"));
    }

    @Test
    void duplicatePresetIdsAreRejected() {
        var first = preset(
                PARENT,
                "First",
                List.of(ProceduralBlockEntry.weighted("minecraft:stone", 1.0)),
                ProceduralAdvancedConfig.DEFAULT
        );
        var second = preset(
                PARENT,
                "Duplicate",
                List.of(ProceduralBlockEntry.weighted("minecraft:dirt", 1.0)),
                ProceduralAdvancedConfig.DEFAULT
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProceduralPatternLibrary(
                        true,
                        PARENT,
                        List.of(first, second)
                )
        );
    }

    private static ProceduralPatternPreset preset(
            UUID id,
            String name,
            List<ProceduralBlockEntry> blocks,
            ProceduralAdvancedConfig advanced
    ) {
        return new ProceduralPatternPreset(
                id,
                name,
                0L,
                8,
                blocks.get(0).itemId(),
                blocks,
                false,
                0,
                0.05,
                false,
                Coordinate.Y,
                false,
                0.1,
                0L,
                false,
                List.of(),
                List.of(),
                0,
                List.of(),
                advanced
        );
    }
}
