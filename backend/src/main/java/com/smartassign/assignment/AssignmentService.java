package com.smartassign.assignment;

import com.smartassign.auth.User;
import com.smartassign.auth.UserRepository;
import com.smartassign.claim.ClaimHistory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.smartassign.audit.AuditEntry;
import com.smartassign.audit.AuditService;
import com.smartassign.claim.Claim;
import com.smartassign.claim.ClaimRepository;
import com.smartassign.common.ApiException;
import com.smartassign.common.JsonSupport;
import com.smartassign.common.PageResponse;
import com.smartassign.config.AppProperties;
import com.smartassign.core.eligibility.EligibilityDecision;
import com.smartassign.core.eligibility.EligibilityEngine;
import com.smartassign.core.eligibility.RuleResult;
import com.smartassign.domain.ClaimStatus;
import com.smartassign.handler.Handler;
import com.smartassign.handler.HandlerRepository;
import com.smartassign.routing.AssignmentRecommendation;
import com.smartassign.routing.AssignmentRecommendationRepository;
import com.smartassign.routing.EligibilityResultEntity;
import com.smartassign.routing.EligibilityResultRepository;
import com.smartassign.routing.RecommendationRun;
import com.smartassign.routing.RecommendationRunRepository;
import com.smartassign.routing.RoutingDtos;
import com.smartassign.settings.SettingsService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Supervisor decisions. Every approval / override runs in ONE transaction that:
 *   locks the assignment, claim and handler rows -> re-checks hard eligibility against LIVE data ->
 *   increments the handler's workload -> marks the claim ASSIGNED -> finalises the assignment -> writes the audit record.
 * An ineligible handler can never be assigned, whatever the client sends.
 */
@Service
public class AssignmentService {

    private final AssignmentRepository assignments;
    private final ClaimRepository claims;
    private final HandlerRepository handlers;
    private final RecommendationRunRepository runs;
    private final AssignmentRecommendationRepository recommendations;
    private final EligibilityResultRepository eligibilityResults;
    private final EligibilityEngine eligibility;
    private final SettingsService settings;
    private final AuditService audit;
    private final JsonSupport json;
    private final int validityMinutes;
    private final UserRepository users;
    private final ClaimHistory history;

    public AssignmentService(AssignmentRepository assignments, ClaimRepository claims, HandlerRepository handlers,
                             RecommendationRunRepository runs, AssignmentRecommendationRepository recommendations,
                             EligibilityResultRepository eligibilityResults, EligibilityEngine eligibility,
                             SettingsService settings, AuditService audit, JsonSupport json, AppProperties props,
                             UserRepository users, ClaimHistory history) {
        this.users = users;
        this.history = history;
        this.assignments = assignments;
        this.claims = claims;
        this.handlers = handlers;
        this.runs = runs;
        this.recommendations = recommendations;
        this.eligibilityResults = eligibilityResults;
        this.eligibility = eligibility;
        this.settings = settings;
        this.audit = audit;
        this.json = json;
        this.validityMinutes = props.routing() == null ? 60 : Math.max(1, props.routing().recommendationValidityMinutes());
    }

    @Transactional
    public AssignmentDtos.AssignmentResponse create(AssignmentDtos.CreateRequest req, String actor) {
        Claim claim = claims.findByIdForUpdate(req.claimId()).orElseThrow(() -> ApiException.notFound("Claim", req.claimId()));
        requireRoutable(claim);
        RecommendationRun run = runs.findById(req.runId()).orElseThrow(() -> ApiException.notFound("Recommendation run", req.runId()));
        if (!run.getClaimId().equals(claim.getId())) {
            throw ApiException.badRequest("Recommendation run " + run.getId() + " does not belong to claim " + claim.getClaimNumber());
        }
        if (run.getRunType() != RecommendationRun.RunType.RECOMMENDATION || run.getOutcome() != RecommendationRun.Outcome.ELIGIBLE_FOUND) {
            throw ApiException.rule("This run has no ranked recommendations to act on");
        }
        RecommendationRun latest = runs.findFirstByClaimIdAndRunTypeOrderByCreatedAtDescIdDesc(claim.getId(),
                RecommendationRun.RunType.RECOMMENDATION).orElseThrow();
        if (!latest.getId().equals(run.getId())) {
            throw ApiException.conflict("Newer recommendations exist for this claim. Please use the latest recommendations.");
        }
        if (run.getCreatedAt().plusSeconds(validityMinutes * 60L).isBefore(Instant.now())) {
            throw ApiException.conflict("These recommendations are more than " + validityMinutes
                    + " minutes old. Please generate fresh recommendations.");
        }
        AssignmentRecommendation top = recommendations.findByRunIdAndRank(run.getId(), 1)
                .orElseThrow(() -> ApiException.rule("This run has no ranked recommendations to act on"));

        assignments.cancelPendingForClaim(claim.getId());
        Assignment a = new Assignment();
        a.setClaimId(claim.getId());
        a.setRunId(run.getId());
        a.setRecommendedHandlerId(top.getHandlerId());
        a.setStatus(Assignment.Status.PENDING);
        a.setRecommendationScore(top.getTotalScore());
        a.setExplanation(top.getExplanation());
        a.setModelVersion(run.getModelVersion());
        a.setMlStatus(run.getMlStatus());
        a.setCreatedBy(actor);
        assignments.save(a);
        return toResponse(a);
    }

    @Transactional
    public AssignmentDtos.AssignmentResponse approve(Long id, AssignmentDtos.ApproveRequest req, String actor) {
        Assignment a = lockPending(id);
        Claim claim = lockRoutableClaim(a);
        Handler handler = handlers.findByIdForUpdate(a.getRecommendedHandlerId())
                .orElseThrow(() -> ApiException.notFound("Handler", a.getRecommendedHandlerId()));
        requireLiveEligible(claim, handler, "The recommended handler");
        finalise(a, claim, handler, Assignment.Status.APPROVED, Assignment.DecisionType.APPROVED_RECOMMENDATION,
                a.getRecommendationScore(), null, actor);
        AuditEntry entry = base("ASSIGNMENT_APPROVED", a, claim, handler, actor).action("APPROVE_RECOMMENDATION");
        if (req != null && req.note() != null && !req.note().isBlank()) {
            entry.details(req.note().trim());
        }
        audit.record(entry);
        return toResponse(a);
    }

    @Transactional
    public AssignmentDtos.AssignmentResponse override(Long id, AssignmentDtos.OverrideRequest req, String actor) {
        if (req.reason().trim().length() < 10) {
            throw ApiException.badRequest("Override reason must be at least 10 characters");
        }
        Assignment a = lockPending(id);
        if (req.handlerId().equals(a.getRecommendedHandlerId())) {
            throw ApiException.badRequest("This is the recommended handler - use Approve instead of Override");
        }
        // 1. must have been ELIGIBLE in the run the supervisor is looking at
        EligibilityResultEntity inRun = eligibilityResults.findByRunIdAndHandlerId(a.getRunId(), req.handlerId())
                .orElseThrow(() -> ApiException.rule("The selected handler was not evaluated for this claim"));
        if (!inRun.isEligible()) {
            throw ApiException.rule("The selected handler is not eligible for this claim and cannot be assigned",
                    inRun.getExclusionReasons() == null ? List.of() : List.of(inRun.getExclusionReasons().split("; ")));
        }
        AssignmentRecommendation chosen = recommendations.findByRunIdAndHandlerId(a.getRunId(), req.handlerId())
                .orElseThrow(() -> ApiException.rule("The selected handler has no ranking in this run"));
        Claim claim = lockRoutableClaim(a);
        Handler handler = handlers.findByIdForUpdate(req.handlerId())
                .orElseThrow(() -> ApiException.notFound("Handler", req.handlerId()));
        // 2. and must STILL be eligible right now
        requireLiveEligible(claim, handler, "The selected handler");
        finalise(a, claim, handler, Assignment.Status.OVERRIDDEN, Assignment.DecisionType.OVERRIDE,
                chosen.getTotalScore(), req.reason().trim(), actor);
        audit.record(base("ASSIGNMENT_OVERRIDDEN", a, claim, handler, actor).action("OVERRIDE")
                .overrideReason(a.getOverrideReason()).detail("selectedRank", chosen.getRank())
                .detail("selectedScore", chosen.getTotalScore()));
        return toResponse(a);
    }

    @Transactional
    public AssignmentDtos.AssignmentResponse manualReview(Long id, AssignmentDtos.ManualReviewRequest req, String actor) {
        Assignment a = lockPending(id);
        Claim claim = lockRoutableClaim(a);
        a.setStatus(Assignment.Status.MANUAL_REVIEW);
        a.setDecisionType(Assignment.DecisionType.MANUAL_REVIEW);
        a.setReviewReason(req.reason().trim());
        a.setDecidedBy(actor);
        a.setDecidedAt(Instant.now());
        ClaimStatus from = claim.getStatus();
        claim.setStatus(ClaimStatus.MANUAL_REVIEW);
        history.record(claim, from, ClaimStatus.MANUAL_REVIEW, "Sent for manual review: " + a.getReviewReason(), user(actor));
        audit.record(base("MANUAL_REVIEW_REQUESTED", a, claim, null, actor).action("MANUAL_REVIEW")
                .details(a.getReviewReason()));
        return toResponse(a);
    }

    @Transactional(readOnly = true)
    public AssignmentDtos.AssignmentResponse get(Long id) {
        return toResponse(assignments.findById(id).orElseThrow(() -> ApiException.notFound("Assignment", id)));
    }

    public record AssignmentFilter(Long claimId, Assignment.Status status, Long handlerId) {
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentDtos.AssignmentResponse> list(AssignmentFilter f, Pageable pageable) {
        Specification<Assignment> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.claimId() != null) p.add(cb.equal(root.get("claimId"), f.claimId()));
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            if (f.handlerId() != null) p.add(cb.equal(root.get("assignedHandlerId"), f.handlerId()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return PageResponse.of(assignments.findAll(spec, pageable), this::toResponse);
    }

    // ------------------------------------------------------------------ internals

    private Assignment lockPending(Long id) {
        Assignment a = assignments.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Assignment", id));
        if (a.getStatus() != Assignment.Status.PENDING) {
            throw ApiException.conflict("This assignment has already been decided (" + a.getStatus() + ")");
        }
        return a;
    }

    private Claim lockRoutableClaim(Assignment a) {
        Claim claim = claims.findByIdForUpdate(a.getClaimId()).orElseThrow(() -> ApiException.notFound("Claim", a.getClaimId()));
        requireRoutable(claim);
        return claim;
    }

    private static void requireRoutable(Claim claim) {
        if (!claim.isRoutable()) {
            throw ApiException.conflict("Claim " + claim.getClaimNumber() + " is " + claim.getStatus() + " and cannot be assigned");
        }
    }

    /** Re-applies every hard rule to the locked, current handler record. */
    private void requireLiveEligible(Claim claim, Handler handler, String who) {
        EligibilityDecision d = eligibility.evaluate(claim.toSnapshot(), handler.toSnapshot(),
                eligibility.effectiveRules(settings.activeRuleConfigs()));
        if (!d.eligible()) {
            throw ApiException.rule(who + " (" + handler.getCode() + ") is no longer eligible. Please generate fresh recommendations.",
                    d.failedRules().stream().map(RuleResult::reason).toList());
        }
    }

    private void finalise(Assignment a, Claim claim, Handler handler, Assignment.Status status,
                          Assignment.DecisionType type, Double handlerScore, String overrideReason, String actor) {
        handler.setCurrentWorkload(handler.getCurrentWorkload() + 1);
        ClaimStatus from = claim.getStatus();
        User supervisor = user(actor);
        claim.setStatus(ClaimStatus.ASSIGNED);
        claim.setAssignedHandler(handler);
        claim.setAssignedBy(supervisor);
        claim.setAssignedAt(Instant.now());
        history.record(claim, from, ClaimStatus.ASSIGNED, "Assigned to " + handler.getFullName() + " (" + handler.getCode() + ")"
                + (type == Assignment.DecisionType.OVERRIDE ? " - supervisor override" : " - recommended handler"), supervisor);
        a.setAssignedHandlerId(handler.getId());
        a.setStatus(status);
        a.setDecisionType(type);
        a.setAssignedHandlerScore(handlerScore);
        a.setOverrideReason(overrideReason);
        a.setDecidedBy(actor);
        a.setDecidedAt(Instant.now());
    }

    private User user(String username) {
        return users.findByUsernameIgnoreCase(username).orElse(null);
    }

    private AuditEntry base(String event, Assignment a, Claim claim, Handler finalHandler, String actor) {
        String recommendedCode = a.getRecommendedHandlerId() == null ? null
                : handlers.findById(a.getRecommendedHandlerId()).map(Handler::getCode).orElse(null);
        RecommendationRun run = a.getRunId() == null ? null : runs.findById(a.getRunId()).orElse(null);
        return AuditEntry.of(event, "ASSIGNMENT", String.valueOf(a.getId()), actor)
                .claim(claim.getClaimNumber())
                .recommended(recommendedCode)
                .finalHandler(finalHandler == null ? null : finalHandler.getCode())
                .score(a.getRecommendationScore())
                .eligibility(run == null ? null : run.getEligibleCount() + " eligible, " + run.getIneligibleCount() + " excluded")
                .explanation(a.getExplanation())
                .modelVersion(a.getModelVersion())
                .detail("runId", a.getRunId())
                .detail("rulesApplied", run == null ? null : json.read(run.getRulesJson(),
                        new TypeReference<List<RoutingDtos.RuleAppliedDto>>() {
                        }));
    }

    private AssignmentDtos.AssignmentResponse toResponse(Assignment a) {
        String claimNumber = claims.findById(a.getClaimId()).map(Claim::getClaimNumber).orElse(null);
        return new AssignmentDtos.AssignmentResponse(a.getId(), a.getClaimId(), claimNumber, a.getRunId(),
                ref(a.getRecommendedHandlerId()), ref(a.getAssignedHandlerId()), a.getStatus().name(),
                a.getDecisionType() == null ? null : a.getDecisionType().name(), a.getRecommendationScore(),
                a.getAssignedHandlerScore(), a.getExplanation(), a.getOverrideReason(), a.getReviewReason(),
                a.getFallbackReason(), a.getCreatedBy(), a.getDecidedBy(), a.getDecidedAt(), a.getCreatedAt());
    }

    private AssignmentDtos.HandlerRef ref(Long handlerId) {
        if (handlerId == null) return null;
        return handlers.findById(handlerId).map(h -> new AssignmentDtos.HandlerRef(h.getId(), h.getCode(), h.getFullName()))
                .orElse(null);
    }
}
