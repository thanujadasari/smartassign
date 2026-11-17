package com.smartassign.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Append-only audit record (the database also blocks UPDATE/DELETE with a trigger). */
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id", length = 40)
    private String entityId;

    @Column(name = "claim_number", length = 30)
    private String claimNumber;

    @Column(name = "recommended_handler_code", length = 20)
    private String recommendedHandlerCode;

    @Column(name = "final_handler_code", length = 20)
    private String finalHandlerCode;

    @Column(name = "recommendation_score")
    private Double recommendationScore;

    @Column(name = "eligibility_summary")
    private String eligibilitySummary;

    @Column(name = "explanation")
    private String explanation;

    @Column(name = "supervisor_action", length = 40)
    private String supervisorAction;

    @Column(name = "override_reason")
    private String overrideReason;

    @Column(name = "model_version", length = 80)
    private String modelVersion;

    @Column(name = "actor_username", nullable = false, length = 60)
    private String actorUsername;

    @Column(name = "details_json")
    private String detailsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    AuditLog(AuditEntry e, String detailsJson) {
        this.eventType = e.eventType;
        this.entityType = e.entityType;
        this.entityId = e.entityId;
        this.claimNumber = e.claimNumber;
        this.recommendedHandlerCode = e.recommendedHandlerCode;
        this.finalHandlerCode = e.finalHandlerCode;
        this.recommendationScore = e.recommendationScore;
        this.eligibilitySummary = e.eligibilitySummary;
        this.explanation = e.explanation;
        this.supervisorAction = e.supervisorAction;
        this.overrideReason = e.overrideReason;
        this.modelVersion = e.modelVersion;
        this.actorUsername = e.actor;
        this.detailsJson = detailsJson;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getClaimNumber() { return claimNumber; }
    public String getRecommendedHandlerCode() { return recommendedHandlerCode; }
    public String getFinalHandlerCode() { return finalHandlerCode; }
    public Double getRecommendationScore() { return recommendationScore; }
    public String getEligibilitySummary() { return eligibilitySummary; }
    public String getExplanation() { return explanation; }
    public String getSupervisorAction() { return supervisorAction; }
    public String getOverrideReason() { return overrideReason; }
    public String getModelVersion() { return modelVersion; }
    public String getActorUsername() { return actorUsername; }
    public String getDetailsJson() { return detailsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
