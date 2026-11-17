package com.smartassign.core.scoring;

import java.util.List;
import java.util.Map;

/** Ranked list plus the weights that were actually applied and whether predicted-fit scores were used. */
public record RankingResult(List<ScoredCandidate> candidates, Map<Factor, Double> effectiveWeights, boolean mlUsed) {

    public boolean isEmpty() {
        return candidates.isEmpty();
    }
}
