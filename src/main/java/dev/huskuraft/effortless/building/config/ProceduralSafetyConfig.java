package dev.huskuraft.effortless.building.config;

/**
 * Client-local procedural execution budgets. These values never enter a
 * server-bound context; the server's advertised permission, reach and volume
 * limits remain authoritative.
 */
public record ProceduralSafetyConfig(
        boolean showPreparationMessages,
        int asyncPreviewPositionThreshold,
        int maxCompiledPositions,
        int maxEstimatedMemoryMiB,
        int maxPacketMiB,
        int maxEstimatedWorkMillions,
        int roadPreviewDistance
) {

    public static final int MAX_COMPILED_POSITIONS_LIMIT = 5_000_000;
    public static final int MAX_MEMORY_MIB_LIMIT = 4_096;
    public static final int MAX_PACKET_MIB_LIMIT = 64;
    public static final int MAX_WORK_MILLIONS_LIMIT = 1_000_000;
    public static final int MAX_PREVIEW_DISTANCE = 1_024;

    public static final ProceduralSafetyConfig DEFAULT =
            new ProceduralSafetyConfig(
                    true,
                    4_096,
                    250_000,
                    64,
                    7,
                    250,
                    128
            );

    public long maxEstimatedMemoryBytes() {
        return maxEstimatedMemoryMiB * 1024L * 1024L;
    }

    public int maxPacketBytes() {
        return maxPacketMiB * 1024 * 1024;
    }

    public long maxEstimatedWork() {
        return maxEstimatedWorkMillions * 1_000_000L;
    }

    public ProceduralSafetyConfig withPreparationMessages(boolean value) {
        return new ProceduralSafetyConfig(
                value, asyncPreviewPositionThreshold, maxCompiledPositions,
                maxEstimatedMemoryMiB, maxPacketMiB,
                maxEstimatedWorkMillions, roadPreviewDistance
        );
    }

    public ProceduralSafetyConfig withAsyncThreshold(int value) {
        return new ProceduralSafetyConfig(
                showPreparationMessages, value, maxCompiledPositions,
                maxEstimatedMemoryMiB, maxPacketMiB,
                maxEstimatedWorkMillions, roadPreviewDistance
        );
    }

    public ProceduralSafetyConfig withMaxPositions(int value) {
        return new ProceduralSafetyConfig(
                showPreparationMessages, asyncPreviewPositionThreshold, value,
                maxEstimatedMemoryMiB, maxPacketMiB,
                maxEstimatedWorkMillions, roadPreviewDistance
        );
    }

    public ProceduralSafetyConfig withMaxMemoryMiB(int value) {
        return new ProceduralSafetyConfig(
                showPreparationMessages, asyncPreviewPositionThreshold,
                maxCompiledPositions, value, maxPacketMiB,
                maxEstimatedWorkMillions, roadPreviewDistance
        );
    }

    public ProceduralSafetyConfig withMaxPacketMiB(int value) {
        return new ProceduralSafetyConfig(
                showPreparationMessages, asyncPreviewPositionThreshold,
                maxCompiledPositions, maxEstimatedMemoryMiB, value,
                maxEstimatedWorkMillions, roadPreviewDistance
        );
    }

    public ProceduralSafetyConfig withMaxWorkMillions(int value) {
        return new ProceduralSafetyConfig(
                showPreparationMessages, asyncPreviewPositionThreshold,
                maxCompiledPositions, maxEstimatedMemoryMiB, maxPacketMiB,
                value, roadPreviewDistance
        );
    }

    public ProceduralSafetyConfig withRoadPreviewDistance(int value) {
        return new ProceduralSafetyConfig(
                showPreparationMessages, asyncPreviewPositionThreshold,
                maxCompiledPositions, maxEstimatedMemoryMiB, maxPacketMiB,
                maxEstimatedWorkMillions, value
        );
    }
}
