package com.smartassign.core;

import com.smartassign.core.eligibility.EligibilityDecision;
import com.smartassign.core.eligibility.EligibilityEngine;
import com.smartassign.core.eligibility.RuleConfig;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Expertise;
import com.smartassign.domain.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.smartassign.core.Fixtures.claim;
import static com.smartassign.core.Fixtures.defaultRules;
import static com.smartassign.core.Fixtures.handler;
import static org.assertj.core.api.Assertions.assertThat;

class EligibilityEngineTest {

    private final EligibilityEngine engine = new EligibilityEngine();

    @Test
    void eligibleHandlerPassesEveryRule() {
        EligibilityDecision d = engine.evaluate(claim(ClaimType.AUTO), handler(1, Expertise.AUTO, 3, 10), defaultRules());
        assertThat(d.eligible()).isTrue();
        assertThat(d.ruleResults()).allMatch(r -> r.passed());
        assertThat(d.exclusionSummary()).isNull();
    }

    @Test
    void unavailableHandlerIsExcludedWithReason() {
        EligibilityDecision d = engine.evaluate(claim(ClaimType.AUTO),
                handler(2, Expertise.AUTO, 3, 10, false, true, 10, "Pune"), defaultRules());
        assertThat(d.eligible()).isFalse();
        assertThat(d.exclusionSummary()).contains("unavailable");
    }

    @Test
    void wrongExpertiseIsExcluded() {
        EligibilityDecision d = engine.evaluate(claim(ClaimType.LIABILITY), handler(3, Expertise.PROPERTY, 0, 10), defaultRules());
        assertThat(d.eligible()).isFalse();
        assertThat(d.exclusionSummary()).contains("Required expertise does not match");
    }

    @Test
    void fullCapacityIsExcluded() {
        EligibilityDecision d = engine.evaluate(claim(ClaimType.AUTO), handler(4, Expertise.AUTO, 10, 10), defaultRules());
        assertThat(d.eligible()).isFalse();
        assertThat(d.exclusionSummary()).contains("Workload capacity reached (10 of 10)");
    }

    @Test
    void inactiveHandlerIsExcluded() {
        EligibilityDecision d = engine.evaluate(claim(ClaimType.AUTO),
                handler(5, Expertise.AUTO, 1, 10, true, false, 10, "Pune"), defaultRules());
        assertThat(d.eligible()).isFalse();
    }

    @Test
    void multiLineHandlerCoversEveryClaimType() {
        for (ClaimType t : ClaimType.values()) {
            assertThat(engine.evaluate(claim(t), handler(6, Expertise.MULTI_P_AND_C, 0, 10), defaultRules()).eligible()).isTrue();
        }
    }

    @Test
    void criticalClaimsRequireExperience() {
        var junior = handler(7, Expertise.AUTO, 0, 10, true, true, 2, "Pune");
        var critical = claim(ClaimType.AUTO, Severity.CRITICAL, "Pune", 0.1);
        assertThat(engine.evaluate(critical, junior, defaultRules()).eligible()).isFalse();
        assertThat(engine.evaluate(claim(ClaimType.AUTO), junior, defaultRules()).eligible()).isTrue();
    }

    @Test
    void highFraudRiskRequiresExperience() {
        var mid = handler(8, Expertise.AUTO, 0, 10, true, true, 6, "Pune");
        assertThat(engine.evaluate(claim(ClaimType.AUTO, Severity.LOW, "Pune", 0.8), mid, defaultRules()).eligible()).isFalse();
        assertThat(engine.evaluate(claim(ClaimType.AUTO, Severity.LOW, "Pune", 0.3), mid, defaultRules()).eligible()).isTrue();
    }

    @Test
    void coreRulesCannotBeSwitchedOffByConfiguration() {
        List<RuleConfig> tampered = List.of(new RuleConfig("AVAILABLE", "Handler is available", false, true, null, null));
        var unavailable = handler(9, Expertise.AUTO, 0, 10, false, true, 10, "Pune");
        assertThat(engine.evaluate(claim(ClaimType.AUTO), unavailable, tampered).eligible()).isFalse();
    }

    @Test
    void disabledBusinessRuleIsNotApplied() {
        List<RuleConfig> rules = defaultRules().stream()
                .map(r -> r.code().equals("CRITICAL_SEVERITY_EXPERIENCE")
                        ? new RuleConfig(r.code(), r.name(), false, false, 5.0, null) : r).toList();
        var junior = handler(10, Expertise.AUTO, 0, 10, true, true, 2, "Pune");
        assertThat(engine.evaluate(claim(ClaimType.AUTO, Severity.CRITICAL, "Pune", 0.1), junior, rules).eligible()).isTrue();
    }

    @Test
    void unknownRuleFailsSafe() {
        var d = engine.evaluate(claim(ClaimType.AUTO), handler(11, Expertise.AUTO, 0, 10),
                List.of(new RuleConfig("SOMETHING_NEW", "New rule", true, false, null, null)));
        assertThat(d.eligible()).isFalse();
    }

    @Test
    void evaluateAllReturnsEveryHandlerSortedByCode() {
        var res = engine.evaluateAll(claim(ClaimType.AUTO),
                List.of(handler(3, Expertise.AUTO, 0, 10), handler(1, Expertise.PROPERTY, 0, 10)), defaultRules());
        assertThat(res).extracting(d -> d.handler().code()).containsExactly("A001", "A003");
        assertThat(res).extracting(EligibilityDecision::eligible).containsExactly(false, true);
    }
}
