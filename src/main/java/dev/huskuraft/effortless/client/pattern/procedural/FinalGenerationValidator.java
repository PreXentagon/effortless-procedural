package dev.huskuraft.effortless.client.pattern.procedural;

import java.util.List;
import java.util.Map;

/**
 * Validates invariants that only have a definitive answer once every target
 * position has a candidate. Implementations must be deterministic and
 * side-effect free.
 */
public interface FinalGenerationValidator<T> {

    List<String> validateFinal(
            Map<GridPosition, Candidate<T>> generated,
            int positionCount
    );
}
