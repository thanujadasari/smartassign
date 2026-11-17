package com.smartassign.claim;

import com.smartassign.core.ClaimSnapshot;
import com.smartassign.domain.ClaimStatus;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Complexity;
import com.smartassign.domain.IncidentType;
import com.smartassign.domain.PolicyType;
import com.smartassign.domain.RecordSource;
import com.smartassign.domain.Severity;
import com.smartassign.auth.User;
import com.smartassign.handler.Handler;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true, length = 30)
    private String claimNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 30)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 30)
    private PolicyType policyType;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Complexity complexity;

    @Column(name = "estimated_loss", nullable = false, precision = 14, scale = 2)
    private BigDecimal estimatedLoss;

    @Column(name = "claim_location", nullable = false, length = 60)
    private String location;

    @Column(name = "claim_age_days", nullable = false)
    private int claimAgeDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 30)
    private IncidentType incidentType;

    @Column(name = "fraud_risk_score", precision = 4, scale = 3)
    private BigDecimal fraudRiskScore;

    @Column(name = "injury_involved", nullable = false)
    private boolean injuryInvolved;

    @Column(name = "third_party_involved", nullable = false)
    private boolean thirdPartyInvolved;

    @Column(name = "previous_reassignments", nullable = false)
    private int previousReassignments;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status = ClaimStatus.SUBMITTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_handler_id")
    private Handler assignedHandler;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecordSource source = RecordSource.CUSTOMER_PORTAL;

    @Column(name = "external_reference", length = 80)
    private String externalReference;

    // ---- customer-entered details
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "policy_number", nullable = false, length = 40)
    private String policyNumber;

    @Column(name = "incident_date", nullable = false)
    private java.time.LocalDate incidentDate;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    /** Customer says the damage or danger is still ongoing (e.g. a leak, an unsafe structure). */
    @Column(name = "ongoing_risk", nullable = false)
    private boolean ongoingRisk;

    // ---- priority (suggested by rules at registration, decided by the supervisor)
    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_priority", length = 10)
    private com.smartassign.domain.Priority suggestedPriority;

    @Column(name = "priority_reasons_json", columnDefinition = "text")
    private String priorityReasonsJson;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private com.smartassign.domain.Priority priority;

    @Column(name = "priority_note", columnDefinition = "text")
    private String priorityNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_set_by_user_id")
    private User prioritySetBy;

    @Column(name = "priority_set_at")
    private Instant prioritySetAt;

    /** 1 HIGH, 2 MEDIUM, 3 LOW - the decided priority, or the suggestion until then. Used to sort queues. */
    @Column(name = "priority_rank", nullable = false)
    private int priorityRank = 2;

    // ---- triage (suggested by rules, decided by the supervisor)
    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_severity", length = 10)
    private Severity suggestedSeverity;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_complexity", length = 10)
    private Complexity suggestedComplexity;

    @Column(name = "suggested_fraud_risk", precision = 4, scale = 3)
    private BigDecimal suggestedFraudRisk;

    @Column(name = "triage_suggestion_json", columnDefinition = "text")
    private String triageSuggestionJson;

    @Column(name = "triage_note", columnDefinition = "text")
    private String triageNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triaged_by_user_id")
    private User triagedBy;

    @Column(name = "triaged_at")
    private Instant triagedAt;

    // ---- assignment + resolution
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private User assignedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "resolution_days", precision = 8, scale = 2)
    private BigDecimal resolutionDays;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Only valid after triage (severity, complexity and fraud risk are then set). */
    public ClaimSnapshot toSnapshot() {
        if (!isTriaged()) {
            throw new IllegalStateException("Claim " + claimNumber + " has not been triaged yet");
        }
        return new ClaimSnapshot(id, claimNumber, claimType, policyType, severity, complexity,
                estimatedLoss.doubleValue(), location, ageDays(), incidentType, fraudRiskScore.doubleValue(),
                injuryInvolved, thirdPartyInvolved);
    }

    public boolean isTriaged() {
        return severity != null && complexity != null && fraudRiskScore != null;
    }

    /** Days since the customer registered the claim (the claim's age in the routing model). */
    public int ageDays() {
        if (createdAt == null) return 0;
        return (int) Math.max(0, java.time.Duration.between(createdAt, Instant.now()).toDays());
    }

    public boolean isRoutable() {
        return isTriaged() && (status == ClaimStatus.UNASSIGNED || status == ClaimStatus.MANUAL_REVIEW);
    }

    public Long getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public void setClaimNumber(String claimNumber) { this.claimNumber = claimNumber; }
    public ClaimType getClaimType() { return claimType; }
    public void setClaimType(ClaimType claimType) { this.claimType = claimType; }
    public PolicyType getPolicyType() { return policyType; }
    public void setPolicyType(PolicyType policyType) { this.policyType = policyType; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public Complexity getComplexity() { return complexity; }
    public void setComplexity(Complexity complexity) { this.complexity = complexity; }
    public BigDecimal getEstimatedLoss() { return estimatedLoss; }
    public void setEstimatedLoss(BigDecimal estimatedLoss) { this.estimatedLoss = estimatedLoss; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public int getClaimAgeDays() { return claimAgeDays; }
    public void setClaimAgeDays(int claimAgeDays) { this.claimAgeDays = claimAgeDays; }
    public IncidentType getIncidentType() { return incidentType; }
    public void setIncidentType(IncidentType incidentType) { this.incidentType = incidentType; }
    public BigDecimal getFraudRiskScore() { return fraudRiskScore; }
    public void setFraudRiskScore(BigDecimal fraudRiskScore) { this.fraudRiskScore = fraudRiskScore; }
    public boolean isInjuryInvolved() { return injuryInvolved; }
    public void setInjuryInvolved(boolean injuryInvolved) { this.injuryInvolved = injuryInvolved; }
    public boolean isThirdPartyInvolved() { return thirdPartyInvolved; }
    public void setThirdPartyInvolved(boolean thirdPartyInvolved) { this.thirdPartyInvolved = thirdPartyInvolved; }
    public int getPreviousReassignments() { return previousReassignments; }
    public void setPreviousReassignments(int previousReassignments) { this.previousReassignments = previousReassignments; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }
    public Handler getAssignedHandler() { return assignedHandler; }
    public void setAssignedHandler(Handler assignedHandler) { this.assignedHandler = assignedHandler; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
    public RecordSource getSource() { return source; }
    public void setSource(RecordSource source) { this.source = source; }
    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }
    public boolean isOngoingRisk() { return ongoingRisk; }
    public void setOngoingRisk(boolean v) { this.ongoingRisk = v; }
    public com.smartassign.domain.Priority getSuggestedPriority() { return suggestedPriority; }
    public void setSuggestedPriority(com.smartassign.domain.Priority v) {
        this.suggestedPriority = v;
        if (priority == null && v != null) priorityRank = v.rank();
    }
    public String getPriorityReasonsJson() { return priorityReasonsJson; }
    public void setPriorityReasonsJson(String v) { this.priorityReasonsJson = v; }
    public com.smartassign.domain.Priority getPriority() { return priority; }
    public void setPriority(com.smartassign.domain.Priority v) {
        this.priority = v;
        if (v != null) priorityRank = v.rank();
    }
    public String getPriorityNote() { return priorityNote; }
    public void setPriorityNote(String v) { this.priorityNote = v; }
    public User getPrioritySetBy() { return prioritySetBy; }
    public void setPrioritySetBy(User v) { this.prioritySetBy = v; }
    public Instant getPrioritySetAt() { return prioritySetAt; }
    public void setPrioritySetAt(Instant v) { this.prioritySetAt = v; }
    public int getPriorityRank() { return priorityRank; }
    public User getCustomer() { return customer; }
    public void setCustomer(User customer) { this.customer = customer; }
    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }
    public java.time.LocalDate getIncidentDate() { return incidentDate; }
    public void setIncidentDate(java.time.LocalDate incidentDate) { this.incidentDate = incidentDate; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public Severity getSuggestedSeverity() { return suggestedSeverity; }
    public void setSuggestedSeverity(Severity v) { this.suggestedSeverity = v; }
    public Complexity getSuggestedComplexity() { return suggestedComplexity; }
    public void setSuggestedComplexity(Complexity v) { this.suggestedComplexity = v; }
    public BigDecimal getSuggestedFraudRisk() { return suggestedFraudRisk; }
    public void setSuggestedFraudRisk(BigDecimal v) { this.suggestedFraudRisk = v; }
    public String getTriageSuggestionJson() { return triageSuggestionJson; }
    public void setTriageSuggestionJson(String v) { this.triageSuggestionJson = v; }
    public String getTriageNote() { return triageNote; }
    public void setTriageNote(String v) { this.triageNote = v; }
    public User getTriagedBy() { return triagedBy; }
    public void setTriagedBy(User v) { this.triagedBy = v; }
    public Instant getTriagedAt() { return triagedAt; }
    public void setTriagedAt(Instant v) { this.triagedAt = v; }
    public User getAssignedBy() { return assignedBy; }
    public void setAssignedBy(User v) { this.assignedBy = v; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant v) { this.resolvedAt = v; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String v) { this.resolutionNote = v; }
    public BigDecimal getResolutionDays() { return resolutionDays; }
    public void setResolutionDays(BigDecimal v) { this.resolutionDays = v; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
