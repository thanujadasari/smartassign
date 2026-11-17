package com.smartassign.core.eligibility;

import com.smartassign.core.ClaimSnapshot;
import com.smartassign.core.HandlerSnapshot;
import com.smartassign.domain.Severity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Applies the mandatory (hard) eligibility rules. A handler is eligible only if EVERY enabled rule passes.
 * Core rules (ACTIVE, AVAILABLE, EXPERTISE, CAPACITY) are always enforced, even if the configuration
 * passed in claims otherwise - hard conditions are never silently relaxed.
 */
public class EligibilityEngine {

    private static final Set<String> CORE_RULES =
            Set.of(RuleCode.ACTIVE, RuleCode.AVAILABLE, RuleCode.EXPERTISE, RuleCode.CAPACITY);

    public EligibilityDecision evaluate(ClaimSnapshot claim, HandlerSnapshot handler, List<RuleConfig> rules) {
        List<RuleResult> results = new ArrayList<>();
        for (RuleConfig rule : effectiveRules(rules)) {
            results.add(apply(rule, claim, handler));
        }
        boolean eligible = results.stream().allMatch(RuleResult::passed);
        return new EligibilityDecision(handler, eligible, results);
    }

    public List<EligibilityDecision> evaluateAll(ClaimSnapshot claim, List<HandlerSnapshot> handlers, List<RuleConfig> rules) {
        List<RuleConfig> effective = effectiveRules(rules);
        return handlers.stream()
                .sorted(Comparator.comparing(HandlerSnapshot::code))
                .map(h -> evaluate(claim, h, effective))
                .toList();
    }

    /** Enabled rules plus any core rule that was missing or disabled in the configuration. */
    public List<RuleConfig> effectiveRules(List<RuleConfig> configured) {
        List<RuleConfig> out = new ArrayList<>();
        for (String core : List.of(RuleCode.ACTIVE, RuleCode.AVAILABLE, RuleCode.EXPERTISE, RuleCode.CAPACITY)) {
            RuleConfig cfg = configured.stream().filter(r -> r.code().equals(core)).findFirst()
                    .orElse(new RuleConfig(core, defaultName(core), true, true, null, null));
            out.add(new RuleConfig(cfg.code(), cfg.name(), true, true, cfg.param1(), cfg.param2()));
        }
        for (RuleConfig cfg : configured) {
            if (!CORE_RULES.contains(cfg.code()) && cfg.enabled()) {
                out.add(cfg);
            }
        }
        return out;
    }

    RuleResult apply(RuleConfig rule, ClaimSnapshot claim, HandlerSnapshot h) {
        return switch (rule.code()) {
            case RuleCode.ACTIVE -> h.active()
                    ? pass(rule, "Active handler")
                    : fail(rule, "Handler is inactive");
            case RuleCode.AVAILABLE -> h.available()
                    ? pass(rule, "Available")
                    : fail(rule, "Currently unavailable");
            case RuleCode.EXPERTISE -> h.skills().contains(claim.claimType())
                    ? pass(rule, "Holds " + claim.claimType().label() + " expertise")
                    : fail(rule, "Required expertise does not match (needs " + claim.claimType().label()
                    + ", handler is " + h.primaryExpertise().label() + ")");
            case RuleCode.CAPACITY -> h.currentWorkload() < h.maxWorkload()
                    ? pass(rule, "Capacity available (" + h.currentWorkload() + " of " + h.maxWorkload() + " open claims)")
                    : fail(rule, "Workload capacity reached (" + h.currentWorkload() + " of " + h.maxWorkload() + ")");
            case RuleCode.CRITICAL_SEVERITY_EXPERIENCE -> {
                int minYears = (int) Math.round(rule.param1OrDefault(5));
                if (claim.severity() != Severity.CRITICAL) {
                    yield pass(rule, "Not applicable (claim is not critical)");
                }
                yield h.experienceYears() >= minYears
                        ? pass(rule, "Meets critical-claim experience requirement (" + h.experienceYears() + " yrs)")
                        : fail(rule, "Critical claims require at least " + minYears + " years of experience (handler has "
                        + h.experienceYears() + ")");
            }
            case RuleCode.HIGH_FRAUD_RISK_EXPERIENCE -> {
                double threshold = rule.param1OrDefault(0.7);
                int minYears = (int) Math.round(rule.param2OrDefault(8));
                if (claim.fraudRiskScore() < threshold) {
                    yield pass(rule, "Not applicable (fraud risk below review threshold)");
                }
                yield h.experienceYears() >= minYears
                        ? pass(rule, "Meets high fraud-risk experience requirement (" + h.experienceYears() + " yrs)")
                        : fail(rule, "High fraud-risk claims require at least " + minYears
                        + " years of experience (handler has " + h.experienceYears() + ")");
            }
            // Fail safe: an unknown rule must never be treated as passed.
            default -> fail(rule, "Rule '" + rule.code() + "' is not recognised; handler excluded for safety");
        };
    }

    private static RuleResult pass(RuleConfig rule, String reason) {
        return new RuleResult(rule.code(), rule.name(), true, reason);
    }

    private static RuleResult fail(RuleConfig rule, String reason) {
        return new RuleResult(rule.code(), rule.name(), false, reason);
    }

    private static String defaultName(String code) {
        return switch (code) {
            case RuleCode.ACTIVE -> "Handler is active";
            case RuleCode.AVAILABLE -> "Handler is available";
            case RuleCode.EXPERTISE -> "Required expertise";
            case RuleCode.CAPACITY -> "Workload capacity";
            default -> code;
        };
    }
}
