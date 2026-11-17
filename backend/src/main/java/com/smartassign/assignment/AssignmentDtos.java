package com.smartassign.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    /** Opens a pending decision from a recommendation run (the run's top-ranked handler is the recommendation). */
    public record CreateRequest(@NotNull(message = "claimId is required") Long claimId,
                                @NotNull(message = "runId is required") Long runId) {
    }

    public record ApproveRequest(@Size(max = 1000) String note) {
    }

    /** Select another ELIGIBLE handler from the same run. A written reason is mandatory. */
    public record OverrideRequest(@NotNull(message = "Select the handler to assign") Long handlerId,
                                  @NotBlank(message = "An override reason is required")
                                  @Size(min = 10, max = 1000, message = "Override reason must be 10-1000 characters") String reason) {
    }

    public record ManualReviewRequest(@NotBlank(message = "A reason for manual review is required")
                                      @Size(min = 5, max = 1000, message = "Reason must be 5-1000 characters") String reason) {
    }

    public record HandlerRef(Long id, String code, String fullName) {
    }

    public record AssignmentResponse(Long id, Long claimId, String claimNumber, Long runId,
                                     HandlerRef recommendedHandler, HandlerRef assignedHandler, String status,
                                     String decisionType, Double recommendationScore, Double assignedHandlerScore,
                                     String explanation, String overrideReason, String reviewReason,
                                     String fallbackReason, String createdBy, String decidedBy, Instant decidedAt,
                                     Instant createdAt) {
    }
}
