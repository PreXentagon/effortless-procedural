package dev.huskuraft.effortless.screen.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClipboardPaneLayoutTest {

    @Test
    void compactHistoryUsesListAndActionRailWithoutOverlap() {
        var frame = WorkbenchScreenLayout.create(428, 256);
        var panes = ClipboardPaneLayout.create(frame, true);

        assertTrue(panes.compact());
        assertEquals(frame.left(), panes.listX());
        assertEquals(panes.workspaceWidth(), panes.listWidth());
        assertFalse(panes.previewVisible());
        assertEquals(
                panes.workspaceX() + panes.workspaceWidth()
                        + WorkbenchScreenLayout.GAP,
                panes.actionsX()
        );
        assertEquals(frame.right(), panes.actionsX() + panes.actionsWidth());
    }

    @Test
    void compactCurrentReservesWorkspaceForTransformControls() {
        var frame = WorkbenchScreenLayout.create(428, 256);
        var panes = ClipboardPaneLayout.create(frame, false);

        assertTrue(panes.compact());
        assertEquals(0, panes.listWidth());
        assertFalse(panes.previewVisible());
        assertTrue(panes.workspaceWidth() >= 200);
    }

    @Test
    void wideHistoryKeepsListPreviewAndActionsDisjoint() {
        var frame = WorkbenchScreenLayout.create(960, 540);
        var panes = ClipboardPaneLayout.create(frame, true);

        assertFalse(panes.compact());
        assertTrue(panes.previewVisible());
        assertTrue(panes.listX() + panes.listWidth() < panes.previewX());
        assertTrue(panes.previewX() + panes.previewWidth()
                < panes.actionsX());
        assertEquals(frame.right(), panes.actionsX() + panes.actionsWidth());
    }
}
