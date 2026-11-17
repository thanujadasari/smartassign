package com.smartassign.core.eligibility;

/** Outcome of one rule for one handler. {@code reason} is business language suitable for supervisors. */
public record RuleResult(String code, String name, boolean passed, String reason) {
}
