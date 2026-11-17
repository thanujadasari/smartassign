package com.smartassign.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A ranked eligible handler within a recommendation run. ml_score is stored for audit only. */
@Entity
@Table(name = "assignment_recommendations")
public class AssignmentRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false) private Long runId;
    @Column(name = "handler_id", nullable = false) private Long handlerId;
    @Column(nullable = false) private int rank;
    @Column(name = "total_score", nullable = false) private Double totalScore;
    @Column(name = "ml_score") private Double mlScore;
    @Column(name = "factor_scores_json", nullable = false) private String factorScoresJson;
    @Column(name = "fairness_adjusted", nullable = false) private boolean fairnessAdjusted;
    @Column(nullable = false) private String explanation;

    protected AssignmentRecommendation() {
    }

    public AssignmentRecommendation(Long runId, Long handlerId, int rank, Double totalScore, Double mlScore,
                                    String factorScoresJson, boolean fairnessAdjusted, String explanation) {
        this.runId = runId;
        this.handlerId = handlerId;
        this.rank = rank;
        this.totalScore = totalScore;
        this.mlScore = mlScore;
        this.factorScoresJson = factorScoresJson;
        this.fairnessAdjusted = fairnessAdjusted;
        this.explanation = explanation;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public Long getHandlerId() { return handlerId; }
    public int getRank() { return rank; }
    public Double getTotalScore() { return totalScore; }
    public Double getMlScore() { return mlScore; }
    public String getFactorScoresJson() { return factorScoresJson; }
    public boolean isFairnessAdjusted() { return fairnessAdjusted; }
    public String getExplanation() { return explanation; }
}
