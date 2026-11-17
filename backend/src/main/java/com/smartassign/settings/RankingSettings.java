package com.smartassign.settings;

import com.smartassign.core.scoring.RankingWeights;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** One version of the ranking weights. A change creates a new row; previous rows are kept as history. */
@Entity
@Table(name = "ranking_settings")
public class RankingSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "w_expertise", nullable = false) private BigDecimal wExpertise;
    @Column(name = "w_workload", nullable = false) private BigDecimal wWorkload;
    @Column(name = "w_experience", nullable = false) private BigDecimal wExperience;
    @Column(name = "w_success_rate", nullable = false) private BigDecimal wSuccessRate;
    @Column(name = "w_similar_claims", nullable = false) private BigDecimal wSimilarClaims;
    @Column(name = "w_proximity", nullable = false) private BigDecimal wProximity;
    @Column(name = "w_ml_suitability", nullable = false) private BigDecimal wMlSuitability;
    @Column(name = "fairness_threshold", nullable = false) private BigDecimal fairnessThreshold;
    @Column(name = "fairness_penalty", nullable = false) private BigDecimal fairnessPenalty;
    @Column(name = "use_ml", nullable = false) private boolean useMl;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_by", length = 60) private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public RankingWeights toWeights() {
        return new RankingWeights(wExpertise.doubleValue(), wWorkload.doubleValue(), wExperience.doubleValue(),
                wSuccessRate.doubleValue(), wSimilarClaims.doubleValue(), wProximity.doubleValue(),
                wMlSuitability.doubleValue(), fairnessThreshold.doubleValue(), fairnessPenalty.doubleValue(), useMl);
    }

    public static RankingSettings from(RankingWeights w, String user) {
        RankingSettings s = new RankingSettings();
        s.wExpertise = bd(w.expertise());
        s.wWorkload = bd(w.workload());
        s.wExperience = bd(w.experience());
        s.wSuccessRate = bd(w.successRate());
        s.wSimilarClaims = bd(w.similarClaims());
        s.wProximity = bd(w.proximity());
        s.wMlSuitability = bd(w.mlSuitability());
        s.fairnessThreshold = BigDecimal.valueOf(w.fairnessThreshold()).setScale(3, java.math.RoundingMode.HALF_UP);
        s.fairnessPenalty = BigDecimal.valueOf(w.fairnessPenalty()).setScale(3, java.math.RoundingMode.HALF_UP);
        s.useMl = w.useMl();
        s.active = true;
        s.createdBy = user;
        return s;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
