package com.smartassign.audit;

import java.time.Instant;

public record AuditDto(Long id, String eventType, String entityType, String entityId, String claimNumber,
                       String recommendedHandlerCode, String finalHandlerCode, Double recommendationScore,
                       String eligibilitySummary, String explanation, String supervisorAction, String overrideReason,
                       String actorUsername, String details, Instant createdAt) {

    /** Supervisor-facing view: model version is internal and intentionally omitted. */
    public static AuditDto from(AuditLog a) {
        return new AuditDto(a.getId(), a.getEventType(), a.getEntityType(), a.getEntityId(), a.getClaimNumber(),
                a.getRecommendedHandlerCode(), a.getFinalHandlerCode(), a.getRecommendationScore(),
                a.getEligibilitySummary(), a.getExplanation(), a.getSupervisorAction(), a.getOverrideReason(),
                a.getActorUsername(), a.getDetailsJson(), a.getCreatedAt());
    }
}
