package com.smartassign.core.eligibility;

/** Codes of the eligibility rules known to the engine. Must match rows in the eligibility_rules table. */
public final class RuleCode {
    public static final String ACTIVE = "ACTIVE";
    public static final String AVAILABLE = "AVAILABLE";
    public static final String EXPERTISE = "EXPERTISE";
    public static final String CAPACITY = "CAPACITY";
    public static final String CRITICAL_SEVERITY_EXPERIENCE = "CRITICAL_SEVERITY_EXPERIENCE";
    public static final String HIGH_FRAUD_RISK_EXPERIENCE = "HIGH_FRAUD_RISK_EXPERIENCE";

    private RuleCode() {
    }
}
