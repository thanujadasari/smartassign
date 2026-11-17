package com.smartassign.core.scoring;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configurable factor weights in percent. They must total exactly 100.
 * fairnessThreshold: projected utilisation above which the fairness penalty applies (0.1 - 1.0).
 * fairnessPenalty: fraction by which the total score is reduced when the threshold is exceeded (0 - 1).
 */
public record RankingWeights(
        double expertise,
        double workload,
        double experience,
        double successRate,
        double similarClaims,
        double proximity,
        double mlSuitability,
        double fairnessThreshold,
        double fairnessPenalty,
        boolean useMl) {

    public static final double TOLERANCE = 0.01;

    public static RankingWeights defaults() {
        return new RankingWeights(10, 20, 10, 15, 10, 15, 20, 0.85, 0.15, true);
    }

    public double total() {
        return expertise + workload + experience + successRate + similarClaims + proximity + mlSuitability;
    }

    /** Throws IllegalArgumentException with a business-readable message when invalid. */
    public void validate() {
        Map<Factor, Double> m = asMap();
        for (Map.Entry<Factor, Double> e : m.entrySet()) {
            if (e.getValue() < 0 || e.getValue() > 100 || Double.isNaN(e.getValue())) {
                throw new IllegalArgumentException(e.getKey().label() + " weight must be between 0 and 100");
            }
        }
        if (Math.abs(total() - 100.0) > TOLERANCE) {
            throw new IllegalArgumentException(String.format("Weights must total 100%% (currently %.2f%%)", total()));
        }
        if (fairnessThreshold < 0.1 || fairnessThreshold > 1.0) {
            throw new IllegalArgumentException("Fairness threshold must be between 10% and 100%");
        }
        if (fairnessPenalty < 0 || fairnessPenalty > 1.0) {
            throw new IllegalArgumentException("Fairness penalty must be between 0% and 100%");
        }
    }

    public Map<Factor, Double> asMap() {
        Map<Factor, Double> m = new EnumMap<>(Factor.class);
        m.put(Factor.EXPERTISE, expertise);
        m.put(Factor.WORKLOAD, workload);
        m.put(Factor.EXPERIENCE, experience);
        m.put(Factor.SUCCESS_RATE, successRate);
        m.put(Factor.SIMILAR_CLAIMS, similarClaims);
        m.put(Factor.PROXIMITY, proximity);
        m.put(Factor.ML_SUITABILITY, mlSuitability);
        return m;
    }

    /**
     * Weights actually applied. When predicted-fit scores are not available (or disabled), the
     * ML weight is redistributed proportionally over the remaining factors so the total stays 100.
     */
    public Map<Factor, Double> effective(boolean mlScoresAvailable) {
        Map<Factor, Double> m = asMap();
        if (useMl && mlScoresAvailable) {
            return m;
        }
        double ml = m.remove(Factor.ML_SUITABILITY);
        double rest = 100.0 - ml;
        Map<Factor, Double> out = new EnumMap<>(Factor.class);
        if (rest <= 0) {
            // degenerate config (100% ML) - fall back to equal weighting of the profile factors
            double each = 100.0 / m.size();
            m.keySet().forEach(f -> out.put(f, each));
        } else {
            m.forEach((f, w) -> out.put(f, w * 100.0 / rest));
        }
        out.put(Factor.ML_SUITABILITY, 0.0);
        return out;
    }
}
