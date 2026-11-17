package com.smartassign.handlerportal;

import com.smartassign.audit.AuditEntry;
import com.smartassign.audit.AuditService;
import com.smartassign.auth.Role;
import com.smartassign.auth.User;
import com.smartassign.claim.Claim;
import com.smartassign.claim.ClaimDtos;
import com.smartassign.claim.ClaimHistory;
import com.smartassign.claim.ClaimRepository;
import com.smartassign.claim.ClaimService;
import com.smartassign.common.ApiException;
import com.smartassign.common.PageResponse;
import com.smartassign.config.AppProperties;
import com.smartassign.domain.ClaimStatus;
import com.smartassign.handler.Handler;
import com.smartassign.handler.HandlerDtos;
import com.smartassign.handler.HandlerRepository;
import com.smartassign.handler.HandlerService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Handler portal. A handler cannot accept or reject an assignment; they can only
 *  - set themselves Available / Unavailable (with a reason),
 *  - view the claims assigned to them (with the supervisor who assigned each one),
 *  - mark an assigned claim resolved, which frees capacity and updates their real statistics.
 */
@Service
public class HandlerPortalService {

    private final HandlerRepository handlers;
    private final HandlerService handlerService;
    private final ClaimRepository claims;
    private final ClaimService claimService;
    private final ClaimHistory history;
    private final AuditService audit;
    private final AppProperties props;

    public HandlerPortalService(HandlerRepository handlers, HandlerService handlerService, ClaimRepository claims,
                                ClaimService claimService, ClaimHistory history, AuditService audit, AppProperties props) {
        this.handlers = handlers;
        this.handlerService = handlerService;
        this.claims = claims;
        this.claimService = claimService;
        this.history = history;
        this.audit = audit;
        this.props = props;
    }

    public record Profile(HandlerDtos.HandlerResponse handler, int resolutionTargetDays,
                          List<HandlerDtos.AvailabilityEvent> availabilityHistory) {
    }

    public record ResolveRequest(@jakarta.validation.constraints.NotBlank(message = "Please describe how the claim was resolved")
                                 @jakarta.validation.constraints.Size(min = 10, max = 2000, message = "Resolution note must be 10-2000 characters")
                                 String note) {
    }

    Handler handlerOf(User user) {
        if (user.getRole() != Role.HANDLER) {
            throw ApiException.forbidden("This area is only for handlers");
        }
        return handlers.findByUserId(user.getId())
                .orElseThrow(() -> ApiException.notFound("Handler profile for user", user.getEmail()));
    }

    @Transactional(readOnly = true)
    public Profile profile(User user) {
        Handler h = handlerOf(user);
        return new Profile(HandlerDtos.HandlerResponse.from(h), targetDays(), handlerService.availabilityHistory(h.getId()));
    }

    @Transactional
    public Profile setAvailability(User user, HandlerDtos.AvailabilityRequest req) {
        Handler h = handlerOf(user);
        HandlerDtos.HandlerResponse r = handlerService.setAvailability(h.getId(), req, user.getUsername());
        return new Profile(r, targetDays(), handlerService.availabilityHistory(h.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimDtos.ClaimResponse> myClaims(User user, List<ClaimStatus> statuses, Pageable pageable) {
        Handler h = handlerOf(user);
        return PageResponse.of(claims.findByAssignedHandlerIdAndStatusIn(h.getId(), statuses, pageable),
                ClaimDtos.ClaimResponse::from);
    }

    @Transactional(readOnly = true)
    public ClaimDtos.ClaimDetail claim(User user, Long claimId) {
        Handler h = handlerOf(user);
        Claim c = claims.findById(claimId).orElseThrow(() -> ApiException.notFound("Claim", claimId));
        if (c.getAssignedHandler() == null || !c.getAssignedHandler().getId().equals(h.getId())) {
            throw ApiException.notFound("Claim", claimId);
        }
        return new ClaimDtos.ClaimDetail(ClaimDtos.ClaimResponse.from(c), claimService.timeline(c.getId()));
    }

    @Transactional
    public ClaimDtos.ClaimDetail resolve(User user, Long claimId, ResolveRequest req) {
        Handler mine = handlerOf(user);
        Claim c = claims.findByIdForUpdate(claimId).orElseThrow(() -> ApiException.notFound("Claim", claimId));
        if (c.getAssignedHandler() == null || !c.getAssignedHandler().getId().equals(mine.getId())) {
            throw ApiException.notFound("Claim", claimId);
        }
        if (c.getStatus() != ClaimStatus.ASSIGNED) {
            throw ApiException.conflict("Only claims that are currently assigned to you can be resolved (claim is " + c.getStatus() + ")");
        }
        Handler h = handlers.findByIdForUpdate(mine.getId()).orElseThrow();
        Instant now = Instant.now();
        Instant from = c.getAssignedAt() == null ? c.getCreatedAt() : c.getAssignedAt();
        double days = Math.max(0, Duration.between(from, now).toMinutes() / 1440.0);
        boolean onTime = days <= targetDays();

        c.setStatus(ClaimStatus.RESOLVED);
        c.setResolvedAt(now);
        c.setResolutionNote(req.note().trim());
        c.setResolutionDays(BigDecimal.valueOf(days).setScale(2, RoundingMode.HALF_UP));
        h.setCurrentWorkload(Math.max(0, h.getCurrentWorkload() - 1));
        h.recordResolution(days, onTime);

        history.record(c, ClaimStatus.ASSIGNED, ClaimStatus.RESOLVED, c.getResolutionNote(), user);
        audit.record(AuditEntry.of("CLAIM_RESOLVED", "CLAIM", String.valueOf(c.getId()), user.getUsername())
                .claim(c.getClaimNumber()).finalHandler(h.getCode())
                .detail("resolutionDays", c.getResolutionDays()).detail("onTime", onTime)
                .details("Resolved in " + c.getResolutionDays() + " days" + (onTime ? "" : " (after the "
                        + targetDays() + "-day target)") + ": " + c.getResolutionNote()));
        return new ClaimDtos.ClaimDetail(ClaimDtos.ClaimResponse.from(c), claimService.timeline(c.getId()));
    }

    int targetDays() {
        return props.claims() == null || props.claims().resolutionTargetDays() <= 0 ? 14 : props.claims().resolutionTargetDays();
    }
}
