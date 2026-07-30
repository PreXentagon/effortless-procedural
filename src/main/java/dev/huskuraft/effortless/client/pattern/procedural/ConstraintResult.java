package dev.huskuraft.effortless.client.pattern.procedural;

public record ConstraintResult(boolean allowed, String reason) {

    public static ConstraintResult allow() {
        return new ConstraintResult(true, "");
    }

    public static ConstraintResult reject(String reason) {
        return new ConstraintResult(false, reason);
    }
}
