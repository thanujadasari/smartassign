package com.smartassign.assignment;

import com.smartassign.domain.MlStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** A supervisor decision about a claim. Decided rows are never overwritten; re-routing creates a new row. */
@Entity
@Table(name = "assignments")
public class Assignment {

    public enum Status { PENDING, APPROVED, OVERRIDDEN, MANUAL_REVIEW, CANCELLED }

    public enum DecisionType { APPROVED_RECOMMENDATION, OVERRIDE, MANUAL_REVIEW, FALLBACK_MANUAL_REVIEW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false) private Long claimId;
    @Column(name = "run_id") private Long runId;
    @Column(name = "recommended_handler_id") private Long recommendedHandlerId;
    @Column(name = "assigned_handler_id") private Long assignedHandlerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Enumerated(EnumType.STRING) @Column(name = "decision_type", length = 40) private DecisionType decisionType;
    @Column(name = "recommendation_score") private Double recommendationScore;
    @Column(name = "assigned_handler_score") private Double assignedHandlerScore;
    @Column(name = "explanation") private String explanation;
    @Column(name = "override_reason") private String overrideReason;
    @Column(name = "review_reason") private String reviewReason;
    @Column(name = "fallback_reason") private String fallbackReason;
    @Column(name = "model_version", length = 80) private String modelVersion;
    @Enumerated(EnumType.STRING) @Column(name = "ml_status", length = 20) private MlStatus mlStatus;
    @Column(name = "created_by", nullable = false, length = 60) private String createdBy;
    @Column(name = "decided_by", length = 60) private String decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getRecommendedHandlerId() { return recommendedHandlerId; }
    public void setRecommendedHandlerId(Long recommendedHandlerId) { this.recommendedHandlerId = recommendedHandlerId; }
    public Long getAssignedHandlerId() { return assignedHandlerId; }
    public void setAssignedHandlerId(Long assignedHandlerId) { this.assignedHandlerId = assignedHandlerId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public DecisionType getDecisionType() { return decisionType; }
    public void setDecisionType(DecisionType decisionType) { this.decisionType = decisionType; }
    public Double getRecommendationScore() { return recommendationScore; }
    public void setRecommendationScore(Double recommendationScore) { this.recommendationScore = recommendationScore; }
    public Double getAssignedHandlerScore() { return assignedHandlerScore; }
    public void setAssignedHandlerScore(Double assignedHandlerScore) { this.assignedHandlerScore = assignedHandlerScore; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getOverrideReason() { return overrideReason; }
    public void setOverrideReason(String overrideReason) { this.overrideReason = overrideReason; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public MlStatus getMlStatus() { return mlStatus; }
    public void setMlStatus(MlStatus mlStatus) { this.mlStatus = mlStatus; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
