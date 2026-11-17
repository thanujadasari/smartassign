package com.smartassign.core.eligibility;

/** Runtime configuration of one eligibility rule (loaded from the eligibility_rules table). */
public record RuleConfig(String code, String name, boolean enabled, boolean locked, Double param1, Double param2) {

    public double param1OrDefault(double fallback) {
        return param1 == null ? fallback : param1;
    }

    public double param2OrDefault(double fallback) {
        return param2 == null ? fallback : param2;
    }
}
