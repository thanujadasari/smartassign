package com.smartassign.domain;

/** Line of business of a claim. Values match the training dataset's claim_type column. */
public enum ClaimType {
    AUTO("Auto"),
    PROPERTY("Property"),
    LIABILITY("Liability"),
    WORKERS_COMPENSATION("Workers' Compensation");

    private final String label;

    ClaimType(String label) { this.label = label; }

    public String label() { return label; }
}
