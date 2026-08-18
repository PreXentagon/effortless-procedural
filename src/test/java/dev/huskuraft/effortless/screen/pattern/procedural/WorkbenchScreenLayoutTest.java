package dev.huskuraft.effortless.screen.pattern.procedural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkbenchScreenLayoutTest {

    @Test
    void centersAndCapsWideScreens() {
        var layout = WorkbenchScreenLayout.create(1920, 1080);

        assertEquals(1100, layout.width());
        assertEquals(410, layout.left());
        assertEquals(1510, layout.right());
        assertEquals(layout.left() + layout.width() / 2,
                layout.centerX());
        assertTrue(layout.headerBottom() < layout.tabsTop());
        assertTrue(layout.tabsTop() < layout.bodyTop());
        assertTrue(layout.bodyBottom() < layout.footerTop());
        assertEquals(1070, layout.footerBottom());
    }

    @Test
    void usesAvailableWidthAtCommonScaledResolution() {
        var layout = WorkbenchScreenLayout.create(960, 540);

        assertEquals(10, layout.left());
        assertEquals(950, layout.right());
        assertEquals(940, layout.width());
        assertEquals(500, layout.footerTop());
        assertEquals(505, layout.footerY());
    }

    @Test
    void tabsFillTheFrameWithoutEscapingIt() {
        var layout = WorkbenchScreenLayout.create(960, 540);
        var first = layout.tab(0, 4);
        var second = layout.tab(1, 4);
        var last = layout.tab(3, 4);

        assertEquals(layout.left(), first.x());
        assertEquals(WorkbenchScreenLayout.GAP,
                second.x() - first.right());
        assertEquals(layout.right(), last.right());
        assertEquals(layout.tabsTop(), first.y());
        assertEquals(WorkbenchScreenLayout.CONTROL_HEIGHT,
                first.height());
    }

    @Test
    void centersBoundedFormsInsideTheSameFrame() {
        var layout = WorkbenchScreenLayout.create(960, 540);
        var form = layout.centeredBody(720);

        assertEquals(720, form.width());
        assertEquals(layout.centerX(), form.x() + form.width() / 2);
        assertEquals(layout.bodyTop(), form.y());
        assertEquals(layout.bodyBottom(), form.bottom());
    }

    @Test
    void rejectsInvalidTabRequests() {
        var layout = WorkbenchScreenLayout.create(960, 540);

        assertThrows(IllegalArgumentException.class,
                () -> layout.tab(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> layout.tab(4, 4));
    }
}
