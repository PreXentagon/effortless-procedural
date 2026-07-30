package dev.huskuraft.effortless.screen.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternLibrary;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralPatternPreset;
import dev.huskuraft.effortless.client.pattern.procedural.config.PatternMaterialSource;

class ProceduralWorkbenchSessionTest {

    @Test
    void textDraftsSurvivePresetAndTabRecreationUntilFinalMaterialize() {
        var first = ProceduralPatternPreset.DEFAULT;
        var second = first.duplicate().withName("Second");
        var session = new ProceduralWorkbenchSession(new ProceduralPatternLibrary(
                true,
                first.id(),
                List.of(first, second)
        ));

        session.setNameDraft("Renamed first");
        session.setSeedDraft("991");
        session.setNoiseSaltDraft("-44");
        session.select(second.id());
        session.setNameDraft("Renamed second");
        session.select(first.id());

        assertEquals("Renamed first", session.selectedTextDraft().name());
        assertEquals("991", session.selectedTextDraft().seed());
        assertEquals("-44", session.selectedTextDraft().noiseSalt());

        var saved = session.materialize();
        assertEquals("Renamed first", saved.presets().get(0).name());
        assertEquals(991L, saved.presets().get(0).seed());
        assertEquals(-44L, saved.presets().get(0).noiseSalt());
        assertEquals("Renamed second", saved.presets().get(1).name());
    }

    @Test
    void malformedTextDraftFailsAtomicallyAtFinalSaveBoundary() {
        var session = new ProceduralWorkbenchSession(
                ProceduralPatternLibrary.DEFAULT
        );
        long originalSeed = session.selectedPreset().seed();
        session.setSeedDraft("-");

        assertThrows(IllegalArgumentException.class, session::materialize);
        assertEquals(originalSeed, session.selectedPreset().seed());
        assertEquals("-", session.selectedTextDraft().seed());
    }

    @Test
    void libraryActionsKeepOneExplicitActivePreset() {
        var session = new ProceduralWorkbenchSession(
                ProceduralPatternLibrary.DEFAULT.withEnabled(true)
        );
        var originalActive = session.library().activePresetId();

        session.duplicateSelected();
        var duplicate = session.selectedPresetId();
        assertNotEquals(originalActive, duplicate);
        assertEquals(originalActive, session.library().activePresetId());

        session.useSelected();
        assertEquals(duplicate, session.library().activePresetId());
        session.deleteSelected();
        assertEquals(
                session.selectedPresetId(),
                session.library().activePresetId()
        );
    }

    @Test
    void paletteOperationsPreserveOrderAndKeepFallbackValid() {
        var session = new ProceduralWorkbenchSession(
                ProceduralPatternLibrary.DEFAULT
        );
        int added = session.addBlock();
        String addedId = session.selectedPreset().blocks().get(added).itemId();

        int moved = session.moveBlock(added, -1);
        assertEquals(added - 1, moved);
        assertEquals(
                addedId,
                session.selectedPreset().blocks().get(moved).itemId()
        );

        session.replaceSelected(
                preset -> preset.withFallbackItemId(addedId)
        );
        session.deleteBlock(moved);
        assertEquals(2, session.selectedPreset().blocks().size());
        org.junit.jupiter.api.Assertions.assertTrue(
                session.selectedPreset().blocks().stream().anyMatch(
                        entry -> entry.itemId().equals(
                                session.selectedPreset().fallbackItemId()
                        )
                )
        );
    }

    @Test
    void selectingARecipeDoesNotMakeTheSharedDraftDirty() {
        var first = ProceduralPatternPreset.DEFAULT;
        var second = first.duplicate().withName("Second");
        var session = new ProceduralWorkbenchSession(new ProceduralPatternLibrary(
                true,
                first.id(),
                List.of(first, second)
        ));

        session.select(second.id());
        session.selectedTextDraft();

        assertFalse(session.isDirty());
    }

    @Test
    void undoRedoAndSavedMarkerCoverWorkbenchMutations() {
        var session = new ProceduralWorkbenchSession(
                ProceduralPatternLibrary.DEFAULT
        );
        assertFalse(session.isDirty());

        session.setMaterialSource(PatternMaterialSource.HOTBAR);
        assertTrue(session.isDirty());
        assertTrue(session.canUndo());
        assertEquals(
                PatternMaterialSource.HOTBAR,
                session.selectedPreset().materialSource()
        );

        session.undo();
        assertFalse(session.isDirty());
        assertTrue(session.canRedo());
        assertEquals(
                PatternMaterialSource.CUSTOM_PALETTE,
                session.selectedPreset().materialSource()
        );

        session.redo();
        assertTrue(session.isDirty());
        assertEquals(
                PatternMaterialSource.HOTBAR,
                session.selectedPreset().materialSource()
        );

        session.markSaved();
        assertFalse(session.isDirty());
    }

    @Test
    void invalidTextRemainsUndoableWithoutPartiallyChangingTheRecipe() {
        var session = new ProceduralWorkbenchSession(
                ProceduralPatternLibrary.DEFAULT
        );
        long original = session.selectedPreset().seed();

        session.setSeedDraft("-");
        assertTrue(session.isDirty());
        assertEquals(original, session.selectedPreset().seed());

        session.undo();
        assertFalse(session.isDirty());
        assertEquals(Long.toString(original), session.selectedTextDraft().seed());
    }
}
