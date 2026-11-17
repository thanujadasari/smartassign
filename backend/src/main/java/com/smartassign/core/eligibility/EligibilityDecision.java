package com.smartassign.core.eligibility;

import com.smartassign.core.HandlerSnapshot;

import java.util.List;
import java.util.stream.Collectors;

public record EligibilityDecision(HandlerSnapshot handler, boolean eligible, List<RuleResult> ruleResults) {

    public EligibilityDecision {
        ruleResults = List.copyOf(ruleResults);
    }

    public List<RuleResult> failedRules() {
        return ruleResults.stream().filter(r -> !r.passed()).toList();
    }

    /** e.g. "Excluded - currently unavailable; workload capacity reached (12 of 12)". */
    public String exclusionSummary() {
        if (eligible) {
            return null;
        }
        return failedRules().stream().map(RuleResult::reason).collect(Collectors.joining("; "));
    }
}
