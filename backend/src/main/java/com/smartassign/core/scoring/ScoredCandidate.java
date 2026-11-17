package com.smartassign.core.scoring;

import com.smartassign.core.HandlerSnapshot;

import java.util.List;

/**
 * A ranked eligible handler. totalScore is a 0-100 suitability score built from the weighted factors
 * (it is a weighted heuristic, NOT a probability). mlScore holds the raw predicted-fit estimate when one was used.
 */
public record ScoredCandidate(
        HandlerSnapshot handler,
        int rank,
        double totalScore,
        double scoreBeforeFairness,
        List<FactorScore> factors,
        Double mlScore,
        boolean fairnessAdjusted,
        double distanceKm,
        String explanation) {

    public ScoredCandidate withRankAndExplanation(int newRank, String newExplanation) {
        return new ScoredCandidate(handler, newRank, totalScore, scoreBeforeFairness, factors, mlScore,
                fairnessAdjusted, distanceKm, newExplanation);
    }
}
