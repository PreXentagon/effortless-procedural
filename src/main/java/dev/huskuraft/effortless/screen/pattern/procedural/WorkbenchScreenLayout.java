package dev.huskuraft.effortless.screen.pattern.procedural;

/**
 * Shared geometry for the full-screen workbench satellite screens.
 *
 * <p>The frame and its widgets must use the same bounds. Keeping those
 * calculations here prevents the header, tabs, body and footer from drifting
 * apart at different GUI scales.</p>
 */
record WorkbenchScreenLayout(
        int screenWidth,
        int screenHeight,
        int left,
        int right,
        int headerTop,
        int headerBottom,
        int tabsTop,
        int bodyTop,
        int bodyBottom,
        int footerTop,
        int footerBottom,
        int footerY
) {

    static final int MARGIN = 10;
    static final int HEADER_HEIGHT = 28;
    static final int FOOTER_HEIGHT = 30;
    static final int GAP = 7;
    static final int CONTROL_HEIGHT = 20;
    static final int MAX_WIDTH = 1100;

    static WorkbenchScreenLayout create(int screenWidth, int screenHeight) {
        return create(screenWidth, screenHeight, MAX_WIDTH);
    }

    static WorkbenchScreenLayout create(
            int screenWidth,
            int screenHeight,
            int maximumWidth
    ) {
        int availableWidth = Math.max(1, screenWidth - MARGIN * 2);
        int width = Math.min(Math.max(1, maximumWidth), availableWidth);
        int left = (screenWidth - width) / 2;
        int right = left + width;
        int headerTop = MARGIN;
        int headerBottom = headerTop + HEADER_HEIGHT;
        int tabsTop = headerBottom + GAP;
        int bodyTop = tabsTop + CONTROL_HEIGHT + GAP;
        int footerTop = Math.max(bodyTop + 40,
                screenHeight - MARGIN - FOOTER_HEIGHT);
        int bodyBottom = Math.max(bodyTop + 40, footerTop - GAP);
        int footerBottom = Math.max(footerTop + FOOTER_HEIGHT,
                screenHeight - MARGIN);
        int footerY = footerTop + (FOOTER_HEIGHT - CONTROL_HEIGHT) / 2;
        return new WorkbenchScreenLayout(
                screenWidth, screenHeight,
                left, right,
                headerTop, headerBottom,
                tabsTop, bodyTop, bodyBottom,
                footerTop, footerBottom, footerY
        );
    }

    int width() {
        return right - left;
    }

    int bodyHeight() {
        return bodyBottom - bodyTop;
    }

    int centerX() {
        return left + width() / 2;
    }

    Bounds tab(int index, int count) {
        if (count <= 0 || index < 0 || index >= count) {
            throw new IllegalArgumentException("Invalid tab index/count");
        }
        int usable = Math.max(count, width() - GAP * (count - 1));
        int baseWidth = usable / count;
        int x = left + index * (baseWidth + GAP);
        int tabRight = index == count - 1 ? right : x + baseWidth;
        return new Bounds(x, tabsTop, tabRight - x, CONTROL_HEIGHT);
    }

    Bounds centeredBody(int preferredWidth) {
        int width = Math.min(Math.max(1, preferredWidth), width());
        return new Bounds(
                centerX() - width / 2,
                bodyTop,
                width,
                bodyHeight()
        );
    }

    record Bounds(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }
}
