package com.smartassign.core.ml;

import com.smartassign.core.ClaimSnapshot;
import com.smartassign.core.HandlerSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the 24 input features expected by the suitability model, using the exact column names of the
 * training dataset. Deliberately EXCLUDED (as in the training notebook): claim_id, adjuster_id
 * (identifiers), previous_reassignments and assignment_delay_hours (post-assignment / leakage risk).
 */
public final class MlFeatureBuilder {

    public static final List<String> FEATURES = List.of(
            "claim_type", "policy_type", "severity", "complexity", "estimated_loss", "claim_location",
            "claim_age_days", "incident_type", "fraud_risk_score", "injury_involved", "third_party_involved",
            "adjuster_expertise", "adjuster_experience_years", "adjuster_current_workload", "adjuster_max_workload",
            "adjuster_availability", "adjuster_location", "adjuster_success_rate", "adjuster_avg_resolution_days",
            "similar_claims_handled", "adjuster_active", "distance_km", "workload_ratio", "expertise_match");

    private MlFeatureBuilder() {
    }

    public static Map<String, Object> build(ClaimSnapshot c, HandlerSnapshot h, double distanceKm) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("claim_type", c.claimType().name());
        f.put("policy_type", c.policyType().name());
        f.put("severity", c.severity().name());
        f.put("complexity", c.complexity().name());
        f.put("estimated_loss", c.estimatedLoss());
        f.put("claim_location", c.location());
        f.put("claim_age_days", c.claimAgeDays());
        f.put("incident_type", c.incidentType().name());
        f.put("fraud_risk_score", c.fraudRiskScore());
        f.put("injury_involved", c.injuryInvolved() ? 1 : 0);
        f.put("third_party_involved", c.thirdPartyInvolved() ? 1 : 0);
        f.put("adjuster_expertise", h.primaryExpertise().name());
        f.put("adjuster_experience_years", h.experienceYears());
        f.put("adjuster_current_workload", h.currentWorkload());
        f.put("adjuster_max_workload", h.maxWorkload());
        f.put("adjuster_availability", h.available() ? 1 : 0);
        f.put("adjuster_location", h.location());
        f.put("adjuster_success_rate", h.successRate());
        f.put("adjuster_avg_resolution_days", h.avgResolutionDays());
        f.put("similar_claims_handled", h.similarClaimsHandled());
        f.put("adjuster_active", h.active() ? 1 : 0);
        f.put("distance_km", Math.round(distanceKm * 10.0) / 10.0);
        f.put("workload_ratio", Math.round(h.utilization() * 1000.0) / 1000.0);
        f.put("expertise_match", h.skills().contains(c.claimType()) ? 1 : 0);
        return f;
    }
}
