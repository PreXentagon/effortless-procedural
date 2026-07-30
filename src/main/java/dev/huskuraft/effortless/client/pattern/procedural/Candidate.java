package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.Objects;

public record Candidate<T>(String id, T value) {

    public Candidate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Candidate id must not be blank");
        }
        Objects.requireNonNull(value, "Candidate value");
    }
}
