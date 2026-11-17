package com.smartassign.routing;

import com.smartassign.claim.ClaimDtos;
import com.smartassign.core.HandlerSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Supervisor-facing routing payloads. Business language only - no model names or raw probabilities. */
public final class RoutingDtos {

    private RoutingDtos() {
    }

    public record RuleResultDto(String code, String name, boolean passed, String reason) {
    }

    public record HandlerSummary(Long id, String code, String fullName, String primaryExpertise, String expertiseLabel,
                                 String location, int experienceYears, int currentWorkload, int maxWorkload,
                                 Double successRate, Integer similarClaimsHandled, boolean hasHistory,
                                 boolean available, boolean active) {
        /** Statistics are null for a handler with no resolved claims - the ranking's neutral values are never shown. */
        public static HandlerSummary from(HandlerSnapshot h) {
            return new HandlerSummary(h.id(), h.code(), h.name(), h.primaryExpertise().name(),
                    h.primaryExpertise().label(), h.location(), h.experienceYears(), h.currentWorkload(),
                    h.maxWorkload(), h.hasHistory() ? h.successRate() : null,
                    h.hasHistory() ? h.similarClaimsHandled() : null, h.hasHistory(), h.available(), h.active());
        }
    }

    public record EligibilityRow(HandlerSummary handler, boolean eligible, List<RuleResultDto> rules,
                                 String exclusionReasons) {
    }

    /** count of handlers failing each rule (a handler can fail several) */
    public record ExclusionCount(String ruleCode, String ruleName, long handlers) {
    }

    public record EligibilityResponse(Long runId, ClaimDtos.ClaimResponse claim, String outcome, int eligibleCount,
                                      int ineligibleCount, List<EligibilityRow> eligible,
                                      List<EligibilityRow> ineligible, List<ExclusionCount> exclusionBreakdown,
                                      List<RuleAppliedDto> rulesApplied, Instant createdAt) {
    }

    public record RuleAppliedDto(String code, String name, Double param1, Double param2) {
    }

    public record FactorDto(String factor, String label, double value, double weight, double points, String detail) {
    }

    public record RecommendationDto(int rank, HandlerSummary handler, double suitabilityScore,
                                    double scoreBeforeFairness, boolean fairnessAdjusted, List<FactorDto> factors,
                                    String explanation, double distanceKm, boolean sameCity) {
    }

    public record FallbackDto(String reason, String claimStatus, Long assignmentId, boolean newlyQueued) {
    }

    /**
     * scoringBasis: PROFILE_AND_PREDICTED_FIT when the predicted-fit factor contributed, otherwise PROFILE_ONLY.
     */
    public record RecommendationResponse(Long runId, ClaimDtos.ClaimResponse claim, String outcome, String scoringBasis,
                                         String scoringNote, Map<String, Double> weightsApplied,
                                         List<RecommendationDto> recommendations, int eligibleCount,
                                         int ineligibleCount, List<EligibilityRow> ineligible,
                                         List<ExclusionCount> exclusionBreakdown, FallbackDto fallback,
                                         Instant validUntil, Instant createdAt) {
    }

    public record RunSummary(Long runId, String runType, String outcome, int eligibleCount, int ineligibleCount,
                             String scoringBasis, String createdBy, Instant createdAt) {
    }
}
