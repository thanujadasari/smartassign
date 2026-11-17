package com.smartassign.claim;

import com.smartassign.auth.User;
import com.smartassign.core.triage.TriageAdvisor;
import com.smartassign.domain.ClaimStatus;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Complexity;
import com.smartassign.domain.IncidentType;
import com.smartassign.domain.PolicyType;
import com.smartassign.domain.Priority;
import com.smartassign.domain.RecordSource;
import com.smartassign.domain.Severity;
import com.smartassign.handler.Handler;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ClaimDtos {

    private ClaimDtos() {
    }

    /** What a customer enters when registering a claim. Severity, complexity and fraud risk are NOT customer inputs. */
    public record CustomerClaimRequest(
            @NotNull(message = "Claim type is required") ClaimType claimType,
            @NotNull(message = "Policy type is required") PolicyType policyType,
            @NotBlank(message = "Policy number is required") @Size(max = 40)
            @Pattern(regexp = "^\\s*[Pp][0-9]{4,12}\\s*$", message = "Policy number must be P followed by 4-12 digits, e.g. P0001") String policyNumber,
            @NotNull(message = "Incident type is required") IncidentType incidentType,
            @NotNull(message = "Incident date is required") @PastOrPresent(message = "Incident date cannot be in the future") LocalDate incidentDate,
            @NotBlank(message = "Location is required") @Size(max = 60) String location,
            @NotNull(message = "Estimated loss is required") @DecimalMin(value = "1.00", message = "Estimated loss must be greater than zero")
            @Digits(integer = 12, fraction = 2, message = "Estimated loss is too large") BigDecimal estimatedLoss,
            @NotNull Boolean injuryInvolved,
            @NotNull Boolean thirdPartyInvolved,
            Boolean ongoingRisk,
            @NotBlank(message = "Please describe what happened") @Size(min = 20, max = 4000, message = "Description must be 20-4000 characters") String description,
            @Pattern(regexp = "^[0-9+()\\- ]{7,30}$", message = "Phone number is not valid") String contactPhone) {
    }

    /** The supervisor's triage decision. */
    public record TriageRequest(
            @NotNull(message = "Priority is required") Priority priority,
            @NotNull(message = "Severity is required") Severity severity,
            @NotNull(message = "Complexity is required") Complexity complexity,
            @NotNull(message = "Fraud risk is required") @DecimalMin(value = "0.0", message = "Fraud risk must be between 0 and 1")
            @DecimalMax(value = "1.0", message = "Fraud risk must be between 0 and 1") BigDecimal fraudRiskScore,
            @Size(max = 1000) String note) {
    }

    /** Change the priority of a claim after triage (any time before it is resolved). */
    public record PriorityRequest(
            @NotNull(message = "Priority is required") Priority priority,
            @NotBlank(message = "Please give a reason for the change") @Size(min = 5, max = 500, message = "Reason must be 5-500 characters") String reason) {
    }

    public record PriorityInfo(Priority priority, Priority suggestedPriority, List<TriageAdvisor.Reason> suggestedReasons,
                               String note, PersonRef setBy, Instant setAt) {
    }

    public record PersonRef(Long id, String fullName, String email, String phone) {
        static PersonRef of(User u) {
            return u == null ? null : new PersonRef(u.getId(), u.getFullName(), u.getEmail(), u.getPhone());
        }
    }

    public record HandlerRef(Long id, String code, String fullName, String email, String phone) {
        static HandlerRef of(Handler h) {
            return h == null ? null : new HandlerRef(h.getId(), h.getCode(), h.getFullName(), h.getEmail(), h.getPhone());
        }
    }

    public record TriageSuggestion(Severity severity, List<TriageAdvisor.Reason> severityReasons,
                                   Complexity complexity, List<TriageAdvisor.Reason> complexityReasons,
                                   BigDecimal fraudRiskScore, List<TriageAdvisor.FraudFlag> fraudFlags, String fraudNote,
                                   Priority priority, List<TriageAdvisor.Reason> priorityReasons) {
        static TriageSuggestion of(TriageAdvisor.Suggestion s) {
            return new TriageSuggestion(s.severity(), s.severityReasons(), s.complexity(), s.complexityReasons(),
                    BigDecimal.valueOf(s.fraudRiskScore()), s.fraudFlags(), s.fraudNote(), s.priority(), s.priorityReasons());
        }
    }

    public record Triage(Severity suggestedSeverity, Complexity suggestedComplexity, BigDecimal suggestedFraudRisk,
                         String note, PersonRef triagedBy, Instant triagedAt) {
    }

    public record StatusEvent(String fromStatus, String toStatus, String note, String actorName, String actorRole,
                              Instant at) {
        static StatusEvent of(ClaimStatusEvent e) {
            return new StatusEvent(e.getFromStatus(), e.getToStatus(), e.getNote(), e.getActorName(), e.getActorRole(),
                    e.getCreatedAt());
        }
    }

    /** Full claim view for supervisors and the assigned handler. */
    public record ClaimResponse(
            Long id, String claimNumber, ClaimType claimType, String claimTypeLabel, PolicyType policyType,
            String policyNumber, IncidentType incidentType, LocalDate incidentDate, String location,
            BigDecimal estimatedLoss, boolean injuryInvolved, boolean thirdPartyInvolved, boolean ongoingRisk,
            String description, String contactPhone, PriorityInfo priority, Severity severity, Complexity complexity,
            BigDecimal fraudRiskScore, int claimAgeDays, int previousReassignments, ClaimStatus status, PersonRef customer, Triage triage,
            HandlerRef assignedHandler, PersonRef assignedBy, Instant assignedAt, Instant resolvedAt,
            String resolutionNote, BigDecimal resolutionDays, RecordSource source, long version, Instant createdAt,
            Instant updatedAt) {

        public static ClaimResponse from(Claim c) {
            PriorityInfo p = new PriorityInfo(c.getPriority(), c.getSuggestedPriority(), reasons(c.getPriorityReasonsJson()), c.getPriorityNote(),
                    PersonRef.of(c.getPrioritySetBy()), c.getPrioritySetAt());
            Triage t = c.getTriagedAt() == null ? null : new Triage(c.getSuggestedSeverity(), c.getSuggestedComplexity(),
                    c.getSuggestedFraudRisk(), c.getTriageNote(), PersonRef.of(c.getTriagedBy()), c.getTriagedAt());
            return new ClaimResponse(c.getId(), c.getClaimNumber(), c.getClaimType(), c.getClaimType().label(),
                    c.getPolicyType(), c.getPolicyNumber(), c.getIncidentType(), c.getIncidentDate(), c.getLocation(),
                    c.getEstimatedLoss(), c.isInjuryInvolved(), c.isThirdPartyInvolved(), c.isOngoingRisk(), c.getDescription(),
                    c.getContactPhone(), p, c.getSeverity(), c.getComplexity(), c.getFraudRiskScore(), c.ageDays(),
                    c.getPreviousReassignments(), c.getStatus(), PersonRef.of(c.getCustomer()), t,
                    HandlerRef.of(c.getAssignedHandler()), PersonRef.of(c.getAssignedBy()), c.getAssignedAt(),
                    c.getResolvedAt(), c.getResolutionNote(), c.getResolutionDays(), c.getSource(), c.getVersion(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    /** Reasons behind the suggested priority, stored as JSON when the claim was registered. */
    static List<TriageAdvisor.Reason> reasons(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return List.of(MAPPER.readValue(json, TriageAdvisor.Reason[].class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return List.of();
        }
    }

    public record ClaimDetail(ClaimResponse claim, List<StatusEvent> timeline) {
    }

    /**
     * What the customer sees. Internal triage values (fraud risk, complexity) are deliberately left out.
     * The assigned handler's name and contact are shown so the customer knows who is working on the claim.
     */
    public record CustomerClaim(
            Long id, String claimNumber, ClaimType claimType, String claimTypeLabel, PolicyType policyType,
            String policyNumber, IncidentType incidentType, LocalDate incidentDate, String location,
            BigDecimal estimatedLoss, boolean injuryInvolved, boolean thirdPartyInvolved, boolean ongoingRisk,
            String description, String contactPhone, String status, String statusLabel, HandlerRef handler, Instant assignedAt,
            Instant resolvedAt, String resolutionNote, Instant createdAt, Instant updatedAt) {

        public static CustomerClaim from(Claim c) {
            return new CustomerClaim(c.getId(), c.getClaimNumber(), c.getClaimType(), c.getClaimType().label(),
                    c.getPolicyType(), c.getPolicyNumber(), c.getIncidentType(), c.getIncidentDate(), c.getLocation(),
                    c.getEstimatedLoss(), c.isInjuryInvolved(), c.isThirdPartyInvolved(), c.isOngoingRisk(), c.getDescription(),
                    c.getContactPhone(), customerStatus(c.getStatus()), customerStatusLabel(c.getStatus()),
                    c.getStatus() == ClaimStatus.ASSIGNED || c.getStatus() == ClaimStatus.RESOLVED
                            ? HandlerRef.of(c.getAssignedHandler()) : null,
                    c.getAssignedAt(), c.getResolvedAt(), c.getResolutionNote(), c.getCreatedAt(), c.getUpdatedAt());
        }

        /** Internal routing states are summarised for the customer. */
        static String customerStatus(ClaimStatus s) {
            return switch (s) {
                case SUBMITTED -> "SUBMITTED";
                case UNASSIGNED, MANUAL_REVIEW -> "IN_REVIEW";
                case ASSIGNED -> "ASSIGNED";
                case RESOLVED -> "RESOLVED";
            };
        }

        static String customerStatusLabel(ClaimStatus s) {
            return switch (s) {
                case SUBMITTED -> "Submitted";
                case UNASSIGNED, MANUAL_REVIEW -> "Under review";
                case ASSIGNED -> "Handler assigned";
                case RESOLVED -> "Resolved";
            };
        }
    }

    public record CustomerClaimDetail(CustomerClaim claim, List<StatusEvent> timeline) {
    }
}
