package com.smartassign.core.ml;

import java.util.Map;

/** Result of a predicted-fit request. {@code scores} is keyed by handler id; empty when unavailable. */
public record SuitabilityScores(boolean available, Map<Long, Double> scores, String modelVersion, String reason) {

    public SuitabilityScores {
        scores = scores == null ? Map.of() : Map.copyOf(scores);
    }

    public static SuitabilityScores unavailable(String reason) {
        return new SuitabilityScores(false, Map.of(), null, reason);
    }
}
