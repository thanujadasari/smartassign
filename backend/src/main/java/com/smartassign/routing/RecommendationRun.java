package com.smartassign.routing;

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

/** One execution of the routing pipeline for a claim (eligibility check, optionally followed by ranking). */
@Entity
@Table(name = "recommendation_runs")
public class RecommendationRun {

    public enum RunType { ELIGIBILITY_ONLY, RECOMMENDATION }

    public enum Outcome { ELIGIBLE_FOUND, NO_ELIGIBLE_HANDLER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false) private Long claimId;
    @Enumerated(EnumType.STRING) @Column(name = "run_type", nullable = false, length = 20) private RunType runType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Outcome outcome;
    @Column(name = "eligible_count", nullable = false) private int eligibleCount;
    @Column(name = "ineligible_count", nullable = false) private int ineligibleCount;
    @Enumerated(EnumType.STRING) @Column(name = "ml_status", nullable = false, length = 20) private MlStatus mlStatus;
    @Column(name = "model_version", length = 80) private String modelVersion;
    @Column(name = "settings_id") private Long settingsId;
    @Column(name = "weights_json") private String weightsJson;
    @Column(name = "rules_json") private String rulesJson;
    @Column(name = "fallback_reason") private String fallbackReason;
    @Column(name = "created_by", nullable = false, length = 60) private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public RunType getRunType() { return runType; }
    public void setRunType(RunType runType) { this.runType = runType; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public int getEligibleCount() { return eligibleCount; }
    public void setEligibleCount(int eligibleCount) { this.eligibleCount = eligibleCount; }
    public int getIneligibleCount() { return ineligibleCount; }
    public void setIneligibleCount(int ineligibleCount) { this.ineligibleCount = ineligibleCount; }
    public MlStatus getMlStatus() { return mlStatus; }
    public void setMlStatus(MlStatus mlStatus) { this.mlStatus = mlStatus; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public Long getSettingsId() { return settingsId; }
    public void setSettingsId(Long settingsId) { this.settingsId = settingsId; }
    public String getWeightsJson() { return weightsJson; }
    public void setWeightsJson(String weightsJson) { this.weightsJson = weightsJson; }
    public String getRulesJson() { return rulesJson; }
    public void setRulesJson(String rulesJson) { this.rulesJson = rulesJson; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
