package dev.huskuraft.effortless.client.pattern.procedural.config;

import dev.huskuraft.effortless.client.pattern.procedural.CandidateQuotaRule;

public record ProceduralQuotaRule(
        String itemId,
        double minimum,
        double maximum,
        CandidateQuotaRule.Unit unit
) {
}
