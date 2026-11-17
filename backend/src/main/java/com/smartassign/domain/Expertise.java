package com.smartassign.domain;

import java.util.EnumSet;
import java.util.Set;

/** Primary expertise of a handler. MULTI_P_AND_C covers every claim type (verified against expertise_match in the dataset). */
public enum Expertise {
    AUTO("Auto"),
    PROPERTY("Property"),
    LIABILITY("Liability"),
    WORKERS_COMPENSATION("Workers' Compensation"),
    MULTI_P_AND_C("Multi-line P&C");

    private final String label;

    Expertise(String label) { this.label = label; }

    public String label() { return label; }

    /** Default skill set implied by this primary expertise. */
    public Set<ClaimType> impliedSkills() {
        if (this == MULTI_P_AND_C) {
            return EnumSet.allOf(ClaimType.class);
        }
        return EnumSet.of(ClaimType.valueOf(name()));
    }

    public boolean isSpecialistFor(ClaimType type) {
        return this != MULTI_P_AND_C && name().equals(type.name());
    }
}
