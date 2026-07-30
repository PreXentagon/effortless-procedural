package dev.huskuraft.effortless.screen.pattern.procedural;

/**
 * Prevents workbench tooltips from obscuring controls while the pointer is
 * still moving through the interface.
 */
final class ProceduralTooltipDelay {

    private static final long DELAY_NANOS = 1_250_000_000L;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private long stationarySince = System.nanoTime();

    boolean isReady(int mouseX, int mouseY) {
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            stationarySince = System.nanoTime();
            return false;
        }
        return System.nanoTime() - stationarySince >= DELAY_NANOS;
    }
}
