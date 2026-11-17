package com.smartassign.core;

import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Complexity;
import com.smartassign.domain.IncidentType;
import com.smartassign.domain.PolicyType;
import com.smartassign.domain.Severity;

/** Immutable, framework-free view of a claim used by the routing engine. */
public record ClaimSnapshot(
        Long id,
        String claimNumber,
        ClaimType claimType,
        PolicyType policyType,
        Severity severity,
        Complexity complexity,
        double estimatedLoss,
        String location,
        int claimAgeDays,
        IncidentType incidentType,
        double fraudRiskScore,
        boolean injuryInvolved,
        boolean thirdPartyInvolved) {
}
