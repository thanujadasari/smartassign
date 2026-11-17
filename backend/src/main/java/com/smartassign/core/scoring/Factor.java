package com.smartassign.core.scoring;

/** Explainable ranking factors. Labels are supervisor-facing business language. */
public enum Factor {
    EXPERTISE("Expertise match"),
    WORKLOAD("Workload fit"),
    EXPERIENCE("Relevant experience"),
    SUCCESS_RATE("Historical success rate"),
    SIMILAR_CLAIMS("Similar claims handled"),
    PROXIMITY("Location proximity"),
    ML_SUITABILITY("Predicted claim fit");

    private final String label;

    Factor(String label) { this.label = label; }

    public String label() { return label; }
}
