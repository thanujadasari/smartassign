package com.smartassign.settings;

import com.smartassign.audit.AuditEntry;
import com.smartassign.audit.AuditService;
import com.smartassign.common.ApiException;
import com.smartassign.core.eligibility.RuleConfig;
import com.smartassign.core.scoring.RankingWeights;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class SettingsService {

    private final RankingSettingsRepository weightsRepo;
    private final EligibilityRuleRepository rulesRepo;
    private final AuditService audit;

    public SettingsService(RankingSettingsRepository weightsRepo, EligibilityRuleRepository rulesRepo, AuditService audit) {
        this.weightsRepo = weightsRepo;
        this.rulesRepo = rulesRepo;
        this.audit = audit;
    }

    /** Active settings row; falls back to documented defaults if the table is somehow empty. */
    @Transactional(readOnly = true)
    public ActiveWeights activeWeights() {
        return weightsRepo.findFirstByActiveTrue()
                .map(s -> new ActiveWeights(s.getId(), s.toWeights()))
                .orElse(new ActiveWeights(null, RankingWeights.defaults()));
    }

    public record ActiveWeights(Long settingsId, RankingWeights weights) {
    }

    @Transactional(readOnly = true)
    public SettingsDtos.WeightsResponse getWeights() {
        return weightsRepo.findFirstByActiveTrue().map(SettingsDtos.WeightsResponse::from)
                .orElseThrow(() -> ApiException.notFound("Ranking settings", "active"));
    }

    @Transactional(readOnly = true)
    public List<SettingsDtos.WeightsResponse> weightHistory() {
        return weightsRepo.findTop20ByOrderByCreatedAtDesc().stream().map(SettingsDtos.WeightsResponse::from).toList();
    }

    @Transactional
    public SettingsDtos.WeightsResponse updateWeights(SettingsDtos.WeightsRequest req, String actor) {
        RankingWeights w = req.toWeights();
        try {
            w.validate();
        } catch (IllegalArgumentException e) {
            throw ApiException.rule(e.getMessage());
        }
        weightsRepo.deactivateAll();
        RankingSettings saved = weightsRepo.save(RankingSettings.from(w, actor));
        audit.record(AuditEntry.of("SETTINGS_WEIGHTS_UPDATED", "SETTINGS", String.valueOf(saved.getId()), actor)
                .detail("expertise", w.expertise()).detail("workload", w.workload())
                .detail("experience", w.experience()).detail("successRate", w.successRate())
                .detail("similarClaims", w.similarClaims()).detail("proximity", w.proximity())
                .detail("predictedFit", w.mlSuitability()).detail("fairnessThreshold", w.fairnessThreshold())
                .detail("fairnessPenalty", w.fairnessPenalty()).detail("usePredictedFit", w.useMl()));
        return SettingsDtos.WeightsResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RuleConfig> activeRuleConfigs() {
        return rulesRepo.findAllByOrderBySortOrderAsc().stream().map(EligibilityRule::toConfig).toList();
    }

    @Transactional(readOnly = true)
    public List<SettingsDtos.RuleResponse> rules() {
        return rulesRepo.findAllByOrderBySortOrderAsc().stream().map(SettingsDtos.RuleResponse::from).toList();
    }

    @Transactional
    public SettingsDtos.RuleResponse updateRule(String code, SettingsDtos.RuleUpdateRequest req, String actor) {
        EligibilityRule r = rulesRepo.findById(code.toUpperCase()).orElseThrow(() -> ApiException.notFound("Eligibility rule", code));
        if (r.isLocked() && !req.enabled()) {
            throw ApiException.rule("'" + r.getName() + "' is a mandatory core rule and cannot be disabled");
        }
        switch (r.getCode()) {
            case "CRITICAL_SEVERITY_EXPERIENCE" -> requireRange(req.param1(), 0, 60, "Minimum years of experience");
            case "HIGH_FRAUD_RISK_EXPERIENCE" -> {
                requireRange(req.param1(), 0, 1, "Fraud risk threshold");
                requireRange(req.param2(), 0, 60, "Minimum years of experience");
            }
            default -> {
                if (req.param1() != null || req.param2() != null) {
                    throw ApiException.badRequest("Rule " + r.getCode() + " has no parameters");
                }
            }
        }
        String before = "enabled=" + r.isEnabled() + ", param1=" + r.getParam1() + ", param2=" + r.getParam2();
        r.setEnabled(req.enabled());
        if (req.param1() != null) r.setParam1(req.param1());
        if (req.param2() != null) r.setParam2(req.param2());
        r.setUpdatedBy(actor);
        r.setUpdatedAt(Instant.now());
        audit.record(AuditEntry.of("ELIGIBILITY_RULE_UPDATED", "RULE", r.getCode(), actor).detail("before", before)
                .detail("after", "enabled=" + r.isEnabled() + ", param1=" + r.getParam1() + ", param2=" + r.getParam2()));
        return SettingsDtos.RuleResponse.from(r);
    }

    private static void requireRange(BigDecimal v, double min, double max, String label) {
        if (v == null) return; // unchanged
        if (v.doubleValue() < min || v.doubleValue() > max) {
            throw ApiException.badRequest(label + " must be between " + min + " and " + max);
        }
    }
}
