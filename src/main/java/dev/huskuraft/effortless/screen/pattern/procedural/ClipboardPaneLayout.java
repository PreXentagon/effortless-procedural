package dev.huskuraft.effortless.screen.pattern.procedural;

/**
 * Responsive pane geometry for the clipboard workspace.
 *
 * <p>At compact GUI widths there is not enough room for a list, a rendered
 * snapshot, and the action rail. The list (or compact transform controls)
 * therefore takes the workspace pane and the snapshot preview is omitted.
 */
record ClipboardPaneLayout(
        boolean compact,
        int workspaceX,
        int workspaceWidth,
        int listX,
        int listWidth,
        int previewX,
        int previewWidth,
        int actionsX,
        int actionsWidth
) {

    private static final int COMPACT_THRESHOLD = 640;

    static ClipboardPaneLayout create(
            WorkbenchScreenLayout layout,
            boolean hasSnapshotList
    ) {
        boolean compact = layout.width() < COMPACT_THRESHOLD;
        int gap = WorkbenchScreenLayout.GAP;
        int actionsWidth = compact
                ? 160
                : Math.clamp(layout.width() / 4, 190, 270);
        int actionsX = layout.right() - actionsWidth;
        int workspaceWidth = actionsX - layout.left() - gap;

        int listWidth = 0;
        int previewX = layout.left();
        int previewWidth = compact ? 0 : workspaceWidth;
        if (hasSnapshotList) {
            listWidth = compact
                    ? workspaceWidth
                    : Math.clamp(workspaceWidth / 3, 170, 300);
            previewX = layout.left() + listWidth + gap;
            previewWidth = compact
                    ? 0
                    : Math.max(60, actionsX - previewX - gap);
        }

        return new ClipboardPaneLayout(
                compact,
                layout.left(), workspaceWidth,
                layout.left(), listWidth,
                previewX, previewWidth,
                actionsX, actionsWidth
        );
    }

    boolean previewVisible() {
        return previewWidth > 0;
    }
}
