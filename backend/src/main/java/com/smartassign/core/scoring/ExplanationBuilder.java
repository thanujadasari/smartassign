package com.smartassign.core.scoring;

import com.smartassign.core.ClaimSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a plain-language explanation from the ACTUAL factor contributions of a candidate.
 * The three strongest contributing factors (value >= 0.5) are cited, plus any caveats.
 */
public class ExplanationBuilder {

    public String explain(ClaimSnapshot claim, ScoredCandidate c, int rank, RankingWeights weights) {
        List<FactorScore> strongest = c.factors().stream()
                .filter(f -> f.weight() > 0 && f.value() >= 0.5)
                // a neutral placeholder is not a reason to recommend anyone
                .filter(f -> c.handler().hasHistory() || (f.factor() != Factor.SUCCESS_RATE && f.factor() != Factor.SIMILAR_CLAIMS))
                .sorted(Comparator.comparingDouble(FactorScore::points).reversed())
                .limit(3)
                .toList();

        List<String> reasons = new ArrayList<>();
        for (FactorScore f : strongest) {
            reasons.add(phrase(f, c, claim));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(rank == 1 ? "Recommended because this handler " : "Ranked #" + rank + " because this handler ");
        if (reasons.isEmpty()) {
            sb.append("meets every mandatory eligibility requirement for this claim");
        } else {
            sb.append(joinNatural(reasons));
        }
        sb.append('.');

        List<String> caveats = new ArrayList<>();
        if (!c.handler().hasHistory()) {
            caveats.add("New to SmartAssign: no resolved claims yet, so success rate and similar-claims experience use neutral values");
        }
        if (c.fairnessAdjusted()) {
            int projected = (int) Math.round(100.0 * (c.handler().currentWorkload() + 1) / Math.max(1, c.handler().maxWorkload()));
            caveats.add("Score reduced to protect workload balance - this claim would take the handler to "
                    + projected + "% of capacity");
        }
        value(c, Factor.WORKLOAD).filter(v -> v < 0.25 && !c.fairnessAdjusted())
                .ifPresent(v -> caveats.add("Handler is close to full capacity"));
        value(c, Factor.PROXIMITY).filter(v -> v < 0.3)
                .ifPresent(v -> caveats.add("Handler is located about " + Math.round(c.distanceKm()) + " km from the loss location"));
        value(c, Factor.EXPERIENCE).filter(v -> v < 0.25)
                .ifPresent(v -> caveats.add("Handler has limited experience (" + c.handler().experienceYears() + " years)"));
        for (String caveat : caveats) {
            sb.append(' ').append(caveat).append('.');
        }
        return sb.toString();
    }

    private static java.util.Optional<Double> value(ScoredCandidate c, Factor f) {
        return c.factors().stream().filter(x -> x.factor() == f && x.weight() > 0).map(FactorScore::value).findFirst();
    }

    private static String phrase(FactorScore f, ScoredCandidate c, ClaimSnapshot claim) {
        var h = c.handler();
        return switch (f.factor()) {
            case EXPERTISE -> h.primaryExpertise().isSpecialistFor(claim.claimType())
                    ? "is a " + claim.claimType().label() + " specialist"
                    : "holds the required " + claim.claimType().label() + " expertise";
            case WORKLOAD -> "has available capacity (" + h.currentWorkload() + " of " + h.maxWorkload() + " open claims)";
            case EXPERIENCE -> "has " + h.experienceYears() + " years of claims experience";
            case SUCCESS_RATE -> h.hasHistory()
                    ? "closed " + Math.round(h.successRate() * 100) + "% of resolved claims on time"
                    : "is new to SmartAssign (no resolved claims yet, so a neutral value is used)";
            case SIMILAR_CLAIMS -> h.hasHistory()
                    ? "has resolved " + h.similarClaimsHandled() + " claims in SmartAssign"
                    : "is new to SmartAssign (no resolved claims yet, so a neutral value is used)";
            case PROXIMITY -> claim.location().equalsIgnoreCase(h.location())
                    ? "is based in the same city as the loss (" + h.location() + ")"
                    : "is located close to the loss (about " + Math.round(c.distanceKm()) + " km)";
            case ML_SUITABILITY -> "is a strong fit for this type of claim based on past assignment outcomes";
        };
    }

    static String joinNatural(List<String> parts) {
        if (parts.size() == 1) return parts.get(0);
        if (parts.size() == 2) return parts.get(0) + " and " + parts.get(1);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }
}
