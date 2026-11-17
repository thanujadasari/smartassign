package com.smartassign.core.scoring;

import com.smartassign.core.ClaimSnapshot;
import com.smartassign.core.HandlerSnapshot;
import com.smartassign.domain.Expertise;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Explainable ranking of ELIGIBLE handlers. Callers must pass only handlers that passed the eligibility
 * engine - this class never decides eligibility and cannot promote an ineligible handler.
 *
 * Factor formulas (each normalised to 0..1):
 *   EXPERTISE       1.0 specialist in the claim line, 0.8 multi-line P&C, 0.7 holds the skill as a secondary skill
 *   WORKLOAD        1 - current / max  (before this claim is added)
 *   EXPERIENCE      min(years / 20, 1)            (20 = dataset maximum)
 *   SUCCESS_RATE    historical success rate
 *   SIMILAR_CLAIMS  min(similar claims / 100, 1)
 *   PROXIMITY       1 - distance / max distance
 *   ML_SUITABILITY  predicted-fit estimate from the suitability service (0..1)
 *
 * total = sum(value * effective weight); if (current + 1) / max > fairnessThreshold the total is multiplied
 * by (1 - fairnessPenalty) - the workload fairness control.
 */
public class ScoringEngine {

    public static final double EXPERIENCE_REFERENCE_YEARS = 20.0;
    public static final double SIMILAR_CLAIMS_REFERENCE = 100.0;

    private final DistanceProvider distances;
    private final ExplanationBuilder explanations = new ExplanationBuilder();

    public ScoringEngine(DistanceProvider distances) {
        this.distances = distances;
    }

    /**
     * @param mlScores predicted-fit scores keyed by handler id. Predicted fit is only used when a score exists
     *                 for EVERY eligible handler, so all candidates are ranked on the same basis.
     */
    public RankingResult rank(ClaimSnapshot claim, List<HandlerSnapshot> eligible, RankingWeights weights,
                              Map<Long, Double> mlScores) {
        boolean mlUsed = weights.useMl() && !eligible.isEmpty() && mlScores != null
                && eligible.stream().allMatch(h -> mlScores.get(h.id()) != null);
        Map<Factor, Double> w = weights.effective(mlUsed);

        List<ScoredCandidate> scored = new ArrayList<>();
        for (HandlerSnapshot h : eligible) {
            scored.add(score(claim, h, w, weights, mlUsed ? mlScores.get(h.id()) : null));
        }
        scored.sort(Comparator.comparingDouble(ScoredCandidate::totalScore).reversed()
                .thenComparingDouble(c -> c.handler().utilization())
                .thenComparing(c -> c.handler().code()));

        List<ScoredCandidate> ranked = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            ScoredCandidate c = scored.get(i);
            ranked.add(c.withRankAndExplanation(i + 1, explanations.explain(claim, c, i + 1, weights)));
        }
        return new RankingResult(List.copyOf(ranked), w, mlUsed);
    }

    ScoredCandidate score(ClaimSnapshot claim, HandlerSnapshot h, Map<Factor, Double> w, RankingWeights cfg, Double ml) {
        double distance = distances.distanceOrMax(claim.location(), h.location());
        double maxDistance = Math.max(1.0, distances.maxDistanceKm());
        boolean distanceKnown = distances.distanceKm(claim.location(), h.location()).isPresent();

        List<FactorScore> factors = new ArrayList<>();
        double expertise = expertiseValue(claim, h);
        factors.add(factor(Factor.EXPERTISE, expertise, w, expertiseDetail(claim, h)));

        double workload = clamp(1.0 - h.utilization());
        factors.add(factor(Factor.WORKLOAD, workload, w,
                h.currentWorkload() + " of " + h.maxWorkload() + " open claims (" + h.openSlots() + " slots free)"));

        double experience = clamp(h.experienceYears() / EXPERIENCE_REFERENCE_YEARS);
        factors.add(factor(Factor.EXPERIENCE, experience, w, h.experienceYears() + " years of claims experience"));

        double success = clamp(h.successRate());
        factors.add(factor(Factor.SUCCESS_RATE, success, w, h.hasHistory()
                ? Math.round(h.successRate() * 100) + "% of resolved claims closed on time"
                : "No resolved claims yet - neutral value used"));

        double similar = clamp(h.similarClaimsHandled() / SIMILAR_CLAIMS_REFERENCE);
        factors.add(factor(Factor.SIMILAR_CLAIMS, similar, w, h.hasHistory()
                ? h.similarClaimsHandled() + " claims resolved in SmartAssign"
                : "No resolved claims yet - neutral value used"));

        double proximity = clamp(1.0 - distance / maxDistance);
        String proximityDetail = !distanceKnown
                ? "Distance to " + claim.location() + " unknown"
                : claim.location().equalsIgnoreCase(h.location())
                ? "Based in " + h.location() + " (same city as the loss)"
                : "Based in " + h.location() + ", about " + Math.round(distance) + " km from the loss";
        factors.add(factor(Factor.PROXIMITY, proximity, w, proximityDetail));

        if (ml != null) {
            double mlValue = clamp(ml);
            factors.add(factor(Factor.ML_SUITABILITY, mlValue, w, describeFit(mlValue)));
        }

        double total = factors.stream().mapToDouble(FactorScore::points).sum();
        double before = total;
        boolean fairness = false;
        double projected = h.maxWorkload() <= 0 ? 1.0 : (h.currentWorkload() + 1.0) / h.maxWorkload();
        if (projected > cfg.fairnessThreshold() && cfg.fairnessPenalty() > 0) {
            total = total * (1.0 - cfg.fairnessPenalty());
            fairness = true;
        }
        total = Math.max(0, Math.min(100, total));
        return new ScoredCandidate(h, 0, round2(total), round2(before), List.copyOf(factors), ml, fairness, distance, null);
    }

    static double expertiseValue(ClaimSnapshot claim, HandlerSnapshot h) {
        if (h.primaryExpertise().isSpecialistFor(claim.claimType())) {
            return 1.0;
        }
        if (h.primaryExpertise() == Expertise.MULTI_P_AND_C) {
            return 0.8;
        }
        return h.skills().contains(claim.claimType()) ? 0.7 : 0.0;
    }

    private static String expertiseDetail(ClaimSnapshot claim, HandlerSnapshot h) {
        if (h.primaryExpertise().isSpecialistFor(claim.claimType())) {
            return claim.claimType().label() + " specialist";
        }
        if (h.primaryExpertise() == Expertise.MULTI_P_AND_C) {
            return "Multi-line P&C handler (covers " + claim.claimType().label() + ")";
        }
        return "Holds " + claim.claimType().label() + " as a secondary skill";
    }

    static String describeFit(double v) {
        if (v >= 0.75) return "Strong predicted fit based on past assignment outcomes";
        if (v >= 0.5) return "Good predicted fit based on past assignment outcomes";
        if (v >= 0.25) return "Moderate predicted fit based on past assignment outcomes";
        return "Limited predicted fit based on past assignment outcomes";
    }

    private static FactorScore factor(Factor f, double value, Map<Factor, Double> w, String detail) {
        double weight = w.getOrDefault(f, 0.0);
        return new FactorScore(f, f.label(), round4(value), round2(weight), round2(value * weight), detail);
    }

    static double clamp(double v) {
        if (Double.isNaN(v)) return 0;
        return Math.max(0.0, Math.min(1.0, v));
    }

    static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
