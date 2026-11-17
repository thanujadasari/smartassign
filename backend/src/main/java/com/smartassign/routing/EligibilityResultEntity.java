package com.smartassign.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "eligibility_results")
public class EligibilityResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false) private Long runId;
    @Column(name = "handler_id", nullable = false) private Long handlerId;
    @Column(nullable = false) private boolean eligible;
    @Column(name = "rule_results_json", nullable = false) private String ruleResultsJson;
    @Column(name = "exclusion_reasons") private String exclusionReasons;

    protected EligibilityResultEntity() {
    }

    public EligibilityResultEntity(Long runId, Long handlerId, boolean eligible, String ruleResultsJson, String exclusionReasons) {
        this.runId = runId;
        this.handlerId = handlerId;
        this.eligible = eligible;
        this.ruleResultsJson = ruleResultsJson;
        this.exclusionReasons = exclusionReasons;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public Long getHandlerId() { return handlerId; }
    public boolean isEligible() { return eligible; }
    public String getRuleResultsJson() { return ruleResultsJson; }
    public String getExclusionReasons() { return exclusionReasons; }
}
