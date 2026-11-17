package com.smartassign.core;

import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Expertise;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable, framework-free view of a handler. Routing creates modified copies with
 * {@link #withWorkload(int)} so that no strategy can mutate another strategy's state.
 */
public record HandlerSnapshot(
        Long id,
        String code,
        String name,
        Expertise primaryExpertise,
        Set<ClaimType> skills,
        int experienceYears,
        int currentWorkload,
        int maxWorkload,
        boolean available,
        boolean active,
        String location,
        double successRate,
        double avgResolutionDays,
        int similarClaimsHandled,
        boolean hasHistory) {

    /**
     * hasHistory = false means the handler has no resolved claims yet: successRate, avgResolutionDays and
     * similarClaimsHandled then hold documented neutral values used for ranking only, never shown as statistics.
     */
    public HandlerSnapshot(Long id, String code, String name, Expertise primaryExpertise, Set<ClaimType> skills,
                           int experienceYears, int currentWorkload, int maxWorkload, boolean available, boolean active,
                           String location, double successRate, double avgResolutionDays, int similarClaimsHandled) {
        this(id, code, name, primaryExpertise, skills, experienceYears, currentWorkload, maxWorkload, available, active,
                location, successRate, avgResolutionDays, similarClaimsHandled, true);
    }

    public HandlerSnapshot {
        skills = skills == null || skills.isEmpty()
                ? Collections.unmodifiableSet(primaryExpertise.impliedSkills())
                : Collections.unmodifiableSet(EnumSet.copyOf(skills));
    }

    public HandlerSnapshot withWorkload(int newWorkload) {
        return new HandlerSnapshot(id, code, name, primaryExpertise, skills, experienceYears, newWorkload,
                maxWorkload, available, active, location, successRate, avgResolutionDays, similarClaimsHandled, hasHistory);
    }

    public double utilization() {
        return maxWorkload <= 0 ? 1.0 : (double) currentWorkload / maxWorkload;
    }

    public int openSlots() {
        return Math.max(0, maxWorkload - currentWorkload);
    }
}
