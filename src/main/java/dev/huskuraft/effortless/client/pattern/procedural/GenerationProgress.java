package dev.huskuraft.effortless.client.pattern.procedural;

@FunctionalInterface
public interface GenerationProgress {

    GenerationProgress NONE = (stage, completed, total) -> {
    };

    void update(Stage stage, int completed, int total);

    enum Stage {
        GENERATING,
        REPAIRING,
        CLEANING
    }
}
