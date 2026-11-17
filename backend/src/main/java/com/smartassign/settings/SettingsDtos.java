package com.smartassign.settings;

import com.smartassign.core.scoring.RankingWeights;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    /** All weights are percentages and must total 100. */
    public record WeightsRequest(
            @NotNull @DecimalMin("0") @DecimalMax("100") Double expertise,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double workload,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double experience,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double successRate,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double similarClaims,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double proximity,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double predictedFit,
            @NotNull @DecimalMin(value = "0.1", message = "Fairness threshold must be between 0.1 and 1.0") @DecimalMax("1.0") Double fairnessThreshold,
            @NotNull @DecimalMin(value = "0.0", message = "Fairness penalty must be between 0 and 1") @DecimalMax("1.0") Double fairnessPenalty,
            @NotNull Boolean usePredictedFit) {

        public RankingWeights toWeights() {
            return new RankingWeights(expertise, workload, experience, successRate, similarClaims, proximity,
                    predictedFit, fairnessThreshold, fairnessPenalty, usePredictedFit);
        }
    }

    /**
     * "predictedFit" is the supervisor-facing name of the ML suitability factor. The response deliberately
     * uses business language and contains no model details.
     */
    public record WeightsResponse(Long id, double expertise, double workload, double experience, double successRate,
                                  double similarClaims, double proximity, double predictedFit, double total,
                                  double fairnessThreshold, double fairnessPenalty, boolean usePredictedFit,
                                  boolean active, String updatedBy, Instant updatedAt) {

        public static WeightsResponse from(RankingSettings s) {
            RankingWeights w = s.toWeights();
            return new WeightsResponse(s.getId(), w.expertise(), w.workload(), w.experience(), w.successRate(),
                    w.similarClaims(), w.proximity(), w.mlSuitability(), Math.round(w.total() * 100) / 100.0,
                    w.fairnessThreshold(), w.fairnessPenalty(), w.useMl(), s.isActive(), s.getCreatedBy(),
                    s.getCreatedAt());
        }
    }

    public record RuleUpdateRequest(@NotNull Boolean enabled, BigDecimal param1, BigDecimal param2) {
    }

    public record RuleResponse(String code, String name, String description, String category, boolean enabled,
                               boolean locked, BigDecimal param1, BigDecimal param2, String param1Label,
                               String param2Label, String updatedBy, Instant updatedAt) {

        public static RuleResponse from(EligibilityRule r) {
            String p1 = switch (r.getCode()) {
                case "CRITICAL_SEVERITY_EXPERIENCE" -> "Minimum years of experience";
                case "HIGH_FRAUD_RISK_EXPERIENCE" -> "Fraud risk threshold (0-1)";
                default -> null;
            };
            String p2 = "HIGH_FRAUD_RISK_EXPERIENCE".equals(r.getCode()) ? "Minimum years of experience" : null;
            return new RuleResponse(r.getCode(), r.getName(), r.getDescription(), r.getCategory(), r.isEnabled(),
                    r.isLocked(), r.getParam1(), r.getParam2(), p1, p2, r.getUpdatedBy(), r.getUpdatedAt());
        }
    }
}
