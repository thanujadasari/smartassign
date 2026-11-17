package com.smartassign.integration;

import java.util.List;

/**
 * INDICATIVE field mapping between SmartAssign and ClaimCenter. ClaimCenter entity and field names vary by version
 * and by customer extensions (fields ending in _Ext); confirm every row against the target data model before use.
 */
public final class ClaimCenterFieldMapping {

    private ClaimCenterFieldMapping() {
    }

    public record Row(String smartAssignField, String claimCenterSource, String direction, String notes) {
    }

    public static final List<Row> CLAIM_FIELDS = List.of(
            new Row("claim_number", "Claim.ClaimNumber", "IN", "Natural key; used for idempotent upserts"),
            new Row("claim_type", "Claim.LossType (mapped to AUTO / PROPERTY / LIABILITY / WORKERS_COMPENSATION)", "IN", "Mapping table required"),
            new Row("policy_type", "Claim.Policy.PolicyType", "IN", "Mapping table required"),
            new Row("severity", "Exposure.Severity / claim-level severity", "IN", "Highest exposure severity if claim-level is absent"),
            new Row("complexity", "Customer extension (e.g. Claim.Complexity_Ext)", "IN", "Not standard - confirm availability"),
            new Row("estimated_loss", "Sum of exposure reserves or Claim.LossEstimate", "IN", "Define the source of truth with finance"),
            new Row("claim_location", "Claim.LossLocation.City", "IN", "Normalise city names to the distance reference table"),
            new Row("claim_age_days", "Derived from Claim.ReportedDate", "IN", "Computed at sync time"),
            new Row("incident_type", "Claim.LossCause", "IN", "Mapping table required"),
            new Row("fraud_risk_score", "SIU / fraud scoring extension", "IN", "Only if a scoring source exists"),
            new Row("injury_involved", "Presence of injury exposures / Claim.InjuredParty", "IN", ""),
            new Row("third_party_involved", "Claim contacts with third-party roles", "IN", ""),
            new Row("assigned handler", "Claim.AssignedUser / Claim.AssignedGroup", "OUT (optional)", "Write-back only after supervisor approval"));

    public static final List<Row> HANDLER_FIELDS = List.of(
            new Row("handler_code", "User.PublicID or employee number", "IN", "Stable identifier"),
            new Row("full_name", "User.Contact name", "IN", ""),
            new Row("expertise / skills", "User authority profiles, group membership or attributes", "IN", "Mapping to claim lines required"),
            new Row("current_workload", "Count of open claims assigned to the user", "IN", "Computed during sync"),
            new Row("max_workload", "Customer-defined capacity (often a group or user attribute)", "IN", ""),
            new Row("available", "User vacation / availability status", "IN", ""),
            new Row("active", "User.Active / credential status", "IN", ""),
            new Row("location", "User or group region", "IN", ""),
            new Row("success_rate / avg_resolution_days / similar_claims_handled", "Derived from closed-claim history", "IN", "Requires a reporting extract"));

    public static final List<String> LIMITATIONS = List.of(
            "No live ClaimCenter connection is configured or tested in this project.",
            "ClaimCenter already provides configurable assignment rules; SmartAssign is a complementary simulator and explanation layer.",
            "Write-back must go through approved APIs, respect ClaimCenter assignment rules and be idempotent.",
            "Data synchronisation latency means workload snapshots may lag the system of record.",
            "Handler performance metrics require a historical reporting extract that may not exist in every installation.");
}
