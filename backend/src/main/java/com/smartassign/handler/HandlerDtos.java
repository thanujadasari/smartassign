package com.smartassign.handler;

import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Expertise;
import com.smartassign.domain.RecordSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class HandlerDtos {

    private HandlerDtos() {
    }

    /**
     * What a supervisor may change on a handler profile. Availability is NOT here - only the handler sets it.
     * Statistics are NOT here - they are computed from claims the handler resolved.
     */
    public record HandlerUpdateRequest(
            @NotNull(message = "Primary expertise is required") Expertise primaryExpertise,
            Set<ClaimType> additionalSkills,
            @NotNull @Min(value = 0, message = "Experience cannot be negative") @Max(value = 60, message = "Experience must be 60 years or less") Integer experienceYears,
            @NotNull @Min(value = 1, message = "Maximum workload must be at least 1") @Max(value = 200, message = "Maximum workload must be 200 or less") Integer maxWorkload,
            @NotBlank(message = "Location is required") @Size(max = 60) String location) {
    }

    /** The handler's own availability. A reason is required when unavailable. */
    public record AvailabilityRequest(@NotNull(message = "Choose Available or Unavailable") Boolean available,
                                      @Size(max = 500) String reason) {
    }

    public record StatusRequest(@NotNull Boolean active, @Size(max = 300) String reason) {
    }

    public record HandlerResponse(
            Long id, String code, String fullName, String email, String phone, Expertise primaryExpertise,
            String primaryExpertiseLabel, Set<ClaimType> skills, int experienceYears, int currentWorkload,
            int maxWorkload, int openSlots, double utilization, boolean available, String unavailableReason,
            Instant availabilityChangedAt, boolean active, String location, int resolvedCount,
            BigDecimal successRate, BigDecimal avgResolutionDays, boolean hasHistory, RecordSource source,
            String status, long version, Instant createdAt, Instant updatedAt) {

        public static HandlerResponse from(Handler h) {
            return new HandlerResponse(h.getId(), h.getCode(), h.getFullName(), h.getEmail(), h.getPhone(),
                    h.getPrimaryExpertise(), h.getPrimaryExpertise().label(), Set.copyOf(h.getSkills()),
                    h.getExperienceYears(), h.getCurrentWorkload(), h.getMaxWorkload(),
                    Math.max(0, h.getMaxWorkload() - h.getCurrentWorkload()),
                    Math.round(h.utilization() * 10000.0) / 10000.0, h.isAvailable(), h.getUnavailableReason(),
                    h.getAvailabilityChangedAt(), h.isActive(), h.getLocation(), h.getResolvedCount(),
                    h.hasHistory() ? h.getSuccessRate() : null, h.hasHistory() ? h.getAvgResolutionDays() : null,
                    h.hasHistory(), h.getSource(), statusOf(h), h.getVersion(), h.getCreatedAt(), h.getUpdatedAt());
        }

        static String statusOf(Handler h) {
            if (!h.isActive()) return "INACTIVE";
            if (!h.isAvailable()) return "UNAVAILABLE";
            if (h.getCurrentWorkload() > h.getMaxWorkload()) return "OVER_CAPACITY";
            if (h.getCurrentWorkload() == h.getMaxWorkload()) return "AT_CAPACITY";
            return "AVAILABLE";
        }
    }

    public record AssignedClaim(Long claimId, String claimNumber, String claimType, String severity, String location,
                                String status, String assignedByName, Instant assignedAt, Instant resolvedAt) {
    }

    public record AvailabilityEvent(boolean available, String reason, String changedBy, Instant changedAt) {
    }

    public record HandlerDetail(HandlerResponse handler, List<AssignedClaim> openClaims, List<AssignedClaim> resolvedClaims,
                                List<AvailabilityEvent> availabilityHistory, long totalAssignments,
                                long overridesReceived, long recommendationsAsTopChoice) {
    }
}
